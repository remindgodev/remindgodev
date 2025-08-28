package com.example.parkingautomation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.GeofencingClient
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionClient



object TrackingManager {

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private lateinit var geofencingClient: GeofencingClient

    private var isOutsideGeofence: Boolean = false
    private var lastReminderAt: Long = 0L



    // ✅ Initialize once (e.g., from MainActivity)
    fun init(context: Context) {
        val appCtx = context.applicationContext
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(appCtx)
        geofencingClient = LocationServices.getGeofencingClient(appCtx)
    }

    // ✅ Get location and store it when session starts
    fun saveCurrentLocationOnSessionStart(
        context: Context,
        onSuccess: (Location) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        // 1) Permission gate (keep yours)
        val fineOk = ActivityCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseOk = ActivityCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fineOk && !coarseOk) {
            onFailure(Exception("Location permissions not granted"))
            return
        }

        // 2) Get a fresh fix (with fallback), then save + add geofence
        val appCtx = context.applicationContext

        getFreshLocation(
            context = appCtx,
            onSuccess = { location ->
                Log.d("GPS", "Fresh location: ${location.latitude}, ${location.longitude}")

                // Save it
                storeParkingLocation(appCtx, location.latitude, location.longitude)

                // Add geofence using the single client held by TrackingManager
                GeofenceManager.addGeofence(
                    context = appCtx,
                    geofencingClient = geofencingClient,
                    latitude = location.latitude,
                    longitude = location.longitude
                )

                // Bubble up success
                onSuccess(location)
            },
            onFailure = { e ->
                Log.e("GPS", "Failed to get location for geofence: ${e.message}", e)
                onFailure(e)
            }
        )
    }


    // ✅ Save location to SharedPreferences
    private fun storeParkingLocation(context: Context, lat: Double, lng: Double) {
        val prefs = context.getSharedPreferences("parking_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putLong("parking_lat", lat.toRawBits())
            .putLong("parking_lng", lng.toRawBits())
            .putLong("parking_time", System.currentTimeMillis())
            .apply()
    }

    @SuppressLint("MissingPermission")
    private fun getFreshLocation(
        context: Context,
        onSuccess: (Location) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val appCtx = context.applicationContext
        val fused = fusedLocationClient  // already initialized in init()

        // Try a fresh, single-shot high-accuracy fix (fast when GPS is available)
        val cts = com.google.android.gms.tasks.CancellationTokenSource()
        fused.getCurrentLocation(
            com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, cts.token
        ).addOnSuccessListener { loc ->
            if (loc != null) {
                onSuccess(loc)
            } else {
                // Fallback to lastLocation if getCurrentLocation returns null
                fused.lastLocation
                    .addOnSuccessListener { last ->
                        if (last != null) onSuccess(last)
                        else onFailure(Exception("No location available (fresh or last)"))
                    }
                    .addOnFailureListener { e -> onFailure(e) }
            }
        }.addOnFailureListener { e ->
            onFailure(e)
        }
    }

    private const val ACTIVITY_UPDATES_RC = 2001

    private fun getActivityUpdatesPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ActivityUpdatesReceiver::class.java)

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        return PendingIntent.getBroadcast(
            context.applicationContext,
            ACTIVITY_UPDATES_RC,
            intent,
            flags
        )
    }

    fun onGeofenceExit(context: Context) {
        isOutsideGeofence = true
        Log.d("GeofenceState", "EXIT → outside=true. Starting activity updates.")
        startActivityUpdates(context.applicationContext)
    }

    fun onGeofenceEnter(context: Context) {
        isOutsideGeofence = false
        Log.d("GeofenceState", "ENTER → outside=false. Stopping activity updates.")
        stopActivityUpdates(context.applicationContext)
    }


    // TODO: Replace with your real session state (e.g., set true on RingGo "start" and false on "end")
    private fun isSessionActive(context: Context): Boolean {
        // Quick placeholder: read a flag from SharedPreferences (default true while testing)
        val prefs = context.getSharedPreferences("parking_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("session_active", true) // <- set this properly in your NotificationListener
    }

    fun startActivityUpdates(context: Context) {
        val appCtx = context.applicationContext
        val client: ActivityRecognitionClient = ActivityRecognition.getClient(appCtx)
        val pi = getActivityUpdatesPendingIntent(appCtx)
        val detectionIntervalMs = 10_000L

        client.requestActivityUpdates(detectionIntervalMs, pi)
            .addOnSuccessListener { Log.d("ActivityUpdates", "✅ Started") }
            .addOnFailureListener { e -> Log.e("ActivityUpdates", "❌ Start failed: ${e.message}", e) }
    }

    fun stopActivityUpdates(context: Context) {
        val appCtx = context.applicationContext
        val client: ActivityRecognitionClient = ActivityRecognition.getClient(appCtx)
        val pi = getActivityUpdatesPendingIntent(appCtx)

        client.removeActivityUpdates(pi)
            .addOnSuccessListener { Log.d("ActivityUpdates", "🛑 Stopped") }
            .addOnFailureListener { e -> Log.e("ActivityUpdates", "⚠️ Stop failed: ${e.message}", e) }
    }

    fun onDrivingDetected(context: Context) {
        val appCtx = context.applicationContext

        // 1) Only alert if we’re currently outside the geofence
        if (!isOutsideGeofence) {
            Log.d("ActivityUpdates", "Driving detected but still inside geofence — no reminder.")
            return
        }

        // 2) Only alert if the parking session is still active
        if (!isSessionActive(appCtx)) {
            Log.d("ActivityUpdates", "No active session — no reminder.")
            stopActivityUpdates(appCtx) // optional: stop listening if no session
            return
        }

        // 3) Throttle reminders (e.g., at most once per 2 minutes)
        val now = System.currentTimeMillis()
        if (now - lastReminderAt < 2 * 60_000L) {
            Log.d("ActivityUpdates", "Reminder throttled.")
            return
        }
        lastReminderAt = now

        // 4) Send the reminder notification
        sendDriveAwayReminder(appCtx)

        // 5) Optional: stop activity updates after alert to save battery
        stopActivityUpdates(appCtx)
    }

    private fun sendDriveAwayReminder(context: Context) {
        val channelId = "parking_reminders"
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                "Parking Reminders",
                android.app.NotificationManager.IMPORTANCE_HIGH
            )
            nm.createNotificationChannel(channel)
        }

        val notif = androidx.core.app.NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("You're leaving your parking zone")
            .setContentText("If you’re done, cancel your RingGo session now.")
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            // TODO: add contentIntent to open RingGo if you want, using getLaunchIntentForPackage(...)
            .build()

        nm.notify(1002, notif)
    }
}


