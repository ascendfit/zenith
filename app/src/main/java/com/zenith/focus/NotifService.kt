package com.zenith.focus

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Clears other apps' notifications while a focus round is running. */
class NotifService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!Prefs.isActive(this)) return
        if (!Prefs.cfg(this).notif) return
        val pkg = sbn.packageName
        if (pkg == packageName || pkg in Targets.NOTIF_ALLOW || sbn.isOngoing) return
        try {
            cancelNotification(sbn.key)
        } catch (_: Exception) {
        }
    }
}
