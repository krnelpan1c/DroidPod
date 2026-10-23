package io.github.krnelpan1c.droidpod.ui.nav

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

sealed interface Screen {
    data object Setup : Screen
    data object Help : Screen
    data object Main : Screen
    data object NowPlaying : Screen
    data object UpNext : Screen
    data object Players : Screen
    /** Resume, open or remove a player that has no active session. */
    data class PlayerOptions(val packageName: String) : Screen
    /** Pick any installed app to list under Players. */
    data object AddPlayer : Screen
    data object Actions : Screen
    data object Settings : Screen
    data object About : Screen
    data object Diagnostics : Screen

    val isText: Boolean get() = this == Help || this == About || this == Diagnostics
}

/** One screen on the back stack, with its own highlight and scroll position. */
class NavEntry(val screen: Screen, selected: Int = 0) {
    var selected by mutableIntStateOf(selected)
    /** First visible row of a menu. */
    var top by mutableIntStateOf(0)
    /** Rows that fit on screen; reported by the UI once laid out. */
    var visibleRows = 6
    val scroll = ScrollState(0)
}

data class MenuItem(
    val label: String,
    val value: String? = null,
    val hasChildren: Boolean = false,
    /** Shows a ▶ marker, e.g. the playing queue item or controlled player. */
    val marked: Boolean = false,
    val onSelect: (() -> Unit)? = null,
)

object MenuWindow {
    /** Keeps the highlighted row on screen, moving the window as little as possible. */
    fun top(selected: Int, currentTop: Int, visible: Int, count: Int): Int {
        if (visible <= 0 || count <= visible) return 0
        var top = currentTop
        if (selected < top) top = selected
        if (selected >= top + visible) top = selected - visible + 1
        return top.coerceIn(0, count - visible)
    }
}
