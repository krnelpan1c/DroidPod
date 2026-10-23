// androidx.media is deprecated in favor of Media3, but it is still the simplest
// way to control other apps' platform sessions. Media3's MediaController is the
// migration path if it ever stops working.
@file:Suppress("DEPRECATION")

package io.github.krnelpan1c.droidpod.media

import android.os.Build
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import io.github.krnelpan1c.droidpod.BuildConfig

/**
 * Phase 0 capability report: what each player actually exposes through its
 * media session. Leaves out titles and artists so it can be pasted publicly.
 */
object Diagnostics {
    private val ACTIONS = listOf(
        PlaybackStateCompat.ACTION_STOP to "STOP",
        PlaybackStateCompat.ACTION_PAUSE to "PAUSE",
        PlaybackStateCompat.ACTION_PLAY to "PLAY",
        PlaybackStateCompat.ACTION_PLAY_PAUSE to "PLAY_PAUSE",
        PlaybackStateCompat.ACTION_REWIND to "REWIND",
        PlaybackStateCompat.ACTION_FAST_FORWARD to "FAST_FORWARD",
        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS to "PREV",
        PlaybackStateCompat.ACTION_SKIP_TO_NEXT to "NEXT",
        PlaybackStateCompat.ACTION_SEEK_TO to "SEEK",
        PlaybackStateCompat.ACTION_SET_RATING to "RATING",
        PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM to "SKIP_TO_QUEUE_ITEM",
        PlaybackStateCompat.ACTION_SET_REPEAT_MODE to "SET_REPEAT",
        PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE to "SET_SHUFFLE",
        PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID to "PLAY_FROM_MEDIA_ID",
        PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH to "PLAY_FROM_SEARCH",
        PlaybackStateCompat.ACTION_PLAY_FROM_URI to "PLAY_FROM_URI",
        PlaybackStateCompat.ACTION_PREPARE to "PREPARE",
    )

    private val STATES = mapOf(
        PlaybackStateCompat.STATE_NONE to "NONE",
        PlaybackStateCompat.STATE_STOPPED to "STOPPED",
        PlaybackStateCompat.STATE_PAUSED to "PAUSED",
        PlaybackStateCompat.STATE_PLAYING to "PLAYING",
        PlaybackStateCompat.STATE_FAST_FORWARDING to "FAST_FORWARDING",
        PlaybackStateCompat.STATE_REWINDING to "REWINDING",
        PlaybackStateCompat.STATE_BUFFERING to "BUFFERING",
        PlaybackStateCompat.STATE_ERROR to "ERROR",
        PlaybackStateCompat.STATE_CONNECTING to "CONNECTING",
        PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS to "SKIPPING_TO_PREVIOUS",
        PlaybackStateCompat.STATE_SKIPPING_TO_NEXT to "SKIPPING_TO_NEXT",
        PlaybackStateCompat.STATE_SKIPPING_TO_QUEUE_ITEM to "SKIPPING_TO_QUEUE_ITEM",
    )

    fun report(sessions: List<Pair<MediaControllerCompat, Boolean>>, apps: AppInfo): String = buildString {
        appendLine("DroidPod ${BuildConfig.VERSION_NAME} diagnostics")
        appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Sessions: ${sessions.size}")
        sessions.forEachIndexed { index, (c, isCurrent) ->
            val s = c.playbackState
            val m = c.metadata
            appendLine()
            appendLine("[${index + 1}] ${apps.label(c.packageName)} (${c.packageName})${if (isCurrent) " *current*" else ""}")
            appendLine("  state=${STATES[s?.state] ?: s?.state ?: "null"} position=${s?.position ?: "?"} speed=${s?.playbackSpeed ?: "?"}")
            val actions = s?.actions ?: 0
            appendLine("  actions=0x${actions.toString(16)} [${ACTIONS.filter { actions and it.first != 0L }.joinToString(",") { it.second }}]")
            val custom = s?.customActions.orEmpty()
            appendLine("  custom=${if (custom.isEmpty()) "none" else custom.joinToString("; ") { "${it.name} (${it.action})" }}")
            appendLine("  sessionReady=${c.isSessionReady} shuffle=${c.shuffleMode} repeat=${c.repeatMode}")
            val queue = c.queue
            appendLine("  queue=${queue?.size ?: "none"} activeItem=${s?.activeQueueItemId ?: "?"} title=${c.queueTitle != null}")
            appendLine("  art=${describeArt(c)}")
            appendLine("  title=${m?.description?.title != null} duration=${m?.getLong(MediaMetadataCompat.METADATA_KEY_DURATION) ?: "?"} rating=${c.ratingType}")
            val info = c.playbackInfo
            appendLine("  playback=${if (info?.playbackType == MediaControllerCompat.PlaybackInfo.PLAYBACK_TYPE_REMOTE) "REMOTE" else "LOCAL"} volume=${info?.currentVolume}/${info?.maxVolume}")
            appendLine("  metadataKeys=${m?.keySet()?.sorted()?.joinToString(",") { it.removePrefix("android.media.metadata.") } ?: "none"}")
        }
    }

    private fun describeArt(c: MediaControllerCompat): String {
        val m = c.metadata ?: return "no metadata"
        ArtLoader.BITMAP_KEYS.forEach { key ->
            m.getBitmap(key)?.let { return "bitmap ${key.removePrefix("android.media.metadata.")} ${it.width}x${it.height}" }
        }
        ArtLoader.URI_KEYS.forEach { key ->
            m.getString(key)?.takeIf { it.isNotBlank() }?.let { uri ->
                return "uri ${key.removePrefix("android.media.metadata.")} scheme=${uri.substringBefore(':')}"
            }
        }
        return "none"
    }
}
