package io.github.grillza.icreader

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.grillza.icreader.data.CardType
import io.github.grillza.icreader.pebble.WatchArtRenderer
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.clipPath
import io.github.grillza.icreader.ui.drawCardBackground
import io.github.grillza.icreader.ui.drawLabel
import org.junit.runner.RunWith
import java.io.File

/**
 * Renders every card design at the watch sizes and checks it fits what the watch accepts. Also
 * writes the decoded art as PNGs (files/watch-art on the device) to look at.
 */
@RunWith(AndroidJUnit4::class)
class WatchArtTest {

    private val out = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "watch-art").apply { mkdirs() }

    @Test
    fun everyDesignFitsOnEveryWatch() {
        // (width, height, max RLE bytes, black and white) as the watch app asks for them.
        val watches = mapOf(
            "emery" to Quad(164, 103, 6144, false),
            "basalt" to Quad(124, 78, 6144, false),
            "chalk" to Quad(116, 73, 6144, false),
            "diorite" to Quad(124, 78, 6144, true),
            "aplite" to Quad(124, 78, 2048, true),
        )
        for ((name, w) in watches) {
            for (type in CardType.entries) {
                val art = WatchArtRenderer.render(type, null, w.width, w.height, w.maxBytes, w.mono)
                assertNotNull("$name ${type.id}", art)
                assertTrue("$name ${type.id}: ${art!!.rle.size} bytes", art.rle.size <= w.maxBytes)
                save(art.rle, w.width, w.height, File(out, "$name-${type.id}.png"))
            }
        }
    }

    /**
     * A card photo (files/test-photo.jpg on the device if present, else a made-up noisy one),
     * cropped as the app saves it, fits on every watch.
     */
    @Test
    fun photoFitsOnEveryWatch() {
        val supplied = File(out.parentFile, "test-photo.jpg")
        val source = if (supplied.exists()) {
            android.graphics.BitmapFactory.decodeFile(supplied.path)
        } else {
            val rnd = java.util.Random(1)
            val px = IntArray(800 * 600) { i ->
                val x = i % 800
                val y = i / 800
                fun ch(base: Int) = (base + rnd.nextInt(40) - 20).coerceIn(0, 255)
                if ((x - 500) * (x - 500) + (y - 250) * (y - 250) < 150 * 150) {
                    (0xFF shl 24) or (ch(230) shl 16) or (ch(90) shl 8) or ch(150)
                } else {
                    (0xFF shl 24) or (ch(120 + x / 8) shl 16) or (ch(130) shl 8) or ch(140 + y / 8)
                }
            }
            Bitmap.createBitmap(px, 800, 600, Bitmap.Config.ARGB_8888)
        }
        val picture = io.github.grillza.icreader.ui.renderCardPhoto(source, io.github.grillza.icreader.ui.CropPlacement(), notched = true)
        val file = File(out, "photo.png")
        file.outputStream().use { picture.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val watches = mapOf(
            "emery" to Quad(164, 103, 6144, false),
            "basalt" to Quad(124, 78, 6144, false),
            "diorite" to Quad(124, 78, 6144, true),
            "aplite" to Quad(124, 78, 2048, true),
        )
        for ((name, w) in watches) {
            val art = WatchArtRenderer.render(CardType.SUICA, file, w.width, w.height, w.maxBytes, w.mono)
            assertNotNull(name, art)
            assertTrue("$name photo: ${art!!.rle.size} bytes", art.rle.size <= w.maxBytes)
            save(art.rle, w.width, w.height, File(out, "$name-photo.png"))
        }
    }

    /** Not a check: saves each card face in full colour (phone-<id>.png) to compare with the watch's. */
    @Test
    fun savePhoneDesigns() {
        val w = 640f
        val h = w / io.github.grillza.icreader.ui.CARD_RATIO
        for (type in CardType.entries) {
            val bitmap = Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            val outline = io.github.grillza.icreader.ui.cardOutlinePath(w, h, type.family == io.github.grillza.icreader.data.CardFamily.TRANSIT)
            androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(
                androidx.compose.ui.unit.Density(1f), androidx.compose.ui.unit.LayoutDirection.Ltr,
                androidx.compose.ui.graphics.Canvas(canvas), androidx.compose.ui.geometry.Size(w, h),
            ) {
                clipPath(outline.asComposePath()) { drawCardBackground(type, flat = false) }
            }
            io.github.grillza.icreader.ui.design(type).labels.forEach { canvas.drawLabel(it, w, h) }
            File(out, "phone-${type.id}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private data class Quad(val width: Int, val height: Int, val maxBytes: Int, val mono: Boolean)

    /** Decodes the RLE the way the watch does and saves it with the Pebble's colours. */
    private fun save(rle: ByteArray, width: Int, height: Int, file: File) {
        val pixels = IntArray(width * height)
        var p = 0
        for (i in rle.indices step 2) {
            val run = rle[i].toInt() and 0xFF
            val c = rle[i + 1].toInt() and 0xFF
            val argb = if (c shr 6 == 0) 0 else {
                fun ch(v: Int) = v * 85
                (0xFF shl 24) or (ch((c shr 4) and 3) shl 16) or (ch((c shr 2) and 3) shl 8) or ch(c and 3)
            }
            repeat(run) { pixels[p++] = argb }
        }
        assertTrue(p == pixels.size)
        val bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
