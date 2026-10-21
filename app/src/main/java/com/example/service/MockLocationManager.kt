package com.example.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import com.example.data.model.GeoPoint
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices

/**
 * Enterprise-grade Mock Location Manager modeled after Sriharan-S/elsewhere.
 *
 * Implements 3-layer protection against location leakage:
 * 1. LocationManager.GPS_PROVIDER test provider
 * 2. LocationManager.NETWORK_PROVIDER test provider
 * 3. LocationManager.FUSED_PROVIDER (Android 12+ API 31+) test provider
 * 4. Google Play Services FusedLocationProviderClient (fusedClient.setMockMode(true) + setMockLocation)
 *    which intercepts location for Google Maps, Grab, Strava, Banking, and social apps!
 */
class MockLocationManager(private val context: Context) {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val activeProviders = mutableSetOf<String>()

    /**
     * Google Play Services fused client, which most modern apps read through.
     * Putting it in mock mode prevents Wi-Fi and cell tower real fixes from bleeding through!
     */
    private val fusedClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    /**
     * Check if Mock Location permission is granted or if Developer mode is active
     * Using AppOpsManager to avoid destructive addTestProvider/removeTestProvider calls.
     */
    fun isMockLocationPermitted(): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    android.app.AppOpsManager.OPSTR_MOCK_LOCATION,
                    android.os.Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    android.app.AppOpsManager.OPSTR_MOCK_LOCATION,
                    android.os.Process.myUid(),
                    context.packageName
                )
            }
            mode == android.app.AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Check if Developer Mode is enabled on the device
     */
    fun isDeveloperOptionsEnabled(): Boolean {
        return try {
            Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                0
            ) != 0
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Initialize test providers for GPS, Network, and Fused (Android 12+)
     * and put Google Play Services Fused Provider into Mock Mode.
     */
    @SuppressLint("MissingPermission")
    fun startMocking(isolateRealLocation: Boolean = true): Boolean {
        val providers = buildList {
            add(LocationManager.GPS_PROVIDER)
            if (isolateRealLocation) {
                add(LocationManager.NETWORK_PROVIDER)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(LocationManager.FUSED_PROVIDER)
            }
        }

        var success = true
        for (provider in providers) {
            try {
                // If previously registered, clean first
                try {
                    locationManager.removeTestProvider(provider)
                } catch (_: Exception) {}

                locationManager.addTestProvider(
                    provider,
                    false, // requiresNetwork
                    false, // requiresSatellite
                    false, // requiresCell
                    false, // hasMonetaryCost
                    true,  // supportsAltitude
                    true,  // supportsSpeed
                    true,  // supportsBearing
                    ProviderProperties.POWER_USAGE_LOW,
                    ProviderProperties.ACCURACY_FINE
                )
                locationManager.setTestProviderEnabled(provider, true)
                activeProviders.add(provider)
            } catch (e: Exception) {
                // If permission missing or provider restricted
                success = false
            }
        }

        // Put Google Play Services Fused Location Client in Mock Mode
        try {
            fusedClient.setMockMode(true)
        } catch (_: Exception) {}

        return success
    }

    /**
     * Push simulated mock location to all active test providers AND Google Play Services
     * at high frequency so real location NEVER bleeds through between ticks.
     */
    @SuppressLint("MissingPermission")
    fun pushMockLocation(
        geoPoint: GeoPoint,
        speedKmh: Float,
        bearing: Float,
        accuracyMeters: Float = 1.0f,
        caloriesKcal: Double = 0.0,
        steps: Long = 0L
    ) {
        val speedMps = (speedKmh * 1000f) / 3600f
        val timeNow = System.currentTimeMillis()
        val elapsedNanos = SystemClock.elapsedRealtimeNanos()

        for (provider in activeProviders) {
            try {
                val mockLocation = buildLocation(
                    provider = provider,
                    geoPoint = geoPoint,
                    speedMps = speedMps,
                    bearing = bearing,
                    accuracyMeters = accuracyMeters,
                    timeNow = timeNow,
                    elapsedNanos = elapsedNanos,
                    caloriesKcal = caloriesKcal,
                    steps = steps,
                    speedKmh = speedKmh
                )
                locationManager.setTestProviderLocation(provider, mockLocation)
            } catch (_: Exception) {}
        }

        // Push to Google Play Services Fused Location Provider Client
        try {
            val fusedLocation = buildLocation(
                provider = "fused",
                geoPoint = geoPoint,
                speedMps = speedMps,
                bearing = bearing,
                accuracyMeters = accuracyMeters,
                timeNow = timeNow,
                elapsedNanos = elapsedNanos,
                caloriesKcal = caloriesKcal,
                steps = steps,
                speedKmh = speedKmh
            )
            fusedClient.setMockLocation(fusedLocation)
        } catch (_: Exception) {}

        // Broadcast standard fitness event for other running services / receivers
        try {
            val broadcastIntent = Intent("com.example.geosim.FITNESS_UPDATE").apply {
                putExtra("latitude", geoPoint.latitude)
                putExtra("longitude", geoPoint.longitude)
                putExtra("speed_kmh", speedKmh)
                putExtra("heart_rate", geoPoint.heartRate)
                putExtra("cadence", geoPoint.cadence)
                putExtra("calories", caloriesKcal)
                putExtra("steps", steps)
            }
            context.sendBroadcast(broadcastIntent)
        } catch (_: Exception) {}
    }

    private fun buildLocation(
        provider: String,
        geoPoint: GeoPoint,
        speedMps: Float,
        bearing: Float,
        accuracyMeters: Float,
        timeNow: Long,
        elapsedNanos: Long,
        caloriesKcal: Double,
        steps: Long,
        speedKmh: Float
    ): Location {
        return Location(provider).apply {
            latitude = geoPoint.latitude
            longitude = geoPoint.longitude
            altitude = geoPoint.altitude
            speed = speedMps
            this.bearing = bearing
            accuracy = accuracyMeters
            time = timeNow
            elapsedRealtimeNanos = elapsedNanos

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                bearingAccuracyDegrees = 0.5f
                speedAccuracyMetersPerSecond = 0.2f
                verticalAccuracyMeters = 1.0f
            }

            val extras = Bundle().apply {
                putInt("satellites", 16)
                putFloat("maxCn0", 42.0f)
                putFloat("meanCn0", 35.0f)
                if (geoPoint.heartRate > 0) {
                    putInt("heart_rate", geoPoint.heartRate)
                    putInt("bpm", geoPoint.heartRate)
                }
                if (geoPoint.cadence > 0) {
                    putInt("cadence", geoPoint.cadence)
                    putInt("spm", geoPoint.cadence)
                }
                if (caloriesKcal > 0.0) {
                    putFloat("calories", caloriesKcal.toFloat())
                }
                if (steps > 0L) {
                    putLong("step_count", steps)
                    putLong("steps", steps)
                }
                putFloat("speed_kmh", speedKmh)
            }
            this.extras = extras
        }
    }

    /**
     * Clean up, remove test providers, and release Google Play Services mock mode
     */
    @SuppressLint("MissingPermission")
    fun stopMocking() {
        for (provider in activeProviders) {
            try {
                locationManager.setTestProviderEnabled(provider, false)
                locationManager.removeTestProvider(provider)
            } catch (_: Exception) {}
        }
        activeProviders.clear()

        try {
            fusedClient.setMockMode(false)
        } catch (_: Exception) {}
    }
}
