package io.github.krnelpan1c.droidpod.wheel

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot

/** Wraps an angle difference into (-PI, PI] so crossing the 9 o'clock seam doesn't jump. */
fun wrapAngle(delta: Double): Double {
    var d = delta % (2 * PI)
    if (d > PI) d -= 2 * PI
    if (d <= -PI) d += 2 * PI
    return d
}

/** Turns continuous rotation into whole ticks, carrying the remainder between calls. */
class WheelTicker(var ticksPerRevolution: Int) {
    private var remainder = 0.0

    fun add(deltaRadians: Double): Int {
        remainder += deltaRadians
        val step = 2 * PI / ticksPerRevolution
        val ticks = (remainder / step).toInt()
        remainder -= ticks * step
        return ticks
    }

    fun reset() {
        remainder = 0.0
    }
}

sealed interface WheelRegion {
    data object Outside : WheelRegion
    data object Center : WheelRegion
    data class Ring(val button: WheelButton) : WheelRegion
}

object WheelGeometry {
    /** Center button radius as a fraction of the wheel radius. */
    const val CENTER_RATIO = 0.36f

    /** [x], [y] are relative to the wheel's center, y pointing down. */
    fun region(x: Float, y: Float, radius: Float): WheelRegion {
        val distance = hypot(x, y)
        return when {
            distance > radius -> WheelRegion.Outside
            distance < radius * CENTER_RATIO -> WheelRegion.Center
            else -> WheelRegion.Ring(buttonAt(angleOf(x, y)))
        }
    }

    /** Screen angle in radians: 0 = right, PI/2 = bottom. */
    fun angleOf(x: Float, y: Float): Double = atan2(y.toDouble(), x.toDouble())

    fun buttonAt(angle: Double): WheelButton {
        val degrees = Math.toDegrees(wrapAngle(angle))
        return when {
            degrees >= -135 && degrees < -45 -> WheelButton.Menu
            degrees >= -45 && degrees < 45 -> WheelButton.Next
            degrees >= 45 && degrees < 135 -> WheelButton.PlayPause
            else -> WheelButton.Previous
        }
    }
}
