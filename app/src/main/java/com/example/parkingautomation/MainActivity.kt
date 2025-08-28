package com.example.parkingautomation

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.parkingautomation.ui.theme.ParkingAutomationTheme
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import android.content.Context
import android.Manifest
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.LocationServices



class MainActivity : ComponentActivity() {

    // 🔐 Permission constants
    private val FOREGROUND_PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    private val PERMISSION_REQUEST_CODE = 1001

    private val BACKGROUND_PERMISSION = Manifest.permission.ACCESS_BACKGROUND_LOCATION

    // 🔍 Check if permissions are granted
    private fun hasForegroundPermissions(): Boolean {
        return FOREGROUND_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun hasBackgroundPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, BACKGROUND_PERMISSION) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestForegroundPermissions() {
        ActivityCompat.requestPermissions(
            this,
            FOREGROUND_PERMISSIONS,
            PERMISSION_REQUEST_CODE
        )
    }

    private fun requestBackgroundPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(BACKGROUND_PERMISSION),
            PERMISSION_REQUEST_CODE + 1 // different request code
        )
    }

    lateinit var geofencingClient: GeofencingClient

    // 🧠 Your existing onCreate()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        geofencingClient = LocationServices.getGeofencingClient(applicationContext)

        if (!hasForegroundPermissions()) {
            requestForegroundPermissions()
        } else if (!hasBackgroundPermission()) {
            requestBackgroundPermission()
        }

        TrackingManager.init(applicationContext)

        setContent {
            ParkingAutomationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NotificationAccessScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }


    // ✅ Handle user's permission response
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            PERMISSION_REQUEST_CODE -> {
                if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    Log.d("Permissions", "Foreground location permissions granted")
                    // Request background permission if not already granted
                    if (!hasBackgroundPermission()) {
                        requestBackgroundPermission()
                    }
                } else {
                    Toast.makeText(
                        this,
                        "Foreground location permissions are required for the app to work.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            PERMISSION_REQUEST_CODE + 1 -> {
                if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    Log.d("Permissions", "Background location permission granted")
                } else {
                    Toast.makeText(
                        this,
                        "Background location permission is required for geofencing.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}

@Composable
fun NotificationAccessScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        val context = LocalContext.current

        Text(
            text = "Smart Parking Reminder",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Button(
            onClick = {
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                context.startActivity(intent)
                Toast.makeText(
                    context,
                    "Please enable notification access for Smart Parking Reminder",
                    Toast.LENGTH_LONG
                ).show()
            },
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Text("Enable Notification Access")
        }

        // 👇 NEW BUTTON: Send test notification
        Button(
            onClick = {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val channelId = "test_channel"

                // Create notification channel (required for Android 8+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = NotificationChannel(
                        channelId,
                        "Test Channel",
                        NotificationManager.IMPORTANCE_DEFAULT
                    )
                    notificationManager.createNotificationChannel(channel)
                }

                // Send a fake RingGo-style notification
                val builder = NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("You're parked in Zone 12345")
                    .setContentText("Your session expires at 15:30")
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)

                notificationManager.notify(1001, builder.build())
            }
        ) {
            Text("Send Test Notification")
        }
    }
}
