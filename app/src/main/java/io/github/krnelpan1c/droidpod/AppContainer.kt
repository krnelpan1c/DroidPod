package io.github.krnelpan1c.droidpod

import android.app.Application
import io.github.krnelpan1c.droidpod.media.AppInfo
import io.github.krnelpan1c.droidpod.media.InstalledApps
import io.github.krnelpan1c.droidpod.media.PlayerController
import io.github.krnelpan1c.droidpod.media.SessionRepository
import io.github.krnelpan1c.droidpod.media.VolumeController
import io.github.krnelpan1c.droidpod.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** App-wide singletons, wired by hand. */
class AppContainer(app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val settings = SettingsRepository(app, scope)
    val apps = AppInfo(app)
    val installedApps = InstalledApps(app, scope, apps)
    val sessions = SessionRepository(app, scope, apps)
    val player = PlayerController(app, scope, sessions, apps)
    val volume = VolumeController(app, sessions)
}
