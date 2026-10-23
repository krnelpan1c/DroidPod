package io.github.krnelpan1c.droidpod.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

// Small transport glyphs drawn as paths, so they never depend on font coverage.
// Each fits a square of side [size] centered on [center].

fun DrawScope.drawPlayGlyph(center: Offset, size: Float, color: Color) =
    drawPlayGlyph(center, size, SolidColor(color))

fun DrawScope.drawPlayGlyph(center: Offset, size: Float, brush: Brush) {
    val h = size / 2
    val path = Path().apply {
        moveTo(center.x - h * 0.75f, center.y - h)
        lineTo(center.x + h * 0.85f, center.y)
        lineTo(center.x - h * 0.75f, center.y + h)
        close()
    }
    drawPath(path, brush)
}

fun DrawScope.drawPauseGlyph(center: Offset, size: Float, brush: Brush) {
    val h = size / 2
    val bar = size * 0.3f
    drawRect(brush, Offset(center.x - h * 0.8f, center.y - h), Size(bar, size))
    drawRect(brush, Offset(center.x + h * 0.8f - bar, center.y - h), Size(bar, size))
}

/** ▶❚❚ as on the bottom of the wheel. */
fun DrawScope.drawPlayPauseGlyph(center: Offset, size: Float, color: Color) {
    drawPlayGlyph(Offset(center.x - size * 0.45f, center.y), size * 0.8f, color)
    val bar = size * 0.16f
    val top = center.y - size * 0.4f
    drawRect(color, Offset(center.x + size * 0.18f, top), Size(bar, size * 0.8f))
    drawRect(color, Offset(center.x + size * 0.48f, top), Size(bar, size * 0.8f))
}

/** ▶▶| (forward) or |◀◀ (back). */
fun DrawScope.drawSkipGlyph(center: Offset, size: Float, color: Color, forward: Boolean) {
    val dir = if (forward) 1f else -1f
    val h = size * 0.38f
    val w = size * 0.36f
    fun triangle(tipX: Float) = Path().apply {
        moveTo(tipX - w * dir, center.y - h)
        lineTo(tipX, center.y)
        lineTo(tipX - w * dir, center.y + h)
        close()
    }
    val tip = center.x + size * 0.32f * dir
    drawPath(triangle(tip - w * dir), color)
    drawPath(triangle(tip), color)
    val bar = size * 0.1f
    val barX = if (forward) tip else tip - bar
    drawRect(color, Offset(barX, center.y - h), Size(bar, h * 2))
}

fun DrawScope.drawChevron(center: Offset, size: Float, color: Color) {
    val h = size / 2
    val path = Path().apply {
        moveTo(center.x - h * 0.35f, center.y - h * 0.7f)
        lineTo(center.x + h * 0.35f, center.y)
        lineTo(center.x - h * 0.35f, center.y + h * 0.7f)
    }
    drawPath(path, color, style = Stroke(width = size * 0.18f, cap = StrokeCap.Square, join = StrokeJoin.Miter))
}

/** Simple speaker, louder with more waves (0..2). */
fun DrawScope.drawSpeakerGlyph(center: Offset, size: Float, color: Color, waves: Int) {
    val h = size / 2
    val body = Path().apply {
        moveTo(center.x - h, center.y - h * 0.3f)
        lineTo(center.x - h * 0.55f, center.y - h * 0.3f)
        lineTo(center.x - h * 0.05f, center.y - h * 0.8f)
        lineTo(center.x - h * 0.05f, center.y + h * 0.8f)
        lineTo(center.x - h * 0.55f, center.y + h * 0.3f)
        lineTo(center.x - h, center.y + h * 0.3f)
        close()
    }
    drawPath(body, color)
    repeat(waves) { i ->
        val r = h * (0.45f + i * 0.4f)
        drawArc(
            color = color,
            startAngle = -45f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(center.x - h * 0.05f - r, center.y - r),
            size = Size(r * 2, r * 2),
            style = Stroke(width = size * 0.1f, cap = StrokeCap.Round),
        )
    }
}
