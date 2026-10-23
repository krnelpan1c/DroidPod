package io.github.krnelpan1c.droidpod.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import io.github.krnelpan1c.droidpod.settings.PodThemeId

/** Case and wheel colors per theme. The screen's look is [LcdStyle]. */
@Immutable
data class PodColors(
    val isLight: Boolean,
    val caseTop: Color,
    val caseBottom: Color,
    val bezel: Color,
    val wheel: Color,
    val wheelEdge: Color,
    val wheelLabel: Color,
    val center: Color,
    val centerEdge: Color,
    val pressed: Color,
)

fun podColors(theme: PodThemeId): PodColors = when (theme) {
    PodThemeId.Classic -> PodColors(
        isLight = true,
        caseTop = Color(0xFFF7F7F8),
        caseBottom = Color(0xFFE0E1E4),
        bezel = Color(0xFF2B2D30),
        wheel = Color(0xFFE9EAED),
        wheelEdge = Color(0xFFCDCFD4),
        wheelLabel = Color(0xFFA3A6AC),
        center = Color(0xFFF9F9FA),
        centerEdge = Color(0xFFD3D5DA),
        pressed = Color(0x14000000),
    )
    PodThemeId.Black -> PodColors(
        isLight = false,
        caseTop = Color(0xFF2C2C2F),
        caseBottom = Color(0xFF131315),
        bezel = Color(0xFF000000),
        wheel = Color(0xFF1D1D20),
        wheelEdge = Color(0xFF3A3A3E),
        wheelLabel = Color(0xFFB9BAC0),
        center = Color(0xFF2A2A2E),
        centerEdge = Color(0xFF3E3E43),
        pressed = Color(0x1FFFFFFF),
    )
    PodThemeId.Red -> PodColors(
        isLight = false,
        caseTop = Color(0xFFD3142F),
        caseBottom = Color(0xFF9A0B1E),
        bezel = Color(0xFF111111),
        wheel = Color(0xFF17171A),
        wheelEdge = Color(0xFF34343A),
        wheelLabel = Color(0xFFDADADF),
        center = Color(0xFFC0102A),
        centerEdge = Color(0xFF7E0817),
        pressed = Color(0x1FFFFFFF),
    )
    PodThemeId.Silver -> PodColors(
        isLight = true,
        caseTop = Color(0xFFDCDEE1),
        caseBottom = Color(0xFFB4B8BE),
        bezel = Color(0xFF1E2023),
        wheel = Color(0xFFF1F1F3),
        wheelEdge = Color(0xFFC3C6CB),
        wheelLabel = Color(0xFF8B8F96),
        center = Color(0xFFD5D8DC),
        centerEdge = Color(0xFFB3B7BD),
        pressed = Color(0x14000000),
    )
}

val LocalPodColors = staticCompositionLocalOf { podColors(PodThemeId.Classic) }
