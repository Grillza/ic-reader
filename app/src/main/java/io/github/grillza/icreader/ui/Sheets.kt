package io.github.grillza.icreader.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contactless
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HideImage
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.grillza.icreader.data.CardFamily
import io.github.grillza.icreader.data.CardType
import java.io.File

/**
 * Picks the card type and nickname. Used both to save a newly scanned card and to edit a saved
 * one ([isNew]); photo controls only apply to saved cards.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CardEditorSheet(
    isNew: Boolean,
    family: CardFamily,
    initialType: CardType,
    suggestedTypes: List<CardType>,
    initialNickname: String,
    image: File?,
    summary: String?,
    onDismiss: () -> Unit,
    onPickPhoto: (() -> Unit)?,
    onRemovePhoto: (() -> Unit)?,
    onSave: (type: CardType, nickname: String, useAsIcon: Boolean) -> Unit,
) {
    var type by rememberSaveable { mutableStateOf(initialType) }
    var nickname by rememberSaveable { mutableStateOf(initialNickname) }
    var useAsIcon by rememberSaveable { mutableStateOf(false) }
    val options = CardType.entries.filter { it.family == family }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
        ) {
            Text(if (isNew) "Save card" else "Edit card", style = MaterialTheme.typography.headlineSmall)
            if (summary != null) {
                Spacer(Modifier.height(4.dp))
                Text(summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))
            CardArt(type, image = image, modifier = Modifier.padding(horizontal = 32.dp))
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it.take(40) },
                label = { Text("Nickname (optional)") },
                placeholder = { Text(type.displayName) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (options.size > 1) {
                Spacer(Modifier.height(20.dp))
                Text("Card type", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (suggestedTypes.size > 1 && isNew) {
                        "The card doesn't say who issued it. From where it's been used it's likely " +
                            suggestedTypes.joinToString(" or ") { it.displayName } + ", so pick yours."
                    } else if (suggestedTypes.isNotEmpty() && isNew) {
                        "Suggested from where the card has been used. Transit cards all share one format, so check it's right."
                    } else {
                        "Transit cards all share one format, so the brand can't be read from the card."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    options.forEach { option ->
                        TypeChip(option, selected = option == type, suggested = option in suggestedTypes) { type = option }
                    }
                }
            }
            if (onPickPhoto != null) {
                Spacer(Modifier.height(20.dp))
                Text("Card picture", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Use a photo of your own card instead of the generated artwork. You fit it to the card's outline next. It shows on your watch too.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onPickPhoto) {
                        Icon(Icons.Default.AddPhotoAlternate, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (image == null) "Choose photo" else "Change photo")
                    }
                    if (image != null && onRemovePhoto != null) {
                        OutlinedButton(onClick = onRemovePhoto) {
                            Icon(Icons.Default.HideImage, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Remove")
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().clickable { useAsIcon = !useAsIcon },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(useAsIcon, onCheckedChange = { useAsIcon = it })
                Text("Use ${type.displayName} as the app icon")
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = { onSave(type, nickname, useAsIcon) }, modifier = Modifier.fillMaxWidth()) {
                Text(if (isNew) "Save card" else "Done")
            }
        }
    }
}

@Composable
private fun TypeChip(type: CardType, selected: Boolean, suggested: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(96.dp)) {
        Box(
            Modifier
                .border(
                    if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else BorderStroke(0.dp, MaterialTheme.colorScheme.surface),
                    shape,
                )
                .padding(3.dp)
                .clickable(onClick = onClick),
        ) {
            CardArt(type, corner = 8.dp, elevation = 1.dp)
            if (selected) {
                Icon(
                    Icons.Default.CheckCircle,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).size(18.dp),
                )
            }
        }
        Text(
            if (suggested) "Likely" else " ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** Prompt shown while waiting for a card. Reading happens whenever the app is open; this just guides. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanSheet(status: ScanStatus, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(160.dp), contentAlignment = Alignment.Center) {
                when (status) {
                    ScanStatus.Reading -> CircularProgressIndicator(Modifier.size(72.dp), strokeWidth = 6.dp)
                    is ScanStatus.Failed -> Icon(Icons.Default.ErrorOutline, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.error)
                    ScanStatus.Idle -> Pulse()
                }
            }
            Text(
                when (status) {
                    ScanStatus.Idle -> "Ready to scan"
                    ScanStatus.Reading -> "Reading…"
                    is ScanStatus.Failed -> "Couldn't read the card"
                },
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                when (status) {
                    ScanStatus.Idle -> "Hold your IC card flat against the back of the phone, near the camera."
                    ScanStatus.Reading -> "Keep the card still."
                    is ScanStatus.Failed -> status.message + "\nTap the card again to retry."
                },
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Pulse() {
    val t = rememberInfiniteTransition(label = "pulse")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart), label = "p")
    val color = MaterialTheme.colorScheme.primary
    Canvas(Modifier.size(160.dp)) {
        for (i in 0..2) {
            val f = (p + i / 3f) % 1f
            drawCircle(color.copy(alpha = (1f - f) * 0.5f), radius = size.minDimension / 2 * (0.35f + 0.65f * f), center = Offset(size.width / 2, size.height / 2), style = Stroke(4.dp.toPx()))
        }
    }
    Icon(Icons.Default.Contactless, null, Modifier.size(56.dp), tint = color)
}
