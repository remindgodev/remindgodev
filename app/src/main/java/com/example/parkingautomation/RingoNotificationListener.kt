package com.example.parkingautomation

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.parkingautomation.TrackingManager

class RingGoNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val ctx = applicationContext
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

                TrackingManager.saveCurrentLocationOnSessionStart(
                    context = applicationContext,
                    onSuccess = {
                        Log.d("RingGoNotif", "✅ Location saved and geofence added")
                    },
                    onFailure = {
                        Log.e("RingGoNotif", "❌ Failed to save location: ${it.message}")
                    }
                )
            }

            if (
                title.contains("ended", ignoreCase = true) ||
                text.contains("session ended", ignoreCase = true) ||
                text.contains("parking ended", ignoreCase = true) ||
                text.contains("has ended", ignoreCase = true) ||
                text.contains("ended successfully", ignoreCase = true) ||
                text.contains("stopped your session", ignoreCase = true) ||
                text.contains("session cancelled", ignoreCase = true) ||
                text.contains("parking stopped", ignoreCase = true)
            ) {
                Log.d("RingGoNotif", "🛑 Session END detected")
                TrackingManager.onSessionEnded(applicationContext)
            }

        }
    }
}
