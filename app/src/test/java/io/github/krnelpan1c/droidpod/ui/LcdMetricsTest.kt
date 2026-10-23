package io.github.krnelpan1c.droidpod.ui

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LcdMetricsTest {
    private val density = Density(density = 2.75f, fontScale = 1f)

    @Test
    fun retro_snapsFontsToThePixelFontGrid() {
        listOf(180.dp, 260.dp, 333.dp, 420.dp).forEach { height ->
            val m = LcdMetrics.forHeight(height, density, retro = true)
            with(density) {
                val font = m.font.toPx()
                val small = m.smallFont.toPx()
                assertEquals("font at $height", 0f, font % 16f, 0.01f)
                assertEquals("small font at $height", 0f, small % 16f, 0.01f)
                assertEquals("pixel at $height", small / 16f, m.pixel.toPx(), 0.01f)
            }
        }
    }

    @Test
    fun retro_isLargerThanClassic() {
        // Chicago's capitals are ~0.56 em vs ~0.71 em, so it needs a bigger size to read the same.
        listOf(260.dp, 333.dp, 420.dp).forEach { height ->
            val retro = LcdMetrics.forHeight(height, density, retro = true)
            val classic = LcdMetrics.forHeight(height, density, retro = false)
            with(density) {
                assertTrue("font at $height", retro.font.toPx() >= classic.font.toPx() * 1.3f)
                assertTrue("small font at $height", retro.smallFont.toPx() >= classic.smallFont.toPx() * 1.3f)
                // A line of the main font still fits in its row.
                assertTrue("row fit at $height", retro.font.toPx() * 1.2f <= retro.rowHeight.toPx())
            }
        }
    }

    @Test
    fun retro_neverDropsBelowOneGridStep() {
        val m = LcdMetrics.forHeight(20.dp, density, retro = true)
        with(density) { assertTrue(m.smallFont.toPx() >= 16f) }
    }

    @Test
    fun classic_keepsProportionalSizesAndHairlines() {
        val m = LcdMetrics.forHeight(333.dp, density, retro = false)
        with(density) {
            assertEquals((m.rowHeight * 0.48f).toPx(), m.font.toPx(), 0.01f)
            assertEquals(1f, m.pixel.toPx(), 0.01f)
        }
    }
}
