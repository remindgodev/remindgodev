package com.example.parkingautomation

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class RingGoNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""

        // ✅ Listen only for your own app’s test notification
        if (packageName.contains("parkingautomation", ignoreCase = true)) {
            Log.d("RingGoNotif", "📩 Detected Test Notification")
            Log.d("RingGoNotif", "Title: $title")
            Log.d("RingGoNotif", "Text: $text")

            if (title.contains("parked", ignoreCase = true) || text.contains("Zone")) {
                Log.d("RingGoNotif", "✅ Simulated Session START")
                // TODO: Save location, start tracking
            }

            if (title.contains("ended", ignoreCase = true) || text.contains("session ended", ignoreCase = true)) {
                Log.d("RingGoNotif", "🛑 Simulated Session END")
                // TODO: Stop tracking
            }
        }
    }
}
