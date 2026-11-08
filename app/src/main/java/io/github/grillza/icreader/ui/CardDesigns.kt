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
private enum class Motif { SUICA_PANEL, CIRCLE_RIGHT, CIRCLE_LEFT, PLAIN, SWOOSH_RIGHT, BARS_LEFT, BUBBLES, WAVE_BOTTOM, CIRCLE, BLOCK_RIGHT, BAND_DIAGONAL, ARC_BOTTOM }

private data class Design(
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
)

private val SILVER = listOf(Color(0xFFE7E9EC), Color(0xFFC5C9CE), Color(0xFFDDE0E3))
private val WHITE = listOf(Color(0xFFFFFFFF), Color(0xFFEDEEF0))
private val PEARL = listOf(Color(0xFFF7F8F9), Color(0xFFE4E6E9), Color(0xFFF0F1F3))

private fun design(type: CardType): Design = when (type) {
    // References: photos of the cards (Suica, PASMO, TOICA, manaca, PiTaPa, SUGOCA, nimoca, ICOCA).
    CardType.SUICA -> Design(PEARL, Motif.SUICA_PANEL, listOf(Color(0xFF62BD2F)), "Suica", Color.White, Alignment.BottomStart)
    CardType.PASMO -> Design(SILVER, Motif.BARS_LEFT, listOf(Color(0xFFEA5A9C)), "PASMO", Color(0xFFEA5A9C), Alignment.BottomStart)
    CardType.ICOCA -> Design(listOf(Color(0xFF2A66C9), Color(0xFF4F92E6)), Motif.PLAIN, listOf(Color.White), "ICOCA", Color(0xFFDCE9FF), Alignment.Center, brandExact = true)
    CardType.TOICA -> Design(SILVER, Motif.WAVE_BOTTOM, listOf(Color(0xFF17A9E1)), "toica", Color.White, Alignment.BottomEnd)
    CardType.MANACA -> Design(listOf(Color(0xFFD3D6DF), Color(0xFFBBC0CC)), Motif.CIRCLE, listOf(Color(0xFFF1E63B)), "manaca", Color(0xFF9DA3B3), Alignment.Center)
    CardType.KITACA -> Design(PEARL, Motif.BAND_DIAGONAL, listOf(Color(0xFF9CCB3B), Color(0xFF7AB52E)), "Kitaca", Color(0xFF6FA82B), Alignment.TopStart, approximate = true)
    CardType.SUGOCA -> Design(PEARL, Motif.CIRCLE_RIGHT, listOf(Color(0xFFB7CFA2)), "SUGOCA", Color(0xFFE4462E), Alignment.BottomStart)
    CardType.NIMOCA -> Design(PEARL, Motif.CIRCLE_LEFT, listOf(Color(0xFF45A2CB)), "nimoca", Color.White, Alignment.Center, brandExact = true, brandShift = -0.13f)
    CardType.HAYAKAKEN -> Design(PEARL, Motif.BAND_DIAGONAL, listOf(Color(0xFF7FC3E8), Color(0xFF3E9ED6)), "Hayakaken", Color(0xFF2E86C1), Alignment.TopStart, approximate = true)
    CardType.PITAPA -> Design(SILVER, Motif.BLOCK_RIGHT, listOf(Color(0xFF8D8EC8)), "PiTaPa", Color(0xFFC9CCD2), Alignment.TopEnd)
    CardType.TRANSIT -> Design(SILVER, Motif.WAVE_BOTTOM, listOf(Color(0xFF607D8B)), "IC", Color.White, Alignment.BottomEnd)
    CardType.EDY -> Design(WHITE, Motif.BAND_DIAGONAL, listOf(Color(0xFFE60012), Color(0xFFBF0000)), "Edy", Color(0xFFD0000F), Alignment.TopStart, approximate = true)
    CardType.NANACO -> Design(listOf(Color(0xFFFFF8E7), Color(0xFFF7EBCB)), Motif.ARC_BOTTOM, listOf(Color(0xFFF7A800), Color(0xFF6CC3E8)), "nanaco", Color(0xFFE38D00), Alignment.TopStart, approximate = true)
    CardType.WAON -> Design(listOf(Color(0xFFCDEBF8), Color(0xFF86CFEF)), Motif.BUBBLES, listOf(Color.White), "WAON", Color(0xFF1C6FD0), Alignment.TopStart, approximate = true)
}

@Composable
internal fun BoxScope.GeneratedArt(type: CardType, showBrand: Boolean) {
    val d = design(type)
    Canvas(Modifier.matchParentSize()) {
        drawRect(Brush.linearGradient(d.base, start = Offset.Zero, end = Offset(size.width, size.height)))
        // Soft sheen like a metallic print.
        drawRect(
            Brush.radialGradient(
                listOf(Color.White.copy(alpha = 0.45f), Color.Transparent),
                center = Offset(size.width * 0.3f, size.height * 0.2f),
                radius = size.width * 0.6f,
            ),
        )
        drawMotif(d)
        // Tap-direction arrow found at the top-left of most IC cards.
        if (d.brandAlign != Alignment.TopStart && d.motif != Motif.BUBBLES) {
            val y = size.height * 0.09f
            val x0 = size.width * 0.05f
            val c = Color(0xFF2B5FA8)
            drawLine(c, Offset(x0, y), Offset(x0 + size.width * 0.06f, y), size.height * 0.012f)
            drawLine(c, Offset(x0, y), Offset(x0 + size.width * 0.015f, y - size.height * 0.02f), size.height * 0.012f)
        }
    }
    if (showBrand) {
        BoxWithConstraints(Modifier.matchParentSize()) {
            val w = maxWidth
            val scale = when {
                d.brandShift != 0f -> 0.075f // inside a circle
                d.brand.length > 7 -> 0.085f
                else -> 0.11f
            }
            val fontSize = with(LocalDensity.current) { (w * scale).toSp() }
            val pad = w * 0.06f
            val modifier = when {
                d.brandExact || d.brandAlign == Alignment.Center ->
                    Modifier.align(d.brandAlign).offset(x = w * d.brandShift)
                d.motif == Motif.SUICA_PANEL -> Modifier.align(Alignment.BottomStart).padding(start = w * 0.09f, bottom = w * 0.045f)
                d.brandAlign == Alignment.BottomStart -> Modifier.align(Alignment.BottomStart).padding(start = pad, bottom = w * 0.05f)
                else -> Modifier.align(d.brandAlign).padding(horizontal = pad, vertical = w * 0.04f)
            }
            Text(
                d.brand,
                color = d.brandColor,
                fontSize = fontSize,
                fontWeight = FontWeight.Black,
                fontStyle = if (d.italic) FontStyle.Italic else FontStyle.Normal,
                letterSpacing = if (d.motif == Motif.SUICA_PANEL) fontSize * 0.12f else androidx.compose.ui.unit.TextUnit.Unspecified,
                modifier = modifier,
            )
        }
    }
}

private fun DrawScope.drawMotif(d: Design) {
    val w = size.width
    val h = size.height
    val accent = if (d.accent.size > 1) Brush.linearGradient(d.accent, Offset(0f, 0f), Offset(w, h)) else null
    val solid = d.accent.first()
    fun fill(path: Path) = if (accent != null) drawPath(path, accent) else drawPath(path, solid)

    when (d.motif) {
        Motif.SUICA_PANEL -> {
            // Rounded green panel on the left; its right edge slants down toward the bottom-right.
            val r = w * 0.035f
            val l = w * 0.045f
            val t = h * 0.27f
            val b = h * 0.95f
            val tr = w * 0.40f
            val br = w * 0.86f
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
            ).forEach { (x, y, r) -> drawCircle(c.copy(alpha = 0.35f), h * r, Offset(w * x, h * y)) }
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
        Motif.ARC_BOTTOM -> {
            drawCircle(d.accent.last().copy(alpha = 0.9f), h * 0.62f, Offset(w * 0.98f, h * 1.15f))
            drawCircle(d.accent.first(), h * 0.50f, Offset(w * 0.72f, h * 1.12f))
        }
    }
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
