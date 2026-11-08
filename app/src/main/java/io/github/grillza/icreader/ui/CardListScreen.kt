package io.github.grillza.icreader.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Contactless
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.grillza.icreader.data.SavedCard
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardListScreen(
    cards: List<SavedCard>,
    nfc: NfcState,
    snackbar: SnackbarHostState,
    imageFile: (String) -> File,
    onOpen: (SavedCard) -> Unit,
    onMove: (SavedCard, Int) -> Unit,
    onScan: () -> Unit,
    onIcons: () -> Unit,
    onSettings: () -> Unit,
    onEnableNfc: () -> Unit,
    onDemo: (() -> Unit)? = null,
) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var menu by remember { mutableStateOf(false) }
    var about by remember { mutableStateOf(false) }
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            androidx.compose.material3.LargeTopAppBar(
                title = { Text("IC Cards") },
                scrollBehavior = scroll,
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("App icon") },
                            leadingIcon = { Icon(Icons.Default.Palette, null) },
                            onClick = { menu = false; onIcons() },
                        )
                        DropdownMenuItem(
                            text = { Text("Settings") },
                            leadingIcon = { Icon(Icons.Default.Settings, null) },
                            onClick = { menu = false; onSettings() },
                        )
                        if (onDemo != null) {
                            DropdownMenuItem(
                                text = { Text("Load demo card (debug)") },
                                leadingIcon = { Icon(Icons.Default.Contactless, null) },
                                onClick = { menu = false; onDemo() },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("About") },
                            leadingIcon = { Icon(Icons.Default.Info, null) },
                            onClick = { menu = false; about = true },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (nfc != NfcState.Unsupported) {
                ExtendedFloatingActionButton(
                    onClick = onScan,
                    icon = { Icon(Icons.Default.Contactless, null) },
                    text = { Text("Scan card") },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (nfc != NfcState.Enabled) {
                item { NfcBanner(nfc, onEnableNfc) }
            }
            if (cards.isEmpty()) {
                item { EmptyState() }
            } else {
                item { TotalHeader(cards) }
                items(cards, key = { it.id }) { card ->
                    WalletCard(
                        card = card,
                        image = card.customImage?.let(imageFile),
                        canMoveUp = card != cards.first(),
                        canMoveDown = card != cards.last(),
                        onOpen = { onOpen(card) },
                        onMove = { onMove(card, it) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
    if (about) {
        AlertDialog(
            onDismissRequest = { about = false },
            confirmButton = { TextButton(onClick = { about = false }) { Text("OK") } },
            title = { Text("IC Reader") },
            text = {
                Text(
                    "Reads the balance and recent history of Japanese FeliCa IC cards (Suica, PASMO, ICOCA and other " +
                        "transit cards, Rakuten Edy, nanaco, WAON). Cards keep only their last 20 records, so scanning " +
                        "regularly builds up a longer history here.\n\nCard artwork is generated in each brand's colours; " +
                        "add a photo of your own card from the card's edit screen for the real thing.\n\n" +
                        "Everything stays on this phone.",
                )
            },
        )
    }
}

@Composable
private fun TotalHeader(cards: List<SavedCard>) {
    Column(Modifier.padding(horizontal = 4.dp)) {
        Text("Total balance", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(formatYen(cards.sumOf { it.balance }), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
        Text(
            if (cards.size == 1) "1 card" else "${cards.size} cards",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WalletCard(
    card: SavedCard,
    image: File?,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onOpen: () -> Unit,
    onMove: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    Column(modifier) {
        Box {
            CardArt(
                type = card.type,
                image = image,
                modifier = Modifier.combinedClickable(onClick = onOpen, onLongClick = { menu = true }),
            ) {
                CardOverlay(card.type, image != null, card.nickname?.takeIf { it.isNotBlank() }, card.balance)
            }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                if (canMoveUp) {
                    DropdownMenuItem(
                        text = { Text("Move up") },
                        leadingIcon = { Icon(Icons.Default.ArrowUpward, null) },
                        onClick = { menu = false; onMove(-1) },
                    )
                }
                if (canMoveDown) {
                    DropdownMenuItem(
                        text = { Text("Move down") },
                        leadingIcon = { Icon(Icons.Default.ArrowDownward, null) },
                        onClick = { menu = false; onMove(1) },
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp)) {
            Text(card.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(
                "Scanned ${relativeTime(card.lastScanned)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NfcBanner(nfc: NfcState, onEnable: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (nfc == NfcState.Disabled) "NFC is off. Turn it on to scan cards." else "This phone doesn't have NFC, so it can't read cards.",
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            if (nfc == NfcState.Disabled) TextButton(onClick = onEnable) { Text("Turn on") }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxWidth().padding(top = 64.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.CreditCard, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("No cards yet", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Hold an IC card against the back of your phone to read its balance and history.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

enum class NfcState { Enabled, Disabled, Unsupported }
