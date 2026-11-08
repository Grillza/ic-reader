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
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
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
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
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
        }
    }
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
