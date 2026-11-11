package io.github.grillza.icreader.pebble

import android.content.Context
import android.content.pm.PackageManager
import android.text.format.DateFormat
import android.util.Log
import androidx.core.content.edit
import io.github.grillza.icreader.IcReaderApp
import io.github.grillza.icreader.data.CardFamily
import io.github.grillza.icreader.data.SavedCard
import io.github.grillza.icreader.data.Station
import io.github.grillza.icreader.data.StationLanguage
import io.github.grillza.icreader.data.StationRef
import io.github.grillza.icreader.data.Transaction
import io.github.grillza.icreader.data.localized
import io.github.grillza.icreader.ui.describeTransaction
import io.github.grillza.icreader.ui.formatYen
import io.rebble.pebblekit2.client.DefaultPebbleAndroidAppPicker
import io.rebble.pebblekit2.client.DefaultPebbleSender
import io.rebble.pebblekit2.common.model.PebbleDictionary
import io.rebble.pebblekit2.common.model.PebbleDictionaryItem
import io.rebble.pebblekit2.common.model.TransmissionResult
import io.rebble.pebblekit2.common.model.WatchIdentifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps the IC Reader watch app up to date. The watch says hello when it opens (with the size it
 * wants the card art and how big a message it takes); the phone answers with the card list and
 * any art the watch doesn't have, sends a card's history when asked, and sends the list again
 * when a scan changes it while the watch app is open.
 */
class WatchSync(private val context: Context) {

    private class Session(val width: Int, val height: Int, val inbox: Int, val maxArt: Int, val mono: Boolean) {
        /** Art hashes this watch already has (since its hello). */
        val sentArt = mutableSetOf<Long>()
    }

    private val app get() = context.applicationContext as IcReaderApp
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sendLock = Mutex()
    private val loadLock = Mutex()
    private var loaded = false
    private val sessions = ConcurrentHashMap<WatchIdentifier, Session>()
    private val sender by lazy { DefaultPebbleSender(context.applicationContext) }
    /** Rendered art by card, so an unchanged card isn't drawn again. */
    private val artCache = ConcurrentHashMap<String, Pair<String, WatchArt?>>()

    private val prefs get() = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(prefs.getBoolean(PREF_ENABLED, false))
    /** Pebble watch integration is switched on (Settings). Off by default. */
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    /**
     * Switches the integration on or off. Off also disables [PebbleListenerService], so the
     * Pebble app can't start IC Reader or pass it messages.
     */
    fun setEnabled(on: Boolean) {
        prefs.edit { putBoolean(PREF_ENABLED, on) }
        _enabled.value = on
        context.packageManager.setComponentEnabledSetting(
            android.content.ComponentName(context, PebbleListenerService::class.java),
            if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
        if (!on) {
            sessions.clear()
            _watchOpen.value = false
        }
    }

    /**
     * Switches the integration on if the Pebble app is installed and the user has never set the
     * switch either way. Once the switch has a value (from here or from Settings) this does nothing,
     * so turning it off sticks.
     */
    fun autoEnableIfPebbleInstalled() {
        if (prefs.contains(PREF_ENABLED)) return
        scope.launch {
            val installed = runCatching {
                DefaultPebbleAndroidAppPicker.getInstance(context.applicationContext).getAllEligibleApps().isNotEmpty()
            }.getOrDefault(false)
            if (installed && !prefs.contains(PREF_ENABLED)) setEnabled(true)
        }
    }

    private val _watchOpen = MutableStateFlow(false)
    /** True while the watch app is open on a watch that has said hello. */
    val watchOpen: StateFlow<Boolean> = _watchOpen.asStateFlow()

    init {
        @OptIn(FlowPreview::class)
        scope.launch {
            app.repository.cards.drop(1).distinctUntilChanged().debounce(500).collect {
                for ((watch, session) in sessions) sendCards(watch, session)
            }
        }
    }

    fun onHello(watch: WatchIdentifier, data: PebbleDictionary) {
        if (!_enabled.value) return
        val width = data.int(WatchProtocol.KEY_WIDTH) ?: return
        val height = data.int(WatchProtocol.KEY_HEIGHT) ?: return
        if (width !in 8..400 || height !in 8..400) return
        val session = Session(
            width = width,
            height = height,
            inbox = data.int(WatchProtocol.KEY_TOTAL) ?: 1024,
            maxArt = data.int(WatchProtocol.KEY_COUNT) ?: 2048,
            mono = data.int(WatchProtocol.KEY_OFFSET) == 1,
        )
        sessions[watch] = session
        _watchOpen.value = true
        scope.launch { sendCards(watch, session) }
    }

    fun onHistoryRequest(watch: WatchIdentifier, data: PebbleDictionary) {
        if (!_enabled.value) return
        val index = data.int(WatchProtocol.KEY_INDEX) ?: return
        val session = sessions[watch] ?: return
        scope.launch { sendHistory(watch, session, index) }
    }

    fun onAppClosed(watch: WatchIdentifier) {
        sessions.remove(watch)
        _watchOpen.value = sessions.isNotEmpty()
    }

    /** Opens IC Reader on the connected watch. False if the Pebble app or the watch can't be reached. */
    suspend fun openOnWatch(): Boolean {
        val result = sender.startAppOnTheWatch(WatchProtocol.APP_UUID, null) ?: return false
        return result.values.any { it == TransmissionResult.Success }
    }

    private suspend fun cards(): List<SavedCard> {
        loadLock.withLock {
            if (!loaded) {
                // Started by the Pebble app with no screen open: nothing has loaded the cards yet.
                if (app.repository.cards.value.isEmpty()) app.repository.load()
                loaded = true
            }
        }
        return app.repository.cards.value.take(WatchProtocol.MAX_CARDS)
    }

    private fun art(card: SavedCard, session: Session): WatchArt? {
        val key = listOf(card.typeId, card.customImage, session.width, session.height, session.maxArt, session.mono).joinToString("|")
        artCache[card.id]?.let { (cachedKey, art) -> if (cachedKey == key) return art }
        val photo = card.customImage?.let { app.repository.imageFile(it) }?.takeIf { it.exists() }
        val art = runCatching {
            WatchArtRenderer.render(card.type, photo, session.width, session.height, session.maxArt, session.mono)
        }.onFailure { Log.w(TAG, "card art failed", it) }.getOrNull()
        artCache[card.id] = key to art
        return art
    }

    private fun watchName(card: SavedCard): String = WatchProtocol.watchText(card.title)

    /** Station names on the watch: Settings > Pebble watch, English unless chosen otherwise. */
    private fun stationLanguage(): StationLanguage =
        runCatching {
            StationLanguage.valueOf(
                context.getSharedPreferences("settings", Context.MODE_PRIVATE).getString(PREF_STATION_LANGUAGE, null)!!,
            )
        }.getOrDefault(StationLanguage.ENGLISH)

    private suspend fun sendCards(watch: WatchIdentifier, session: Session) = sendLock.withLock {
        val cards = cards()
        val arts = cards.map { art(it, session) }
        val records = cards.mapIndexed { i, card ->
            WatchProtocol.encodeCard(
                balance = card.balance,
                scannedSeconds = card.lastScanned / 1000,
                artHash = arts[i]?.hash ?: 0,
                flags = if (card.family == CardFamily.TRANSIT) WatchProtocol.FLAG_TRANSIT else 0,
                color = WatchArtRenderer.placeholderColor(card.type),
                name = watchName(card),
            )
        }
        val list = records.fold(ByteArray(0)) { acc, r -> acc + r }
        if (!send(watch, mapOf(
                WatchProtocol.KEY_TYPE to PebbleDictionaryItem.UInt8(WatchProtocol.MSG_CARDS),
                WatchProtocol.KEY_COUNT to PebbleDictionaryItem.UInt8(cards.size),
                WatchProtocol.KEY_DATA to PebbleDictionaryItem.Bytes(list),
            ))
        ) return@withLock
        val budget = WatchProtocol.messageBudget(session.inbox)
        arts.forEachIndexed { index, art ->
            if (art == null || art.hash in session.sentArt) return@forEachIndexed
            var offset = 0
            while (offset < art.rle.size) {
                val end = minOf(art.rle.size, offset + budget)
                val ok = send(watch, mapOf(
                    WatchProtocol.KEY_TYPE to PebbleDictionaryItem.UInt8(WatchProtocol.MSG_ART),
                    WatchProtocol.KEY_INDEX to PebbleDictionaryItem.UInt8(index),
                    WatchProtocol.KEY_OFFSET to PebbleDictionaryItem.UInt16(offset),
                    WatchProtocol.KEY_TOTAL to PebbleDictionaryItem.UInt16(art.rle.size),
                    WatchProtocol.KEY_DATA to PebbleDictionaryItem.Bytes(art.rle.copyOfRange(offset, end)),
                ))
                if (!ok) return@withLock
                offset = end
            }
            session.sentArt += art.hash
        }
    }

    private suspend fun sendHistory(watch: WatchIdentifier, session: Session, index: Int) = sendLock.withLock {
        val card = cards().getOrNull(index) ?: return@withLock
        val records = card.transactions.take(WatchProtocol.MAX_HISTORY).map { historyRecord(card, it) }
        val budget = WatchProtocol.messageBudget(session.inbox)
        val messages = WatchProtocol.pack(records, budget).ifEmpty { listOf(0 to ByteArray(0)) }
        for ((first, bytes) in messages) {
            val ok = send(watch, mapOf(
                WatchProtocol.KEY_TYPE to PebbleDictionaryItem.UInt8(WatchProtocol.MSG_HISTORY),
                WatchProtocol.KEY_INDEX to PebbleDictionaryItem.UInt8(index),
                WatchProtocol.KEY_OFFSET to PebbleDictionaryItem.UInt16(first),
                WatchProtocol.KEY_TOTAL to PebbleDictionaryItem.UInt16(records.size),
                WatchProtocol.KEY_DATA to PebbleDictionaryItem.Bytes(bytes),
            ))
            if (!ok) return@withLock
        }
    }

    private fun historyRecord(card: SavedCard, tx: Transaction): ByteArray {
        val language = stationLanguage()
        val title = describeTransaction(tx, card.family) { ref -> stationForWatch(ref, language) }.first
        val time = tx.dateTime?.toLocalTime()?.let { t ->
            if (DateFormat.is24HourFormat(context)) "%d:%02d".format(t.hour, t.minute)
            else "%d:%02d %s".format(if (t.hour % 12 == 0) 12 else t.hour % 12, t.minute, if (t.hour < 12) "AM" else "PM")
        }
        val whenText = listOfNotNull(tx.date.format(DAY), time).joinToString(" ")
        val amount = tx.amount?.let { a ->
            when {
                a > 0 -> "+" + formatYen(a)
                a < 0 -> "-" + formatYen(-a)
                else -> formatYen(0)
            }
        } ?: ("Bal " + formatYen(tx.balance))
        return WatchProtocol.encodeTx(
            kind = tx.kind.ordinal,
            flags = if ((tx.amount ?: 0) > 0) WatchProtocol.TX_FLAG_CREDIT else 0,
            title = WatchProtocol.watchText(title),
            whenText = whenText,
            amount = amount,
        )
    }

    /** The station in the chosen language; one without an English name stays in Japanese. */
    private fun stationForWatch(ref: StationRef, language: StationLanguage): Station? =
        app.stations.lookup(ref)?.localized(language)

    /** Sends one message, retrying a NACK or timeout twice. False when it couldn't be delivered. */
    private suspend fun send(watch: WatchIdentifier, data: PebbleDictionary): Boolean {
        repeat(3) { attempt ->
            val result = runCatching { sender.sendDataToPebble(WatchProtocol.APP_UUID, data, listOf(watch)) }
                .onFailure { Log.w(TAG, "send failed", it) }
                .getOrNull()
                ?: return false // Pebble app not reachable
            when (val outcome = result[watch]) {
                TransmissionResult.Success -> return true
                TransmissionResult.FailedWatchNotConnected, TransmissionResult.FailedDifferentAppOpen -> {
                    onAppClosed(watch)
                    return false
                }
                TransmissionResult.FailedNoPermissions -> {
                    Log.w(TAG, "the watch app doesn't list this package as its companion")
                    return false
                }
                else -> {
                    Log.i(TAG, "send attempt ${attempt + 1}: $outcome")
                    kotlinx.coroutines.delay(200L * (attempt + 1))
                }
            }
        }
        return false
    }

    private fun PebbleDictionary.int(key: UInt): Int? = when (val v = this[key]) {
        is PebbleDictionaryItem.UInt32 -> v.value.toInt()
        is PebbleDictionaryItem.Int32 -> v.value
        is PebbleDictionaryItem.UInt16 -> v.value.toInt()
        is PebbleDictionaryItem.Int16 -> v.value.toInt()
        is PebbleDictionaryItem.UInt8 -> v.value.toInt()
        is PebbleDictionaryItem.Int8 -> v.value.toInt()
        else -> null
    }

    companion object {
        private const val TAG = "WatchSync"
        /** In the app's "settings" preferences. */
        const val PREF_STATION_LANGUAGE = "watch_station_language"
        const val PREF_ENABLED = "pebble_enabled"
        private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    }
}
