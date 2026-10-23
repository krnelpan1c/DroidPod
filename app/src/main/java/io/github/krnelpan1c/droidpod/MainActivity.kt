package io.github.krnelpan1c.droidpod

import android.media.AudioManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.krnelpan1c.droidpod.settings.Settings
import io.github.krnelpan1c.droidpod.ui.PodApp
import io.github.krnelpan1c.droidpod.ui.PodController
import io.github.krnelpan1c.droidpod.ui.theme.podColors

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Hardware volume keys change music volume even when nothing is playing.
        volumeControlStream = AudioManager.STREAM_MUSIC
        val container = container
        setContent {
            val scope = rememberCoroutineScope()
            val controller = remember { PodController(this, container, scope) }
            container.settings.settings.collectAsStateWithLifecycle().value?.let { WindowSettings(it) }
            PodApp(controller, container)
        }
    }

    override fun onStart() {
        super.onStart()
        container.sessions.start()
        container.volume.start()
        container.installedApps.refresh()
    }

    override fun onResume() {
        super.onResume()
        // Picks up access granted in Settings and any sessions missed while we were away.
        container.sessions.refresh()
    }

    override fun onStop() {
        container.sessions.stop()
        container.volume.stop()
        super.onStop()
    }

    @Composable
    private fun WindowSettings(settings: Settings) {
        LaunchedEffect(settings.keepScreenOn) {
            if (settings.keepScreenOn) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        LaunchedEffect(settings.showOnLockScreen) { setShowWhenLocked(settings.showOnLockScreen) }
        LaunchedEffect(settings.fullScreen, settings.theme) {
            val insets = WindowCompat.getInsetsController(window, window.decorView)
            val light = podColors(settings.theme).isLight
            insets.isAppearanceLightStatusBars = light
            insets.isAppearanceLightNavigationBars = light
            if (settings.fullScreen) {
                insets.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                insets.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                insets.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}
