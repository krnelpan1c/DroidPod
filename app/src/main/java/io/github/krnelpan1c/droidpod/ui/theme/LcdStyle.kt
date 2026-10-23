package io.github.krnelpan1c.droidpod.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import io.github.krnelpan1c.droidpod.R
import io.github.krnelpan1c.droidpod.settings.SystemStyle

/** How the screen is drawn. Case and wheel colors live in [PodColors]. */
@Immutable
data class LcdStyle(
    /** Monochrome LCD: one ink color, flat fills, square bars, pixel font, no artwork. */
    val retro: Boolean,
    val screen: Color,
    val text: Color,
    val secondaryText: Color,
    val highlight: Brush,
    val highlightText: Color,
    val status: Brush,
    val divider: Color,
    val fill: Brush,
    val track: Color,
    val font: FontFamily,
) {
    val showArt: Boolean get() = !retro
}

/** Chicago Kare by Duane King (MIT): a faithful outline of the 1984 Chicago bitmap font. */
private val ChicagoKare = FontFamily(Font(R.font.chicago_kare))

private val Ink = Color(0xFF26261F)
private val RetroLcd = Color(0xFFC5C5BE)

fun lcdStyle(style: SystemStyle): LcdStyle = when (style) {
    SystemStyle.Classic -> LcdStyle(
        retro = false,
        screen = Color.White,
        text = Color(0xFF111111),
        secondaryText = Color(0xFF5A5F66),
        highlight = Brush.verticalGradient(listOf(Color(0xFF6FB1F6), Color(0xFF1C6CD6))),
        highlightText = Color.White,
        status = Brush.verticalGradient(listOf(Color(0xFFFDFDFD), Color(0xFFCDD2D8))),
        divider = Color(0xFF9AA1A9),
        fill = Brush.verticalGradient(listOf(Color(0xFF7DB8F7), Color(0xFF2A78DD))),
        track = Color(0xFFE3E7EC),
        font = FontFamily.SansSerif,
    )
    SystemStyle.Retro -> LcdStyle(
        retro = true,
        screen = RetroLcd,
        text = Ink,
        secondaryText = Ink,
        highlight = SolidColor(Ink),
        highlightText = RetroLcd,
        status = SolidColor(RetroLcd),
        divider = Ink,
        fill = SolidColor(Ink),
        track = RetroLcd,
        font = ChicagoKare,
    )
}

val LocalLcdStyle = staticCompositionLocalOf { lcdStyle(SystemStyle.Classic) }
