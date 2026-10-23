@file:Suppress("DEPRECATION")

package io.github.krnelpan1c.droidpod.media

import android.support.v4.media.session.PlaybackStateCompat
import androidx.compose.ui.graphics.ImageBitmap

enum class PlayStatus { None, Stopped, Paused, Playing, Buffering, Error }

enum class RepeatSetting(val label: String) { Off("Off"), One("One"), All("All") }

data class CustomActionInfo(val action: String, val name: String) {
    // Matched by id, e.g. YouTube Music's "shuffle_action" / "loop_mode_action" and
    // Spotify's "TURN_REPEAT_ALL_ON". Names are localized, ids aren't.
    val isShuffle: Boolean get() = "shuffle" in action.lowercase()
    val isRepeat: Boolean get() = action.lowercase().let { "repeat" in it || "loop" in it }
}

data class QueueEntry(val id: Long, val title: String, val subtitle: String = "") {
    /** Identifies the track for [QueueCounter]. Ids aren't used when there's a title: some apps renumber them. */
    val trackKey: String get() = if (title.isNotBlank()) "$title\n$subtitle" else "#$id"
}

/** Everything the UI needs about the session being controlled. */
data class NowPlaying(
    val key: String,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val durationMs: Long,
    val art: ImageBitmap?,
    val status: PlayStatus,
    val positionMs: Long,
    /** SystemClock.elapsedRealtime() of positionMs. */
    val positionUpdatedAt: Long,
    val speed: Float,
    val actions: Long,
    val customActions: List<CustomActionInfo>,
    /** Null when the app doesn't expose shuffle/repeat. */
    val shuffle: Boolean?,
    val repeat: RepeatSetting?,
    val queue: List<QueueEntry>,
    val activeQueueId: Long,
    /** Position in the whole album or playlist, which may be longer than [queue]. */
    val queuePosition: QueuePosition?,
) {
    val isPlaying: Boolean get() = status == PlayStatus.Playing || status == PlayStatus.Buffering

    /** Some players report no actions at all; treat that as "anything goes". */
    fun can(action: Long): Boolean = actions == 0L || actions and action != 0L

    val canSeek: Boolean get() = can(PlaybackStateCompat.ACTION_SEEK_TO)

    /** The app's own shuffle/repeat buttons, which it always honors (unlike the generic commands). */
    val shuffleAction: CustomActionInfo? get() = customActions.firstOrNull { it.isShuffle }
    val repeatAction: CustomActionInfo? get() = customActions.firstOrNull { it.isRepeat }

    val canShuffle: Boolean get() = shuffle != null || shuffleAction != null
    val canRepeat: Boolean get() = repeat != null || repeatAction != null

    val queueIndex: Int get() = queue.indexOfFirst { it.id == activeQueueId }
}

data class PlayerInfo(
    val key: String,
    val packageName: String,
    val appLabel: String,
    val isPlaying: Boolean,
)

data class PlayerState(
    val hasAccess: Boolean = false,
    val current: NowPlaying? = null,
    /** Every app with an active session, in system priority order. */
    val players: List<PlayerInfo> = emptyList(),
)
