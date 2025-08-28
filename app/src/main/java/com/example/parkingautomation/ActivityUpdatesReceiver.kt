package com.example.parkingautomation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.DetectedActivity

class ActivityUpdatesReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityRecognitionResult.hasResult(intent)) {
            Log.w("ActivityReceiver", "No ActivityRecognitionResult"); return
        }
        val result = ActivityRecognitionResult.extractResult(intent) ?: run {
            Log.w("ActivityReceiver", "Result was null"); return
        }
        val probable = result.mostProbableActivity
        val type = probable.type
        val confidence = probable.confidence
        Log.d("ActivityReceiver", "Activity=$type conf=$confidence")

        if (type == DetectedActivity.IN_VEHICLE && confidence >= 70) {
            Log.d("ActivityReceiver", "IN_VEHICLE detected (>=70%)")
            TrackingManager.onDrivingDetected(context.applicationContext)
        }
    }
}
