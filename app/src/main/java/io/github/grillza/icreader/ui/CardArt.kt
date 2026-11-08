package io.github.grillza.icreader.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import io.github.grillza.icreader.data.CardType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Standard ID-1 card proportions. */
const val CARD_RATIO = 1.586f

/**
 * A card face: the user's photo if they set one, otherwise generated artwork in the brand's
 * colours. [overlay] draws on top (title, balance, …).
 */
@Composable
fun CardArt(
    type: CardType,
    modifier: Modifier = Modifier,
    image: File? = null,
    corner: Dp = 18.dp,
    elevation: Dp = 6.dp,
    showBrand: Boolean = true,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val shape = RoundedCornerShape(corner)
    val bitmap by produceState<ImageBitmap?>(null, image) {
        value = image?.let { f ->
            withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(f.path)?.asImageBitmap() }.getOrNull() }
        }
    }
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(CARD_RATIO)
            .shadow(elevation, shape)
            .clip(shape),
    ) {
        val photo = bitmap
        if (photo != null) {
            Image(photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.55f)),
                ),
            )
        } else {
            GeneratedArt(type, showBrand)
        }
        overlay()
    }
}

@Composable
private fun BoxScope.GeneratedArt(type: CardType, showBrand: Boolean) {
    val start = Color(type.colorStart)
    val end = Color(type.colorEnd)
    val on = Color(type.onColor)
    Canvas(Modifier.matchParentSize()) {
        drawRect(Brush.linearGradient(listOf(start, end), start = Offset.Zero, end = Offset(size.width, size.height)))
        // Sweeping bands across the right side.
        val r = size.height * 1.1f
        for (i in 0..2) {
            drawCircle(
                color = Color.White.copy(alpha = 0.10f - i * 0.025f),
                radius = r - i * size.height * 0.22f,
                center = Offset(size.width * 1.02f, size.height * 1.15f),
                style = Stroke(width = size.height * 0.09f),
            )
        }
        // IC chip.
        val chipW = size.width * 0.13f
        val chipH = chipW * 0.78f
        val chipTopLeft = Offset(size.width * 0.075f, size.height * 0.42f)
        drawRoundRect(
            Brush.linearGradient(listOf(Color(0xFFF3DA8B), Color(0xFFC9A44A)), chipTopLeft, chipTopLeft + Offset(chipW, chipH)),
            topLeft = chipTopLeft,
            size = Size(chipW, chipH),
            cornerRadius = CornerRadius(chipW * 0.16f),
        )
        val line = Color(0x66806020)
        drawLine(line, chipTopLeft + Offset(0f, chipH / 2), chipTopLeft + Offset(chipW, chipH / 2), 1.2f)
        drawLine(line, chipTopLeft + Offset(chipW / 2, 0f), chipTopLeft + Offset(chipW / 2, chipH), 1.2f)
        // Contactless mark.
        val c = Offset(size.width * 0.89f, size.height * 0.17f)
        for (i in 1..3) {
            val rr = size.height * 0.035f * i
            drawArc(
                color = on.copy(alpha = 0.85f),
                startAngle = -45f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = c - Offset(rr, rr),
                size = Size(rr * 2, rr * 2),
                style = Stroke(width = size.height * 0.018f, cap = StrokeCap.Round),
            )
        }
    }
    if (showBrand) {
        Column(Modifier.align(Alignment.TopStart).padding(start = 18.dp, top = 14.dp)) {
            Text(
                type.displayName,
                color = on,
                fontSize = brandSize(type),
                fontWeight = FontWeight.Black,
                fontStyle = if (type == CardType.SUICA || type == CardType.ICOCA) FontStyle.Italic else FontStyle.Normal,
                letterSpacing = 0.5.sp,
            )
            Text(type.nameJa, color = on.copy(alpha = 0.85f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}

private fun brandSize(type: CardType): TextUnit = if (type.displayName.length > 8) 22.sp else 28.sp

/** Standard overlay: name bottom-left, balance bottom-right. */
@Composable
fun BoxScope.CardOverlay(type: CardType, hasPhoto: Boolean, title: String?, balance: Int?, large: Boolean = false) {
    val on = if (hasPhoto) Color.White else Color(type.onColor)
    if (title != null) {
        Text(
            title,
            color = on.copy(alpha = 0.9f),
            fontSize = if (large) 15.sp else 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 18.dp, bottom = 16.dp, end = 120.dp),
        )
    }
    if (balance != null) {
        Text(
            formatYen(balance),
            color = on,
            fontSize = if (large) 32.sp else 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 12.dp),
        )
    }
}
