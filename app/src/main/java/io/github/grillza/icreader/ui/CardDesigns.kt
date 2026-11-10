package io.github.grillza.icreader.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.withSave
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.grillza.icreader.data.CardType

/**
 * Card faces that echo each brand's colour layout (most transit cards are silver with one bold
 * colour shape) so a card is recognisable at a glance. They deliberately don't reproduce logos
 * or characters; users who want the real thing can attach a photo of their card.
 */
internal enum class Motif { SUICA_PANEL, CIRCLE_RIGHT, CIRCLE_LEFT, PLAIN, SWOOSH_RIGHT, BARS_LEFT, BUBBLES, WAVE_BOTTOM, CIRCLE, BLOCK_RIGHT, BAND_DIAGONAL, ARC_BOTTOM,
    ICOCA_PANEL, SUGOCA_PANEL, KITACA_FOREST, HAYAKAKEN_PANEL, PITAPA_PANEL }

internal data class Design(
    val base: List<Color>,
    val motif: Motif,
    val accent: List<Color>,
    val brand: String,
    val brandColor: Color,
    val brandAlign: Alignment,
    val italic: Boolean = false,
    /** Place the brand exactly at [brandAlign] (e.g. inside a circle) instead of inset from the edge. */
    val brandExact: Boolean = false,
    /** Horizontal shift of a centred brand, as a fraction of card width (to sit inside a shape). */
    val brandShift: Float = 0f,
    /** True when no reference photo was available and the layout is a best guess. */
    val approximate: Boolean = false,
    /**
     * Flat-colour (watch) overrides, where the usual colours round to the wrong Pebble colour or
     * to the same one as the card behind them.
     */
    val flatAccent: Color? = null,
    val flatBrandColor: Color? = null,
    /** Lettering placed as on the real card. When set, it replaces [brand]. */
    val labels: List<Label> = emptyList(),
)

/**
 * A line of lettering fitted to where it sits on a real card: its ink runs from [start] to [end]
 * and its lowest ink (the baseline, for text without descenders) is at [bottom]. Across the card,
 * start/end are fractions of its width and bottom of its height. [vertical] text reads top to
 * bottom with the letters' tops to the right; start/end are then fractions of the height and
 * bottom (the left edge of the ink) a fraction of the width. [spacing] is extra space between
 * letters in ems; [charColors] recolours single letters by index.
 */
internal data class Label(
    val text: String,
    val color: Color,
    val start: Float,
    val end: Float,
    val bottom: Float,
    val family: String = "sans-serif",
    val weight: Int = 700,
    val spacing: Float = 0f,
    val charColors: Map<Int, Color> = emptyMap(),
    val vertical: Boolean = false,
    /** Colour on the watch, where the usual one would be hard to read. */
    val flatColor: Color? = null,
)

private val SILVER = listOf(Color(0xFFE7E9EC), Color(0xFFC5C9CE), Color(0xFFDDE0E3))
private val WHITE = listOf(Color(0xFFFFFFFF), Color(0xFFEDEEF0))
private val PEARL = listOf(Color(0xFFF7F8F9), Color(0xFFE4E6E9), Color(0xFFF0F1F3))

/**
 * Suica's wordmark: large, widely spaced, along the bottom of the green panel (fractions of
 * card width; spacing in ems). Shared with the watch's card faces.
 */
internal const val SUICA_TEXT_SIZE = 0.14f
internal const val SUICA_TEXT_SPACING = 0.18f
internal const val SUICA_TEXT_LEFT = 0.065f
internal const val SUICA_TEXT_BOTTOM = 0.05f

internal fun design(type: CardType): Design = when (type) {
    // References: photos of the cards (Suica, PASMO, TOICA, manaca, PiTaPa, SUGOCA, nimoca, ICOCA,
    // Kitaca, Hayakaken). Panels, colours and lettering are measured from them; logos and characters are left out.
    CardType.SUICA -> Design(PEARL, Motif.SUICA_PANEL, listOf(Color(0xFF62BD2F)), "Suica", Color.White, Alignment.BottomStart)
    CardType.PASMO -> Design(SILVER, Motif.BARS_LEFT, listOf(Color(0xFFEA5A9C)), "PASMO", Color(0xFFEA5A9C), Alignment.BottomStart)
    CardType.ICOCA -> Design(
        listOf(Color(0xFFD5D6D9), Color(0xFFC3C5C9), Color(0xFFCFD0D3)), Motif.ICOCA_PANEL, listOf(Color(0xFF0AB5D1)), "ICOCA", Color(0xFFC8C9CB), Alignment.Center,
        labels = listOf(Label("ICOCA", Color(0xFFC8C9CB), 0.154f, 0.867f, 0.592f, family = "serif", spacing = 0.04f, flatColor = Color.White)),
    )
    CardType.TOICA -> Design(SILVER, Motif.WAVE_BOTTOM, listOf(Color(0xFF17A9E1)), "toica", Color.White, Alignment.BottomEnd)
    CardType.MANACA -> Design(listOf(Color(0xFFD3D6DF), Color(0xFFBBC0CC)), Motif.CIRCLE, listOf(Color(0xFFF1E63B)), "manaca", Color(0xFF9DA3B3), Alignment.Center)
    CardType.KITACA -> Design(
        listOf(Color(0xFFF8F4E3), Color(0xFFEFE9D3)), Motif.KITACA_FOREST, listOf(Color(0xFFB6B9C0)), "Kitaca", Color(0xFF7DCB2E), Alignment.BottomStart,
        flatAccent = Color(0xFFAAAAAA),
        labels = listOf(Label("Kitaca", Color(0xFF7DCB2E), 0.054f, 0.735f, 0.94f, weight = 500, spacing = 0.3f, charColors = mapOf(1 to Color(0xFFAAAAAA)))),
    )
    CardType.SUGOCA -> Design(
        listOf(Color(0xFFC0D5BB), Color(0xFFB3CAAE)), Motif.SUGOCA_PANEL, listOf(Color(0xFFF6FAE9)), "SUGOCA", Color(0xFFE67A93), Alignment.BottomStart,
        labels = listOf(Label("SUGOCA", Color(0xFFE67A93), 0.045f, 0.589f, 0.93f, weight = 900, spacing = 0.16f)),
    )
    CardType.NIMOCA -> Design(PEARL, Motif.CIRCLE_LEFT, listOf(Color(0xFF45A2CB)), "nimoca", Color.White, Alignment.Center, brandExact = true, brandShift = -0.13f)
    CardType.HAYAKAKEN -> Design(
        listOf(Color(0xFFFBFAF8), Color(0xFFEDECE9)), Motif.HAYAKAKEN_PANEL, listOf(Color(0xFF4FD2EA)), "Hayakaken", Color(0xFF2B3A93), Alignment.TopStart,
        labels = listOf(Label("HAYAKAKEN", Color(0xFF2B3A93), 0.043f, 0.338f, 0.232f, spacing = 0.06f)),
    )
    CardType.PITAPA -> Design(
        listOf(Color(0xFFB4BFBD), Color(0xFFA1ADAB), Color(0xFFAFBAB8)), Motif.PITAPA_PANEL, listOf(Color(0xFF898CB0)), "PiTaPa", Color(0xFFB4BFBD), Alignment.TopEnd,
        flatAccent = Color(0xFFAA55FF),
        // Reads top to bottom down the panel, as on the card.
        labels = listOf(Label("PiTaPa", Color(0xFFB4BFBD), 0.10f, 0.935f, 0.513f, family = "serif", spacing = -0.06f, vertical = true, flatColor = Color.White)),
    )
    CardType.TRANSIT -> Design(SILVER, Motif.WAVE_BOTTOM, listOf(Color(0xFF607D8B)), "IC", Color.White, Alignment.BottomEnd)
    CardType.EDY -> Design(WHITE, Motif.BAND_DIAGONAL, listOf(Color(0xFFE60012), Color(0xFFBF0000)), "Edy", Color(0xFFD0000F), Alignment.TopStart, approximate = true)
    CardType.NANACO -> Design(listOf(Color(0xFFFFF8E7), Color(0xFFF7EBCB)), Motif.ARC_BOTTOM, listOf(Color(0xFFF7A800), Color(0xFF6CC3E8)), "nanaco", Color(0xFFE38D00), Alignment.TopStart, approximate = true)
    CardType.WAON -> Design(listOf(Color(0xFFCDEBF8), Color(0xFF86CFEF)), Motif.BUBBLES, listOf(Color.White), "WAON", Color(0xFF1C6FD0), Alignment.TopStart, approximate = true)
}

@Composable
internal fun BoxScope.GeneratedArt(type: CardType, showBrand: Boolean) {
    val d = design(type)
    Canvas(Modifier.matchParentSize()) {
        drawCardBackground(type, flat = false)
        if (showBrand) drawIntoCanvas { canvas -> d.labels.forEach { canvas.nativeCanvas.drawLabel(it, size.width, size.height) } }
    }
    if (showBrand && d.labels.isEmpty()) {
        BoxWithConstraints(Modifier.matchParentSize()) {
            val w = maxWidth
            val scale = when {
                d.motif == Motif.SUICA_PANEL -> SUICA_TEXT_SIZE
                d.brandShift != 0f -> 0.075f // inside a circle
                d.brand.length > 7 -> 0.085f
                else -> 0.11f
            }
            val fontSize = with(LocalDensity.current) { (w * scale).toSp() }
            val pad = w * 0.06f
            val modifier = when {
                d.brandExact || d.brandAlign == Alignment.Center ->
                    Modifier.align(d.brandAlign).offset(x = w * d.brandShift)
                d.motif == Motif.SUICA_PANEL -> Modifier.align(Alignment.BottomStart).padding(start = w * SUICA_TEXT_LEFT, bottom = w * SUICA_TEXT_BOTTOM)
                d.brandAlign == Alignment.BottomStart -> Modifier.align(Alignment.BottomStart).padding(start = pad, bottom = w * 0.05f)
                else -> Modifier.align(d.brandAlign).padding(horizontal = pad, vertical = w * 0.04f)
            }
            Text(
                d.brand,
                color = d.brandColor,
                fontSize = fontSize,
                fontWeight = FontWeight.Black,
                fontStyle = if (d.italic) FontStyle.Italic else FontStyle.Normal,
                letterSpacing = if (d.motif == Motif.SUICA_PANEL) fontSize * SUICA_TEXT_SPACING else androidx.compose.ui.unit.TextUnit.Unspecified,
                modifier = modifier,
            )
        }
    }
}

/**
 * Everything on the card face except the brand name. [flat] drops the gradients and sheen, for
 * screens with few colours (the Pebble's 64), where gradients turn into bands. [mono] draws a
 * white card with its shapes in black, for black-and-white screens.
 */
internal fun DrawScope.drawCardBackground(type: CardType, flat: Boolean, mono: Boolean = false) {
    val d = design(type)
    if (mono) {
        drawRect(Color.White)
    } else if (flat) {
        // The middle of a three-stop metallic base is its typical tone; two stops are averaged.
        val base = if (d.base.size == 3) d.base[1] else androidx.compose.ui.graphics.lerp(d.base.first(), d.base.last(), 0.5f)
        // A silver with a faint tint would round to a pastel colour; keep it grey.
        val spread = maxOf(base.red, base.green, base.blue) - minOf(base.red, base.green, base.blue)
        val grey = (base.red + base.green + base.blue) / 3
        drawRect(if (spread < 0.08f) Color(grey, grey, grey) else base)
    } else {
        drawRect(Brush.linearGradient(d.base, start = Offset.Zero, end = Offset(size.width, size.height)))
        // Soft sheen like a metallic print.
        drawRect(
            Brush.radialGradient(
                listOf(Color.White.copy(alpha = 0.45f), Color.Transparent),
                center = Offset(size.width * 0.3f, size.height * 0.2f),
                radius = size.width * 0.6f,
            ),
        )
    }
    val shapes = when {
        mono -> d.copy(accent = d.accent.map { Color.Black })
        flat && d.flatAccent != null -> d.copy(accent = d.accent.map { d.flatAccent })
        else -> d
    }
    drawMotif(shapes, flat || mono)
}

private fun DrawScope.drawMotif(d: Design, flat: Boolean) {
    val w = size.width
    val h = size.height
    val accent = if (d.accent.size > 1 && !flat) Brush.linearGradient(d.accent, Offset(0f, 0f), Offset(w, h)) else null
    val solid = d.accent.first()
    fun fill(path: Path) = if (accent != null) drawPath(path, accent) else drawPath(path, solid)

    when (d.motif) {
        Motif.SUICA_PANEL -> {
            // Tall rounded green panel on the left, almost the card's full height; its right edge
            // slants out a little toward the bottom (measured from a real card).
            val r = w * 0.03f
            val l = w * 0.035f
            val t = h * 0.06f
            val b = h * 0.94f
            val tr = w * 0.43f
            val br = w * 0.62f
            // Unit vector along the slanted edge, for rounding its two corners.
            val dx = br - tr
            val dy = b - t
            val len = kotlin.math.sqrt(dx * dx + dy * dy)
            val ux = dx / len
            val uy = dy / len
            val rr = r * 1.6f
            fill(
                Path().apply {
                    moveTo(l + r, t)
                    lineTo(tr - rr, t)
                    quadraticTo(tr, t, tr + ux * rr, t + uy * rr)
                    lineTo(br - ux * r, b - uy * r)
                    quadraticTo(br, b, br - r, b)
                    lineTo(l + r, b)
                    quadraticTo(l, b, l, b - r)
                    lineTo(l, t + r)
                    quadraticTo(l, t, l + r, t)
                    close()
                },
            )
        }
        Motif.CIRCLE_RIGHT -> drawCircle(solid, h * 0.37f, Offset(w * 0.76f, h * 0.36f))
        Motif.CIRCLE_LEFT -> drawCircle(solid, h * 0.36f, Offset(w * 0.37f, h * 0.5f))
        Motif.PLAIN -> {}
        Motif.SWOOSH_RIGHT -> {
            // A broad green sweep rising from the bottom-left into the right side.
            fill(
                Path().apply {
                    moveTo(w * 0.18f, h)
                    cubicTo(w * 0.42f, h * 0.78f, w * 0.52f, h * 0.18f, w * 0.78f, 0f)
                    lineTo(w, 0f)
                    lineTo(w, h)
                    close()
                },
            )
            drawPath(
                Path().apply {
                    moveTo(w * 0.30f, h)
                    cubicTo(w * 0.52f, h * 0.80f, w * 0.62f, h * 0.30f, w * 0.90f, 0f)
                },
                Color.White.copy(alpha = 0.35f),
                style = Stroke(width = h * 0.025f),
            )
        }
        Motif.BARS_LEFT -> {
            val bw = w * 0.30f
            val bh = h * 0.13f
            listOf(0.14f, 0.36f).forEach { y ->
                drawRoundRect(solid, Offset(w * 0.07f, h * y), Size(bw, bh), CornerRadius(bh * 0.35f))
                drawCircle(solid, bh * 0.38f, Offset(w * 0.07f + bw + bh * 0.7f, h * y + bh * 0.55f))
            }
        }
        Motif.BUBBLES -> {
            val c = d.accent.first()
            listOf(
                Triple(0.80f, 0.30f, 0.22f), Triple(0.62f, 0.72f, 0.14f), Triple(0.90f, 0.80f, 0.10f),
                Triple(0.45f, 0.35f, 0.07f), Triple(0.72f, 0.08f, 0.06f),
            ).forEach { (x, y, r) -> drawCircle(c.copy(alpha = if (flat) 0.6f else 0.35f), h * r, Offset(w * x, h * y)) }
        }
        Motif.WAVE_BOTTOM -> fill(
            Path().apply {
                moveTo(0f, h * 0.62f)
                cubicTo(w * 0.20f, h * 0.38f, w * 0.38f, h * 0.40f, w * 0.55f, h * 0.62f)
                cubicTo(w * 0.70f, h * 0.80f, w * 0.85f, h * 0.70f, w, h * 0.52f)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            },
        )
        Motif.CIRCLE -> drawCircle(solid, h * 0.43f, Offset(w * 0.49f, h * 0.53f))
        Motif.BLOCK_RIGHT -> {
            drawRect(solid, Offset(w * 0.33f, h * 0.06f), Size(w * 0.67f, h * 0.94f))
            drawRect(solid, Offset(0f, h * 0.06f), Size(w * 0.33f, h * 0.06f))
            drawCircle(solid, h * 0.16f, Offset(w * 0.18f, h * 0.42f))
            drawRect(solid, Offset(w * 0.25f, h * 0.42f), Size(w * 0.08f, h * 0.28f))
        }
        Motif.BAND_DIAGONAL -> fill(
            Path().apply {
                moveTo(w * 0.55f, h)
                lineTo(w, h * 0.25f)
                lineTo(w, h)
                close()
                moveTo(w * 0.35f, h)
                lineTo(w * 0.47f, h)
                lineTo(w, h * 0.12f)
                lineTo(w, h * -0.08f)
                close()
            },
        )
        Motif.ICOCA_PANEL -> {
            // Blue panel inside a silver border, with its top-left and bottom-right corners cut
            // off at 45 degrees (the lower cut clears the notch).
            rounded(solid, w * 0.02f, polygon(w, h, 0.274f to 0.033f, 0.979f to 0.033f, 0.979f to 0.683f, 0.812f to 0.971f, 0.023f to 0.971f, 0.023f to 0.447f))
            drawPath(polygon(w, h, 0.036f to 0.047f, 0.066f to 0.030f, 0.066f to 0.064f), solid)
        }
        Motif.SUGOCA_PANEL -> {
            // A pale panel inside the green border, its right side cut away by a big green circle.
            val panel = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(w * 0.025f, h * 0.042f, w * 0.99f, h * 0.96f, CornerRadius(w * 0.02f))) }
            val circle = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(w * 0.899f, h * 0.5f), h * 0.544f)) }
            drawPath(Path().apply { op(panel, circle, PathOperation.Difference) }, solid)
            drawPath(polygon(w, h, 0.044f to 0.026f, 0.064f to 0.013f, 0.064f to 0.039f), if (flat) Color.Black else Color(0xFF222222))
        }
        Motif.KITACA_FOREST -> {
            // Grey sky over a pale forest: the panel's lower edge is a line of treetops.
            val points = mutableListOf(0.034f to 0.047f, 0.964f to 0.047f)
            KITACA_TREES.reversed().zipWithNext().forEach { (a, b) ->
                points += a
                points += (a.first + b.first) / 2 to maxOf(a.second, b.second) + 0.035f
            }
            points += KITACA_TREES.first()
            rounded(solid, w * 0.012f, polygon(w, h, *points.toTypedArray()))
            val arrow = Color(0xFF2A2A2A)
            drawRect(arrow, Offset(w * 0.05f, h * 0.068f), Size(w * 0.055f, h * 0.009f))
            drawPath(polygon(w, h, 0.044f to 0.0725f, 0.058f to 0.06f, 0.058f to 0.085f), arrow)
        }
        Motif.HAYAKAKEN_PANEL -> {
            drawRoundRect(solid, Offset(w * 0.040f, h * 0.275f), Size(w * 0.924f, h * 0.676f), CornerRadius(w * 0.025f))
            drawRect(solid, Offset(w * 0.051f, h * 0.044f), Size(w * 0.05f, h * 0.012f))
        }
        Motif.PITAPA_PANEL -> drawRoundRect(solid, Offset(w * 0.30f, h * 0.055f), Size(w * 0.677f, h * 0.903f), CornerRadius(w * 0.03f))
        Motif.ARC_BOTTOM -> {
            drawCircle(d.accent.last().copy(alpha = 0.9f), h * 0.62f, Offset(w * 0.98f, h * 1.15f))
            drawCircle(d.accent.first(), h * 0.50f, Offset(w * 0.72f, h * 1.12f))
        }
    }
}

/** Kitaca's treetops, left to right: (x, top) as fractions of the card, measured from a real card. */
private val KITACA_TREES = listOf(
    0.034f to 0.62f, 0.06f to 0.60f, 0.085f to 0.585f, 0.11f to 0.555f, 0.135f to 0.59f, 0.16f to 0.575f,
    0.19f to 0.60f, 0.215f to 0.565f, 0.245f to 0.595f, 0.275f to 0.58f, 0.31f to 0.60f, 0.345f to 0.575f,
    0.38f to 0.59f, 0.41f to 0.57f, 0.44f to 0.60f, 0.47f to 0.58f, 0.50f to 0.595f, 0.53f to 0.57f,
    0.56f to 0.59f, 0.60f to 0.565f, 0.635f to 0.585f, 0.67f to 0.57f, 0.70f to 0.585f, 0.74f to 0.56f,
    0.78f to 0.585f, 0.82f to 0.555f, 0.86f to 0.53f, 0.90f to 0.515f, 0.935f to 0.54f, 0.964f to 0.53f,
)

/** A closed path through [points] given as fractions of the card's width and height. */
private fun polygon(w: Float, h: Float, vararg points: Pair<Float, Float>) = Path().apply {
    points.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(w * x, h * y) else lineTo(w * x, h * y) }
    close()
}

/** Fills [path] with its corners rounded to [radius]. */
private fun DrawScope.rounded(color: Color, radius: Float, path: Path) = drawIntoCanvas {
    it.drawPath(path, Paint().apply { this.color = color; isAntiAlias = true; pathEffect = PathEffect.cornerPathEffect(radius) })
}

/**
 * Draws [label] on a card [w] by [h] (see [Label]). Plain Android canvas text, so the app and the
 * watch draw lettering identically. [color] overrides the label's colours.
 */
internal fun android.graphics.Canvas.drawLabel(label: Label, w: Float, h: Float, color: Int? = null, flat: Boolean = false) {
    val paint = labelPaint(label)
    val chars = label.text.map { it.toString() }
    // Lay out at size 100, then scale so the ink spans start..end.
    val bounds = android.graphics.Rect()
    val xs = FloatArray(chars.size)
    var x = 0f
    var inkLeft = Float.MAX_VALUE
    var inkRight = -Float.MAX_VALUE
    var inkBottom = 0
    chars.forEachIndexed { i, c ->
        paint.getTextBounds(c, 0, c.length, bounds)
        if (!bounds.isEmpty) {
            inkLeft = minOf(inkLeft, x + bounds.left)
            inkRight = maxOf(inkRight, x + bounds.right)
            inkBottom = maxOf(inkBottom, bounds.bottom)
        }
        xs[i] = x
        x += paint.measureText(c) + label.spacing * paint.textSize
    }
    if (inkRight <= inkLeft) return
    val (length, along, across) = if (label.vertical) Triple(h, h * label.start, w * label.bottom) else Triple(w, w * label.start, h * label.bottom)
    val k = length * (label.end - label.start) / (inkRight - inkLeft)
    paint.textSize *= k
    withSave {
        if (label.vertical) {
            translate(across, along)
            rotate(90f)
        } else {
            translate(along, across)
        }
        chars.forEachIndexed { i, c ->
            paint.color = color ?: (if (flat) label.flatColor else null)?.toArgb() ?: (label.charColors[i] ?: label.color).toArgb()
            drawText(c, (xs[i] - inkLeft) * k, -inkBottom * k, paint)
        }
    }
}

/** The middle of [label]'s lettering, roughly, in pixels (to see what's behind it). */
internal fun Label.center(w: Float, h: Float): Pair<Float, Float> =
    if (vertical) w * bottom + w * 0.04f to h * (start + end) / 2 else w * (start + end) / 2 to h * bottom - w * 0.04f

private fun labelPaint(label: Label) = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
    val family = android.graphics.Typeface.create(label.family, android.graphics.Typeface.NORMAL)
    typeface = if (android.os.Build.VERSION.SDK_INT >= 28) {
        android.graphics.Typeface.create(family, label.weight, false)
    } else {
        android.graphics.Typeface.create(family, if (label.weight >= 600) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
    }
    textSize = 100f
}

/** Card types whose generated art is a best guess (no reference photo was available). */
val APPROXIMATE_DESIGNS: Set<CardType> = CardType.entries.filter { design(it).approximate }.toSet()

/**
 * Card outline with the small notch on the right edge that transit IC cards have (it lets you
 * feel which way round the card is).
 */
class NotchedCardShape(private val corner: androidx.compose.ui.unit.Dp) : androidx.compose.ui.graphics.Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density,
    ): androidx.compose.ui.graphics.Outline {
        val r = with(density) { corner.toPx() }
        val card = Path().apply {
            addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, size.height, CornerRadius(r)))
        }
        val depth = size.width * 0.018f
        val notch = Path().apply {
            moveTo(size.width + 1f, size.height * 0.69f)
            lineTo(size.width - depth, size.height * 0.725f)
            lineTo(size.width + 1f, size.height * 0.85f)
            close()
        }
        val result = Path().apply { op(card, notch, androidx.compose.ui.graphics.PathOperation.Difference) }
        return androidx.compose.ui.graphics.Outline.Generic(result)
    }
}
