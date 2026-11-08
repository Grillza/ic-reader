package io.github.grillza.icreader.ui

import io.github.grillza.icreader.data.CardFamily
import io.github.grillza.icreader.data.ScanResult
import io.github.grillza.icreader.nfc.TransitParser
import java.time.LocalDate

/** A plausible Suica history built from real station codes, for trying the UI without a card. */
internal object DemoData {
    private class Rec(val daysAgo: Long, val terminal: Int, val process: Int, val b6: Int, val b7: Int, val b8: Int, val b9: Int, val balance: Int)

    fun scan(): ScanResult {
        val time = { h: Int, m: Int -> (h shl 11) or (m shl 5) }
        val records = listOf(
            Rec(9, 0x16, 0x01, 37, 7, 37, 10, 3460), // 渋谷 → 新宿
            Rec(8, 0x09, 0x02, 0, 0, 37, 10, 6460), // charge ¥3,000
            Rec(8, 0x16, 0x01, 37, 10, 37, 15, 6250), // 新宿 → 池袋
            Rec(6, 0xC8, 0x46, time(8, 12) shr 8, time(8, 12) and 0xFF, 0, 0, 6090), // vending machine
            Rec(5, 0x16, 0x01, 37, 15, 3, 1, 5890), // 池袋 → 東京
            Rec(3, 0x16, 0x01, 3, 1, 37, 1, 5720), // 東京 → 品川
            Rec(2, 0xC7, 0x46, time(19, 40) shr 8, time(19, 40) and 0xFF, 0, 0, 4870), // shop
            Rec(1, 0x16, 0x01, 37, 1, 37, 7, 4660), // 品川 → 渋谷
            Rec(0, 0x16, 0x01, 227, 61, 227, 54, 4480), // 表参道 → 銀座 (Tokyo Metro)
        )
        val today = LocalDate.now()
        val txs = records.mapIndexed { i, r ->
            val d = today.minusDays(r.daysAgo)
            val date = ((d.year - 2000) shl 9) or (d.monthValue shl 5) or d.dayOfMonth
            val seq = 0x0120 + i
            val block = byteArrayOf(
                r.terminal.toByte(), r.process.toByte(), 0, 0,
                (date shr 8).toByte(), date.toByte(),
                r.b6.toByte(), r.b7.toByte(), r.b8.toByte(), r.b9.toByte(),
                r.balance.toByte(), (r.balance shr 8).toByte(), 0,
                (seq shr 8).toByte(), seq.toByte(), 0,
            )
            TransitParser.history(block)!!
        }.reversed()
        return ScanResult("01120000DE0DE000", CardFamily.TRANSIT, records.last().balance, txs, listOf(0x0003, 0xFE00))
    }
}
