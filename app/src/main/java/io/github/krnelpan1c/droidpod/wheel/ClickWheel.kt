package io.github.krnelpan1c.droidpod.wheel

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.krnelpan1c.droidpod.ui.drawPlayPauseGlyph
import io.github.krnelpan1c.droidpod.ui.drawSkipGlyph
import io.github.krnelpan1c.droidpod.ui.theme.LocalPodColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min

interface WheelFeedback {
    fun tick()
    fun press()
}

private const val LONG_PRESS_MS = 500L
private const val HOLD_REPEAT_MS = 250L
/** How long a tapped button stays lit after the finger lifts. */
private const val TAP_FLASH_MS = 120L

/** Rotation needed before a touch on the ring counts as scrolling instead of a tap. */
private const val SCROLL_THRESHOLD = PI / 18 // 10°

@Composable
fun ClickWheel(
    onEvent: (WheelEvent) -> Unit,
    ticksPerRevolution: Int,
    feedback: WheelFeedback,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPodColors.current
    val currentOnEvent by rememberUpdatedState(onEvent)
    val currentFeedback by rememberUpdatedState(feedback)
    val ticker = remember { WheelTicker(ticksPerRevolution) }
    SideEffect { ticker.ticksPerRevolution = ticksPerRevolution }
    var pressed by remember { mutableStateOf<WheelButton?>(null) }
    val scope = rememberCoroutineScope()
    val textMeasurer = rememberTextMeasurer()

    Canvas(
        modifier
            .aspectRatio(1f)
            .pointerInput(Unit) {
                trackWheel(
                    ticker = ticker,
                    onPressed = { pressed = it },
                    onTapped = { button ->
                        pressed = button
                        scope.launch {
                            delay(TAP_FLASH_MS)
                            if (pressed == button) pressed = null
                        }
                    },
                    emit = { currentOnEvent(it) },
                    feedback = { currentFeedback },
                )
            },
    ) {
        val radius = min(size.width, size.height) / 2
        val c = center
        val centerRadius = radius * WheelGeometry.CENTER_RATIO
        val labelRadius = radius * (1 + WheelGeometry.CENTER_RATIO) / 2

        drawCircle(colors.wheel, radius, c)
        drawCircle(colors.wheelEdge, radius - 0.5.dp.toPx(), c, style = Stroke(1.dp.toPx()))

        pressed?.takeIf { it != WheelButton.Select }?.let { button ->
            val start = when (button) {
                WheelButton.Menu -> -135f
                WheelButton.Next -> -45f
                WheelButton.PlayPause -> 45f
                else -> 135f
            }
            drawArc(colors.pressed, start, 90f, useCenter = true, topLeft = c - Offset(radius, radius), size = Size(radius * 2, radius * 2))
        }

        drawCircle(colors.center, centerRadius, c)
        drawCircle(colors.centerEdge, centerRadius, c, style = Stroke(1.dp.toPx()))
        if (pressed == WheelButton.Select) drawCircle(colors.pressed, centerRadius, c)

        val glyph = radius * 0.13f
        val label = textMeasurer.measure(
            "MENU",
            TextStyle(color = colors.wheelLabel, fontSize = (radius * 0.085f).toSp(), fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
        )
        drawText(label, topLeft = Offset(c.x - label.size.width / 2f, c.y - labelRadius - label.size.height / 2f))
        drawSkipGlyph(Offset(c.x - labelRadius, c.y), glyph, colors.wheelLabel, forward = false)
        drawSkipGlyph(Offset(c.x + labelRadius, c.y), glyph, colors.wheelLabel, forward = true)
        drawPlayPauseGlyph(Offset(c.x - glyph * 0.05f, c.y + labelRadius), glyph, colors.wheelLabel)
    }
}

private suspend fun PointerInputScope.trackWheel(
    ticker: WheelTicker,
    onPressed: (WheelButton?) -> Unit,
    onTapped: (WheelButton) -> Unit,
    emit: (WheelEvent) -> Unit,
    feedback: () -> WheelFeedback,
) = awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false)
    val radius = min(size.width, size.height) / 2f
    val center = Offset(size.width / 2f, size.height / 2f)
    val rel = down.position - center
    val button = when (val region = WheelGeometry.region(rel.x, rel.y, radius)) {
        WheelRegion.Outside -> return@awaitEachGesture
        WheelRegion.Center -> WheelButton.Select
        is WheelRegion.Ring -> region.button
    }
    down.consume()
    // The center can't scroll, so it lights up right away. Ring buttons only light up
    // once the touch turns out to be a press, so starting a scroll never flashes one.
    if (button == WheelButton.Select) onPressed(button)
    val tapped = try {
        trackPress(
            down = down,
            button = button,
            allowScroll = button != WheelButton.Select,
            center = center,
            deadZone = radius * WheelGeometry.CENTER_RATIO * 0.6f,
            ticker = ticker,
            onHoldStart = { onPressed(button) },
            emit = emit,
            feedback = feedback,
        )
    } finally {
        onPressed(null)
    }
    if (tapped) onTapped(button)
}

private suspend fun AwaitPointerEventScope.trackPress(
    down: PointerInputChange,
    button: WheelButton,
    allowScroll: Boolean,
    center: Offset,
    deadZone: Float,
    ticker: WheelTicker,
    onHoldStart: () -> Unit,
    emit: (WheelEvent) -> Unit,
    feedback: () -> WheelFeedback,
): Boolean {
    var lastAngle = (down.position - center).let { WheelGeometry.angleOf(it.x, it.y) }
    var preScroll = 0.0
    var scrolling = false
    var holdCount = -1
    var nextHoldAt = SystemClock.uptimeMillis() + LONG_PRESS_MS
    ticker.reset()

    while (true) {
        val event = if (scrolling) {
            awaitPointerEvent()
        } else {
            withTimeoutOrNull((nextHoldAt - SystemClock.uptimeMillis()).coerceAtLeast(0)) { awaitPointerEvent() }
        }
        if (event == null) {
            holdCount++
            if (holdCount == 0) {
                onHoldStart()
                feedback().press()
            }
            emit(WheelEvent.Hold(button, holdCount))
            nextHoldAt += HOLD_REPEAT_MS
            continue
        }
        val change = event.changes.firstOrNull { it.id == down.id }
        if (change == null || !change.pressed) {
            change?.consume()
            when {
                scrolling -> emit(WheelEvent.ScrollEnd)
                holdCount >= 0 -> emit(WheelEvent.HoldEnd(button))
                // Lifted after the gesture was cancelled elsewhere: don't fire a tap.
                change == null -> Unit
                else -> {
                    feedback().press()
                    emit(WheelEvent.Press(button))
                    return true
                }
            }
            return false
        }
        change.consume()
        if (!allowScroll || holdCount >= 0) continue

        val rel = change.position - center
        val angle = WheelGeometry.angleOf(rel.x, rel.y)
        // Near the middle tiny movements swing the angle wildly; skip them.
        if (rel.getDistance() < deadZone) {
            lastAngle = angle
            continue
        }
        val delta = wrapAngle(angle - lastAngle)
        lastAngle = angle

        val rotation = if (scrolling) {
            delta
        } else {
            preScroll += delta
            if (abs(preScroll) < SCROLL_THRESHOLD) continue
            scrolling = true
            preScroll
        }
        val ticks = ticker.add(rotation)
        if (ticks != 0) {
            feedback().tick()
            emit(WheelEvent.Scroll(ticks))
        }
    }
}
