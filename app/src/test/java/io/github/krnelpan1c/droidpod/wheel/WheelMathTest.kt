package io.github.krnelpan1c.droidpod.wheel

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI

class WheelMathTest {
    @Test
    fun wrapAngle_keepsSmallDeltas() {
        assertEquals(0.3, wrapAngle(0.3), 1e-9)
        assertEquals(-0.3, wrapAngle(-0.3), 1e-9)
    }

    @Test
    fun wrapAngle_crossingTheSeamIsASmallStep() {
        // From just below +PI to just above -PI is a small clockwise move, not a full turn back.
        val delta = wrapAngle((-PI + 0.05) - (PI - 0.05))
        assertEquals(0.1, delta, 1e-9)
        assertEquals(-0.1, wrapAngle((PI - 0.05) - (-PI + 0.05)), 1e-9)
    }

    @Test
    fun ticker_emitsWholeTicksAndCarriesRemainder() {
        val ticker = WheelTicker(ticksPerRevolution = 24)
        val step = 2 * PI / 24
        assertEquals(0, ticker.add(step * 0.6))
        assertEquals(1, ticker.add(step * 0.6))
        assertEquals(3, ticker.add(step * 2.9))
        assertEquals(-1, ticker.add(-step * 1.5))
    }

    @Test
    fun ticker_fullRevolutionMatchesSetting() {
        val ticker = WheelTicker(ticksPerRevolution = 16)
        var total = 0
        repeat(100) { total += ticker.add(2 * PI / 100) }
        // Absorb floating-point shortfall from summing 100 slices.
        total += ticker.add(1e-9)
        assertEquals(16, total)
    }

    @Test
    fun ticker_resetDropsRemainder() {
        val ticker = WheelTicker(ticksPerRevolution = 24)
        ticker.add(2 * PI / 24 * 0.9)
        ticker.reset()
        assertEquals(0, ticker.add(2 * PI / 24 * 0.2))
    }

    @Test
    fun region_mapsQuadrantsToButtons() {
        val r = 100f
        assertEquals(WheelRegion.Ring(WheelButton.Menu), WheelGeometry.region(0f, -70f, r))
        assertEquals(WheelRegion.Ring(WheelButton.Next), WheelGeometry.region(70f, 0f, r))
        assertEquals(WheelRegion.Ring(WheelButton.PlayPause), WheelGeometry.region(0f, 70f, r))
        assertEquals(WheelRegion.Ring(WheelButton.Previous), WheelGeometry.region(-70f, 0f, r))
        assertEquals(WheelRegion.Ring(WheelButton.Menu), WheelGeometry.region(30f, -60f, r))
    }

    @Test
    fun region_centerAndOutside() {
        assertEquals(WheelRegion.Center, WheelGeometry.region(10f, 10f, 100f))
        assertEquals(WheelRegion.Outside, WheelGeometry.region(90f, 90f, 100f))
    }
}
