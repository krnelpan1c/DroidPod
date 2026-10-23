package io.github.krnelpan1c.droidpod.ui

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.krnelpan1c.droidpod.media.NowPlaying
import io.github.krnelpan1c.droidpod.media.PositionMath
import io.github.krnelpan1c.droidpod.media.VolumeLevel
import io.github.krnelpan1c.droidpod.ui.theme.LcdStyle
import io.github.krnelpan1c.droidpod.ui.theme.LocalLcdStyle
import kotlinx.coroutines.delay

@Composable
fun NowPlayingScreen(
    np: NowPlaying?,
    mode: NowPlayingMode,
    volumeVisible: Boolean,
    scrubPosition: Long?,
    volume: VolumeLevel,
) {
    val style = LocalLcdStyle.current
    val metrics = LocalLcdMetrics.current
    if (np == null) {
        Column(
            Modifier.fillMaxSize().padding(metrics.padding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText("Nothing Playing", style = lcdText())
            Spacer(Modifier.height(metrics.padding * 0.5f))
            BasicText("Start music in any app", style = lcdText(metrics.smallFont, style.secondaryText, FontWeight.Normal))
        }
        return
    }

    val livePosition by produceState(PositionMath.current(np, SystemClock.elapsedRealtime()), np) {
        while (true) {
            value = PositionMath.current(np, SystemClock.elapsedRealtime())
            delay(250)
        }
    }
    val position = scrubPosition ?: livePosition

    Column(Modifier.fillMaxSize().padding(metrics.padding)) {
        if (style.showArt) {
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val artSize = minOf(maxHeight, maxWidth * 0.45f)
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Art(np, Modifier.size(artSize))
                    Spacer(Modifier.width(metrics.padding))
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                        QueuePosition(np)
                        TrackInfo(np)
                    }
                }
            }
        } else {
            // Monochrome models had no artwork: "3 of 12" in the corner, track info centered.
            QueuePosition(np)
            Column(
                Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TrackInfo(np)
            }
        }
        Spacer(Modifier.height(metrics.padding * 0.6f))
        Box(Modifier.fillMaxWidth().height(metrics.rowHeight)) {
            if (mode == NowPlayingMode.Volume && volumeVisible) {
                VolumeBar(volume)
            } else {
                ProgressBar(position, np.durationMs, scrubbing = mode == NowPlayingMode.Scrub)
            }
        }
    }
}

@Composable
private fun QueuePosition(np: NowPlaying) {
    val position = np.queuePosition ?: return
    val metrics = LocalLcdMetrics.current
    BasicText(
        "${position.number} of ${position.total}",
        style = lcdText(metrics.smallFont, LocalLcdStyle.current.secondaryText, FontWeight.Normal),
    )
}

@Composable
private fun TrackInfo(np: NowPlaying) {
    val style = LocalLcdStyle.current
    val metrics = LocalLcdMetrics.current
    // Without artwork the text gets the whole screen, so it's menu-sized and scrolls instead of truncating.
    val large = !style.showArt
    BasicText(
        np.title.ifBlank { "Unknown" },
        style = lcdText(),
        maxLines = 1,
        modifier = Modifier.basicMarquee(initialDelayMillis = 1_500),
    )
    np.artist?.let { InfoLine(it, large) }
    np.album?.let { InfoLine(it, large) }
    BasicText(
        np.appLabel,
        // The pixel font stays on its grid; Classic's label is a touch smaller than the info lines.
        style = lcdText(if (style.retro) metrics.smallFont else metrics.smallFont * 0.85f, style.secondaryText, FontWeight.Normal),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun InfoLine(text: String, large: Boolean) {
    val metrics = LocalLcdMetrics.current
    BasicText(
        text,
        style = lcdText(if (large) metrics.font else metrics.smallFont, LocalLcdStyle.current.text, FontWeight.Normal),
        maxLines = 1,
        overflow = if (large) TextOverflow.Clip else TextOverflow.Ellipsis,
        modifier = if (large) Modifier.basicMarquee(initialDelayMillis = 1_500) else Modifier,
    )
}

@Composable
private fun Art(np: NowPlaying, modifier: Modifier) {
    val style = LocalLcdStyle.current
    val art = np.art
    if (art != null) {
        Image(art, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier.border(0.5.dp, style.divider))
    } else {
        // Placeholder: a musical note on a soft gradient.
        Canvas(
            modifier
                .background(Brush.verticalGradient(listOf(Color(0xFFE9ECF0), Color(0xFFC6CCD4))))
                .border(0.5.dp, style.divider),
        ) {
            val s = size.minDimension
            val note = Color(0xFF8C95A1)
            val stemX = center.x + s * 0.08f
            drawCircle(note, s * 0.11f, Offset(stemX - s * 0.1f, center.y + s * 0.18f))
            drawRect(note, Offset(stemX - s * 0.02f, center.y - s * 0.28f), Size(s * 0.04f, s * 0.47f))
            val flag = Path().apply {
                moveTo(stemX + s * 0.02f, center.y - s * 0.28f)
                lineTo(stemX + s * 0.2f, center.y - s * 0.16f)
                lineTo(stemX + s * 0.2f, center.y - s * 0.06f)
                lineTo(stemX + s * 0.02f, center.y - s * 0.18f)
                close()
            }
            drawPath(flag, note)
        }
    }
}

/** Progress and volume bar: square in both styles, glossy in Classic and outlined in Retro. */
private fun DrawScope.drawBar(style: LcdStyle, fraction: Float, pixel: Float, showFill: Boolean = true) {
    drawRect(style.track)
    if (showFill) drawRect(style.fill, size = Size(size.width * fraction, size.height))
    drawRect(
        style.divider,
        topLeft = Offset(pixel / 2, pixel / 2),
        size = Size(size.width - pixel, size.height - pixel),
        style = Stroke(pixel),
    )
}

@Composable
private fun ProgressBar(position: Long, duration: Long, scrubbing: Boolean) {
    val style = LocalLcdStyle.current
    val metrics = LocalLcdMetrics.current
    val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Canvas(Modifier.fillMaxWidth().height(metrics.rowHeight * 0.32f)) {
            // In Retro the fill is the same ink as the marker, so hide it while scrubbing.
            drawBar(style, fraction, metrics.pixel.toPx(), showFill = !(scrubbing && style.retro))
            if (scrubbing) {
                // Diamond marker, like the original's scrubber.
                val x = size.width * fraction
                val h = size.height * 0.9f
                val diamond = Path().apply {
                    moveTo(x, center.y - h)
                    lineTo(x + h * 0.7f, center.y)
                    lineTo(x, center.y + h)
                    lineTo(x - h * 0.7f, center.y)
                    close()
                }
                drawPath(diamond, style.text)
            }
        }
        Spacer(Modifier.height(metrics.padding * 0.3f))
        Row(Modifier.fillMaxWidth()) {
            val small = lcdText(metrics.smallFont * 0.9f, style.text, FontWeight.SemiBold)
            BasicText(PositionMath.format(position), style = small)
            Spacer(Modifier.weight(1f))
            if (duration > 0) BasicText("-" + PositionMath.format(duration - position), style = small)
        }
    }
}

@Composable
private fun VolumeBar(volume: VolumeLevel) {
    val style = LocalLcdStyle.current
    val metrics = LocalLcdMetrics.current
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        val glyph = metrics.rowHeight * 0.5f
        Canvas(Modifier.size(glyph)) { drawSpeakerGlyph(center, size.minDimension, style.text, waves = 0) }
        Spacer(Modifier.width(metrics.padding * 0.6f))
        Canvas(Modifier.weight(1f).height(metrics.rowHeight * 0.32f)) {
            drawBar(style, volume.fraction, metrics.pixel.toPx())
        }
        Spacer(Modifier.width(metrics.padding * 0.6f))
        Canvas(Modifier.size(glyph)) { drawSpeakerGlyph(center, size.minDimension, style.text, waves = 2) }
    }
}
