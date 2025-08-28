package com.example.parkingautomation

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import android.os.Build
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.GeofenceStatusCodes



object GeofenceManager {

    private lateinit var geofencingClient: GeofencingClient
    private const val GEOFENCE_RADIUS_METERS = 100f
    private const val GEOFENCE_ID = "parking_geofence"

    fun init(context: Context) {
        geofencingClient = LocationServices.getGeofencingClient(context)
    }

    fun addGeofence(
        context: Context,
        geofencingClient: GeofencingClient,
        latitude: Double,
        longitude: Double
    ) {
        val geofence = Geofence.Builder()
            .setRequestId(GEOFENCE_ID)
            .setCircularRegion(latitude, longitude, GEOFENCE_RADIUS_METERS)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(
                Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT
            )
            .build()

        val geofencingRequest = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        val pendingIntent = getGeofencePendingIntent(context)

        geofencingClient.addGeofences(geofencingRequest, pendingIntent)
            .addOnSuccessListener {
                Log.d("Geofence", "✅ Geofence added at $latitude, $longitude (r=${GEOFENCE_RADIUS_METERS})")
            }
            .addOnFailureListener { e ->
                val msg = if (e is ApiException) {
                    val name = GeofenceStatusCodes.getStatusCodeString(e.statusCode)
                    "status=${e.statusCode} ($name)"
                } else e.message ?: e.toString()
                Log.e("Geofence", "❌ Failed to add geofence: $msg", e)
            }
    }


    private fun getGeofencePendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(context, 1001, intent, flags)
    }


    fun removeGeofence(context: Context) {
        geofencingClient.removeGeofences(getGeofencePendingIntent(context))
            .addOnSuccessListener {
                Log.d("Geofence", "Geofence removed")
            }
            .addOnFailureListener {
                Log.e("Geofence", "Failed to remove geofence: ${it.message}")
            }
    }
}


