package io.github.krnelpan1c.droidpod.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.krnelpan1c.droidpod.ui.nav.MenuItem
import io.github.krnelpan1c.droidpod.ui.nav.MenuWindow
import io.github.krnelpan1c.droidpod.ui.nav.NavEntry
import io.github.krnelpan1c.droidpod.ui.theme.LocalLcdStyle
import io.github.krnelpan1c.droidpod.ui.theme.LocalPodColors
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Sizes derived from the LCD's height so the layout scales like a fixed-resolution screen. */
@Immutable
data class LcdMetrics(
    val statusHeight: Dp,
    val rowHeight: Dp,
    val font: TextUnit,
    val smallFont: TextUnit,
    val padding: Dp,
    /** Width of hairlines: one device pixel, or one LCD pixel in the retro style. */
    val pixel: Dp,
    /** Diagnostics text; the same in both styles so reports stay readable. */
    val monoFont: TextUnit,
) {
    companion object {
        const val ROWS = 7

        /** Chicago Kare is drawn on a 16-unit grid per em; whole multiples render crisp. */
        private const val PIXEL_FONT_GRID = 16

        // Font sizes as a fraction of the row height. Chicago's capitals are only 0.56 em
        // (the system font's are ~0.71), and the original menus used big type: capitals
        // about 40% of the row. So Retro needs much larger sizes for the same look.
        private const val CLASSIC_FONT = 0.48f
        private const val CLASSIC_SMALL = 0.38f
        private const val RETRO_FONT = 0.70f
        private const val RETRO_SMALL = 0.56f

        fun forHeight(height: Dp, density: Density, retro: Boolean): LcdMetrics = with(density) {
            val status = height * 0.11f
            val row = (height - status) / ROWS
            var fontPx = (row * if (retro) RETRO_FONT else CLASSIC_FONT).toPx()
            var smallPx = (row * if (retro) RETRO_SMALL else CLASSIC_SMALL).toPx()
            if (retro) {
                fontPx = snapToGrid(fontPx)
                smallPx = snapToGrid(smallPx)
            }
            val pixel = if (retro) (smallPx / PIXEL_FONT_GRID).toDp() else 1f.toDp()
            val mono = (row * CLASSIC_SMALL * 0.8f).toSp()
            LcdMetrics(status, row, fontPx.toSp(), smallPx.toSp(), row * 0.3f, pixel, mono)
        }

        private fun snapToGrid(px: Float): Float =
            ((px / PIXEL_FONT_GRID).roundToInt().coerceAtLeast(1) * PIXEL_FONT_GRID).toFloat()
    }
}

val LocalLcdMetrics = staticCompositionLocalOf {
    LcdMetrics(20.dp, 28.dp, TextUnit.Unspecified, TextUnit.Unspecified, 8.dp, 1.dp, TextUnit.Unspecified)
}

/** Text on the LCD. The retro font has a single weight, so bold is never faked. */
@Composable
fun lcdText(
    size: TextUnit = LocalLcdMetrics.current.font,
    color: Color = LocalLcdStyle.current.text,
    weight: FontWeight = FontWeight.Bold,
): TextStyle {
    val style = LocalLcdStyle.current
    return TextStyle(
        color = color,
        fontSize = size,
        fontWeight = if (style.retro) FontWeight.Normal else weight,
        fontFamily = style.font,
        fontSynthesis = FontSynthesis.None,
    )
}

/** The screen: bezel, status bar and content. */
@Composable
fun Lcd(
    title: String,
    playGlyph: Boolean?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = LocalPodColors.current
    val style = LocalLcdStyle.current
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier
            .background(colors.bezel, RoundedCornerShape(10.dp))
            .padding(4.dp),
    ) {
        val metrics = remember(maxHeight, density, style.retro) { LcdMetrics.forHeight(maxHeight, density, style.retro) }
        CompositionLocalProvider(LocalLcdMetrics provides metrics) {
            Column(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(6.dp))
                    .background(style.screen),
            ) {
                StatusBar(title, playGlyph)
                Box(Modifier.fillMaxWidth().weight(1f).clipToBounds()) { content() }
            }
        }
    }
}

@Composable
private fun StatusBar(title: String, playGlyph: Boolean?) {
    val style = LocalLcdStyle.current
    val metrics = LocalLcdMetrics.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(metrics.statusHeight)
            .background(style.status),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val line = metrics.pixel.toPx()
            drawRect(style.divider, Offset(0f, size.height - line), Size(size.width, line))
        }
        if (playGlyph != null) {
            // Its own canvas, so the progress bar's gradient spans the glyph rather than the bar.
            val glyph = metrics.statusHeight * 0.45f
            Canvas(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = metrics.statusHeight * 0.6f - glyph / 2)
                    .size(glyph),
            ) {
                if (playGlyph) {
                    drawPlayGlyph(center, size.minDimension, style.fill)
                } else {
                    drawPauseGlyph(center, size.minDimension * 0.9f, style.fill)
                }
            }
        }
        BasicText(
            title,
            style = lcdText(metrics.smallFont),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = metrics.statusHeight * 1.6f),
        )
        Battery(Modifier.align(Alignment.CenterEnd).padding(end = metrics.padding))
    }
}

@Composable
private fun Battery(modifier: Modifier) {
    val style = LocalLcdStyle.current
    val metrics = LocalLcdMetrics.current
    val context = LocalContext.current
    var level by remember { mutableFloatStateOf(1f) }
    var charging by remember { mutableStateOf(false) }
    DisposableEffect(context) {
        fun update(intent: Intent?) {
            intent ?: return
            val raw = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            if (raw >= 0 && scale > 0) level = raw.toFloat() / scale
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) = update(intent)
        }
        update(ContextCompat.registerReceiver(context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED))
        onDispose { context.unregisterReceiver(receiver) }
    }
    val height = metrics.statusHeight * 0.48f
    Canvas(modifier.size(width = height * 2.1f, height = height)) {
        val nub = size.width * 0.07f
        val body = Size(size.width - nub, size.height)
        if (style.retro) {
            // Square outline with one block per quarter of charge, like the monochrome models.
            val px = metrics.pixel.toPx()
            drawRect(style.text, topLeft = Offset(px / 2, px / 2), size = Size(body.width - px, body.height - px), style = Stroke(px))
            drawRect(style.text, Offset(body.width, size.height * 0.3f), Size(nub, size.height * 0.4f))
            val segments = ceil(level * BATTERY_SEGMENTS).toInt().coerceIn(0, BATTERY_SEGMENTS)
            val inner = px * 2
            val gap = px
            val segmentWidth = (body.width - inner * 2 - gap * (BATTERY_SEGMENTS - 1)) / BATTERY_SEGMENTS
            repeat(segments) { i ->
                drawRect(
                    style.text,
                    Offset(inner + i * (segmentWidth + gap), inner),
                    Size(segmentWidth, size.height - inner * 2),
                )
            }
        } else {
            val stroke = size.height * 0.1f
            drawRoundRect(style.text, size = body, cornerRadius = CornerRadius(stroke * 1.5f), style = Stroke(stroke))
            drawRect(style.text, Offset(body.width, size.height * 0.3f), Size(nub, size.height * 0.4f))
            val inset = stroke * 1.6f
            val fill = if (!charging && level < 0.2f) Color(0xFFE0402F) else Color(0xFF3BB54A)
            drawRect(
                Brush.verticalGradient(listOf(fill.copy(alpha = 0.75f), fill)),
                Offset(inset, inset),
                Size(((body.width - inset * 2) * level).coerceAtLeast(0f), size.height - inset * 2),
            )
        }
    }
}

private const val BATTERY_SEGMENTS = 4

/** A highlighted-row list, scrolled by the wheel. */
@Composable
fun MenuList(items: List<MenuItem>, entry: NavEntry, header: String? = null) {
    val style = LocalLcdStyle.current
    val metrics = LocalLcdMetrics.current
    Column(Modifier.fillMaxSize()) {
        if (header != null) {
            BasicText(
                header,
                style = lcdText(metrics.smallFont, style.secondaryText, FontWeight.Normal),
                modifier = Modifier.padding(horizontal = metrics.padding, vertical = metrics.padding * 0.6f),
            )
        }
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val visible = (maxHeight / metrics.rowHeight).toInt().coerceAtLeast(1)
            SideEffect { entry.visibleRows = visible }
            val selected = entry.selected.coerceIn(0, (items.size - 1).coerceAtLeast(0))
            val top = MenuWindow.top(selected, entry.top, visible, items.size)
            val scrollable = items.size > visible
            Column(Modifier.fillMaxSize().padding(end = if (scrollable) metrics.padding else 0.dp)) {
                for (index in top until minOf(items.size, top + visible)) {
                    MenuRow(items[index], highlighted = index == selected)
                }
            }
            if (scrollable) ScrollBar(top, visible, items.size, Modifier.align(Alignment.CenterEnd))
        }
    }
}

@Composable
private fun MenuRow(item: MenuItem, highlighted: Boolean) {
    val style = LocalLcdStyle.current
    val metrics = LocalLcdMetrics.current
    val textColor = if (highlighted) style.highlightText else style.text
    Row(
        Modifier
            .fillMaxWidth()
            .height(metrics.rowHeight)
            .then(if (highlighted) Modifier.background(style.highlight) else Modifier)
            .padding(horizontal = metrics.padding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            item.label,
            style = lcdText(color = textColor),
            maxLines = 1,
            overflow = if (highlighted) TextOverflow.Clip else TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .then(if (highlighted) Modifier.basicMarquee(initialDelayMillis = 1_200) else Modifier),
        )
        item.value?.let {
            Spacer(Modifier.width(metrics.padding))
            BasicText(it, style = lcdText(color = if (highlighted) textColor else style.secondaryText, weight = FontWeight.Normal), maxLines = 1)
        }
        if (item.marked || item.hasChildren) {
            Spacer(Modifier.width(metrics.padding * 0.8f))
            if (item.hasChildren && !item.marked && style.retro) {
                // The pixel font's own ">" matches the original menus.
                BasicText(">", style = lcdText(color = textColor))
            } else {
                Canvas(Modifier.size(metrics.rowHeight * 0.4f)) {
                    if (item.marked) {
                        drawSpeakerGlyph(center, size.minDimension, textColor, waves = 1)
                    } else {
                        drawChevron(center, size.minDimension, textColor)
                    }
                }
            }
        }
    }
}

@Composable
private fun ScrollBar(top: Int, visible: Int, count: Int, modifier: Modifier) {
    val style = LocalLcdStyle.current
    val metrics = LocalLcdMetrics.current
    Canvas(modifier.fillMaxHeight().width(metrics.padding * 0.8f)) {
        val px = metrics.pixel.toPx()
        val thumbHeight = size.height * visible / count
        val thumbTop = size.height * top / count
        if (style.retro) {
            drawRect(style.text, topLeft = Offset(px / 2, px / 2), size = Size(size.width - px, size.height - px), style = Stroke(px))
            drawRect(style.fill, Offset(px * 2, thumbTop + px * 2), Size(size.width - px * 4, (thumbHeight - px * 4).coerceAtLeast(px)))
        } else {
            drawRect(style.track)
            drawRect(style.divider, Offset.Zero, Size(px, size.height))
            drawRect(style.fill, Offset(px, thumbTop), Size(size.width - px, thumbHeight))
        }
    }
}

/** Read-only page (About, Help, Diagnostics) scrolled by the wheel. */
@Composable
fun TextPage(text: String, entry: NavEntry, monospace: Boolean = false) {
    val metrics = LocalLcdMetrics.current
    val style = lcdText(metrics.smallFont, LocalLcdStyle.current.text, FontWeight.Normal)
        .let { if (monospace) it.copy(fontFamily = FontFamily.Monospace, fontSize = metrics.monoFont) else it }
    BasicText(
        text,
        style = style,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(entry.scroll)
            .padding(metrics.padding),
    )
}
