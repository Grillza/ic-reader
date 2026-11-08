package io.github.grillza.icreader.ui

import android.app.Application
import android.net.Uri
import android.nfc.Tag
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.grillza.icreader.IcReaderApp
import io.github.grillza.icreader.data.CardFamily
import io.github.grillza.icreader.data.CardType
import io.github.grillza.icreader.data.SavedCard
import io.github.grillza.icreader.data.ScanResult
import io.github.grillza.icreader.data.Station
import io.github.grillza.icreader.data.StationRef
import io.github.grillza.icreader.nfc.CardReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

sealed interface Screen {
    data object Cards : Screen
    data class Detail(val cardId: String) : Screen
    data object Icons : Screen

    /** A scanned card that isn't saved yet; shown read-only with a "Save card" action. */
    data object Preview : Screen
}

sealed interface ScanStatus {
    data object Idle : ScanStatus
    data object Reading : ScanStatus
    data class Failed(val message: String) : ScanStatus
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val container = app as IcReaderApp
    private val repo = container.repository
    private val stations = container.stations
    private val icons = container.icons

    val cards: StateFlow<List<SavedCard>> = repo.cards

    private val backStack = MutableStateFlow<List<Screen>>(listOf(Screen.Cards))
    val stack: StateFlow<List<Screen>> = backStack.asStateFlow()

    private val _scan = MutableStateFlow<ScanStatus>(ScanStatus.Idle)
    val scan: StateFlow<ScanStatus> = _scan.asStateFlow()

    /** A card that hasn't been saved yet, waiting for the user to confirm its type. */
    private val _pendingCard = MutableStateFlow<ScanResult?>(null)
    val pendingCard: StateFlow<ScanResult?> = _pendingCard.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _iconId = MutableStateFlow(APP_ICON_UNKNOWN)
    val iconId: StateFlow<String> = _iconId.asStateFlow()

    private val _stationsReady = MutableStateFlow(false)
    val stationsReady: StateFlow<Boolean> = _stationsReady.asStateFlow()

    init {
        viewModelScope.launch { repo.load() }
        viewModelScope.launch(Dispatchers.IO) {
            _iconId.value = icons.current().id
            stations.warmUp()
            _stationsReady.value = true
        }
    }

    fun navigate(screen: Screen) = backStack.update { it + screen }

    /** Returns false when already at the root, so the activity can finish. */
    fun back(): Boolean {
        if (backStack.value.lastOrNull() == Screen.Preview) {
            dismissPending()
            return true
        }
        if (backStack.value.size <= 1) return false
        backStack.update { it.dropLast(1) }
        return true
    }

    fun onTag(tag: Tag) {
        if (_scan.value == ScanStatus.Reading) return
        _scan.value = ScanStatus.Reading
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val read = CardReader.read(tag)
                    if (read.family == CardFamily.TRANSIT) read.copy(suggestedType = stations.suggestTransitType(read.transactions)) else read
                }
                _scan.value = ScanStatus.Idle
                if (repo.get(result.idm) == null) {
                    _pendingCard.value = result
                    backStack.value = listOf(Screen.Cards, Screen.Preview)
                } else {
                    val saved = repo.saveScan(result)
                    showCard(saved.card.id)
                    _message.value = buildString {
                        append("${saved.card.title} updated")
                        if (saved.balanceChange != 0) append(" · ${formatSigned(saved.balanceChange)}")
                        if (saved.newTransactions > 0) append(" · ${saved.newTransactions} new")
                    }
                }
            } catch (e: IOException) {
                _scan.value = ScanStatus.Failed(
                    e.message?.takeIf { e is io.github.grillza.icreader.nfc.UnsupportedCardException }
                        ?: "Lost the card. Hold it still against the back of the phone and try again.",
                )
            } catch (e: SecurityException) {
                _scan.value = ScanStatus.Failed("The card moved away. Tap it again.")
            }
        }
    }

    /** Debug builds only: feeds a made-up Suica scan through the normal new-card flow. */
    val demoAvailable: Boolean =
        (app.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0

    fun loadDemoCard() {
        if (!demoAvailable) return
        viewModelScope.launch {
            val scan = withContext(Dispatchers.Default) { DemoData.scan() }
            val result = scan.copy(suggestedType = withContext(Dispatchers.IO) { stations.suggestTransitType(scan.transactions) })
            if (repo.get(result.idm) == null) {
                _pendingCard.value = result
                backStack.value = listOf(Screen.Cards, Screen.Preview)
            } else {
                repo.saveScan(result)
                showCard(result.idm)
            }
        }
    }

    fun clearScanError() {
        if (_scan.value is ScanStatus.Failed) _scan.value = ScanStatus.Idle
    }

    fun confirmPending(type: CardType, nickname: String, useAsIcon: Boolean) {
        val pending = _pendingCard.value ?: return
        viewModelScope.launch {
            val saved = repo.saveScan(pending, type, nickname.trim().ifEmpty { null })
            _pendingCard.value = null
            if (useAsIcon) selectIcon(type.id)
            showCard(saved.card.id)
            _message.value = "${saved.card.title} saved"
        }
    }

    /** Discards an unsaved card (leaving the preview). */
    fun dismissPending() {
        _pendingCard.value = null
        backStack.update { stack -> stack.filterNot { it == Screen.Preview }.ifEmpty { listOf(Screen.Cards) } }
    }

    /** The pending scan as a card, for showing it before it is saved. */
    fun previewCard(scan: ScanResult): SavedCard = SavedCard(
        id = scan.idm,
        family = scan.family,
        typeId = (scan.suggestedType ?: CardType.defaultFor(scan.family)).id,
        balance = scan.balance,
        addedAt = 0,
        lastScanned = System.currentTimeMillis(),
        transactions = io.github.grillza.icreader.data.HistoryMerge.merge(emptyList(), scan.transactions),
    )

    fun updateCard(id: String, type: CardType, nickname: String) = viewModelScope.launch {
        repo.edit(id) { it.copy(typeId = type.id, nickname = nickname.trim().ifEmpty { null }) }
    }

    fun setCustomImage(id: String, uri: Uri?) = viewModelScope.launch { repo.setCustomImage(id, uri) }

    fun deleteCard(id: String) = viewModelScope.launch {
        repo.delete(id)
        backStack.update { stack -> stack.filterNot { it is Screen.Detail && it.cardId == id }.ifEmpty { listOf(Screen.Cards) } }
        _message.value = "Card removed"
    }

    fun moveCard(id: String, delta: Int) = viewModelScope.launch { repo.move(id, delta) }

    fun selectIcon(id: String) {
        if (id == _iconId.value) return
        icons.request(id)
        _iconId.value = id
        _message.value = "App icon will change when you leave the app."
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun station(ref: StationRef): Station? = if (_stationsReady.value) stations.lookup(ref) else null

    fun imageFile(name: String) = repo.imageFile(name)

    private fun showCard(id: String) {
        _pendingCard.value = null
        backStack.value = listOf(Screen.Cards, Screen.Detail(id))
    }

    companion object {
        const val APP_ICON_UNKNOWN = ""
    }
}
