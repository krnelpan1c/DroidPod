// androidx.media is deprecated in favor of Media3, but it is still the simplest
// way to control other apps' platform sessions. Media3's MediaController is the
// migration path if it ever stops working.
@file:Suppress("DEPRECATION")

package io.github.krnelpan1c.droidpod.media

import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.support.v4.media.session.MediaControllerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

data class VolumeLevel(val current: Int, val max: Int) {
    val fraction: Float get() = if (max > 0) current.toFloat() / max else 0f
}

/** Music volume on this phone, or the cast device's volume if the player is casting. */
class VolumeController(private val context: Context, private val sessions: SessionRepository) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val _level = MutableStateFlow(readLocal())
    val level: StateFlow<VolumeLevel> = _level.asStateFlow()

    // Volume changes from hardware keys or other apps land in Settings.System.
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            if (remoteController() == null) _level.value = readLocal()
        }
    }

    fun start() {
        context.contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, observer)
        _level.value = read()
    }

    fun stop() {
        context.contentResolver.unregisterContentObserver(observer)
    }

    fun adjust(steps: Int) {
        if (steps == 0) return
        val direction = if (steps > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        val remote = remoteController()
        if (remote != null) {
            repeat(abs(steps)) { remote.adjustVolume(direction, 0) }
            // The cast device reports back asynchronously; show the expected level now.
            val info = remote.playbackInfo
            _level.value = VolumeLevel((info.currentVolume + steps).coerceIn(0, info.maxVolume), info.maxVolume)
        } else {
            // Flag 0: no system volume panel; DroidPod draws its own bar.
            repeat(abs(steps)) { audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0) }
            _level.value = readLocal()
        }
    }

    private fun remoteController(): MediaControllerCompat? = sessions.currentController()
        ?.takeIf { it.playbackInfo?.playbackType == MediaControllerCompat.PlaybackInfo.PLAYBACK_TYPE_REMOTE }

    private fun read(): VolumeLevel =
        remoteController()?.playbackInfo?.let { VolumeLevel(it.currentVolume, it.maxVolume) } ?: readLocal()

    private fun readLocal() = VolumeLevel(
        audio.getStreamVolume(AudioManager.STREAM_MUSIC),
        audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
    )
}
