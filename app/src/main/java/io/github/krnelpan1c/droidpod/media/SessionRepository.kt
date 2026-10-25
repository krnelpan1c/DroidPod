// androidx.media is deprecated in favor of Media3, but it is still the simplest
// way to control other apps' platform sessions. Media3's MediaController is the
// migration path if it ever stops working.
@file:Suppress("DEPRECATION")

package io.github.krnelpan1c.droidpod.media

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks every active media session on the device and publishes the one we're
 * controlling as [PlayerState]. Main thread only.
 *
 * Sessions are wrapped in MediaControllerCompat: it reads the same platform
 * session but also exposes shuffle/repeat, which the platform controller lacks.
 */
class SessionRepository(
    private val context: Context,
    scope: CoroutineScope,
    private val apps: AppInfo,
) {
    private val manager = context.getSystemService(MediaSessionManager::class.java)
    private val listenerComponent = ComponentName(context, MediaListenerService::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val art = ArtLoader(context, scope) { publish() }

    private val tracked = HashMap<MediaSession.Token, TrackedSession>()
    /**
     * One per app rather than per session: sessions are released whenever the UI
     * stops, and the count has to pick up where it left off when we come back.
     */
    private val counters = HashMap<String, QueueCounter>()
    /** Session tokens in the system's priority order. */
    private var order: List<MediaSession.Token> = emptyList()
    private var nextId = 0
    private var pin: Pin? = null
    private var currentKey: String? = null
    private var started = false
    private var listening = false

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        sync(controllers.orEmpty())
    }

    /** Called while the UI is visible. */
    fun start() {
        started = true
        refresh()
    }

    fun stop() {
        started = false
        stopListening()
        releaseAll()
    }

    /** Re-reads access and sessions; cheap enough to call on every resume. */
    fun refresh() {
        if (!started) return
        if (!NotificationAccess.isGranted(context)) {
            revokeAccess()
            return
        }
        val controllers = try {
            if (!listening) {
                manager.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent, mainHandler)
                listening = true
            }
            manager.getActiveSessions(listenerComponent)
        } catch (_: SecurityException) {
            revokeAccess()
            return
        }
        sync(controllers)
    }

    /** Makes [key] the controlled session until another app starts playing. */
    fun pin(key: String) {
        pin = Pin(key, SystemClock.elapsedRealtime())
        publish()
    }

    fun currentController(): MediaControllerCompat? =
        tracked.values.firstOrNull { it.key == currentKey }?.controller

    fun diagnosticReport(): String =
        Diagnostics.report(order.mapNotNull { tracked[it] }.map { it.controller to (it.key == currentKey) }, apps)

    private fun revokeAccess() {
        stopListening()
        releaseAll()
        _state.value = PlayerState(hasAccess = false)
    }

    private fun stopListening() {
        if (listening) {
            manager.removeOnActiveSessionsChangedListener(sessionsListener)
            listening = false
        }
    }

    private fun releaseAll() {
        tracked.values.forEach { it.release() }
        tracked.clear()
        order = emptyList()
    }

    private fun sync(controllers: List<MediaController>) {
        if (!started) return
        val tokens = controllers.map { it.sessionToken }
        val gone = tracked.keys - tokens.toSet()
        gone.forEach { tracked.remove(it)?.release() }

        val now = SystemClock.elapsedRealtime()
        controllers.forEachIndexed { index, platform ->
            if (platform.sessionToken in tracked) return@forEachIndexed
            val compat = MediaControllerCompat(context, MediaSessionCompat.Token.fromToken(platform.sessionToken))
            val session = TrackedSession("${platform.packageName}#${nextId++}", compat)
            // Sessions already playing when we arrive keep the system's priority order.
            if (session.isPlaying) session.lastPlayingAt = now - index
            compat.registerCallback(session.callback, mainHandler)
            tracked[platform.sessionToken] = session
        }
        order = tokens
        publish()
    }

    private fun publish() {
        if (!started) return
        val sessions = order.mapNotNull { tracked[it] }
        val key = SessionSelector.select(
            sessions.map { SessionCandidate(it.key, it.isPlaying, it.lastPlayingAt) },
            pin,
        )
        if (pin != null && key != pin?.key) pin = null
        currentKey = key
        _state.value = PlayerState(
            hasAccess = true,
            current = sessions.firstOrNull { it.key == key }?.let(::buildNowPlaying),
            players = sessions.map { PlayerInfo(it.key, it.packageName, apps.label(it.packageName), it.isPlaying) },
        )
    }

    private fun buildNowPlaying(session: TrackedSession): NowPlaying {
        val c = session.controller
        val m = c.metadata
        val s = c.playbackState
        fun text(vararg keys: String): String? =
            keys.firstNotNullOfOrNull { key -> m?.getString(key)?.takeIf { it.isNotBlank() } }

        val actions = s?.actions ?: 0
        fun allows(action: Long) = actions == 0L || actions and action != 0L
        val ready = c.isSessionReady
        val shuffle = if (ready && allows(PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE)) {
            when (c.shuffleMode) {
                PlaybackStateCompat.SHUFFLE_MODE_NONE -> false
                PlaybackStateCompat.SHUFFLE_MODE_ALL, PlaybackStateCompat.SHUFFLE_MODE_GROUP -> true
                else -> null
            }
        } else null
        val repeat = if (ready && allows(PlaybackStateCompat.ACTION_SET_REPEAT_MODE)) {
            when (c.repeatMode) {
                PlaybackStateCompat.REPEAT_MODE_NONE -> RepeatSetting.Off
                PlaybackStateCompat.REPEAT_MODE_ONE -> RepeatSetting.One
                PlaybackStateCompat.REPEAT_MODE_ALL, PlaybackStateCompat.REPEAT_MODE_GROUP -> RepeatSetting.All
                else -> null
            }
        } else null

        val queue = c.queue.orEmpty().map { item ->
            QueueEntry(
                item.queueId,
                item.description.title?.toString().orEmpty(),
                item.description.subtitle?.toString().orEmpty(),
            )
        }
        val activeQueueId = s?.activeQueueItemId ?: MediaSessionCompat.QueueItem.UNKNOWN_ID.toLong()
        val title = text(MediaMetadataCompat.METADATA_KEY_TITLE, MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE)
            ?: m?.description?.title?.toString()
            ?: ""
        // While skipping quickly, YouTube Music keeps the old active id but moves the
        // queue and metadata along; the title still says which track is playing.
        val playingIndex = queue.indexOfFirst { it.id == activeQueueId }
            .takeIf { it >= 0 || title.isBlank() }
            ?: queue.indexOfFirst { it.title == title }

        return NowPlaying(
            key = session.key,
            packageName = session.packageName,
            appLabel = apps.label(session.packageName),
            title = title,
            artist = text(
                MediaMetadataCompat.METADATA_KEY_ARTIST,
                MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST,
                MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE,
                MediaMetadataCompat.METADATA_KEY_AUTHOR,
            ),
            album = text(MediaMetadataCompat.METADATA_KEY_ALBUM, MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION),
            durationMs = m?.getLong(MediaMetadataCompat.METADATA_KEY_DURATION)?.coerceAtLeast(0) ?: 0,
            art = art.artFor(m),
            status = s.toStatus(),
            positionMs = s?.position ?: 0,
            positionUpdatedAt = s?.lastPositionUpdateTime ?: 0,
            speed = s?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
            actions = actions,
            customActions = s?.customActions.orEmpty().map { CustomActionInfo(it.action, it.name.toString()) },
            shuffle = shuffle,
            repeat = repeat,
            queue = queue,
            activeQueueId = activeQueueId,
            queuePosition = session.counter.update(queue.map { it.trackKey }, playingIndex),
        )
    }

    private inner class TrackedSession(val key: String, val controller: MediaControllerCompat) {
        val packageName: String = controller.packageName
        var lastPlayingAt = 0L
        val counter = counters.getOrPut(packageName) { QueueCounter() }
        val isPlaying: Boolean get() = controller.playbackState.isActive()
        private var wasPlaying = isPlaying

        val callback = object : MediaControllerCompat.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
                val playing = state.isActive()
                if (playing && !wasPlaying) lastPlayingAt = SystemClock.elapsedRealtime()
                wasPlaying = playing
                publish()
            }

            override fun onMetadataChanged(metadata: MediaMetadataCompat?) = publish()
            override fun onQueueChanged(queue: MutableList<MediaSessionCompat.QueueItem>?) = publish()
            override fun onShuffleModeChanged(shuffleMode: Int) = publish()
            override fun onRepeatModeChanged(repeatMode: Int) = publish()
            override fun onSessionReady() = publish()
            override fun onAudioInfoChanged(info: MediaControllerCompat.PlaybackInfo?) = publish()
            override fun onSessionDestroyed() = refresh()
        }

        fun release() = controller.unregisterCallback(callback)
    }
}

private fun PlaybackStateCompat?.isActive(): Boolean = when (this?.state) {
    PlaybackStateCompat.STATE_PLAYING,
    PlaybackStateCompat.STATE_BUFFERING,
    PlaybackStateCompat.STATE_CONNECTING,
    PlaybackStateCompat.STATE_FAST_FORWARDING,
    PlaybackStateCompat.STATE_REWINDING,
    PlaybackStateCompat.STATE_SKIPPING_TO_NEXT,
    PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS,
    PlaybackStateCompat.STATE_SKIPPING_TO_QUEUE_ITEM -> true
    else -> false
}

private fun PlaybackStateCompat?.toStatus(): PlayStatus {
    val state = this?.state ?: return PlayStatus.None
    return when (state) {
        PlaybackStateCompat.STATE_NONE -> PlayStatus.None
        PlaybackStateCompat.STATE_STOPPED -> PlayStatus.Stopped
        PlaybackStateCompat.STATE_PAUSED -> PlayStatus.Paused
        PlaybackStateCompat.STATE_PLAYING,
        PlaybackStateCompat.STATE_FAST_FORWARDING,
        PlaybackStateCompat.STATE_REWINDING -> PlayStatus.Playing
        PlaybackStateCompat.STATE_BUFFERING,
        PlaybackStateCompat.STATE_CONNECTING,
        PlaybackStateCompat.STATE_SKIPPING_TO_NEXT,
        PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS,
        PlaybackStateCompat.STATE_SKIPPING_TO_QUEUE_ITEM -> PlayStatus.Buffering
        PlaybackStateCompat.STATE_ERROR -> PlayStatus.Error
        else -> PlayStatus.None
    }
}
