package com.example.parkingautomation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent)

        if (event == null) {
            Log.e("GeofenceReceiver", "Received null GeofencingEvent")
            return
        }

        if (event.hasError()) {
            Log.e("GeofenceReceiver", "Error: ${event.errorCode}")
            return
        }

        val transition = event.geofenceTransition
        val ids = event.triggeringGeofences?.joinToString { it.requestId } ?: "[]"
        val loc = event.triggeringLocation
        Log.d("GeofenceReceiver", "Geofence transition=$transition ids=$ids loc=$loc")

        when (transition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> {
                Log.d("GeofenceReceiver", "🚪 ENTER geofence: $ids")
                TrackingManager.onGeofenceEnter(context.applicationContext)
            }
            Geofence.GEOFENCE_TRANSITION_EXIT -> {
                Log.d("GeofenceReceiver", "🏃 EXIT geofence: $ids")
                TrackingManager.onGeofenceExit(context.applicationContext)
            }
            else -> {
                Log.w("GeofenceReceiver", "UNKNOWN transition=$transition for $ids")
            }
        }
    }
}
