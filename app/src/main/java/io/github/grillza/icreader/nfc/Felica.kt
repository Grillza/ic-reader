package io.github.grillza.icreader.nfc

import java.io.ByteArrayOutputStream
import java.io.IOException

/** Raw FeliCa frame exchange; implemented over android.nfc.tech.NfcF, or a fake in tests. */
fun interface FelicaTransport {
    /** Sends a full frame (length byte first) and returns the response frame. */
    @Throws(IOException::class)
    fun transceive(frame: ByteArray): ByteArray
}

class FelicaException(message: String) : IOException(message)

/** System codes we care about. */
object SystemCode {
    const val TRANSIT = 0x0003 // CJRC "cyberne" transit area (Suica, PASMO, ICOCA, …)
    const val COMMON = 0xFE00 // common area: Edy, nanaco, WAON
}

/**
 * The handful of FeliCa commands needed to read the public (unencrypted) areas of a card:
 * Polling, Request System Code and Read Without Encryption.
 */
class Felica(private val transport: FelicaTransport) {

    /** Polls for [systemCode]; returns that system's IDm, or null if the card doesn't have it. */
    fun poll(systemCode: Int): ByteArray? {
        val frame = frame(0x00, byteArrayOf((systemCode shr 8).toByte(), systemCode.toByte(), 0x01, 0x00))
        val res = try {
            transport.transceive(frame)
        } catch (_: IOException) {
            return null
        }
        if (res.size < 18 || res[1] != 0x01.toByte()) return null
        return res.copyOfRange(2, 10)
    }

    /** Lists the systems on the card. Empty if the card doesn't support the command. */
    fun requestSystemCodes(idm: ByteArray): List<Int> {
        val res = try {
            transport.transceive(frame(0x0C, idm))
        } catch (_: IOException) {
            return emptyList()
        }
        if (res.size < 11 || res[1] != 0x0D.toByte()) return emptyList()
        val n = res[10].toInt() and 0xFF
        return (0 until n).mapNotNull { i ->
            val o = 11 + i * 2
            if (o + 1 < res.size) ((res[o].toInt() and 0xFF) shl 8) or (res[o + 1].toInt() and 0xFF) else null
        }
    }

    /**
     * Reads blocks [first, first+count) of [serviceCode]. Stops early (returning what it has) when
     * the card reports an error, which is how "past the last block" shows up.
     */
    fun readBlocks(idm: ByteArray, serviceCode: Int, count: Int, first: Int = 0): List<ByteArray> {
        val out = ArrayList<ByteArray>(count)
        var chunk = 4
        var next = first
        while (next < first + count) {
            val n = minOf(chunk, first + count - next)
            val blocks = try {
                readWithoutEncryption(idm, serviceCode, (next until next + n).toList())
            } catch (e: FelicaException) {
                if (chunk > 1) {
                    // Some cards cap blocks-per-read; fall back to one at a time.
                    chunk = 1
                    continue
                }
                if (out.isEmpty() && next == first) throw e
                break
            }
            out += blocks
            next += n
        }
        return out
    }

    fun readWithoutEncryption(idm: ByteArray, serviceCode: Int, blocks: List<Int>): List<ByteArray> {
        val body = ByteArrayOutputStream()
        body.write(idm)
        body.write(1) // number of services
        body.write(serviceCode and 0xFF) // service code, little endian
        body.write((serviceCode shr 8) and 0xFF)
        body.write(blocks.size)
        for (b in blocks) {
            body.write(0x80) // 2-byte block list element, service index 0
            body.write(b)
        }
        val res = transport.transceive(frame(0x06, body.toByteArray()))
        if (res.size < 12 || res[1] != 0x07.toByte()) throw FelicaException("Bad response")
        val status1 = res[10].toInt() and 0xFF
        val status2 = res[11].toInt() and 0xFF
        if (status1 != 0) throw FelicaException("Read failed: %02X %02X".format(status1, status2))
        val n = res[12].toInt() and 0xFF
        if (res.size < 13 + n * 16) throw FelicaException("Short response")
        return (0 until n).map { i -> res.copyOfRange(13 + i * 16, 13 + (i + 1) * 16) }
    }

    private fun frame(command: Int, payload: ByteArray): ByteArray {
        val f = ByteArray(payload.size + 2)
        f[0] = f.size.toByte()
        f[1] = command.toByte()
        payload.copyInto(f, 2)
        return f
    }
}

fun ByteArray.toHex(): String = joinToString("") { "%02X".format(it) }

fun String.hexToBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()

internal fun ByteArray.u8(i: Int): Int = this[i].toInt() and 0xFF
internal fun ByteArray.be16(i: Int): Int = (u8(i) shl 8) or u8(i + 1)
internal fun ByteArray.le16(i: Int): Int = u8(i) or (u8(i + 1) shl 8)
internal fun ByteArray.be24(i: Int): Int = (u8(i) shl 16) or (u8(i + 1) shl 8) or u8(i + 2)
internal fun ByteArray.be32(i: Int): Long =
    (u8(i).toLong() shl 24) or (u8(i + 1).toLong() shl 16) or (u8(i + 2).toLong() shl 8) or u8(i + 3).toLong()
internal fun ByteArray.le32(i: Int): Long =
    u8(i).toLong() or (u8(i + 1).toLong() shl 8) or (u8(i + 2).toLong() shl 16) or (u8(i + 3).toLong() shl 24)
