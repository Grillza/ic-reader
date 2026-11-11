package io.github.grillza.icreader

import io.github.grillza.icreader.pebble.WatchColors
import io.github.grillza.icreader.pebble.WatchProtocol
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchColorsTest {

    @Test
    fun silverBecomesWhiteAndColoursGetStronger() {
        val silver = 0xFFC8CACD.toInt()
        val suicaGreen = 0xFF62BD2F.toInt()
        val transparent = 0x00000000
        val out = WatchColors.vivid(intArrayOf(silver, suicaGreen, transparent), photo = false)
        assertEquals(0xFF, WatchColors.toPebble(out[0]))  // white
        // Plain rounding gave (85, 170, 85), a grey-green on the screen; matched by how the
        // screen shows it, it's (0, 170, 0), the deepest green there.
        assertEquals(0xC0 or (0 shl 4) or (2 shl 2) or 0, WatchColors.toPebble(out[1]))
        assertEquals(transparent, out[2])
    }

    @Test
    fun matchesByLookOnTheScreen() {
        assertEquals(0xFF, WatchColors.toPebble(0xFFFFFFFF.toInt()))
        // Full yellow (0xFF on the watch) shows as pale yellow, still nearer than any orange.
        assertEquals(0xFC, WatchColors.toPebble(0xFFF3E63A.toInt()))
        // A mid grey stays grey (0xD5 = 85,85,85 or 0xEA = 170,170,170), not grey-green.
        assertTrue(WatchColors.toPebble(0xFF808080.toInt()) in setOf(0xD5, 0xEA))
        assertEquals(0xC0, WatchColors.toPebble(0xFF000000.toInt()))
        assertEquals(0, WatchColors.toPebble(0x00FFFFFF))
    }

    @Test
    fun photoLevelsStretchToFullRange() {
        val dull = IntArray(100) { if (it < 50) 0xFF303030.toInt() else 0xFFD0D0D0.toInt() }
        val out = WatchColors.vivid(dull, photo = true)
        assertEquals(0xFF000000.toInt(), out[0])
        assertEquals(0xFFFFFFFF.toInt(), out[99])
    }

    @Test
    fun lowContrastPhotoIsNotPulledApart() {
        // Light grey lettering on a lighter grey card (manaca) stays grey, not black.
        val card = IntArray(100) { if (it < 10) 0xFF9C9C9C.toInt() else 0xFFDCDCDC.toInt() }
        val out = WatchColors.vivid(card, photo = true)
        assertEquals(0xEA, WatchColors.toPebble(out[0]))  // (170, 170, 170)
        assertEquals(0xFF, WatchColors.toPebble(out[99]))
    }

    @Test
    fun photoWithoutWhiteKeepsItsColours() {
        // PiTaPa: grey-green and lavender, nothing white. Was white and royal blue, or cyan.
        val grey = 0xFFA9B4B2.toInt()
        val lavender = 0xFF898CB0.toInt()
        val out = WatchColors.vivid(IntArray(100) { if (it < 60) lavender else grey }, photo = true)
        assertEquals(0xEA, WatchColors.toPebble(out[99], greyBelow = 0.22f))
        val l = WatchColors.toPebble(out[0], greyBelow = 0.22f)
        assertTrue("lavender is blue-purple, got %02X".format(l), (l and 0x03) > ((l shr 4) and 0x03) && (l and 0x03) > ((l shr 2) and 0x03))
    }

    @Test
    fun printedGreyOnWhiteCardStaysGrey() {
        // Kitaca: a grey sky on a cream card. The sky used to go white, and its grey letters with it.
        val cream = 0xFFF5EEDA.toInt()
        val sky = 0xFFB2B6BC.toInt()
        val out = WatchColors.vivid(IntArray(100) { if (it < 40) sky else cream }, photo = true)
        assertEquals(0xFF, WatchColors.toPebble(out[99], greyBelow = 0.22f))
        assertEquals(0xEA, WatchColors.toPebble(out[0], greyBelow = 0.22f))
    }

    @Test
    fun nearlySamePatchJoinsItsNeighbour() {
        val green = 0xC8   // (0, 170, 0)
        val teal = 0xC9    // (0, 170, 85): looks almost the same on the screen
        val white = 0xFF
        // 6 x 4: a teal wedge on the top row of a green panel, white lettering below.
        val img = intArrayOf(
            teal, teal, teal, green, green, green,
            green, green, green, green, green, green,
            green, white, white, green, green, green,
            green, green, green, green, green, green,
        )
        val out = WatchColors.mergeSimilar(img, 6)
        assertEquals(green, out[0])
        assertEquals(white, out[13])
    }

    @Test
    fun flattenKeepsStrongEdges() {
        val green = 0xC8      // (0, 170, 0)
        val green2 = 0xD8     // (85, 170, 0): looks almost the same on screen
        val white = 0xFF
        val row = intArrayOf(green, green2, green, white, white, green, green2, 0, green)
        val flat = WatchColors.flattenRows(row, row.size, 3000)
        assertArrayEquals(intArrayOf(green, green, green, white, white, green, green, 0, green), flat)
        assertArrayEquals(row, WatchColors.flattenRows(row, row.size, 0))
    }

    @Test
    fun shortRunsMergeIntoTheirNeighbour() {
        val a = 0xC1; val b = 0xFE; val c = 0xD5
        val row = intArrayOf(a, a, a, b, c, c, c, c)
        assertArrayEquals(intArrayOf(a, a, a, a, c, c, c, c), WatchColors.mergeShortRuns(row, 8, 2))
        assertArrayEquals(row, WatchColors.mergeShortRuns(row, 8, 1))
        // A short run at the start of a row takes the colour after it; transparency is kept.
        assertArrayEquals(intArrayOf(a, a, a, 0, 0), WatchColors.mergeShortRuns(intArrayOf(b, a, a, 0, 0), 5, 2))
    }

    @Test
    fun monoSplitsAtMedianBrightness() {
        val out = WatchColors.mono(intArrayOf(0xFF202020.toInt(), 0xFFE0E0E0.toInt(), 0))
        assertArrayEquals(intArrayOf(0xC0, 0xFF, 0), out)
        assertTrue(out.size == 3)
    }
}
