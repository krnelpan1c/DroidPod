package io.github.krnelpan1c.droidpod.media

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Streaming apps listed under Players whenever they're installed. Discovering
 * players from manifest metadata pulled in unrelated apps (assistants,
 * browsers, fitness apps), so the list is curated; users add others by hand.
 */
object SupportedPlayers {
    val PACKAGES = listOf(
        "com.spotify.music", // Spotify
        "com.apple.android.music", // Apple Music
        "com.google.android.apps.youtube.music", // YouTube Music
        "com.amazon.mp3", // Amazon Music
        "com.aspiro.tidal", // Tidal
        "com.qobuz.music", // Qobuz
    )
}

data class InstalledApp(val packageName: String, val label: String)

/** Every app with a launcher icon, A–Z, for the Add Player picker. */
class InstalledApps(
    private val context: Context,
    private val scope: CoroutineScope,
    private val apps: AppInfo,
) {
    private val _apps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val installed: StateFlow<List<InstalledApp>> = _apps.asStateFlow()

    fun refresh() {
        scope.launch {
            _apps.value = withContext(Dispatchers.IO) {
                apps.clearCache()
                query()
            }
        }
    }

    @Suppress("DEPRECATION") // The flag-object overload is API 33+; plain ints still work.
    private fun query(): List<InstalledApp> {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return context.packageManager.queryIntentActivities(launcher, 0)
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it != context.packageName }
            .map { InstalledApp(it, apps.label(it)) }
            .sortedBy { it.label.lowercase() }
    }
}
