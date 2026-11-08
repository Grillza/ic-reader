package io.github.grillza.icreader.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
private data class Store(val version: Int = 1, val cards: List<SavedCard> = emptyList())

/** Outcome of saving a scan. */
data class ScanSaved(val card: SavedCard, val isNew: Boolean, val newTransactions: Int, val balanceChange: Int)

/** Saved cards, kept in one JSON file in app storage (a handful of cards, a few hundred records). */
class CardRepository(private val context: Context) {

    private val file = File(context.filesDir, "cards.json")
    private val imageDir = File(context.filesDir, "card_images").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val mutex = Mutex()

    private val _cards = MutableStateFlow<List<SavedCard>>(emptyList())
    val cards: StateFlow<List<SavedCard>> = _cards.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock {
            _cards.value = runCatching { json.decodeFromString<Store>(file.readText()).cards }.getOrDefault(emptyList())
        }
    }

    fun get(id: String): SavedCard? = _cards.value.firstOrNull { it.id == id }

    /** Merges a scan into the saved card with the same IDm, or adds a new card. */
    suspend fun saveScan(scan: ScanResult, type: CardType? = null, nickname: String? = null): ScanSaved =
        update { cards ->
            val now = System.currentTimeMillis()
            val existing = cards.firstOrNull { it.id == scan.idm }
            if (existing == null) {
                val card = SavedCard(
                    id = scan.idm,
                    family = scan.family,
                    typeId = (type ?: scan.suggestedType ?: CardType.defaultFor(scan.family)).id,
                    nickname = nickname,
                    balance = scan.balance,
                    addedAt = now,
                    lastScanned = now,
                    transactions = HistoryMerge.merge(emptyList(), scan.transactions),
                    cardInfo = scan.cardInfo,
                )
                (cards + card) to ScanSaved(card, true, scan.transactions.size, 0)
            } else {
                val card = existing.copy(
                    balance = scan.balance,
                    lastScanned = now,
                    transactions = HistoryMerge.merge(existing.transactions, scan.transactions),
                    cardInfo = scan.cardInfo.ifEmpty { existing.cardInfo },
                )
                cards.map { if (it.id == card.id) card else it } to
                    ScanSaved(card, false, HistoryMerge.newCount(existing.transactions, scan.transactions), scan.balance - existing.balance)
            }
        }

    suspend fun edit(id: String, transform: (SavedCard) -> SavedCard) {
        update { cards -> cards.map { if (it.id == id) transform(it) else it } to Unit }
    }

    suspend fun delete(id: String) {
        val removed = update { cards -> cards.filterNot { it.id == id } to cards.firstOrNull { it.id == id } }
        removed?.customImage?.let { File(imageDir, it).delete() }
    }

    suspend fun move(id: String, delta: Int) {
        update { cards ->
            val i = cards.indexOfFirst { it.id == id }
            val j = i + delta
            if (i < 0 || j !in cards.indices) return@update cards to Unit
            cards.toMutableList().apply { add(j, removeAt(i)) } to Unit
        }
    }

    /** Copies (and downsizes) a picked photo into app storage and attaches it to the card. */
    suspend fun setCustomImage(id: String, uri: Uri?) {
        val old = get(id)?.customImage
        val name = if (uri == null) null else withContext(Dispatchers.IO) {
            val bitmap = decodeScaled(uri, 1400) ?: return@withContext null
            val fileName = "$id-${System.currentTimeMillis()}.jpg"
            File(imageDir, fileName).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            fileName
        }
        if (uri != null && name == null) return
        edit(id) { it.copy(customImage = name) }
        if (old != null && old != name) File(imageDir, old).delete()
    }

    fun imageFile(name: String): File = File(imageDir, name)

    private fun decodeScaled(uri: Uri, maxSide: Int): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxSide || bounds.outHeight / (sample * 2) >= maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }

    private suspend fun <T> update(block: (List<SavedCard>) -> Pair<List<SavedCard>, T>): T =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val (cards, result) = block(_cards.value)
                if (cards !== _cards.value) {
                    val tmp = File(file.parentFile, "cards.json.tmp")
                    tmp.writeText(json.encodeToString(Store(cards = cards)))
                    tmp.renameTo(file)
                    _cards.value = cards
                }
                result
            }
        }
}
