package io.github.grillza.icreader

import io.github.grillza.icreader.data.HistoryMerge
import io.github.grillza.icreader.data.StationDb
import io.github.grillza.icreader.data.StationRef
import io.github.grillza.icreader.data.localized
import io.github.grillza.icreader.data.TxKind
import io.github.grillza.icreader.nfc.EdyParser
import io.github.grillza.icreader.nfc.Felica
import io.github.grillza.icreader.nfc.FelicaTransport
import io.github.grillza.icreader.nfc.CardReader
import io.github.grillza.icreader.nfc.TransitParser
import io.github.grillza.icreader.nfc.hexToBytes
import io.github.grillza.icreader.data.CardFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import io.github.grillza.icreader.data.CardType

class ParserTest {

    /** Builds a transit history block. */
    private fun transit(
        terminal: Int, process: Int, y: Int, m: Int, d: Int,
        b6: Int = 0, b7: Int = 0, b8: Int = 0, b9: Int = 0, balance: Int, seq: Int, region: Int = 0,
    ): ByteArray {
        val date = ((y - 2000) shl 9) or (m shl 5) or d
        return byteArrayOf(
            terminal.toByte(), process.toByte(), 0, 0,
            (date shr 8).toByte(), date.toByte(),
            b6.toByte(), b7.toByte(), b8.toByte(), b9.toByte(),
            balance.toByte(), (balance shr 8).toByte(), 0,
            (seq shr 8).toByte(), seq.toByte(), region.toByte(),
        )
    }

    @Test
    fun parsesTrainRecord() {
        val tx = TransitParser.history(transit(0x16, 0x01, 2026, 11, 8, 0x25, 0x0A, 0x25, 0x14, balance = 1234, seq = 0x0102))!!
        assertEquals(TxKind.TRAIN, tx.kind)
        assertEquals(2026, tx.year)
        assertEquals(11, tx.month)
        assertEquals(8, tx.day)
        assertEquals(1234, tx.balance)
        assertEquals(0x0102, tx.seq)
        assertEquals(StationRef(0, 0x25, 0x0A), tx.entry)
        assertEquals(StationRef(0, 0x25, 0x14), tx.exit)
    }

    @Test
    fun privateLineUsesRegionBits() {
        // Line 0xE0 with entry region 2 (Kansai) in bits 7-6.
        val tx = TransitParser.history(transit(0x16, 0x01, 2026, 1, 2, 0xE0, 0x01, 0xE0, 0x05, balance = 0, seq = 1, region = 0x80 or 0x20))!!
        assertEquals(2, tx.entry!!.area)
        assertEquals(2, tx.exit!!.area)
    }

    @Test
    fun parsesPurchaseTime() {
        val t = (13 shl 11) or (45 shl 5) or 15 // 13:45:30
        val tx = TransitParser.history(transit(0xC7, 0x46, 2026, 3, 4, t shr 8, t and 0xFF, balance = 500, seq = 9))!!
        assertEquals(TxKind.PURCHASE, tx.kind)
        assertEquals(13 * 3600 + 45 * 60 + 30, tx.secondOfDay)
        assertNull(tx.entry)
    }

    @Test
    fun emptyBlockIsSkipped() {
        assertNull(TransitParser.history(ByteArray(16)))
    }

    @Test
    fun attributeBalanceIsLittleEndian() {
        val block = ByteArray(16).also { it[11] = 0x39; it[12] = 0x30 }
        assertEquals(12345, TransitParser.balance(block))
    }

    @Test
    fun mergeComputesAmountsAndDeduplicates() {
        val a = TransitParser.history(transit(0x16, 0x01, 2026, 1, 1, balance = 1000, seq = 10))!!
        val b = TransitParser.history(transit(0x16, 0x01, 2026, 1, 2, balance = 790, seq = 11))!!
        val c = TransitParser.history(transit(0x09, 0x02, 2026, 1, 3, balance = 2790, seq = 12))!!
        val first = HistoryMerge.merge(emptyList(), listOf(b, a))
        val second = HistoryMerge.merge(first, listOf(c, b))
        assertEquals(listOf(12, 11, 10), second.map { it.seq })
        assertEquals(listOf(2000, -210, null), second.map { it.amount })
        assertEquals(1, HistoryMerge.newCount(first, listOf(c, b)))
    }

    @Test
    fun amountUnknownAcrossGap() {
        val a = TransitParser.history(transit(0x16, 0x01, 2026, 1, 1, balance = 1000, seq = 10))!!
        val b = TransitParser.history(transit(0x16, 0x01, 2026, 1, 5, balance = 500, seq = 14))!!
        assertEquals(listOf(null, null), HistoryMerge.merge(emptyList(), listOf(a, b)).map { it.amount })
    }

    @Test
    fun parsesEdyRecord() {
        // payment, seq 5, day 9800 at 12:00:00, ¥300, balance ¥1,700
        val dt = (9800L shl 17) or 43200L
        val block = byteArrayOf(
            0x20, 0, 0, 5,
            (dt shr 24).toByte(), (dt shr 16).toByte(), (dt shr 8).toByte(), dt.toByte(),
            0, 0, 0x01, 0x2C,
            0, 0, 0x06, 0xA4.toByte(),
        )
        val tx = EdyParser.history(block)!!
        assertEquals(TxKind.PURCHASE, tx.kind)
        assertEquals(-300, tx.amount)
        assertEquals(1700, tx.balance)
        assertEquals(43200, tx.secondOfDay)
        assertEquals(java.time.LocalDate.of(2000, 1, 1).plusDays(9800), tx.date)
    }

    @Test
    fun readerReadsTransitCardThroughFrames() {
        val idm = "0101020304050607".hexToBytes()
        val history = listOf(
            transit(0x16, 0x01, 2026, 1, 2, 0x25, 0x0A, 0x25, 0x14, balance = 790, seq = 11),
            transit(0x16, 0x01, 2026, 1, 1, balance = 1000, seq = 10),
        )
        val attrs = ByteArray(16).also { it[11] = 0x16; it[12] = 0x03 } // 790
        val fake = FelicaTransport { frame ->
            when (frame[1].toInt()) {
                0x0C -> byteArrayOf(13, 0x0D) + idm + byteArrayOf(1, 0x00, 0x03)
                0x00 -> byteArrayOf(18, 0x01) + idm + ByteArray(8)
                0x06 -> {
                    val service = (frame[11].toInt() and 0xFF) or ((frame[12].toInt() and 0xFF) shl 8)
                    val n = frame[13].toInt()
                    val blocks = (0 until n).map { frame[15 + it * 2].toInt() }
                    val data = blocks.map { b ->
                        when (service) {
                            TransitParser.SERVICE_ATTRIBUTES -> attrs
                            else -> history.getOrNull(b) ?: ByteArray(16)
                        }
                    }
                    byteArrayOf(0, 0x07) + idm + byteArrayOf(0, 0, n.toByte()) + data.fold(ByteArray(0)) { acc, x -> acc + x }
                }
                else -> throw java.io.IOException("unexpected")
            }
        }
        val result = CardReader.read(Felica(fake), idm)
        assertEquals(CardFamily.TRANSIT, result.family)
        assertEquals(790, result.balance)
        assertEquals(2, result.transactions.size)
        assertEquals("0101020304050607", result.idm)
    }

    @Test
    fun stationDbLooksUpAndSuggests() {
        val csv = "0,37,10,東日本旅客鉄道,山手線,渋谷\n0,37,20,ＪＲ東日本,山手線,新宿\n2,224,1,阪急電鉄,神戸線,梅田\n0,1,1,西日本鉄道,天神大牟田,福岡\n"
        val db = StationDb({ csv.byteInputStream() })
        assertEquals("渋谷", db.lookup(StationRef(0, 37, 10))?.name)
        assertEquals("JR東日本", db.lookup(StationRef(0, 37, 20))?.company)
        // Falls back to other areas when the region bits are off.
        assertEquals("梅田", db.lookup(StationRef(0, 224, 1))?.name)
        val tx = TransitParser.history(transit(0x16, 0x01, 2026, 1, 2, 37, 10, 37, 20, balance = 0, seq = 1))!!
        assertEquals(io.github.grillza.icreader.data.CardType.SUICA, db.suggestTransitType(listOf(tx)))
        // Nishitetsu (西日本鉄道) must not be mistaken for JR West: it suggests the Kyushu cards.
        val nishitetsu = TransitParser.history(transit(0x16, 0x01, 2026, 1, 3, 1, 1, 1, 1, balance = 0, seq = 2))!!
        val kyushu = db.suggestTransitTypes(listOf(nishitetsu))
        assert(CardType.NIMOCA in kyushu && CardType.ICOCA !in kyushu) { kyushu.toString() }
    }

    @Test
    fun subwayHeavySuicaSuggestsKantoWithSuicaFirst() {
        // Mostly Tokyo Metro trips, a few on JR East: can't tell Suica from PASMO, so offer both.
        val csv = "0,37,10,東日本旅客鉄道,山手,新宿\n0,227,54,東京地下鉄,3号線銀座,銀座\n0,227,61,東京地下鉄,3号線銀座,表参道\n"
        val db = StationDb({ csv.byteInputStream() })
        val metro = (1..5).map { TransitParser.history(transit(0x16, 0x01, 2026, 1, it, 227, 54, 227, 61, balance = 0, seq = it))!! }
        val jr = TransitParser.history(transit(0x16, 0x01, 2026, 1, 9, 37, 10, 37, 10, balance = 0, seq = 9))!!
        assertEquals(listOf(CardType.SUICA, CardType.PASMO), db.suggestTransitTypes(metro + jr))
    }

    @Test
    fun englishNamesLocalize() {
        val csv = "0,227,54,東京地下鉄,3号線銀座,銀座\n0,1,1,東日本旅客鉄道,東海道本,東京\n"
        val en = "0,227,54,Ginza,Tokyo Metro\n0,1,1,,JR East\n"
        val db = StationDb({ csv.byteInputStream() }, { en.byteInputStream() })
        val ginza = db.lookup(StationRef(0, 227, 54))!!
        assertEquals("Ginza", ginza.localized(io.github.grillza.icreader.data.StationLanguage.ENGLISH).name)
        assertEquals("Tokyo Metro", ginza.localized(io.github.grillza.icreader.data.StationLanguage.ENGLISH).line)
        assertEquals("Ginza (銀座)", ginza.localized(io.github.grillza.icreader.data.StationLanguage.BOTH).name)
        assertEquals("銀座", ginza.localized(io.github.grillza.icreader.data.StationLanguage.JAPANESE).name)
        // No English name: falls back to Japanese.
        assertEquals("東京", db.lookup(StationRef(0, 1, 1))!!.localized(io.github.grillza.icreader.data.StationLanguage.ENGLISH).name)
    }
}
