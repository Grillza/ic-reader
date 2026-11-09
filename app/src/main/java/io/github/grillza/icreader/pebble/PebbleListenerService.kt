package io.github.grillza.icreader.pebble

import io.github.grillza.icreader.IcReaderApp
import io.rebble.pebblekit2.client.BasePebbleListenerService
import io.rebble.pebblekit2.common.model.PebbleDictionary
import io.rebble.pebblekit2.common.model.PebbleDictionaryItem
import io.rebble.pebblekit2.common.model.ReceiveResult
import io.rebble.pebblekit2.common.model.WatchIdentifier
import java.util.UUID

/** The Pebble app binds this while the IC Reader watch app is open. */
class PebbleListenerService : BasePebbleListenerService() {
    private val sync get() = (application as IcReaderApp).watchSync

    override suspend fun onMessageReceived(watchappUUID: UUID, data: PebbleDictionary, watch: WatchIdentifier): ReceiveResult {
        if (watchappUUID != WatchProtocol.APP_UUID) return ReceiveResult.Nack
        when ((data[WatchProtocol.KEY_TYPE] as? PebbleDictionaryItem.UInt32)?.value?.toInt()
            ?: (data[WatchProtocol.KEY_TYPE] as? PebbleDictionaryItem.Int32)?.value) {
            WatchProtocol.MSG_HELLO -> sync.onHello(watch, data)
            WatchProtocol.MSG_HISTORY_REQUEST -> sync.onHistoryRequest(watch, data)
        }
        return ReceiveResult.Ack
    }

    override fun onAppClosed(watchappUUID: UUID, watch: WatchIdentifier) {
        if (watchappUUID == WatchProtocol.APP_UUID) sync.onAppClosed(watch)
    }
}
