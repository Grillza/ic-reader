package io.github.grillza.icreader

import io.github.grillza.icreader.pebble.WatchProtocol
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchProtocolTest {

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test
    fun cardRecordMatchesTheWatchLayout() {
        // Same record as test_cards in watchapp/tests/test_main.c.
        val record = WatchProtocol.encodeCard(1234, 0x01020304, 0xAABBCCDD, WatchProtocol.FLAG_TRANSIT, 0x1D, "Suica")
        assertArrayEquals(
            bytes(0xD2, 0x04, 0, 0, 4, 3, 2, 1, 0xDD, 0xCC, 0xBB, 0xAA, 1, 0x1D, 5, 'S'.code, 'u'.code, 'i'.code, 'c'.code, 'a'.code),
            record,
        )
        val negative = WatchProtocol.encodeCard(-10, 0, 0, 0, 0x3F, "")
        assertArrayEquals(bytes(0xF6, 0xFF, 0xFF, 0xFF, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x3F, 0), negative)
    }

    @Test
    fun longNamesAreCutOnACharacterBoundary() {
        val record = WatchProtocol.encodeCard(0, 0, 0, 0, 0, "あ".repeat(20))
        assertEquals(30, record[14].toInt())  // 10 three-byte characters fit in 31 bytes
        assertEquals(15 + 30, record.size)
    }

    @Test
    fun historyRecordHasThreeLengthPrefixedStrings() {
        val record = WatchProtocol.encodeTx(3, WatchProtocol.TX_FLAG_CREDIT, "Charge", "3 Nov.", "+¥5")
        assertArrayEquals(
            bytes(3, 1, 6, 'C'.code, 'h'.code, 'a'.code, 'r'.code, 'g'.code, 'e'.code, 6, '3'.code, ' '.code, 'N'.code, 'o'.code, 'v'.code, '.'.code,
                4, '+'.code, 0xC2, 0xA5, '5'.code),
            record,
        )
    }

    @Test
    fun packKeepsRecordsWholeAndRemembersWhereEachMessageStarts() {
        val records = List(5) { ByteArray(40) { _ -> it.toByte() } }
        val messages = WatchProtocol.pack(records, 100)
        assertEquals(listOf(0, 2, 4), messages.map { it.first })
        assertEquals(listOf(80, 80, 40), messages.map { it.second.size })
        assertTrue(WatchProtocol.pack(emptyList(), 100).isEmpty())
        // A record bigger than the budget still goes, on its own.
        assertEquals(1, WatchProtocol.pack(listOf(ByteArray(150)), 100).size)
    }

    @Test
    fun watchTextKeepsJapaneseAndFoldsMacrons() {
        assertEquals("Ningyocho > Oshiage", WatchProtocol.watchText("Ningyōchō → Oshiage"))
        assertEquals("¥1,234 - café", WatchProtocol.watchText("¥1,234 − café"))
        assertEquals("表参道 > 銀座", WatchProtocol.watchText("表参道 → 銀座"))
        assertEquals("Omotesando (表参道)", WatchProtocol.watchText("Omotesando (表参道)"))
    }

    @Test
    fun coloursMapToTheNearestPebbleColour() {
        assertEquals(0xFF, WatchProtocol.pebbleColor(0xFFFFFFFF.toInt()))
        assertEquals(0xC0, WatchProtocol.pebbleColor(0xFF000000.toInt()))
        assertEquals(0xD9, WatchProtocol.pebbleColor(0xFF62BD2F.toInt()))  // Suica green -> 55AA55
        assertEquals(0xEA, WatchProtocol.pebbleColor(0xFFC5C9CE.toInt()))  // silver -> light grey
        assertEquals(0, WatchProtocol.pebbleColor(0x40FFFFFF))             // mostly transparent
    }

    @Test
    fun rleRunsCapAt255AndCoverEveryPixel() {
        val pixels = IntArray(300) { 0xFFFFFFFF.toInt() } + IntArray(2) { 0xFF000000.toInt() }
        assertArrayEquals(bytes(255, 0xFF, 45, 0xFF, 2, 0xC0), WatchProtocol.rle(pixels))
        // Colours that land on the same Pebble colour join one run.
        assertArrayEquals(bytes(2, 0xFF), WatchProtocol.rle(intArrayOf(0xFFFFFFFF.toInt(), 0xFFF0F0F0.toInt())))
    }
}
