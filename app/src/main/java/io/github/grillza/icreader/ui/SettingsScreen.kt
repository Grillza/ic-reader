package io.github.grillza.icreader.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.grillza.icreader.data.StationLanguage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    stationLanguage: StationLanguage,
    onStationLanguage: (StationLanguage) -> Unit,
    onIcons: () -> Unit,
    pebble: AppViewModel.PebbleState,
    watchStationLanguage: StationLanguage,
    onWatchStationLanguage: (StationLanguage) -> Unit,
    onInstallWatchApp: () -> Unit,
    onOpenOnWatch: () -> Unit,
    snackbar: SnackbarHostState,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            SectionHeader("Station names")
            Column(Modifier.selectableGroup()) {
                StationLanguage.entries.forEach { option ->
                    ListItem(
                        headlineContent = { Text(option.label) },
                        supportingContent = { Text(example(option)) },
                        leadingContent = { RadioButton(selected = option == stationLanguage, onClick = null) },
                        modifier = Modifier.selectable(
                            selected = option == stationLanguage,
                            role = Role.RadioButton,
                            onClick = { onStationLanguage(option) },
                        ),
                    )
                }
            }
            Text(
                "English names come from Wikidata and cover most stations; any without one stay in Japanese.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionHeader("Appearance")
            ListItem(
                headlineContent = { Text("App icon") },
                supportingContent = { Text("Match the home-screen icon to your card") },
                leadingContent = { Icon(Icons.Default.Palette, null) },
                modifier = Modifier.selectable(selected = false, onClick = onIcons),
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionHeader("Backup")
            ListItem(
                headlineContent = { Text("Back up cards") },
                supportingContent = { Text("Save your cards, their history and photos to a file") },
                leadingContent = { Icon(Icons.Default.Save, null) },
                modifier = Modifier.selectable(selected = false, onClick = onBackup),
            )
            ListItem(
                headlineContent = { Text("Restore from backup") },
                supportingContent = { Text("Add the cards and history from a backup file") },
                leadingContent = { Icon(Icons.Default.Restore, null) },
                modifier = Modifier.selectable(selected = false, onClick = onRestore),
            )
            Text(
                "Restoring adds to what's here and never removes anything: a card you already have keeps " +
                    "its newer balance and gains the backup's older history.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            PebbleSection(pebble, watchStationLanguage, onWatchStationLanguage, onInstallWatchApp, onOpenOnWatch)
        }
    }
}

@Composable
private fun PebbleSection(
    pebble: AppViewModel.PebbleState,
    stationLanguage: StationLanguage,
    onStationLanguage: (StationLanguage) -> Unit,
    onInstall: () -> Unit,
    onOpen: () -> Unit,
) {
    SectionHeader("Pebble watch")
    val status = when {
        !pebble.pebbleApp -> "Pebble app not installed"
        pebble.watches.isEmpty() -> "No watch connected"
        pebble.watchAppOpen -> "${pebble.watches.joinToString()} · IC Reader open"
        else -> "Connected to ${pebble.watches.joinToString()}"
    }
    ListItem(
        headlineContent = { Text(status) },
        supportingContent = {
            Text(
                if (pebble.pebbleApp) {
                    "Your watch shows each card's balance, when it was last scanned and its history."
                } else {
                    "Install the Pebble app and pair your watch to see your cards on it."
                },
            )
        },
        leadingContent = { Icon(Icons.Default.Watch, null) },
    )
    ListItem(
        headlineContent = { Text("Install on watch") },
        supportingContent = { Text("Adds IC Reader to your watch through the Pebble app") },
        leadingContent = { Icon(Icons.Default.Download, null) },
        modifier = Modifier.selectable(selected = false, enabled = pebble.pebbleApp, onClick = onInstall),
    )
    ListItem(
        headlineContent = { Text("Open on watch") },
        supportingContent = { Text("UP and DOWN switch cards; SELECT shows the history") },
        leadingContent = { Icon(Icons.Default.PlayArrow, null) },
        modifier = Modifier.selectable(selected = false, enabled = pebble.watches.isNotEmpty(), onClick = onOpen),
    )
    Text(
        "Station names on the watch",
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
    )
    Column(Modifier.selectableGroup()) {
        StationLanguage.entries.forEach { option ->
            ListItem(
                headlineContent = { Text(option.label) },
                supportingContent = { Text(example(option)) },
                leadingContent = { RadioButton(selected = option == stationLanguage, onClick = null) },
                modifier = Modifier.selectable(
                    selected = option == stationLanguage,
                    role = Role.RadioButton,
                    onClick = { onStationLanguage(option) },
                ),
            )
        }
    }
    Text(
        "Japanese needs the Japanese language pack on the watch (Pebble app → watch settings → Language); " +
            "without it, Japanese names show blank. Takes effect the next time you open a card's history.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

private fun example(language: StationLanguage) = when (language) {
    StationLanguage.JAPANESE -> "押上 → 人形町 · 1号線浅草"
    StationLanguage.ENGLISH -> "Oshiage → Ningyōchō · Toei"
    StationLanguage.BOTH -> "Oshiage (押上) → Ningyōchō (人形町) · Toei"
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}
