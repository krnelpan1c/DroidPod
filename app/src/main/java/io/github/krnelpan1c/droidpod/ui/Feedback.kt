package io.github.krnelpan1c.droidpod.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import io.github.krnelpan1c.droidpod.R
import io.github.krnelpan1c.droidpod.settings.HapticStrength
import io.github.krnelpan1c.droidpod.settings.Settings
import io.github.krnelpan1c.droidpod.wheel.WheelFeedback

/** The wheel's tick: a short sonification sound that never takes audio focus from the player. */
class ClickSound(context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val pool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val soundId = pool.load(context, R.raw.click, 1)
    private var lastPlayed = 0L

    fun play() {
        if (audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        val now = SystemClock.uptimeMillis()
        if (now - lastPlayed < MIN_INTERVAL_MS) return
        lastPlayed = now
        pool.play(soundId, VOLUME, VOLUME, 0, 0, 1f)
    }

    fun release() = pool.release()

    private companion object {
        const val MIN_INTERVAL_MS = 25L
        const val VOLUME = 0.35f
    }
}

/**
 * The "Strong" haptics level. The view's haptic constants are tuned to be subtle,
 * so this drives the vibrator directly with click effects instead of ticks.
 */
class StrongHaptics(context: Context) {
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java).defaultVibrator
    } else {
        context.getSystemService(Vibrator::class.java)
    }

    private val tickEffect: VibrationEffect =
        if (vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)) {
            VibrationEffect.startComposition().addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f).compose()
        } else {
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
        }
    private val pressEffect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)

    fun tick() = vibrate(tickEffect)

    fun press() = vibrate(pressEffect)

    private fun vibrate(effect: VibrationEffect) {
        // Touch usage keeps the system's "touch feedback" switch in charge, like the Light level.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
        } else {
            vibrator.vibrate(effect)
        }
    }
}

@Composable
fun rememberWheelFeedback(settings: Settings): WheelFeedback {
    val view = LocalView.current
    val context = LocalContext.current
    val click = remember { ClickSound(context) }
    val strong = remember { StrongHaptics(context) }
    DisposableEffect(click) { onDispose { click.release() } }
    return remember(view, click, strong, settings.haptics, settings.clickSound) {
        object : WheelFeedback {
            override fun tick() {
                when (settings.haptics) {
                    HapticStrength.Off -> Unit
                    HapticStrength.Light -> view.performHapticFeedback(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
                        } else {
                            HapticFeedbackConstants.CLOCK_TICK
                        },
                    )
                    HapticStrength.Strong -> strong.tick()
                }
                if (settings.clickSound) click.play()
            }

            override fun press() {
                when (settings.haptics) {
                    HapticStrength.Off -> Unit
                    HapticStrength.Light -> view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    HapticStrength.Strong -> strong.press()
                }
                if (settings.clickSound) click.play()
            }
        }
    }
}
