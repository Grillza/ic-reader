package io.github.grillza.icreader.nfc

import io.github.grillza.icreader.data.StationRef
import io.github.grillza.icreader.data.Transaction
import io.github.grillza.icreader.data.TxKind
import java.time.LocalDate

/**
 * Decoders for the public data areas. Layouts follow the community documentation of the CJRC
 * transit format and the Edy / nanaco / WAON e-money areas.
 */
object TransitParser {
    const val SERVICE_ATTRIBUTES = 0x008B
    const val SERVICE_HISTORY = 0x090F
    const val HISTORY_BLOCKS = 20

    private val BUS_PROCESSES = setOf(0x0D, 0x0F, 0x1F, 0x23)
    private val PURCHASE_PROCESSES = setOf(0x46, 0x47, 0x4A, 0x4B, 0xC6, 0xCB)
    private val CHARGE_PROCESSES = setOf(0x02, 0x14, 0x15, 0x48, 0x49)
    private val PURCHASE_TERMINALS = setOf(0xC7, 0xC8)

    /** Balance from the attribute block (bytes 11-12, little endian). */
    fun balance(attributes: ByteArray): Int = attributes.le16(11)

    /** Returns null for empty (all-zero) history slots. */
    fun history(block: ByteArray): Transaction? {
        if (block.all { it == 0.toByte() }) return null
        val terminal = block.u8(0)
        val process = block.u8(1)
        val d = block.be16(4)
        val year = 2000 + (d shr 9)
        val month = (d shr 5) and 0x0F
        val day = d and 0x1F
        val balance = block.le16(10)
        val seq = block.be16(13)
        val region = block.u8(15)
        val baseProcess = process and 0x7F

        val kind = when {
            baseProcess == 0x1F -> TxKind.CHARGE // charge on a bus
            baseProcess in BUS_PROCESSES || terminal == 0x05 -> TxKind.BUS
            process in PURCHASE_PROCESSES || baseProcess in PURCHASE_PROCESSES || terminal in PURCHASE_TERMINALS ->
                TxKind.PURCHASE
            baseProcess in CHARGE_PROCESSES -> TxKind.CHARGE
            baseProcess in TRAIN_PROCESSES -> TxKind.TRAIN
            else -> TxKind.OTHER
        }

        var secondOfDay: Int? = null
        var entry: StationRef? = null
        var exit: StationRef? = null
        var busOperator: Int? = null
        var busStop: Int? = null
        when (kind) {
            TxKind.PURCHASE -> {
                val t = block.be16(6)
                val h = t shr 11
                val m = (t shr 5) and 0x3F
                val s = (t and 0x1F) * 2
                if (h < 24 && m < 60 && s < 60) secondOfDay = h * 3600 + m * 60 + s
            }
            TxKind.BUS -> {
                busOperator = block.be16(6)
                busStop = block.be16(8)
            }
            else -> {
                val inLine = block.u8(6)
                val outLine = block.u8(8)
                if (inLine != 0 || block.u8(7) != 0) entry = StationRef(area(inLine, (region shr 6) and 3), inLine, block.u8(7))
                if (outLine != 0 || block.u8(9) != 0) exit = StationRef(area(outLine, (region shr 4) and 3), outLine, block.u8(9))
            }
        }

        return Transaction(
            key = "t:%04X:%04X".format(seq, d),
            seq = seq,
            year = year,
            month = month,
            day = day,
            secondOfDay = secondOfDay,
            kind = kind,
            terminal = terminal,
            process = process,
            balance = balance,
            entry = entry,
            exit = exit,
            busOperator = busOperator,
            busStop = busStop,
            raw = block.toHex(),
        )
    }

    /** JR lines (< 0x80) are all in area 0; private lines use the region bits from byte 15. */
    private fun area(line: Int, regionBits: Int): Int = if (line < 0x80) 0 else regionBits

    private val TRAIN_PROCESSES = setOf(0x01, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x11, 0x13, 0x0B, 0x0C)
}

object EdyParser {
    const val SERVICE_BALANCE = 0x1317
    const val SERVICE_HISTORY = 0x170F
    const val HISTORY_BLOCKS = 6

    fun balance(block: ByteArray): Int = block.le32(0).toInt()

    fun history(block: ByteArray): Transaction? {
        if (block.all { it == 0.toByte() }) return null
        val type = block.u8(0)
        val seq = block.be24(1)
        val dt = block.be32(4)
        val days = (dt ushr 17).toInt()
        val secs = (dt and 0x1FFFF).toInt()
        val date = LocalDate.of(2000, 1, 1).plusDays(days.toLong())
        val value = block.be32(8).toInt()
        val balance = block.be32(12).toInt()
        val kind = when (type) {
            0x02, 0x04 -> TxKind.CHARGE // charge, Edy gift
            0x20 -> TxKind.PURCHASE
            else -> TxKind.OTHER
        }
        return Transaction(
            key = "e:$seq",
            seq = seq,
            year = date.year,
            month = date.monthValue,
            day = date.dayOfMonth,
            secondOfDay = secs.takeIf { it < 86400 },
            kind = kind,
            process = type,
            balance = balance,
            amount = if (kind == TxKind.PURCHASE) -value else value,
            amountFromCard = true,
            raw = block.toHex(),
        )
    }
}

object NanacoParser {
    const val SERVICE_BALANCE = 0x5597
    const val SERVICE_HISTORY = 0x564F
    const val HISTORY_BLOCKS = 5

    fun balance(block: ByteArray): Int = block.le32(0).toInt()

    fun history(block: ByteArray): Transaction? {
        if (block.all { it == 0.toByte() }) return null
        val type = block.u8(0)
        val value = block.be32(1).toInt()
        val balance = block.be32(5).toInt()
        val v = block.be32(9)
        val year = 2000 + (v ushr 21).toInt()
        val month = ((v ushr 17) and 0x0F).toInt()
        val day = ((v ushr 12) and 0x1F).toInt()
        val hour = ((v ushr 6) and 0x3F).toInt()
        val minute = (v and 0x3F).toInt()
        val seq = block.be16(13)
        val kind = when (type) {
            0x47 -> TxKind.PURCHASE
            0x6F, 0x70 -> TxKind.CHARGE
            0x35, 0x83 -> TxKind.OTHER // point exchange / transfer
            else -> TxKind.OTHER
        }
        val signed = when (kind) {
            TxKind.PURCHASE -> -value
            else -> value
        }
        return Transaction(
            key = "n:$seq",
            seq = seq,
            year = year,
            month = month,
            day = day,
            secondOfDay = if (hour < 24 && minute < 60) hour * 3600 + minute * 60 else null,
            kind = kind,
            process = type,
            balance = balance,
            amount = signed,
            amountFromCard = true,
            raw = block.toHex(),
        )
    }
}

object WaonParser {
    const val SERVICE_BALANCE = 0x6817

    /** Balance is a 24-bit little-endian value at the start of the purse block. */
    fun balance(block: ByteArray): Int = block.u8(0) or (block.u8(1) shl 8) or (block.u8(2) shl 16)
}
