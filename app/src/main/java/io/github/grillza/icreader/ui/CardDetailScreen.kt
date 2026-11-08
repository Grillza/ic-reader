package io.github.grillza.icreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Train
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.grillza.icreader.data.CardFamily
import io.github.grillza.icreader.data.Codes
import io.github.grillza.icreader.data.SavedCard
import io.github.grillza.icreader.data.Station
import io.github.grillza.icreader.data.StationRef
import io.github.grillza.icreader.data.Transaction
import io.github.grillza.icreader.data.TxKind
import io.github.grillza.icreader.ui.theme.ChargeGreen
import io.github.grillza.icreader.ui.theme.ChargeGreenDark
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardDetailScreen(
    card: SavedCard,
    isPreview: Boolean,
    image: File?,
    snackbar: SnackbarHostState,
    station: (StationRef) -> Station?,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onEdit: () -> Unit,
    onUseAsIcon: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Transaction?>(null) }
    var showInfo by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isPreview) "New card" else card.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (!isPreview) {
                        IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Edit card") }
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                        DropdownMenu(menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Use as app icon") },
                                leadingIcon = { Icon(Icons.Default.Palette, null) },
                                onClick = { menu = false; onUseAsIcon() },
                            )
                            DropdownMenuItem(
                                text = { Text("Export history (CSV)") },
                                leadingIcon = { Icon(Icons.Default.FileDownload, null) },
                                enabled = card.transactions.isNotEmpty(),
                                onClick = { menu = false; onExport() },
                            )
                            if (card.cardInfo.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("Card info") },
                                    leadingIcon = { Icon(Icons.Default.Info, null) },
                                    onClick = { menu = false; showInfo = true },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Remove card") },
                                leadingIcon = { Icon(Icons.Default.Delete, null) },
                                onClick = { menu = false; confirmDelete = true },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (isPreview) {
                Surface(tonalElevation = 3.dp) {
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Discard") }
                        Button(onClick = onSave, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Save, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Save card")
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val grouped = remember(card.transactions) { card.transactions.groupBy { it.date } }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp),
        ) {
            item {
                CardArt(card.type, image = image, modifier = Modifier.padding(horizontal = 16.dp)) {
                    CardBalance(card.balance, large = true)
                }
            }
            if (isPreview) {
                item {
                    Text(
                        "This card isn't saved. Save it to keep its balance and build up its history over future scans.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp),
                    )
                }
            }
            item { Stats(card) }
            item {
                Text(
                    "History",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 4.dp),
                )
            }
            if (card.transactions.isEmpty()) {
                item {
                    Text(
                        if (card.family == CardFamily.WAON) "WAON history isn't supported yet; balance only." else "No history on this card.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp),
                    )
                }
            }
            grouped.forEach { (date, txs) ->
                item(key = "h$date") { DayHeader(date) }
                items(txs, key = { it.key }) { tx ->
                    TransactionRow(tx, card.family, station, onClick = { selected = tx })
                }
            }
        }
    }

    selected?.let { tx ->
        TransactionSheet(tx, card.family, station, onDismiss = { selected = null })
    }
    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text("Card info") },
            text = {
                SelectionContainer {
                    Column {
                        card.cardInfo.forEach { (k, v) ->
                            Text(k, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(v.chunked(2).joinToString(" "), fontFamily = FontFamily.Monospace, modifier = Modifier.padding(bottom = 10.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showInfo = false }) { Text("Close") } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Remove ${card.title}?") },
            text = { Text("Its saved history will be deleted from this phone. Scanning the card again adds it back, but only with the records still on the card.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Stats(card: SavedCard) {
    val month = YearMonth.now()
    val monthName = month.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH)
    val thisMonth = card.transactions.filter { YearMonth.of(it.date.year, it.date.month) == month }
    val spent = thisMonth.mapNotNull { it.amount }.filter { it < 0 }.sum()
    val charged = thisMonth.mapNotNull { it.amount }.filter { it > 0 }.sum()
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatTile("Spent ($monthName)", formatYen(-spent), Modifier.weight(1f))
        StatTile("Charged ($monthName)", formatYen(charged), Modifier.weight(1f))
        StatTile("Records", card.transactions.size.toString(), Modifier.weight(0.8f))
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun DayHeader(date: LocalDate) {
    Text(
        date.format(DAY_HEADER),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp),
    )
}

/** Title and subtitle for a history row. */
private fun describe(tx: Transaction, family: CardFamily, station: (StationRef) -> Station?): Pair<String, String> {
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

private fun icon(kind: TxKind): ImageVector = when (kind) {
    TxKind.TRAIN -> Icons.Default.Train
    TxKind.BUS -> Icons.Default.DirectionsBus
    TxKind.PURCHASE -> Icons.Default.ShoppingBag
    TxKind.CHARGE -> Icons.Default.AddCard
    TxKind.OTHER -> Icons.Default.Receipt
}

@Composable
private fun TransactionRow(tx: Transaction, family: CardFamily, station: (StationRef) -> Station?, onClick: () -> Unit) {
    val (title, subtitle) = describe(tx, family, station)
    val green = if (isSystemInDarkTheme()) ChargeGreenDark else ChargeGreen
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon(tx.kind), null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            val amount = tx.amount
            Text(
                if (amount == null) "—" else formatSigned(amount),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (amount != null && amount > 0) green else MaterialTheme.colorScheme.onSurface,
            )
            Text(formatYen(tx.balance), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionSheet(tx: Transaction, family: CardFamily, station: (StationRef) -> Station?, onDismiss: () -> Unit) {
    val (title, _) = describe(tx, family, station)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                tx.date.format(LONG_DATE) + (tx.secondOfDay?.let { " · " + formatTime(it) } ?: ""),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            DetailRow("Amount", tx.amount?.let(::formatSigned) ?: "Unknown (no earlier record)")
            DetailRow("Balance after", formatYen(tx.balance))
            when (family) {
                CardFamily.TRANSIT -> {
                    DetailRow("Type", Codes.process(tx.process))
                    DetailRow("Terminal", Codes.terminal(tx.terminal))
                    tx.entry?.let { DetailRow("From", stationText(it, station)) }
                    tx.exit?.let { DetailRow("To", stationText(it, station)) }
                    if (tx.busOperator != null) DetailRow("Bus operator / stop", "%04X / %04X".format(tx.busOperator, tx.busStop ?: 0))
                }
                CardFamily.EDY -> DetailRow("Type", Codes.edyType(tx.process))
                CardFamily.NANACO -> DetailRow("Type", Codes.nanacoType(tx.process))
                CardFamily.WAON -> {}
            }
            DetailRow("Record no.", tx.seq.toString())
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Text("Raw record", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SelectionContainer {
                Text(tx.raw.chunked(2).joinToString(" "), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun stationText(ref: StationRef, station: (StationRef) -> Station?): String {
    val s = station(ref) ?: return "Unknown station (line %02X, station %02X)".format(ref.line, ref.station)
    return if (s.line == s.company) "${s.name}\n${s.company}" else "${s.name}\n${s.company} ${s.line}"
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(140.dp))
        Text(value, modifier = Modifier.weight(1f))
    }
}
