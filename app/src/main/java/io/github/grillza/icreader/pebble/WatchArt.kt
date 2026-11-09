package io.github.grillza.icreader.pebble

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import androidx.compose.ui.Alignment
import androidx.core.graphics.createBitmap
import androidx.core.graphics.get
import androidx.core.graphics.withClip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import io.github.grillza.icreader.data.CardFamily
import io.github.grillza.icreader.data.CardType
import io.github.grillza.icreader.ui.Motif
import io.github.grillza.icreader.ui.design
import io.github.grillza.icreader.ui.drawCardBackground
import java.io.File
import java.util.zip.CRC32

/** A card face for the watch: RLE-compressed Pebble colours (see [WatchProtocol.rle]). */
class WatchArt(val rle: ByteArray) {
    /** Lets the watch keep art it already has when the card list is sent again. */
    val hash: Long = CRC32().apply { update(rle) }.value
}

/**
 * Draws card faces the way the app does (same designs, or the user's photo), at the watch's size
 * and in flat colours.
 */
object WatchArtRenderer {

    /** [mono]: for a black-and-white screen, a white card with black shapes. */
    fun render(type: CardType, photo: File?, width: Int, height: Int, maxBytes: Int, mono: Boolean): WatchArt? {
        if (photo != null) {
            val art = draw(type, photo, width, height, mono)
            // A photo rarely compresses well; fall back to the design if it's too big.
            if (art != null && art.rle.size <= maxBytes) return art
        }
        return draw(type, null, width, height, mono)?.takeIf { it.rle.size <= maxBytes }
    }

    private fun draw(type: CardType, photo: File?, width: Int, height: Int, mono: Boolean): WatchArt? {
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        val outline = outline(type, width.toFloat(), height.toFloat())
        val image = photo?.let { runCatching { BitmapFactory.decodeFile(it.path) }.getOrNull() }
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(canvas), Size(width.toFloat(), height.toFloat())) {
            clipPath(outline.asComposePath()) {
                if (image == null) drawCardBackground(type, flat = true, mono = mono)
            }
        }
        if (image != null) {
            canvas.withClip(outline) {
                drawBitmap(image, centerCrop(image.width, image.height, width, height), RectF(0f, 0f, width.toFloat(), height.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
            }
            image.recycle()
        } else {
            drawBrand(canvas, bitmap, type, width.toFloat(), height.toFloat(), mono)
        }
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        bitmap.recycle()
        return WatchArt(WatchProtocol.rle(pixels))
    }

    /** The card's outline with rounded corners, and the notch on transit cards (as in CardArt). */
    private fun outline(type: CardType, w: Float, h: Float): android.graphics.Path {
        val r = w * 0.055f
        val card = android.graphics.Path().apply { addRoundRect(RectF(0f, 0f, w, h), r, r, android.graphics.Path.Direction.CW) }
        if (type.family != CardFamily.TRANSIT) return card
        val depth = (w * 0.018f).coerceAtLeast(2f)
        val notch = android.graphics.Path().apply {
            moveTo(w + 1f, h * 0.69f)
            lineTo(w - depth, h * 0.725f)
            lineTo(w + 1f, h * 0.85f)
            close()
        }
        return android.graphics.Path().apply { op(card, notch, android.graphics.Path.Op.DIFFERENCE) }
    }

    private fun centerCrop(srcW: Int, srcH: Int, dstW: Int, dstH: Int): Rect {
        val scale = maxOf(dstW.toFloat() / srcW, dstH.toFloat() / srcH)
        val w = (dstW / scale).toInt()
        val h = (dstH / scale).toInt()
        val x = (srcW - w) / 2
        val y = (srcH - h) / 2
        return Rect(x, y, x + w, y + h)
    }

    /** The brand name, placed as GeneratedArt places it. */
    private fun drawBrand(canvas: Canvas, bitmap: Bitmap, type: CardType, w: Float, h: Float, mono: Boolean) {
        val d = design(type)
        val scale = when {
            d.brandShift != 0f -> 0.075f
            d.brand.length > 7 -> 0.085f
            else -> 0.11f
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = (d.flatBrandColor ?: d.brandColor).toArgb()
            textSize = w * scale * 1.15f  // a little larger: the watch has few pixels to spare
            typeface = if (Build.VERSION.SDK_INT >= 28) {
                Typeface.create(Typeface.DEFAULT, 900, d.italic)
            } else {
                Typeface.create(Typeface.DEFAULT, if (d.italic) Typeface.BOLD_ITALIC else Typeface.BOLD)
            }
            if (d.motif == Motif.SUICA_PANEL) letterSpacing = 0.12f
        }
        val fm = paint.fontMetrics
        val textW = paint.measureText(d.brand)
        val pad = w * 0.06f
        val (x, baseline) = when {
            d.brandExact || d.brandAlign == Alignment.Center ->
                (w - textW) / 2 + w * d.brandShift to h / 2 - (fm.ascent + fm.descent) / 2
            d.motif == Motif.SUICA_PANEL -> w * 0.09f to h - w * 0.045f - fm.descent
            d.brandAlign == Alignment.BottomStart -> pad to h - w * 0.05f - fm.descent
            d.brandAlign == Alignment.BottomEnd -> w - pad - textW to h - w * 0.04f - fm.descent
            d.brandAlign == Alignment.TopEnd -> w - pad - textW to w * 0.04f - fm.ascent
            else -> pad to w * 0.04f - fm.ascent
        }
        if (mono) {
            // Black or white, whichever stands out from what's behind the text.
            val cx = (x + textW / 2).toInt().coerceIn(0, bitmap.width - 1)
            val cy = (baseline + (fm.ascent + fm.descent) / 2).toInt().coerceIn(0, bitmap.height - 1)
            val behind = bitmap[cx, cy]
            val light = android.graphics.Color.red(behind) + android.graphics.Color.green(behind) + android.graphics.Color.blue(behind) > 384
            paint.color = if (light) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        }
        canvas.drawText(d.brand, x, baseline, paint)
    }

    /** The card's main colour as a Pebble colour, shown until its art arrives. */
    fun placeholderColor(type: CardType): Int = WatchProtocol.pebbleColor(type.colorStart.toInt())
}
