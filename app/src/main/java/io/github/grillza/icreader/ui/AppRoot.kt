package io.github.grillza.icreader.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.grillza.icreader.data.Codes
import io.github.grillza.icreader.data.SavedCard
import io.github.grillza.icreader.data.CardFamily
import io.github.grillza.icreader.data.StationRef
import java.io.File

@Composable
fun AppRoot(vm: AppViewModel, nfc: NfcState, onExit: () -> Unit) {
    val context = LocalContext.current
    val cards by vm.cards.collectAsStateWithLifecycle()
    val stack by vm.stack.collectAsStateWithLifecycle()
    val scan by vm.scan.collectAsStateWithLifecycle()
    val pending by vm.pendingCard.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val iconId by vm.iconId.collectAsStateWithLifecycle()
    vm.stationsReady.collectAsStateWithLifecycle().value // recompose once names are loaded

    val snackbar = remember { SnackbarHostState() }
    var scanSheet by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var savingPending by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let {
            vm.consumeMessage()
            snackbar.showSnackbar(it)
        }
    }
    // Close the scan prompt once a card has been read.
    LaunchedEffect(stack, pending) {
        if (scan == ScanStatus.Idle && (pending != null || stack.last() is Screen.Detail)) scanSheet = false
    }
    // Errors outside the scan prompt go to the snackbar.
    LaunchedEffect(scan) {
        val s = scan
        if (s is ScanStatus.Failed && !scanSheet) {
            vm.clearScanError()
            snackbar.showSnackbar(s.message)
        }
    }

    BackHandler { if (!vm.back()) onExit() }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val id = editing
        if (uri != null && id != null) vm.setCustomImage(id, uri)
    }
    val station: (StationRef) -> io.github.grillza.icreader.data.Station? = vm::station

    AnimatedContent(
        targetState = stack.last(),
        transitionSpec = {
            if (stack.size > 1 && targetState != Screen.Cards) {
                (slideInHorizontally { it / 3 } + fadeIn()) togetherWith fadeOut()
            } else {
                fadeIn() togetherWith (slideOutHorizontally { it / 3 } + fadeOut())
            }
        },
        label = "screen",
    ) { screen ->
        when (screen) {
            Screen.Cards -> CardListScreen(
                cards = cards,
                nfc = nfc,
                snackbar = snackbar,
                imageFile = vm::imageFile,
                onOpen = { vm.navigate(Screen.Detail(it.id)) },
                onMove = { card, delta -> vm.moveCard(card.id, delta) },
                onScan = { vm.clearScanError(); scanSheet = true },
                onIcons = { vm.navigate(Screen.Icons) },
                onEnableNfc = { openNfcSettings(context) },
                onDemo = if (vm.demoAvailable) vm::loadDemoCard else null,
            )
            is Screen.Detail -> {
                val card = cards.firstOrNull { it.id == screen.cardId }
                if (card != null) {
                    CardDetailScreen(
                        card = card,
                        isPreview = false,
                        image = card.customImage?.let(vm::imageFile),
                        snackbar = snackbar,
                        station = station,
                        onBack = { vm.back() },
                        onSave = {},
                        onEdit = { editing = card.id },
                        onUseAsIcon = { vm.selectIcon(card.type.id) },
                        onExport = { exportCsv(context, card, station) },
                        onDelete = { vm.deleteCard(card.id) },
                    )
                }
            }
            Screen.Preview -> {
                val scanResult = pending
                if (scanResult != null) {
                    CardDetailScreen(
                        card = remember(scanResult) { vm.previewCard(scanResult) },
                        isPreview = true,
                        image = null,
                        snackbar = snackbar,
                        station = station,
                        onBack = { vm.dismissPending() },
                        onSave = { savingPending = true },
                        onEdit = {},
                        onUseAsIcon = {},
                        onExport = {},
                        onDelete = {},
                    )
                }
            }
            Screen.Icons -> IconsScreen(iconId, snackbar, onBack = { vm.back() }, onSelect = { vm.selectIcon(it.id) })
        }
    }

    if (scanSheet) {
        ScanSheet(scan, onDismiss = { scanSheet = false; vm.clearScanError() })
    }

    val toSave = pending
    if (savingPending && toSave != null) {
        val preview = remember(toSave) { vm.previewCard(toSave) }
        CardEditorSheet(
            isNew = true,
            family = toSave.family,
            initialType = preview.type,
            suggestedType = toSave.suggestedType,
            initialNickname = "",
            image = null,
            summary = "${formatYen(toSave.balance)} · ${toSave.transactions.size} records on the card",
            onDismiss = { savingPending = false },
            onPickPhoto = null,
            onRemovePhoto = null,
            onSave = { type, nickname, useAsIcon ->
                savingPending = false
                vm.confirmPending(type, nickname, useAsIcon)
            },
        )
    } else if (savingPending) {
        savingPending = false
    }

    editing?.let { id ->
        val card = cards.firstOrNull { it.id == id }
        if (card == null) {
            editing = null
        } else {
            CardEditorSheet(
                isNew = false,
                family = card.family,
                initialType = card.type,
                suggestedType = null,
                initialNickname = card.nickname.orEmpty(),
                image = card.customImage?.let(vm::imageFile),
                summary = null,
                onDismiss = { editing = null },
                onPickPhoto = { holdIcon(context); photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onRemovePhoto = { vm.setCustomImage(id, null) },
                onSave = { type, nickname, useAsIcon ->
                    editing = null
                    vm.updateCard(id, type, nickname)
                    if (useAsIcon) vm.selectIcon(type.id)
                },
            )
        }
    }
}

/** Keeps a queued icon change from closing the app while the user is in another screen. */
private fun holdIcon(context: Context) {
    (context.applicationContext as io.github.grillza.icreader.IcReaderApp).icons.hold = true
}

private fun openNfcSettings(context: Context) {
    holdIcon(context)
    try {
        context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
    }
}

private fun exportCsv(context: Context, card: SavedCard, station: (StationRef) -> io.github.grillza.icreader.data.Station?) {
    fun esc(s: String) = if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
    val csv = buildString {
        appendLine("date,time,type,from,to,amount,balance,record,raw")
        card.transactions.forEach { tx ->
            val type = when (card.family) {
                CardFamily.TRANSIT -> Codes.process(tx.process)
                CardFamily.EDY -> Codes.edyType(tx.process)
                CardFamily.NANACO -> Codes.nanacoType(tx.process)
                CardFamily.WAON -> ""
            }
            appendLine(
                listOf(
                    tx.date.toString(),
                    tx.secondOfDay?.let(::formatTime).orEmpty(),
                    type,
                    tx.entry?.let(station)?.name.orEmpty(),
                    tx.exit?.let(station)?.name.orEmpty(),
                    tx.amount?.toString().orEmpty(),
                    tx.balance.toString(),
                    tx.seq.toString(),
                    tx.raw,
                ).joinToString(",") { esc(it) },
            )
        }
    }
    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
    val safeName = card.title.replace(Regex("[^A-Za-z0-9_-]+"), "_")
    val file = File(dir, "$safeName-history.csv")
    // UTF-8 BOM so spreadsheet apps show station names correctly.
    file.writeText(Char(0xFEFF) + csv)
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    holdIcon(context)
    context.startActivity(Intent.createChooser(send, "Export history"))
}
