package io.github.krnelpan1c.droidpod.wheel

enum class WheelButton { Menu, Previous, Next, PlayPause, Select }

sealed interface WheelEvent {
    /** Positive = clockwise (down the list, volume up). */
    data class Scroll(val ticks: Int) : WheelEvent

    /** Finger lifted after scrolling. */
    data object ScrollEnd : WheelEvent

    /** Short tap on a button. */
    data class Press(val button: WheelButton) : WheelEvent

    /** Button held: repeat 0 fires at the long-press timeout, then again at a steady rate. */
    data class Hold(val button: WheelButton, val repeat: Int) : WheelEvent

    data class HoldEnd(val button: WheelButton) : WheelEvent
}
