package io.github.krnelpan1c.droidpod.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.krnelpan1c.droidpod.AppContainer
import io.github.krnelpan1c.droidpod.BuildConfig
import io.github.krnelpan1c.droidpod.media.InstalledApp
import io.github.krnelpan1c.droidpod.media.NotificationAccess
import io.github.krnelpan1c.droidpod.media.NowPlaying
import io.github.krnelpan1c.droidpod.media.PlayerState
import io.github.krnelpan1c.droidpod.media.PositionMath
import io.github.krnelpan1c.droidpod.media.SupportedPlayers
import io.github.krnelpan1c.droidpod.settings.HapticStrength
import io.github.krnelpan1c.droidpod.settings.PodThemeId
import io.github.krnelpan1c.droidpod.settings.Settings
import io.github.krnelpan1c.droidpod.settings.SystemStyle
import io.github.krnelpan1c.droidpod.settings.WheelSensitivity
import io.github.krnelpan1c.droidpod.ui.nav.MenuItem
import io.github.krnelpan1c.droidpod.ui.nav.MenuWindow
import io.github.krnelpan1c.droidpod.ui.nav.NavEntry
import io.github.krnelpan1c.droidpod.ui.nav.Screen
import io.github.krnelpan1c.droidpod.wheel.WheelButton
import io.github.krnelpan1c.droidpod.wheel.WheelEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class NowPlayingMode { Volume, Scrub }

/**
 * The iPod's brain: owns the back stack and turns wheel events into navigation
 * or player commands depending on the screen in front.
 */
@Stable
class PodController(
    private val context: Context,
    private val container: AppContainer,
    private val scope: CoroutineScope,
) {
    private val player = container.player
    private val sessions = container.sessions
    private val settingsRepo = container.settings

    val stack = mutableStateListOf(NavEntry(Screen.Main))
    val top: NavEntry get() = stack.last()

    /** Direction of the last navigation, for the slide animation. */
    var forward by mutableStateOf(true)
        private set

    var mode by mutableStateOf(NowPlayingMode.Volume)
        private set
    var volumeVisible by mutableStateOf(false)
        private set
    /** Position shown while scrubbing; null means show the live position. */
    var scrubPosition by mutableStateOf<Long?>(null)
        private set

    /** Pixels one wheel tick scrolls a text page; set by the UI from the row height. */
    var lineStepPx = 40f

    private var scrubPending = false
    private var volumeHideJob: Job? = null
    private var scrubCommitJob: Job? = null
    private var scrubExitJob: Job? = null
    private var holdSeekTarget: Long? = null

    private val state: PlayerState get() = sessions.state.value
    private val settings: Settings get() = settingsRepo.settings.value ?: Settings()

    fun onAccessChanged(hasAccess: Boolean) {
        val root = if (hasAccess) Screen.Main else Screen.Setup
        if (stack.first().screen != root) {
            forward = hasAccess
            stack.clear()
            stack.add(NavEntry(root))
            resetNowPlaying()
        }
    }

    fun push(screen: Screen, selected: Int = 0) {
        forward = true
        stack.add(NavEntry(screen, selected))
        resetNowPlaying()
    }

    fun pop(): Boolean {
        if (stack.size <= 1) return false
        forward = false
        stack.removeAt(stack.lastIndex)
        resetNowPlaying()
        return true
    }

    private fun popToRoot() {
        if (stack.size <= 1) return
        forward = false
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
        resetNowPlaying()
    }

    /** Goes back to Now Playing if it's on the stack, otherwise opens it. */
    private fun showNowPlaying() {
        val index = stack.indexOfLast { it.screen == Screen.NowPlaying }
        if (index < 0) {
            push(Screen.NowPlaying)
            return
        }
        forward = false
        while (stack.size > index + 1) stack.removeAt(stack.lastIndex)
        resetNowPlaying()
    }

    fun onWheel(event: WheelEvent) {
        when (event) {
            is WheelEvent.Press -> when (event.button) {
                WheelButton.Menu -> pop()
                WheelButton.PlayPause -> player.playPause()
                WheelButton.Next -> player.next()
                WheelButton.Previous -> player.previous()
                WheelButton.Select -> onSelect()
            }
            is WheelEvent.Hold -> onHold(event)
            is WheelEvent.HoldEnd -> holdSeekTarget = null
            is WheelEvent.Scroll -> onScroll(event.ticks)
            WheelEvent.ScrollEnd -> if (top.screen == Screen.NowPlaying && scrubPending) commitScrub()
        }
    }

    private fun onSelect() {
        val entry = top
        when {
            entry.screen == Screen.NowPlaying -> toggleMode()
            entry.screen == Screen.Diagnostics -> copyDiagnostics()
            entry.screen.isText -> Unit
            else -> menuItems(entry.screen).getOrNull(entry.selected)?.onSelect?.invoke()
        }
    }

    private fun onHold(event: WheelEvent.Hold) {
        when (event.button) {
            WheelButton.Menu -> if (event.repeat == 0) popToRoot()
            WheelButton.PlayPause -> if (event.repeat == 0) player.pause()
            WheelButton.Select -> if (event.repeat == 0 && state.current != null && top.screen != Screen.Actions) {
                push(Screen.Actions)
            }
            WheelButton.Next, WheelButton.Previous -> {
                // Held skip buttons fast-forward / rewind, speeding up the longer they're held.
                val np = state.current ?: return
                if (!np.canSeek) return
                val step = when {
                    event.repeat < 8 -> 5_000L
                    event.repeat < 20 -> 10_000L
                    else -> 20_000L
                }
                val from = holdSeekTarget ?: PositionMath.current(np, SystemClock.elapsedRealtime())
                val direction = if (event.button == WheelButton.Next) 1 else -1
                val target = (from + step * direction).coerceIn(0, np.durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE)
                holdSeekTarget = target
                player.seekTo(target)
            }
        }
    }

    private fun onScroll(ticks: Int) {
        val entry = top
        when {
            entry.screen == Screen.NowPlaying -> scrollNowPlaying(ticks)
            entry.screen.isText -> scope.launch { entry.scroll.scrollBy(ticks * lineStepPx) }
            else -> {
                val count = menuItems(entry.screen).size
                if (count == 0) return
                entry.selected = (entry.selected + ticks).coerceIn(0, count - 1)
                entry.top = MenuWindow.top(entry.selected, entry.top, entry.visibleRows, count)
            }
        }
    }

    // --- Now Playing -------------------------------------------------------

    private fun scrollNowPlaying(ticks: Int) {
        val np = state.current ?: return
        when (mode) {
            NowPlayingMode.Volume -> {
                container.volume.adjust(ticks)
                volumeVisible = true
                volumeHideJob?.cancel()
                volumeHideJob = scope.launch {
                    delay(VOLUME_VISIBLE_MS)
                    volumeVisible = false
                }
            }
            NowPlayingMode.Scrub -> {
                val step = maxOf(1_000L, np.durationMs / 200)
                val from = scrubPosition ?: PositionMath.current(np, SystemClock.elapsedRealtime())
                scrubPosition = (from + ticks * step).coerceIn(0, np.durationMs.coerceAtLeast(0))
                scrubPending = true
                scrubExitJob?.cancel()
                scrubCommitJob?.cancel()
                scrubCommitJob = scope.launch {
                    delay(SCRUB_COMMIT_MS)
                    commitScrub()
                }
            }
        }
    }

    private fun toggleMode() {
        val np = state.current ?: return
        if (mode == NowPlayingMode.Scrub) {
            resetNowPlaying()
            return
        }
        if (!np.canSeek || np.durationMs <= 0) return
        volumeHideJob?.cancel()
        volumeVisible = false
        mode = NowPlayingMode.Scrub
        scheduleScrubExit()
    }

    private fun commitScrub() {
        scrubCommitJob?.cancel()
        if (scrubPending) {
            scrubPending = false
            scrubPosition?.let(player::seekTo)
        }
        scheduleScrubExit()
    }

    /** Leaves scrub mode after a few idle seconds, like the original. */
    private fun scheduleScrubExit() {
        scrubExitJob?.cancel()
        scrubExitJob = scope.launch {
            // Give the player a moment to report the new position before going live again.
            delay(SCRUB_SETTLE_MS)
            if (!scrubPending) scrubPosition = null
            delay(SCRUB_EXIT_MS - SCRUB_SETTLE_MS)
            if (!scrubPending) mode = NowPlayingMode.Volume
        }
    }

    private fun resetNowPlaying() {
        if (scrubPending) commitScrub()
        scrubCommitJob?.cancel()
        scrubExitJob?.cancel()
        volumeHideJob?.cancel()
        mode = NowPlayingMode.Volume
        volumeVisible = false
        scrubPosition = null
        holdSeekTarget = null
    }

    // --- Menus -------------------------------------------------------------

    fun title(screen: Screen): String = when (screen) {
        Screen.Setup -> "Setup"
        Screen.Help -> "Help"
        Screen.Main -> "DroidPod"
        Screen.NowPlaying -> "Now Playing"
        Screen.UpNext -> "Up Next"
        Screen.Players -> "Players"
        is Screen.PlayerOptions -> container.apps.label(screen.packageName)
        Screen.AddPlayer -> "Add Player"
        Screen.Actions -> state.current?.appLabel ?: "Actions"
        Screen.Settings -> "Settings"
        Screen.CaseColor -> "Case Color"
        Screen.About -> "About"
        Screen.Diagnostics -> "Diagnostics"
    }

    fun menuItems(
        screen: Screen,
        state: PlayerState = this.state,
        settings: Settings = this.settings,
        installed: List<InstalledApp> = container.installedApps.installed.value,
    ): List<MenuItem> {
        val np = state.current
        return when (screen) {
            Screen.Setup -> listOf(
                MenuItem("Grant Access", hasChildren = true) { NotificationAccess.openListenerSettings(context) },
                MenuItem("Restricted Settings", hasChildren = true) { NotificationAccess.openAppInfo(context) },
                MenuItem("Help", hasChildren = true) { push(Screen.Help) },
            )
            Screen.Main -> buildList {
                add(MenuItem("Players", hasChildren = true) { push(Screen.Players) })
                if (np != null && np.queue.isNotEmpty()) {
                    add(MenuItem("Up Next", hasChildren = true) { push(Screen.UpNext, np.queueIndex.coerceAtLeast(0)) })
                }
                np?.shuffleItem()?.let(::add)
                np?.repeatItem()?.let(::add)
                if (np == null || !np.isPlaying) add(MenuItem("Resume Playback") { player.playPause() })
                add(MenuItem("Settings", hasChildren = true) { push(Screen.Settings) })
                if (np != null) add(MenuItem("Now Playing", hasChildren = true) { showNowPlaying() })
            }
            Screen.Players -> buildList {
                // Apps with a session first: selecting one controls it.
                state.players.forEach { p ->
                    add(MenuItem(p.appLabel, value = if (p.isPlaying) "Playing" else "Paused", marked = p.key == np?.key) {
                        player.select(p.key)
                        showNowPlaying()
                    })
                }
                // Then the supported apps that are installed, followed by the user's own additions.
                val active = state.players.map { it.packageName }.toSet()
                (SupportedPlayers.PACKAGES + settings.customPlayers)
                    .distinct()
                    .filter { it !in active && container.apps.isLaunchable(it) }
                    .forEach { pkg ->
                        add(MenuItem(container.apps.label(pkg), hasChildren = true) { push(Screen.PlayerOptions(pkg)) })
                    }
                add(MenuItem("Add Player", hasChildren = true) { push(Screen.AddPlayer) })
            }
            Screen.AddPlayer -> {
                val listed = SupportedPlayers.PACKAGES + settings.customPlayers
                installed.filter { it.packageName !in listed }
                    .map { app ->
                        MenuItem(app.label) {
                            settingsRepo.addCustomPlayer(app.packageName)
                            pop()
                        }
                    }
                    .ifEmpty { listOf(MenuItem("No Other Apps")) }
            }
            is Screen.PlayerOptions -> listOf(
                MenuItem("Resume Playback") {
                    player.resume(screen.packageName)
                    showNowPlaying()
                },
                MenuItem("Open ${container.apps.label(screen.packageName)}") { player.openApp(screen.packageName) },
            ) + if (screen.packageName in settings.customPlayers) {
                listOf(
                    MenuItem("Remove from Players") {
                        settingsRepo.removeCustomPlayer(screen.packageName)
                        pop()
                    },
                )
            } else {
                emptyList()
            }
            Screen.UpNext -> np?.queue.orEmpty().map { q ->
                MenuItem(q.title.ifBlank { "Untitled" }, marked = q.id == np?.activeQueueId) {
                    player.skipToQueueItem(q.id)
                    showNowPlaying()
                }
            }
            Screen.Actions -> buildList {
                np ?: return@buildList
                // The app's own shuffle/repeat buttons are what our Shuffle/Repeat items press,
                // so they aren't listed twice.
                np.customActions
                    .filterNot { it.isShuffle || it.isRepeat }
                    .forEach { a -> add(MenuItem(a.name) { player.customAction(a.action) }) }
                np.shuffleItem()?.let(::add)
                np.repeatItem()?.let(::add)
                add(MenuItem("Open ${np.appLabel}") { player.openApp(np.packageName) })
                add(MenuItem("Players", hasChildren = true) { push(Screen.Players) })
            }
            Screen.Settings -> listOf(
                MenuItem("System Style", value = settings.style.label) {
                    settingsRepo.update { it.copy(style = it.style.next()) }
                },
                MenuItem("Case Color", value = settings.theme.label, hasChildren = true) {
                    push(Screen.CaseColor, settings.theme.ordinal)
                },
                toggle("Click Sound", settings.clickSound) { copy(clickSound = !clickSound) },
                MenuItem("Haptics", value = settings.haptics.label) {
                    settingsRepo.update { it.copy(haptics = it.haptics.next()) }
                },
                MenuItem("Wheel Sensitivity", value = settings.sensitivity.label) {
                    settingsRepo.update { it.copy(sensitivity = it.sensitivity.next()) }
                },
                toggle("Full Screen", settings.fullScreen) { copy(fullScreen = !fullScreen) },
                toggle("Keep Screen On", settings.keepScreenOn) { copy(keepScreenOn = !keepScreenOn) },
                toggle("Show on Lock Screen", settings.showOnLockScreen) { copy(showOnLockScreen = !showOnLockScreen) },
                MenuItem("Notification Access", hasChildren = true) { NotificationAccess.openListenerSettings(context) },
                MenuItem("Diagnostics", hasChildren = true) { push(Screen.Diagnostics) },
                MenuItem("About", hasChildren = true) { push(Screen.About) },
            )
            Screen.CaseColor -> PodThemeId.entries.map { theme ->
                MenuItem(theme.label, marked = theme == settings.theme) {
                    settingsRepo.update { it.copy(theme = theme) }
                }
            }
            Screen.Help, Screen.About, Screen.Diagnostics, Screen.NowPlaying -> emptyList()
        }
    }

    fun pageText(screen: Screen): String = when (screen) {
        Screen.Help -> HELP_TEXT
        Screen.About -> "DroidPod ${BuildConfig.VERSION_NAME}\n\n" + ABOUT_TEXT
        Screen.Diagnostics -> "Press the center button to copy this report.\n\n" + sessions.diagnosticReport()
        else -> ""
    }

    // The value is left blank when the app has a button but doesn't report the current mode.
    private fun NowPlaying.shuffleItem(): MenuItem? = if (!canShuffle) null else {
        MenuItem("Shuffle", value = shuffle?.let { if (it) "On" else "Off" }) { player.toggleShuffle() }
    }

    private fun NowPlaying.repeatItem(): MenuItem? = if (!canRepeat) null else {
        MenuItem("Repeat", value = repeat?.label) { player.cycleRepeat() }
    }

    private fun toggle(label: String, on: Boolean, flip: Settings.() -> Settings) =
        MenuItem(label, value = if (on) "On" else "Off") { settingsRepo.update { it.flip() } }

    private fun copyDiagnostics() {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("DroidPod diagnostics", sessions.diagnosticReport()))
        // Android 13+ shows its own "copied" confirmation.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
        }
    }

    private companion object {
        const val VOLUME_VISIBLE_MS = 2_000L
        const val SCRUB_COMMIT_MS = 1_000L
        const val SCRUB_SETTLE_MS = 800L
        const val SCRUB_EXIT_MS = 5_000L

        val HELP_TEXT = """
            DroidPod reads what's playing through Android's media controls, the same ones on your lock screen. To see them it needs Notification Access.

            1. Choose Grant Access and turn on DroidPod.

            2. If the switch is greyed out (common when installing from GitHub), go back, choose Restricted Settings, tap ⋮ in the top corner and pick "Allow restricted settings". Then try Grant Access again.

            DroidPod never reads or stores your notifications, and it has no internet access.
        """.trimIndent()

        val ABOUT_TEXT = """
            A click wheel for whatever's playing on your phone. Works with any app that shows media controls on the lock screen.

            Free software under the GNU GPL v3.

            Retro style uses the Chicago Kare typeface by Duane King, MIT License.

            Not affiliated with Apple, Spotify or Google.
        """.trimIndent()

        fun HapticStrength.next() = HapticStrength.entries[(ordinal + 1) % HapticStrength.entries.size]
        fun WheelSensitivity.next() = WheelSensitivity.entries[(ordinal + 1) % WheelSensitivity.entries.size]
        fun SystemStyle.next() = SystemStyle.entries[(ordinal + 1) % SystemStyle.entries.size]
    }
}
