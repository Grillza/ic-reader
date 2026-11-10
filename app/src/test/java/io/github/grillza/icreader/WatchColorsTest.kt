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
        val dull = IntArray(100) { if (it < 50) 0xFF606060.toInt() else 0xFFA0A0A0.toInt() }
        val out = WatchColors.vivid(dull, photo = true)
        assertEquals(0xFF000000.toInt(), out[0])
        assertEquals(0xFFFFFFFF.toInt(), out[99])
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
