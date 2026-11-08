package io.github.grillza.icreader.nfc

import android.nfc.Tag
import android.nfc.tech.NfcF
import io.github.grillza.icreader.data.CardFamily
import io.github.grillza.icreader.data.ScanResult
import java.io.IOException

class UnsupportedCardException(message: String) : IOException(message)

/** Works out what kind of card is on the reader and reads its balance and history. */
object CardReader {

    fun read(tag: Tag): ScanResult {
        val nfcF = NfcF.get(tag) ?: throw UnsupportedCardException("This isn't a FeliCa card.")
        nfcF.connect()
        try {
            nfcF.timeout = 1500
            return read(Felica(nfcF::transceive), tag.id)
        } finally {
            runCatching { nfcF.close() }
        }
    }

    fun read(felica: Felica, tagId: ByteArray): ScanResult {
        val systems = felica.requestSystemCodes(tagId)

        val transitIdm = if (systems.isEmpty() || SystemCode.TRANSIT in systems) felica.poll(SystemCode.TRANSIT) else null
        if (transitIdm != null) return readTransit(felica, transitIdm, systems)

        val commonIdm = if (systems.isEmpty() || SystemCode.COMMON in systems) felica.poll(SystemCode.COMMON) else null
        if (commonIdm != null) {
            readEdy(felica, commonIdm, systems)?.let { return it }
            readNanaco(felica, commonIdm, systems)?.let { return it }
            readWaon(felica, commonIdm, systems)?.let { return it }
        }

        val codes = systems.joinToString { "%04X".format(it) }.ifEmpty { "none" }
        throw UnsupportedCardException("Card not recognised (systems: $codes).")
    }

    private fun readTransit(felica: Felica, idm: ByteArray, systems: List<Int>): ScanResult {
        val history = felica.readBlocks(idm, TransitParser.SERVICE_HISTORY, TransitParser.HISTORY_BLOCKS)
            .mapNotNull(TransitParser::history)
        val balance = runCatching {
            felica.readBlocks(idm, TransitParser.SERVICE_ATTRIBUTES, 1).firstOrNull()?.let(TransitParser::balance)
        }.getOrNull() ?: history.firstOrNull()?.balance ?: 0
        return ScanResult(idm.toHex(), CardFamily.TRANSIT, balance, history, systems)
    }

    private fun readEdy(felica: Felica, idm: ByteArray, systems: List<Int>): ScanResult? {
        val balanceBlock = runCatching { felica.readBlocks(idm, EdyParser.SERVICE_BALANCE, 1).firstOrNull() }
            .getOrNull() ?: return null
        val history = runCatching { felica.readBlocks(idm, EdyParser.SERVICE_HISTORY, EdyParser.HISTORY_BLOCKS) }
            .getOrDefault(emptyList()).mapNotNull(EdyParser::history)
        return ScanResult(idm.toHex(), CardFamily.EDY, EdyParser.balance(balanceBlock), history, systems)
    }

    private fun readNanaco(felica: Felica, idm: ByteArray, systems: List<Int>): ScanResult? {
        val balanceBlock = runCatching { felica.readBlocks(idm, NanacoParser.SERVICE_BALANCE, 1).firstOrNull() }
            .getOrNull() ?: return null
        val history = runCatching { felica.readBlocks(idm, NanacoParser.SERVICE_HISTORY, NanacoParser.HISTORY_BLOCKS) }
            .getOrDefault(emptyList()).mapNotNull(NanacoParser::history)
        return ScanResult(idm.toHex(), CardFamily.NANACO, NanacoParser.balance(balanceBlock), history, systems)
    }

    private fun readWaon(felica: Felica, idm: ByteArray, systems: List<Int>): ScanResult? {
        val balanceBlock = runCatching { felica.readBlocks(idm, WaonParser.SERVICE_BALANCE, 1).firstOrNull() }
            .getOrNull() ?: return null
        return ScanResult(idm.toHex(), CardFamily.WAON, WaonParser.balance(balanceBlock), emptyList(), systems)
    }
}
