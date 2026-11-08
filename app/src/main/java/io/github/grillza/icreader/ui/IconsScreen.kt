package io.github.grillza.icreader.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.grillza.icreader.icon.APP_ICONS
import io.github.grillza.icreader.icon.AppIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconsScreen(currentId: String, snackbar: SnackbarHostState, onBack: () -> Unit, onSelect: (AppIcon) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App icon") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(96.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Pick the icon shown on your home screen. Match it to the card you use most.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                )
            }
            items(APP_ICONS, key = { it.id }) { icon ->
                IconOption(icon, selected = icon.id == currentId) { onSelect(icon) }
            }
        }
    }
}

@Composable
private fun IconOption(icon: AppIcon, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 6.dp),
    ) {
        Box(
            Modifier
                .size(72.dp)
                .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
                .padding(5.dp),
            contentAlignment = Alignment.Center,
        ) {
            AppIconPreview(icon, Modifier.size(62.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(icon.label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
    }
}

/** Renders an adaptive icon the way a circular launcher mask would. */
@Composable
fun AppIconPreview(icon: AppIcon, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(CircleShape).background(colorResource(icon.background)),
        contentAlignment = Alignment.Center,
    ) {
        // The foreground is 108dp with a 72dp visible area, so draw it 1.5x the mask size.
        Image(painterResource(icon.foreground), null, Modifier.requiredSize(93.dp))
    }
}
