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
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat



object TrackingManager {

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private lateinit var geofencingClient: GeofencingClient

    private var isOutsideGeofence: Boolean = false
    private var lastReminderAt: Long = 0L

    // --- New state for re-entry logic ---
    private var initialEnterConsumed = false   // ignore the very first ENTER after add
    private var armedAfterReentry = false      // set true on genuine re-ENTER (back to car)
    private var insideGeofence = false         // our current inside/outside view
    private var lastInVehicleAt: Long = 0L     // last time we saw IN_VEHICLE
    private const val DRIVE_RECENCY_MS = 90_000L // 90s window to count as “recent driving”



    // ✅ Initialize once (e.g., from MainActivity)
    fun init(context: Context) {
        val appCtx = context.applicationContext
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(appCtx)
        geofencingClient = LocationServices.getGeofencingClient(appCtx)
    }

    // ✅ Get location and store it when session starts
    // this function is called in RingoNotificationListener when session starts
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

    // TODO: Replace with your real session state (e.g., set true on RingGo "start" and false on "end")
    private fun isSessionActive(context: Context): Boolean {
        // Quick placeholder: read a flag from SharedPreferences (default true while testing)
        val prefs = context.getSharedPreferences("parking_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("session_active", true) // <- set this properly in your NotificationListener
    }


    fun onGeofenceEnter(context: Context) {
        insideGeofence = true
        val appCtx = context.applicationContext

        // The very first ENTER often fires immediately when a geofence is added.
        if (!initialEnterConsumed) {
            initialEnterConsumed = true
            Log.d("GeofenceState", "Initial ENTER consumed; not arming yet.")
            return
        }

        // Genuine re-ENTER: we’re back to the car → arm and start Activity Recognition
        armedAfterReentry = true
        startActivityUpdates(appCtx)
        Log.d("GeofenceState", "ENTER (re-entry) → armed=true, started ActivityRecognition.")
    }

    fun onGeofenceExit(context: Context) {
        insideGeofence = false
        val appCtx = context.applicationContext
        val now = System.currentTimeMillis()
        val droveRecently = (now - lastInVehicleAt) <= DRIVE_RECENCY_MS

        // If we had re-entered (armed) and recently saw driving, fire the reminder now
        if (armedAfterReentry && isSessionActive(appCtx) && droveRecently) {
            sendDriveAwayReminder(appCtx)
            armedAfterReentry = false
            stopActivityUpdates(appCtx)
            Log.d("GeofenceState", "EXIT after re-entry with recent driving → reminder sent.")
        } else {
            Log.d("GeofenceState", "EXIT: armed=$armedAfterReentry, droveRecently=$droveRecently → no reminder yet.")
            // AR stays running from the prior ENTER; if IN_VEHICLE arrives while outside, onDrivingDetected will fire the reminder.
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

    // This bit has will fire the reminder if it detects in vehicle for first time and yo are outside
    // geofence.  If you park go outside geofence and get in another car it will send reminder even
    // if you want to keep parking active rare but could occur
    fun onDrivingDetected(context: Context) {
        val appCtx = context.applicationContext
        lastInVehicleAt = System.currentTimeMillis()

        // If we re-entered (armed) and we're already outside, alert immediately
        if (armedAfterReentry && !insideGeofence && isSessionActive(appCtx)) {
            sendDriveAwayReminder(appCtx)
            armedAfterReentry = false
            stopActivityUpdates(appCtx)
            Log.d("ActivityUpdates", "Reminder sent on driving while outside.")
        } else {
            Log.d("ActivityUpdates", "Driving detected. Armed=$armedAfterReentry, inside=$insideGeofence")
        }
    }

        // old Chatgpt suggestions may be useful especially 5 if not already above
        // 4) Send the reminder notification
        //sendDriveAwayReminder(appCtx)

        // 5) Optional: stop activity updates after alert to save battery
        //stopActivityUpdates(appCtx)


    private fun sendDriveAwayReminder(context: Context) {
        val channelId = "parking_reminders"
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val ch = NotificationChannel(channelId, "Parking Reminders", NotificationManager.IMPORTANCE_HIGH)
            nm.createNotificationChannel(ch)
        }

        val notif = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("You're leaving your parking zone")
            .setContentText("If you’re done, cancel your RingGo session now.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        nm.notify(1002, notif)
    }
    fun onSessionEnded(context: Context) {
        val appCtx = context.applicationContext
        armedAfterReentry = false
        initialEnterConsumed = false
        stopActivityUpdates(appCtx)
        GeofenceManager.removeGeofence(appCtx)
        Log.d("Session", "Session ended: state reset, AR stopped.")
    }

}


