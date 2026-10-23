// androidx.media is deprecated in favor of Media3, but it is still the simplest
// way to control other apps' platform sessions. Media3's MediaController is the
// migration path if it ever stops working.
@file:Suppress("DEPRECATION")

package io.github.krnelpan1c.droidpod.media

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.support.v4.media.session.PlaybackStateCompat
import android.view.KeyEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Sends commands to whichever session [SessionRepository] picked. */
class PlayerController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val sessions: SessionRepository,
    private val apps: AppInfo,
) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val current: NowPlaying? get() = sessions.state.value.current
    private val controls get() = sessions.currentController()?.transportControls

    fun playPause() {
        val np = current
        when {
            np == null -> resumeLastPlayer()
            np.isPlaying -> controls?.pause()
            else -> controls?.play()
        }
    }

    fun pause() {
        if (current?.isPlaying == true) controls?.pause()
    }

    fun next() {
        if (current?.can(PlaybackStateCompat.ACTION_SKIP_TO_NEXT) == true) controls?.skipToNext()
    }

    fun previous() {
        if (current?.can(PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS) == true) controls?.skipToPrevious()
    }

    fun seekTo(positionMs: Long) {
        if (current?.can(PlaybackStateCompat.ACTION_SEEK_TO) == true) controls?.seekTo(positionMs.coerceAtLeast(0))
    }

    /**
     * Presses the app's own shuffle button when it has one. The generic shuffle/repeat
     * commands travel as compat custom actions that some apps silently ignore, while
     * their own buttons (the ones in their notification) always work.
     */
    fun toggleShuffle() {
        val np = current ?: return
        np.shuffleAction?.let {
            customAction(it.action)
            return
        }
        val on = np.shuffle ?: return
        controls?.setShuffleMode(if (on) PlaybackStateCompat.SHUFFLE_MODE_NONE else PlaybackStateCompat.SHUFFLE_MODE_ALL)
    }

    /** The app's own repeat button if it has one, otherwise Off -> All -> One -> Off. */
    fun cycleRepeat() {
        val np = current ?: return
        np.repeatAction?.let {
            customAction(it.action)
            return
        }
        val next = when (np.repeat ?: return) {
            RepeatSetting.Off -> PlaybackStateCompat.REPEAT_MODE_ALL
            RepeatSetting.All -> PlaybackStateCompat.REPEAT_MODE_ONE
            RepeatSetting.One -> PlaybackStateCompat.REPEAT_MODE_NONE
        }
        controls?.setRepeatMode(next)
    }

    fun customAction(action: String) {
        controls?.sendCustomAction(action, null)
    }

    fun skipToQueueItem(id: Long) {
        controls?.skipToQueueItem(id)
    }

    fun select(key: String) = sessions.pin(key)

    /** Media key to whatever the system considers the last player (what Bluetooth headsets do). */
    fun resumeLastPlayer() {
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY))
    }

    /** Asks a specific player to resume; opens it if it doesn't start within a couple of seconds. */
    fun resume(packageName: String) {
        for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            context.sendBroadcast(
                Intent(Intent.ACTION_MEDIA_BUTTON)
                    .setPackage(packageName)
                    .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(action, KeyEvent.KEYCODE_MEDIA_PLAY)),
            )
        }
        scope.launch {
            delay(RESUME_TIMEOUT_MS)
            val started = sessions.state.value.players.any { it.packageName == packageName && it.isPlaying }
            if (!started) openApp(packageName)
        }
    }

    fun openApp(packageName: String) {
        apps.launchIntent(packageName)?.let { context.tryStartActivity(it) }
    }

    private companion object {
        const val RESUME_TIMEOUT_MS = 2_000L
    }
}
