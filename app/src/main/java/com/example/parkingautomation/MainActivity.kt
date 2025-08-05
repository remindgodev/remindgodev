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


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ParkingAutomationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NotificationAccessScreen(modifier = Modifier.padding(innerPadding))
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
