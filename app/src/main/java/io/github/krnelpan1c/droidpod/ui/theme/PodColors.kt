package io.github.krnelpan1c.droidpod.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import io.github.krnelpan1c.droidpod.settings.PodThemeId

/** Case and wheel colors per theme. The screen's look is [LcdStyle]. */
@Immutable
data class PodColors(
    val isLight: Boolean,
    val case: Brush,
    /** Drawn over [case]: light from above on the aluminum cases. */
    val caseShade: Brush? = null,
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
        case = plastic(Color(0xFFF7F7F8), Color(0xFFE0E1E4)),
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
        case = plastic(Color(0xFF2C2C2F), Color(0xFF131315)),
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
        case = plastic(Color(0xFFD3142F), Color(0xFF9A0B1E)),
        bezel = Color(0xFF111111),
        wheel = Color(0xFF17171A),
        wheelEdge = Color(0xFF34343A),
        wheelLabel = Color(0xFFDADADF),
        center = Color(0xFFC0102A),
        centerEdge = Color(0xFF7E0817),
        pressed = Color(0x1FFFFFFF),
    )
    // The 2004 iPod mini colors. Its wheel is the same pale grey on every case, with
    // labels printed in the case color (grey on silver).
    PodThemeId.Silver -> mini(Color(0xFFC8CBD0), label = Color(0xFF8B8F96))
    PodThemeId.Gold -> mini(Color(0xFFD8C291), label = Color(0xFFA88A4F))
    PodThemeId.Pink -> mini(Color(0xFFE8AFC6), label = Color(0xFFCB6A91))
    PodThemeId.Blue -> mini(Color(0xFF9DC1E2), label = Color(0xFF4F87BF))
    PodThemeId.Green -> mini(Color(0xFFB4D293), label = Color(0xFF6F9D48))
}

private fun plastic(top: Color, bottom: Color) = Brush.verticalGradient(listOf(top, bottom))

private fun mini(base: Color, label: Color) = PodColors(
    isLight = true,
    case = anodized(base),
    caseShade = Brush.verticalGradient(
        0f to Color.White.copy(alpha = 0.14f),
        0.4f to Color.Transparent,
        1f to Color.Black.copy(alpha = 0.10f),
    ),
    bezel = Color(0xFF26282B),
    wheel = Color(0xFFEEEFF1),
    wheelEdge = Color(0xFFC9CBD0),
    wheelLabel = label,
    center = Color(0xFFE2E3E6),
    centerEdge = Color(0xFFC3C5CA),
    pressed = Color(0x14000000),
)

/**
 * The mini's case is an aluminum tube with rounded sides, so the light lies in
 * soft vertical bands: darker at the edges, a broad satin highlight left of center.
 */
private fun anodized(base: Color): Brush {
    fun light(f: Float) = lerp(base, Color.White, f)
    fun dark(f: Float) = lerp(base, Color.Black, f)
    return Brush.horizontalGradient(
        0f to dark(0.22f),
        0.06f to dark(0.06f),
        0.3f to light(0.32f),
        0.42f to light(0.18f),
        0.62f to base,
        0.8f to light(0.10f),
        0.94f to dark(0.06f),
        1f to dark(0.20f),
    )
}

val LocalPodColors = staticCompositionLocalOf { podColors(PodThemeId.Classic) }
