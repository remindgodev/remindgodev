package com.example.parkingautomation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices

object TrackingManager {

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // ✅ Initialize once (e.g., from MainActivity)
    fun init(context: Context) {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    }

    // ✅ Get location and store it when session starts
    fun saveCurrentLocationOnSessionStart(
        context: Context,
        onSuccess: (Location) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            onFailure(Exception("Location permissions not granted"))
            return
        }

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                if (location != null) {
                    Log.d("GPS", "Location captured: ${location.latitude}, ${location.longitude}")
                    storeParkingLocation(context, location.latitude, location.longitude)
                    onSuccess(location)
                } else {
                    onFailure(Exception("Location is null"))
                }
            }
            .addOnFailureListener { exception ->
                onFailure(exception)
            }
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
}


