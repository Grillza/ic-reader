package io.github.grillza.icreader

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.grillza.icreader.data.CardType
import io.github.grillza.icreader.pebble.WatchArtRenderer
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
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
