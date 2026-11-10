package io.github.grillza.icreader.ui

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withClip
import androidx.core.graphics.withSave
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Size of a saved card picture, at the card's proportions. */
const val CARD_PHOTO_WIDTH = 1200
val CARD_PHOTO_HEIGHT = (CARD_PHOTO_WIDTH / CARD_RATIO).roundToInt()

/**
 * Where the photo sits in the card frame. Independent of the frame's size on screen, so the
 * saved picture matches what was shown: [scale] is relative to the smallest scale at which the
 * photo still covers the card, and [offsetX]/[offsetY] are in card widths.
 */
data class CropPlacement(
    val quarterTurns: Int = 0,
    val angle: Float = 0f,
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
) {
    val degrees: Float get() = quarterTurns * 90f + angle

    /** Pixels per photo pixel for a frame of [w] × [h]. */
    fun pixelScale(image: Bitmap, w: Float, h: Float): Float {
        val rad = Math.toRadians(degrees.toDouble())
        val c = abs(cos(rad)).toFloat()
        val s = abs(sin(rad)).toFloat()
        // The rotated photo covers the frame when it's at least as large as the frame's
        // bounding box measured along the photo's own axes.
        val cover = maxOf((w * c + h * s) / image.width, (w * s + h * c) / image.height)
        return cover * scale
    }

    /**
     * This placement with the photo moved back, if needed, so it still covers the whole card
     * (no empty corners). [w] × [h] is any frame size at the card's proportions.
     */
    fun clamped(image: Bitmap, w: Float = CARD_RATIO, h: Float = 1f): CropPlacement {
        val rad = Math.toRadians(degrees.toDouble())
        val c = cos(rad).toFloat()
        val sn = sin(rad).toFloat()
        val px = pixelScale(image, w, h)
        // Room to move along the photo's own axes, in frame pixels.
        val roomU = maxOf(0f, image.width * px / 2 - (w * abs(c) + h * abs(sn)) / 2)
        val roomV = maxOf(0f, image.height * px / 2 - (w * abs(sn) + h * abs(c)) / 2)
        val ox = offsetX * w
        val oy = offsetY * w
        val u = (ox * c + oy * sn).coerceIn(-roomU, roomU)
        val v = (-ox * sn + oy * c).coerceIn(-roomV, roomV)
        return copy(offsetX = (u * c - v * sn) / w, offsetY = (u * sn + v * c) / w)
    }

    companion object {
        const val MAX_SCALE = 8f
        const val MAX_ANGLE = 45f
    }
}

/**
 * The card outline, [w] × [h]: rounded corners, and the notch on the right edge of transit
 * cards (the same shape as [NotchedCardShape] and the watch's card faces).
 */
fun cardOutlinePath(w: Float, h: Float, notched: Boolean): android.graphics.Path {
    val r = w * 0.055f
    val card = android.graphics.Path().apply { addRoundRect(RectF(0f, 0f, w, h), r, r, android.graphics.Path.Direction.CW) }
    if (!notched) return card
    val depth = (w * 0.018f).coerceAtLeast(2f)
    val notch = android.graphics.Path().apply {
        moveTo(w + 1f, h * 0.69f)
        lineTo(w - depth, h * 0.725f)
        lineTo(w + 1f, h * 0.85f)
        close()
    }
    return android.graphics.Path().apply { op(card, notch, android.graphics.Path.Op.DIFFERENCE) }
}

/**
 * Renders the card picture: [image] placed as [placement] says, cut to the card's outline
 * (corners and notch transparent), at [CARD_PHOTO_WIDTH] × [CARD_PHOTO_HEIGHT].
 */
fun renderCardPhoto(image: Bitmap, placement: CropPlacement, notched: Boolean): Bitmap {
    val w = CARD_PHOTO_WIDTH.toFloat()
    val h = CARD_PHOTO_HEIGHT.toFloat()
    val out = createBitmap(CARD_PHOTO_WIDTH, CARD_PHOTO_HEIGHT)
    val canvas = android.graphics.Canvas(out)
    canvas.withClip(cardOutlinePath(w, h, notched)) {
        drawColor(android.graphics.Color.BLACK)  // shows only if the photo is moved off an edge
        withSave {
            translate(w / 2 + placement.offsetX * w, h / 2 + placement.offsetY * w)
            rotate(placement.degrees)
            val s = placement.pixelScale(image, w, h)
            scale(s, s)
            drawBitmap(image, -image.width / 2f, -image.height / 2f, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        }
    }
    return out
}

/**
 * Full-screen editor for a card picture: the photo behind a card-shaped frame (with the notch
 * on transit cards). Drag to move, pinch to zoom, twist or use the slider to straighten, and
 * the buttons turn it a quarter at a time.
 */
@Composable
fun PhotoCropDialog(image: Bitmap, notched: Boolean, onDone: (CropPlacement?) -> Unit) {
    var quarter by rememberSaveable { mutableIntStateOf(0) }
    var angle by rememberSaveable { mutableFloatStateOf(0f) }
    var scale by rememberSaveable { mutableFloatStateOf(1f) }
    var offsetX by rememberSaveable { mutableFloatStateOf(0f) }
    var offsetY by rememberSaveable { mutableFloatStateOf(0f) }
    val placement = CropPlacement(quarter, angle, scale, offsetX, offsetY)

    // Keeps the photo covering the card after every move, zoom or turn.
    fun settle() {
        val p = CropPlacement(quarter, angle, scale, offsetX, offsetY).clamped(image)
        offsetX = p.offsetX
        offsetY = p.offsetY
    }
    val bitmap: ImageBitmap = remember(image) { image.asImageBitmap() }
    var frame by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }

    fun turn(by: Int) {
        quarter = ((quarter + by) % 4 + 4) % 4
        // Keep the same part of the photo in the middle of the card.
        val (x, y) = offsetX to offsetY
        offsetX = if (by > 0) -y else y
        offsetY = if (by > 0) x else -x
        settle()
    }

    Dialog(onDismissRequest = { onDone(null) }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(Color.Black)) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { onDone(null) }) { Text("Cancel", color = Color.White) }
                Spacer(Modifier.weight(1f))
                Text("Fit your card in the frame", color = Color.White)
                Spacer(Modifier.weight(1f))
                Button(onClick = { onDone(placement) }) { Text("Save") }
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTransformGestures { centroid, pan, zoom, rotation ->
                            val f = frame
                            if (f.width <= 0f) return@detectTransformGestures
                            val newScale = (scale * zoom).coerceIn(1f, CropPlacement.MAX_SCALE)
                            val newAngle = (angle + rotation).coerceIn(-CropPlacement.MAX_ANGLE, CropPlacement.MAX_ANGLE)
                            val zoomBy = newScale / scale
                            val rad = Math.toRadians((newAngle - angle).toDouble())
                            // Zoom and turn about the fingers, then follow the pan.
                            val cx = (centroid.x - f.center.x) / f.width
                            val cy = (centroid.y - f.center.y) / f.width
                            val dx = offsetX - cx
                            val dy = offsetY - cy
                            offsetX = ((dx * cos(rad) - dy * sin(rad)) * zoomBy + cx + pan.x / f.width).toFloat()
                            offsetY = ((dx * sin(rad) + dy * cos(rad)) * zoomBy + cy + pan.y / f.width).toFloat()
                            scale = newScale
                            angle = newAngle
                            settle()
                        }
                    },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val pad = 24.dp.toPx()
                    val fw = minOf(size.width - 2 * pad, (size.height - 2 * pad) * CARD_RATIO)
                    val fh = fw / CARD_RATIO
                    val f = androidx.compose.ui.geometry.Rect(Offset((size.width - fw) / 2, (size.height - fh) / 2), Size(fw, fh))
                    frame = f
                    val s = placement.pixelScale(image, fw, fh)
                    withTransform({
                        translate(f.center.x + offsetX * fw, f.center.y + offsetY * fw)
                        rotate(placement.degrees, pivot = Offset.Zero)
                        scale(s, s, pivot = Offset.Zero)
                    }) {
                        drawImage(
                            bitmap,
                            dstOffset = IntOffset(-image.width / 2, -image.height / 2),
                            dstSize = IntSize(image.width, image.height),
                        )
                    }
                    val card = cardOutlinePath(fw, fh, notched).asComposePath().apply { translate(f.topLeft) }
                    val shade = Path().apply {
                        addRect(androidx.compose.ui.geometry.Rect(Offset.Zero, size))
                        addPath(card)
                        fillType = PathFillType.EvenOdd
                    }
                    drawPath(shade, Color.Black.copy(alpha = 0.65f))
                    drawPath(card, Color.White, style = Stroke(1.5.dp.toPx()))
                }
            }
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Straighten  ${"%+.1f".format(angle)}°", color = Color.White)
                Slider(
                    value = angle,
                    onValueChange = { angle = it; settle() },
                    valueRange = -CropPlacement.MAX_ANGLE..CropPlacement.MAX_ANGLE,
                    colors = SliderDefaults.colors(inactiveTrackColor = Color.White.copy(alpha = 0.3f)),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { turn(-1) }) {
                        Icon(Icons.AutoMirrored.Filled.RotateLeft, "Rotate left", tint = Color.White)
                    }
                    IconButton(onClick = {
                        quarter = 0; angle = 0f; scale = 1f; offsetX = 0f; offsetY = 0f
                    }) {
                        Icon(Icons.Default.Restore, "Reset", tint = Color.White)
                    }
                    IconButton(onClick = { turn(1) }) {
                        Icon(Icons.AutoMirrored.Filled.RotateRight, "Rotate right", tint = Color.White)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Drag to move, pinch to zoom, twist to turn",
                    color = Color.White.copy(alpha = 0.7f),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
