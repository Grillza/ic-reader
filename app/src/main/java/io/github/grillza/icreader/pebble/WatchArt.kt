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
import androidx.core.graphics.scale
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
import io.github.grillza.icreader.ui.cardOutlinePath
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
            val pixels = drawPhoto(type, photo, width, height)
            if (pixels != null) fitPhoto(pixels, width, mono, maxBytes)?.let { return WatchArt(it) }
        }
        val pixels = drawDesign(type, width, height, mono)
        val colours = WatchColors.vivid(pixels, photo = false).let { if (mono) IntArray(it.size) { i -> WatchProtocol.pebbleColor(it[i]) } else WatchColors.toPebble(it) }
        return WatchArt(WatchProtocol.rlePebble(colours)).takeIf { it.rle.size <= maxBytes }
    }

    private fun drawDesign(type: CardType, width: Int, height: Int, mono: Boolean): IntArray {
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        val outline = cardOutlinePath(width.toFloat(), height.toFloat(), type.family == CardFamily.TRANSIT)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(canvas), Size(width.toFloat(), height.toFloat())) {
            clipPath(outline.asComposePath()) { drawCardBackground(type, flat = true, mono = mono) }
        }
        drawBrand(canvas, bitmap, type, width.toFloat(), height.toFloat(), mono)
        return bitmap.pixels().also { bitmap.recycle() }
    }

    /** The user's card picture (already card-shaped), scaled to the watch, or null if unreadable. */
    private fun drawPhoto(type: CardType, photo: File, width: Int, height: Int): IntArray? {
        val image = runCatching { BitmapFactory.decodeFile(photo.path) }.getOrNull()?.let { halveDown(it, width * 2) }
            ?: return null
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        // Older pictures were saved as plain rectangles, so cut the outline here too.
        canvas.withClip(cardOutlinePath(width.toFloat(), height.toFloat(), type.family == CardFamily.TRANSIT)) {
            drawBitmap(image, centerCrop(image.width, image.height, width, height), RectF(0f, 0f, width.toFloat(), height.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
        }
        image.recycle()
        return bitmap.pixels().also { bitmap.recycle() }
    }

    /**
     * A photo in the watch's 64 colours is mostly one- and two-pixel runs, far too big to send.
     * Flattens it a little more each time until it fits in [maxBytes], keeping strong edges.
     */
    private fun fitPhoto(pixels: IntArray, width: Int, mono: Boolean, maxBytes: Int): ByteArray? {
        val vivid = WatchColors.vivid(WatchColors.smooth(pixels, width), photo = true)
        if (mono) {
            val bw = WatchColors.mono(vivid)
            for (minRun in 1..16) {
                val rle = WatchProtocol.rlePebble(WatchColors.mergeShortRuns(bw, width, minRun))
                if (rle.size <= maxBytes) return rle
            }
            return null
        }
        val colours = WatchColors.tidy(WatchColors.toPebble(vivid, photo = true), width)
        for (threshold in FLATTEN_STEPS) {
            val rle = WatchProtocol.rlePebble(WatchColors.flattenRows(colours, width, threshold))
            if (rle.size <= maxBytes) return rle
        }
        val flat = WatchColors.flattenRows(colours, width, FLATTEN_STEPS.last())
        for (minRun in 2..16) {
            val rle = WatchProtocol.rlePebble(WatchColors.mergeShortRuns(flat, width, minRun))
            if (rle.size <= maxBytes) return rle
        }
        return null
    }

    /** Thresholds for [WatchColors.flattenRows], gentlest first. */
    private val FLATTEN_STEPS = intArrayOf(0, 400, 800, 1300, 2000, 3000, 4500, 6500, 9000, 12000)

    /**
     * Halves [image] until it's less than twice [minWidth] wide. Shrinking a lot in one step
     * skips most pixels, which breaks up thin lines like a card's lettering.
     */
    private fun halveDown(image: Bitmap, minWidth: Int): Bitmap {
        var current = image
        while (current.width / 2 >= minWidth) {
            val half = current.scale(current.width / 2, current.height / 2)
            if (current !== image) current.recycle()
            current = half
        }
        if (current !== image) image.recycle()
        return current
    }

    private fun Bitmap.pixels(): IntArray = IntArray(width * height).also { getPixels(it, 0, width, 0, 0, width, height) }

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
    fun placeholderColor(type: CardType): Int = WatchColors.toPebble(WatchColors.vivid(intArrayOf(type.colorStart.toInt()), photo = false)[0])
}
