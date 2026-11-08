package io.github.grillza.icreader.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
    val shape = if (type.family == io.github.grillza.icreader.data.CardFamily.TRANSIT) NotchedCardShape(corner) else RoundedCornerShape(corner)
    val bitmap by produceState<ImageBitmap?>(null, image) {
        value = image?.let { f ->
            withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(f.path)?.asImageBitmap() }.getOrNull() }
        }
    }
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(CARD_RATIO)
            // Shadows don't follow the notch reliably (a pale patch shows there), so notched
            // cards get a fine edge line instead.
            .then(if (shape is NotchedCardShape) Modifier else Modifier.shadow(elevation, shape))
            .clip(shape)
            .border(0.75.dp, Color.Black.copy(alpha = if (shape is NotchedCardShape) 0.18f else 0.06f), shape),
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

/** Balance in the bottom-right corner, on a pill so it reads over any art or photo. */
@Composable
fun BoxScope.CardBalance(balance: Int, large: Boolean = false) {
    Text(
        formatYen(balance),
        color = Color.White,
        fontSize = if (large) 26.sp else 20.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 12.dp, bottom = 12.dp)
            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 4.dp),
    )
}
