package io.github.grillza.icreader.pebble

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Colour work for card faces on the watch. Pebble colour screens show 64 colours, and show
 * them much paler than their values suggest (full green looks pastel), so faces are made
 * bolder and each colour is matched to the Pebble colour that *looks* closest on the screen.
 * Pixels are ARGB ints, except where the doc says Pebble colours (see [WatchProtocol.pebbleColor]).
 */
object WatchColors {

    /**
     * How each Pebble colour (index = colour & 0x3F, i.e. RRGGBB levels) looks on a Pebble Time
     * screen. From pebble-tool's screenshot colour correction (MIT licence).
     */
    private val DISPLAY = intArrayOf(
        0x000000, 0x001E41, 0x004387, 0x0068CA, 0x2B4A2C, 0x27514F, 0x16638D, 0x007DCE,
        0x5E9860, 0x5C9B72, 0x57A5A2, 0x4CB4DB, 0x8EE391, 0x8EE69E, 0x8AEBC0, 0x84F5F1,
        0x4A161B, 0x482748, 0x40488A, 0x2F6BCC, 0x564E36, 0x545454, 0x4F6790, 0x4180D0,
        0x759A64, 0x759D76, 0x71A6A4, 0x69B5DD, 0x9EE594, 0x9DE7A0, 0x9BECC2, 0x95F6F2,
        0x99353F, 0x983E5A, 0x955694, 0x8F74D2, 0x9D5B4D, 0x9D6064, 0x9A7099, 0x9587D5,
        0xAFA072, 0xAEA382, 0xABABAB, 0xA7BAE2, 0xC9E89D, 0xC9EAA7, 0xC7F0C8, 0xC3F9F7,
        0xE35462, 0xE25874, 0xE16AA3, 0xDE83DC, 0xE66E6B, 0xE6727C, 0xE37FA7, 0xE194DF,
        0xF1AA86, 0xF1AD93, 0xEFB5B8, 0xECC3EB, 0xFFEEAB, 0xFFF1B5, 0xFFF6D3, 0xFFFFFF,
    )

    private val DISPLAY_HUE = FloatArray(DISPLAY.size) { hueOf(DISPLAY[it]) }
    private val DISPLAY_SAT = FloatArray(DISPLAY.size) { satOf(DISPLAY[it]) }

    /**
     * The Pebble colour that looks closest to [argb] on the watch's screen; 0 if transparent.
     * Closeness is a weighted RGB distance plus a penalty for changing the hue (or tinting a
     * grey), since the screen can't show strong colours but yellow should still look yellow.
     */
    fun toPebble(argb: Int, greyBelow: Float = 0.12f): Int {
        if ((argb ushr 24) < 128) return 0
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        val hue = hueOf(argb)
        val sat = satOf(argb)
        var best = 0
        var bestDist = Float.MAX_VALUE
        for (i in DISPLAY.indices) {
            val d = DISPLAY[i]
            val dr = (r - ((d shr 16) and 0xFF)).toFloat()
            val dg = (g - ((d shr 8) and 0xFF)).toFloat()
            val db = (b - (d and 0xFF)).toFloat()
            val rm = (r + ((d shr 16) and 0xFF)) / 2f
            var dist = (2f + rm / 256f) * dr * dr + 4f * dg * dg + (2f + (255f - rm) / 256f) * db * db
            if (sat > greyBelow) {
                var dh = if (DISPLAY_SAT[i] == 0f) 180f else kotlin.math.abs(hue - DISPLAY_HUE[i])
                if (dh > 180f) dh = 360f - dh
                dist += HUE_WEIGHT * dh * dh * min(sat, 1f)
            } else {
                val tint = DISPLAY_SAT[i] * 300f
                dist += HUE_WEIGHT * tint * tint
            }
            if (dist < bestDist) {
                bestDist = dist
                best = i
            }
        }
        return 0xC0 or best
    }

    private const val HUE_WEIGHT = 5f

    private fun hueOf(rgb: Int): Float {
        val hsv = FloatArray(3)
        rgbToHsv((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF, hsv)
        return hsv[0]
    }

    private fun satOf(rgb: Int): Float {
        val hsv = FloatArray(3)
        rgbToHsv((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF, hsv)
        return hsv[1]
    }

    /**
     * [toPebble] for every pixel. [photo]: colours that are only faintly tinted count as grey,
     * as a white card under warm or cool light should still look white.
     */
    fun toPebble(pixels: IntArray, photo: Boolean = false): IntArray {
        val cache = HashMap<Int, Int>()
        val greyBelow = if (photo) 0.22f else 0.12f
        return IntArray(pixels.size) { cache.getOrPut(pixels[it]) { toPebble(pixels[it], greyBelow) } }
    }

    /** How far apart two Pebble colours look on the screen (squared RGB distance). */
    fun screenDistance(a: Int, b: Int): Int {
        val x = DISPLAY[a and 0x3F]
        val y = DISPLAY[b and 0x3F]
        val dr = ((x shr 16) and 0xFF) - ((y shr 16) and 0xFF)
        val dg = ((x shr 8) and 0xFF) - ((y shr 8) and 0xFF)
        val db = (x and 0xFF) - (y and 0xFF)
        return dr * dr + dg * dg + db * db
    }

    /**
     * Flattens a photo in Pebble colours along each row: a pixel joins the run before it when
     * the two look less than [threshold] apart ([screenDistance]). Gentle shading and noise
     * merge into long runs; strong edges, like white lettering on green, stay sharp.
     * Transparent pixels (0) are left alone.
     */
    fun flattenRows(colours: IntArray, width: Int, threshold: Int): IntArray {
        if (threshold <= 0) return colours
        val out = colours.copyOf()
        for (row in out.indices step width) {
            var run = 0
            for (x in 0 until width) {
                val c = out[row + x]
                if (c == 0 || run == 0 || screenDistance(c, run) >= threshold) {
                    run = c
                } else {
                    out[row + x] = run
                }
            }
        }
        return out
    }

    /**
     * Bolder colours. Designs ([photo] false): near-greys (the silver card base) go to white and
     * colours get stronger. Photos: the brightness range is stretched to full (ignoring the
     * darkest 1% and lightest 8%) and colours get a little stronger (more makes white cards
     * look tinted).
     */
    fun vivid(pixels: IntArray, photo: Boolean): IntArray {
        val (lo, hi) = if (photo) levels(pixels) else 0 to 255
        val hsv = FloatArray(3)
        return IntArray(pixels.size) { i ->
            val p = pixels[i]
            val a = p ushr 24
            if (a < 128) return@IntArray p
            var r = (p shr 16) and 0xFF
            var g = (p shr 8) and 0xFF
            var b = p and 0xFF
            // A design colour that's already one of the watch's was chosen for it (a flat override).
            if (!photo && r % 85 == 0 && g % 85 == 0 && b % 85 == 0) return@IntArray p
            if (photo) {
                r = stretch(r, lo, hi); g = stretch(g, lo, hi); b = stretch(b, lo, hi)
            }
            rgbToHsv(r, g, b, hsv)
            if (photo && hsv[1] < 0.25f && hsv[2] >= 0.6f) {
                // Pale, nearly colourless tones are the card's white in shadow or tinted by the
                // light: the screen's only light grey (170) and its pale greens show them as blotches,
                // so they go white.
                hsv[1] = 0f
                hsv[2] = 1f
            } else if (photo) {
                hsv[1] = min(1f, hsv[1] * 1.15f)
            } else if (hsv[1] < 0.12f) {
                if (hsv[2] >= 0.55f) hsv[2] = 1f
            } else {
                // Not all the way: fully saturated yellow would match the screen's orange.
                hsv[1] = min(max(hsv[1], 0.85f), hsv[1] * 1.4f)
            }
            hsvToRgb(hsv)
        }
    }

    /** The 1st and 92nd percentile of opaque pixels' brightness (the card's white goes white). */
    private fun levels(pixels: IntArray): Pair<Int, Int> {
        val hist = IntArray(256)
        var n = 0
        for (p in pixels) {
            if ((p ushr 24) < 128) continue
            hist[luma(p)]++
            n++
        }
        if (n == 0) return 0 to 255
        fun at(fraction: Float): Int {
            var seen = 0
            for (v in 0..255) {
                seen += hist[v]
                if (seen >= n * fraction) return v
            }
            return 255
        }
        val lo = at(0.01f)
        val hi = at(0.92f)
        return if (hi - lo < 32) 0 to 255 else lo to hi
    }

    private fun stretch(v: Int, lo: Int, hi: Int) = ((v - lo) * 255f / (hi - lo)).roundToInt().coerceIn(0, 255)

    private fun luma(p: Int) = (((p shr 16) and 0xFF) * 299 + ((p shr 8) and 0xFF) * 587 + (p and 0xFF) * 114) / 1000

    /**
     * Black or white Pebble colours (0 where transparent), split at the photo's median
     * brightness so about half of it is each.
     */
    fun mono(pixels: IntArray): IntArray {
        val lumas = pixels.filter { (it ushr 24) >= 128 }.map(::luma).sorted()
        val split = if (lumas.isEmpty()) 128 else lumas[lumas.size / 2].coerceIn(64, 192)
        return IntArray(pixels.size) { i ->
            val p = pixels[i]
            when {
                (p ushr 24) < 128 -> 0
                luma(p) >= split -> 0xFF
                else -> 0xC0
            }
        }
    }

    /**
     * Edge-preserving smoothing of ARGB [pixels]: each pixel becomes the average of the pixels
     * within [radius] that are within [range] of its colour, repeated [passes] times. Shading
     * and grain in a flat area even out (so it doesn't flicker between two Pebble colours),
     * while edges between different colours, like lettering, stay sharp.
     */
    fun smooth(pixels: IntArray, width: Int, radius: Int = 2, range: Int = 40, passes: Int = 3): IntArray {
        val height = pixels.size / width
        val range2 = range * range
        var src = pixels
        repeat(passes) {
            val out = src.copyOf()
            for (y in 0 until height) for (x in 0 until width) {
                val p = src[y * width + x]
                if ((p ushr 24) < 128) continue
                val r0 = (p shr 16) and 0xFF
                val g0 = (p shr 8) and 0xFF
                val b0 = p and 0xFF
                var sr = 0
                var sg = 0
                var sb = 0
                var n = 0
                for (dy in -radius..radius) {
                    val ny = y + dy
                    if (ny !in 0 until height) continue
                    for (dx in -radius..radius) {
                        val nx = x + dx
                        if (nx !in 0 until width) continue
                        val q = src[ny * width + nx]
                        if ((q ushr 24) < 128) continue
                        val r = (q shr 16) and 0xFF
                        val g = (q shr 8) and 0xFF
                        val b = q and 0xFF
                        val d = (r - r0) * (r - r0) + (g - g0) * (g - g0) + (b - b0) * (b - b0)
                        if (d > range2) continue
                        sr += r; sg += g; sb += b; n++
                    }
                }
                out[y * width + x] = (0xFF shl 24) or ((sr / n) shl 16) or ((sg / n) shl 8) or (sb / n)
            }
            src = out
        }
        return src
    }

    /**
     * Splits ARGB [pixels] into regions of similar colour and paints each region its average,
     * so a card's panel lit unevenly comes out one flat colour instead of two or three
     * patches. A pixel joins its neighbour's region when the two differ by less than [step]
     * (so a gradual change of light stays in one region) and it's within [spread] of where
     * the region started (so it can't creep across a real edge). Regions smaller than
     * [minSize] keep their own pixels: that's fine detail such as lettering.
     */
    fun regions(pixels: IntArray, width: Int, step: Int = 22, spread: Int = 80, minSize: Int = 12): IntArray {
        val height = pixels.size / width
        val label = IntArray(pixels.size) { -1 }
        val out = pixels.copyOf()
        val queue = IntArray(pixels.size)
        val step2 = step * step
        val spread2 = spread * spread
        fun dist2(a: Int, b: Int): Int {
            val dr = ((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)
            val dg = ((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)
            val db = (a and 0xFF) - (b and 0xFF)
            return dr * dr + dg * dg + db * db
        }
        var next = 0
        for (start in pixels.indices) {
            if (label[start] >= 0 || (pixels[start] ushr 24) < 128) continue
            val seed = pixels[start]
            val id = next++
            var head = 0
            var tail = 0
            queue[tail++] = start
            label[start] = id
            var sr = 0L
            var sg = 0L
            var sb = 0L
            while (head < tail) {
                val i = queue[head++]
                val p = pixels[i]
                sr += (p shr 16) and 0xFF; sg += (p shr 8) and 0xFF; sb += p and 0xFF
                val x = i % width
                val y = i / width
                for (d in 0 until 4) {
                    val nx = x + (if (d == 0) 1 else if (d == 1) -1 else 0)
                    val ny = y + (if (d == 2) 1 else if (d == 3) -1 else 0)
                    if (nx !in 0 until width || ny !in 0 until height) continue
                    val j = ny * width + nx
                    if (label[j] >= 0) continue
                    val q = pixels[j]
                    if ((q ushr 24) < 128) continue
                    if (dist2(p, q) >= step2 || dist2(seed, q) >= spread2) continue
                    label[j] = id
                    queue[tail++] = j
                }
            }
            if (tail < minSize) continue
            val avg = (0xFF shl 24) or ((sr / tail).toInt() shl 16) or ((sg / tail).toInt() shl 8) or (sb / tail).toInt()
            for (k in 0 until tail) out[queue[k]] = avg
        }
        return out
    }

    /**
     * Tidies Pebble-colour [colours]: a pixel takes the most common colour around it (3 × 3)
     * when that colour looks nearly the same on the screen. Removes stray pixels in flat areas
     * without touching edges or lettering. Transparent pixels (0) stay and don't count.
     */
    fun tidy(colours: IntArray, width: Int, passes: Int = 2): IntArray {
        val height = colours.size / width
        var src = colours
        val seen = IntArray(9)
        val counts = IntArray(9)
        repeat(passes) {
            val out = src.copyOf()
            for (y in 0 until height) for (x in 0 until width) {
                val c = src[y * width + x]
                if (c == 0) continue
                var kinds = 0
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = x + dx
                    val ny = y + dy
                    if (nx !in 0 until width || ny !in 0 until height) continue
                    val n = src[ny * width + nx]
                    if (n == 0) continue
                    var k = 0
                    while (k < kinds && seen[k] != n) k++
                    if (k == kinds) { seen[k] = n; counts[k] = 0; kinds++ }
                    counts[k]++
                }
                var best = 0
                for (k in 1 until kinds) if (counts[k] > counts[best]) best = k
                if (seen[best] != c && counts[best] >= 5 && screenDistance(c, seen[best]) < TIDY_DISTANCE) {
                    out[y * width + x] = seen[best]
                }
            }
            src = out
        }
        return src
    }

    /** Colours closer than this on the screen count as "nearly the same" for [tidy]. */
    private const val TIDY_DISTANCE = 4000

    /**
     * Within each row, runs of Pebble colour shorter than [minRun] take the colour of the run
     * before them (or after, at the start of a row), so the image compresses into fewer, longer
     * runs. Transparent pixels (0) are left alone.
     */
    fun mergeShortRuns(colours: IntArray, width: Int, minRun: Int): IntArray {
        if (minRun <= 1) return colours
        val out = colours.copyOf()
        val height = colours.size / width
        for (y in 0 until height) {
            val row = y * width
            var x = 0
            while (x < width) {
                val c = out[row + x]
                var end = x + 1
                while (end < width && colours[row + end] == colours[row + x]) end++
                if (c != 0 && end - x < minRun) {
                    val before = if (x > 0) out[row + x - 1] else 0
                    val after = if (end < width) colours[row + end] else 0
                    val fill = if (before != 0) before else after
                    if (fill != 0) for (i in x until end) out[row + i] = fill
                }
                x = end
            }
        }
        return out
    }

    /** [hsv] = hue (0-360), saturation and value (0-1), as android.graphics.Color does. */
    private fun rgbToHsv(r: Int, g: Int, b: Int, hsv: FloatArray) {
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        val d = (mx - mn).toFloat()
        hsv[2] = mx / 255f
        hsv[1] = if (mx == 0) 0f else d / mx
        hsv[0] = when {
            d == 0f -> 0f
            mx == r -> 60f * (((g - b) / d) % 6f)
            mx == g -> 60f * ((b - r) / d + 2f)
            else -> 60f * ((r - g) / d + 4f)
        }.let { if (it < 0f) it + 360f else it }
    }

    private fun hsvToRgb(hsv: FloatArray): Int {
        val c = hsv[2] * hsv[1]
        val h = hsv[0] / 60f
        val x = c * (1f - kotlin.math.abs(h % 2f - 1f))
        val (r, g, b) = when (h.toInt()) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val m = hsv[2] - c
        fun ch(v: Float) = ((v + m) * 255f).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }
}
