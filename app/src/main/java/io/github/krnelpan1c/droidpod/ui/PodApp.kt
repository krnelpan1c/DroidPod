package io.github.krnelpan1c.droidpod.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.krnelpan1c.droidpod.AppContainer
import io.github.krnelpan1c.droidpod.media.InstalledApp
import io.github.krnelpan1c.droidpod.media.PlayerState
import io.github.krnelpan1c.droidpod.media.VolumeLevel
import io.github.krnelpan1c.droidpod.settings.Settings
import io.github.krnelpan1c.droidpod.ui.nav.NavEntry
import io.github.krnelpan1c.droidpod.ui.nav.Screen
import io.github.krnelpan1c.droidpod.ui.theme.LocalLcdStyle
import io.github.krnelpan1c.droidpod.ui.theme.LocalPodColors
import io.github.krnelpan1c.droidpod.ui.theme.lcdStyle
import io.github.krnelpan1c.droidpod.ui.theme.podColors
import io.github.krnelpan1c.droidpod.wheel.ClickWheel

@Composable
fun PodApp(controller: PodController, container: AppContainer) {
    val settings = container.settings.settings.collectAsStateWithLifecycle().value ?: return
    val state by container.sessions.state.collectAsStateWithLifecycle()
    val volume by container.volume.level.collectAsStateWithLifecycle()
    val installed by container.installedApps.installed.collectAsStateWithLifecycle()

    LaunchedEffect(state.hasAccess) { controller.onAccessChanged(state.hasAccess) }
    // System back (including predictive back) acts like MENU; on the root it leaves the app.
    BackHandler(enabled = controller.stack.size > 1) { controller.pop() }

    val colors = podColors(settings.theme)
    val feedback = rememberWheelFeedback(settings)

    CompositionLocalProvider(LocalPodColors provides colors, LocalLcdStyle provides lcdStyle(settings.style)) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(colors.case)
                .then(colors.caseShade?.let { Modifier.background(it) } ?: Modifier)
                .safeDrawingPadding()
                .padding(16.dp),
        ) {
            val width = maxWidth
            val height = maxHeight
            val wide = width > height
            val screen = @Composable { modifier: Modifier ->
                Lcd(
                    title = controller.title(controller.top.screen),
                    playGlyph = state.current?.isPlaying,
                    modifier = modifier,
                ) {
                    ScreenStack(controller, state, settings, installed, volume)
                }
            }
            val wheel = @Composable { diameter: Dp ->
                ClickWheel(
                    onEvent = controller::onWheel,
                    ticksPerRevolution = settings.sensitivity.ticksPerRevolution,
                    feedback = feedback,
                    modifier = Modifier.size(diameter),
                )
            }
            if (wide) {
                // Tablets and unfolded foldables can't be locked to portrait: screen left, wheel right.
                val lcdHeight = minOf(height, width * 0.55f * 0.75f)
                Row(
                    Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    screen(Modifier.size(width = lcdHeight / 0.75f, height = lcdHeight))
                    wheel(minOf(height * 0.85f, width * 0.38f))
                }
            } else {
                val lcdHeight = width * 0.75f
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceEvenly,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    screen(Modifier.size(width = width, height = lcdHeight))
                    wheel(minOf(width * 0.82f, (height - lcdHeight) * 0.9f))
                }
            }
        }
    }
}

@Composable
private fun ScreenStack(
    controller: PodController,
    state: PlayerState,
    settings: Settings,
    installed: List<InstalledApp>,
    volume: VolumeLevel,
) {
    val metrics = LocalLcdMetrics.current
    val density = LocalDensity.current
    SideEffect { controller.lineStepPx = with(density) { metrics.rowHeight.toPx() } * 0.5f }

    AnimatedContent(
        targetState = controller.top,
        transitionSpec = {
            val dir = if (controller.forward) 1 else -1
            slideInHorizontally(tween(SLIDE_MS)) { it * dir } togetherWith
                slideOutHorizontally(tween(SLIDE_MS)) { -it * dir }
        },
        modifier = Modifier.fillMaxSize().clipToBounds(),
        label = "screen",
    ) { entry: NavEntry ->
        when (entry.screen) {
            Screen.NowPlaying -> NowPlayingScreen(
                np = state.current,
                mode = controller.mode,
                volumeVisible = controller.volumeVisible,
                scrubPosition = controller.scrubPosition,
                volume = volume,
            )
            // ScreenStack recomposes on every session update, so the Diagnostics report stays live.
            Screen.Help, Screen.About, Screen.Diagnostics ->
                TextPage(controller.pageText(entry.screen), entry, monospace = entry.screen == Screen.Diagnostics)
            Screen.Setup -> MenuList(
                controller.menuItems(entry.screen, state, settings),
                entry,
                header = "DroidPod needs Notification Access to see what's playing.",
            )
            else -> MenuList(controller.menuItems(entry.screen, state, settings, installed), entry)
        }
    }
}

private const val SLIDE_MS = 220
