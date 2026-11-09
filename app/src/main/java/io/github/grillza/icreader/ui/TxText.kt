package io.github.grillza.icreader.ui

import io.github.grillza.icreader.data.CardFamily
import io.github.grillza.icreader.data.Codes
import io.github.grillza.icreader.data.Station
import io.github.grillza.icreader.data.StationRef
import io.github.grillza.icreader.data.Transaction
import io.github.grillza.icreader.data.TxKind

/** Title and subtitle for a history row. */
fun describeTransaction(tx: Transaction, family: CardFamily, station: (StationRef) -> Station?): Pair<String, String> {
    val time = tx.secondOfDay?.let(::formatTime)
    return when (family) {
        CardFamily.EDY -> Codes.edyType(tx.process) to listOfNotNull(time).joinToString()
        CardFamily.NANACO -> Codes.nanacoType(tx.process) to listOfNotNull(time).joinToString()
        CardFamily.WAON -> "Transaction" to ""
        CardFamily.TRANSIT -> when (tx.kind) {
            TxKind.TRAIN -> {
                val inSt = tx.entry?.let(station)
                val outSt = tx.exit?.let(station)
                val title = when {
                    inSt != null && outSt != null -> "${inSt.name} → ${outSt.name}"
                    inSt != null -> inSt.name
                    outSt != null -> outSt.name
                    else -> Codes.process(tx.process)
                }
                val sub = listOfNotNull(
                    Codes.process(tx.process).takeIf { inSt != null || outSt != null },
                    listOfNotNull(inSt?.line, outSt?.line?.takeIf { it != inSt?.line }).joinToString(" / ").ifEmpty { null },
                ).joinToString(" · ")
                title to sub
            }
            TxKind.BUS -> Codes.process(tx.process).let { if (it.startsWith("Process")) "Bus" else it } to
                listOfNotNull(Codes.terminal(tx.terminal), time).joinToString(" · ")
            else -> Codes.process(tx.process) to listOfNotNull(Codes.terminal(tx.terminal), time).joinToString(" · ")
        }
    }
}
