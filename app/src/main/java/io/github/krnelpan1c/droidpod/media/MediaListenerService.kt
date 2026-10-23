package io.github.krnelpan1c.droidpod.media

import android.service.notification.NotificationListenerService
import io.github.krnelpan1c.droidpod.container

/**
 * Holding Notification Access is what lets us call
 * MediaSessionManager.getActiveSessions(). We never read notifications.
 */
class MediaListenerService : NotificationListenerService() {
    override fun onListenerConnected() {
        container.sessions.refresh()
    }
}
