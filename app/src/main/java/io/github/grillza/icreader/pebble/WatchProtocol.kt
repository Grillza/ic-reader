package io.github.grillza.icreader.pebble

import java.io.ByteArrayOutputStream
import java.text.Normalizer
import java.util.UUID

/**
 * Messages exchanged with the IC Reader watch app (watchapp/ in this repository; see
 * watchapp/src/c/protocol.h and codec.h for the watch side). Pure functions, unit tested.
 */
object WatchProtocol {
    val APP_UUID: UUID = UUID.fromString("c54c8b91-69f7-45b4-85b8-02522fc73be5")
    const val VERSION = 1

    // Dictionary keys: the Pebble build numbers watchapp/package.json's messageKeys from 10000.
    const val KEY_TYPE = 10000u
    const val KEY_INDEX = 10001u
    const val KEY_COUNT = 10002u
    const val KEY_OFFSET = 10003u
    const val KEY_TOTAL = 10004u
    const val KEY_WIDTH = 10005u
    const val KEY_HEIGHT = 10006u
    const val KEY_DATA = 10007u
    const val KEY_VERSION = 10008u

    const val MSG_HELLO = 1
    const val MSG_HISTORY_REQUEST = 2
    const val MSG_CARDS = 10
    const val MSG_ART = 11
    const val MSG_HISTORY = 12

    const val MAX_CARDS = 16
    const val MAX_HISTORY = 50
    const val FLAG_TRANSIT = 1
    const val TX_FLAG_CREDIT = 1

    // Field sizes on the watch, less one for the terminator.
    private const val NAME_BYTES = 31
    private const val TITLE_BYTES = 47
    private const val WHEN_BYTES = 23
    private const val AMOUNT_BYTES = 15

    /** Dictionary header and the tuples around Data, with room to spare. */
    fun messageBudget(inbox: Int): Int = (inbox - 64).coerceAtLeast(64)

    fun encodeCard(balance: Int, scannedSeconds: Long, artHash: Long, flags: Int, color: Int, name: String): ByteArray {
        val nameBytes = utf8Prefix(name, NAME_BYTES)
        val out = ByteArrayOutputStream()
        out.le32(balance.toLong())
        out.le32(scannedSeconds)
        out.le32(artHash)
        out.write(flags)
        out.write(color)
        out.write(nameBytes.size)
        out.write(nameBytes)
        return out.toByteArray()
    }

    fun encodeTx(kind: Int, flags: Int, title: String, whenText: String, amount: String): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(kind)
        out.write(flags)
        for ((text, max) in listOf(title to TITLE_BYTES, whenText to WHEN_BYTES, amount to AMOUNT_BYTES)) {
            val bytes = utf8Prefix(text, max)
            out.write(bytes.size)
            out.write(bytes)
        }
        return out.toByteArray()
    }

    /**
     * Groups records into messages of at most [budget] bytes without splitting one. Returns each
     * message's index of its first record and its bytes.
     */
    fun pack(records: List<ByteArray>, budget: Int): List<Pair<Int, ByteArray>> {
        val out = mutableListOf<Pair<Int, ByteArray>>()
        var first = 0
        val current = ByteArrayOutputStream()
        records.forEachIndexed { i, r ->
            if (current.size() > 0 && current.size() + r.size > budget) {
                out += first to current.toByteArray()
                current.reset()
                first = i
            }
            current.write(r)
        }
        if (current.size() > 0) out += first to current.toByteArray()
        return out
    }

    /** The longest prefix of [s] that fits in [maxBytes] of UTF-8, never splitting a character. */
    fun utf8Prefix(s: String, maxBytes: Int): ByteArray {
        val out = ByteArrayOutputStream()
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            val bytes = String(Character.toChars(cp)).toByteArray(Charsets.UTF_8)
            if (out.size() + bytes.size > maxBytes) break
            out.write(bytes)
            i += Character.charCount(cp)
        }
        return out.toByteArray()
    }

    /**
     * Text the watch's fonts can show: they cover Latin-1, not Japanese or macrons. Accents
     * outside Latin-1 are dropped (Ningyōchō -> Ningyocho); anything else becomes '?'.
     */
    fun watchText(s: String): String = buildString {
        for (ch in s) {
            when {
                ch.code < 0x100 -> append(ch)
                ch == '→' -> append('>')
                ch == '−' || ch == '–' || ch == '—' -> append('-')
                else -> {
                    val base = Normalizer.normalize(ch.toString(), Normalizer.Form.NFD).filter { it.code < 0x100 }
                    append(base.ifEmpty { "?" })
                }
            }
        }
    }

    /** True when [watchText] would lose characters from [s]. */
    fun needsFallback(s: String): Boolean = watchText(s).count { it == '?' } > s.count { it == '?' }

    /** A Pebble colour (GColor8: 0b11rrggbb) for an ARGB colour; fully transparent below half alpha. */
    fun pebbleColor(argb: Int): Int {
        if ((argb ushr 24) < 128) return 0
        fun level(v: Int) = (v + 42) / 85
        val r = level((argb shr 16) and 0xFF)
        val g = level((argb shr 8) and 0xFF)
        val b = level(argb and 0xFF)
        return 0xC0 or (r shl 4) or (g shl 2) or b
    }

    /** Pebble-colour pixels, row by row, as (run, colour) pairs. See codec_decode_art on the watch. */
    fun rle(argbPixels: IntArray): ByteArray {
        val out = ByteArrayOutputStream()
        var i = 0
        while (i < argbPixels.size) {
            val c = pebbleColor(argbPixels[i])
            var run = 1
            while (run < 255 && i + run < argbPixels.size && pebbleColor(argbPixels[i + run]) == c) run++
            out.write(run)
            out.write(c)
            i += run
        }
        return out.toByteArray()
    }

    private fun ByteArrayOutputStream.le32(v: Long) {
        for (shift in 0 until 32 step 8) write(((v shr shift) and 0xFF).toInt())
    }
}
