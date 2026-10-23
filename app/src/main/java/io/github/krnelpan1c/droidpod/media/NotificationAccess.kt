package io.github.krnelpan1c.droidpod.media

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

object NotificationAccess {
    fun isGranted(context: Context): Boolean =
        context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

    /** Opens DroidPod's own toggle where supported, otherwise the full listener list. */
    fun openListenerSettings(context: Context) {
        val component = ComponentName(context, MediaListenerService::class.java)
        val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString())
        if (!context.tryStartActivity(detail)) {
            context.tryStartActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }

    /** App info, where sideloaded apps find "Allow restricted settings" (Android 13+). */
    fun openAppInfo(context: Context) {
        context.tryStartActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
        )
    }
}

fun Context.tryStartActivity(intent: Intent): Boolean = try {
    if (this !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
    true
} catch (_: ActivityNotFoundException) {
    false
}
