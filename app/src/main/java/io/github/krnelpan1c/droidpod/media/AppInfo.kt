package io.github.krnelpan1c.droidpod.media

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/** Looks up player names and launch intents (visible thanks to the manifest's queries). */
class AppInfo(context: Context) {
    private val pm = context.packageManager
    private val labels = HashMap<String, String>()
    private val launchable = HashMap<String, Boolean>()

    @Synchronized
    fun label(packageName: String): String = labels.getOrPut(packageName) {
        try {
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    fun launchIntent(packageName: String): Intent? = pm.getLaunchIntentForPackage(packageName)

    /** Cached, because menus ask on every wheel tick. */
    @Synchronized
    fun isLaunchable(packageName: String): Boolean =
        launchable.getOrPut(packageName) { launchIntent(packageName) != null }

    /** Apps may have been installed, removed or renamed since we last looked. */
    @Synchronized
    fun clearCache() {
        labels.clear()
        launchable.clear()
    }
}
