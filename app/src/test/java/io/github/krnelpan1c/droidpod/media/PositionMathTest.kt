package io.github.krnelpan1c.droidpod.media

import org.junit.Assert.assertEquals
import org.junit.Test

class PositionMathTest {
    @Test
    fun playing_advancesWithElapsedTimeAndSpeed() {
        assertEquals(15_000, PositionMath.extrapolate(10_000, 1_000, 1f, true, 6_000, 200_000))
        assertEquals(20_000, PositionMath.extrapolate(10_000, 1_000, 2f, true, 6_000, 200_000))
    }

    @Test
    fun paused_staysPut() {
        assertEquals(10_000, PositionMath.extrapolate(10_000, 1_000, 1f, false, 60_000, 200_000))
    }

    @Test
    fun clampsToDuration() {
        assertEquals(200_000, PositionMath.extrapolate(199_000, 1_000, 1f, true, 60_000, 200_000))
    }

    @Test
    fun unknownDuration_doesNotClamp() {
        assertEquals(70_000, PositionMath.extrapolate(10_000, 1_000, 1f, true, 61_000, 0))
    }

    @Test
    fun unknownPosition_isZero() {
        assertEquals(0, PositionMath.extrapolate(-1, 1_000, 1f, true, 6_000, 200_000))
    }

    @Test
    fun format_minutesAndHours() {
        assertEquals("0:00", PositionMath.format(0))
        assertEquals("0:07", PositionMath.format(7_900))
        assertEquals("1:23", PositionMath.format(83_000))
        assertEquals("1:02:03", PositionMath.format(3_723_000))
    }
}
