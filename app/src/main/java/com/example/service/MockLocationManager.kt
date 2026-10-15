package com.example.service

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

class MockLocationManager(private val context: Context) {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val activeProviders = mutableSetOf<String>()

    /**
     * Check if Mock Location permission is granted or if Developer mode is active
     */
    fun isMockLocationPermitted(): Boolean {
        return try {
            val provider = LocationManager.GPS_PROVIDER
            locationManager.addTestProvider(
                provider,
                false,
                false,
                false,
                false,
                true,
                true,
                true,
                ProviderProperties.POWER_USAGE_LOW,
                ProviderProperties.ACCURACY_FINE
            )
            locationManager.removeTestProvider(provider)
            true
        } catch (_: SecurityException) {
            false
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
     * Initialize test providers for GPS and optionally Network provider
     */
    fun startMocking(isolateRealLocation: Boolean = true): Boolean {
        val providers = if (isolateRealLocation) {
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        } else {
            listOf(LocationManager.GPS_PROVIDER)
        }

        var success = true
        for (provider in providers) {
            try {
                // If previously registered, remove first to clean state
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
                success = false
            }
        }
        return success
    }

    /**
     * Push simulated mock location to all active test providers with realistic metadata
     */
    fun pushMockLocation(
        geoPoint: GeoPoint,
        speedKmh: Float,
        bearing: Float,
        accuracyMeters: Float = 3.2f,
        caloriesKcal: Double = 0.0,
        steps: Long = 0L
    ) {
        val speedMps = (speedKmh * 1000f) / 3600f
        val timeNow = System.currentTimeMillis()
        val elapsedNanos = SystemClock.elapsedRealtimeNanos()

        for (provider in activeProviders) {
            try {
                val mockLocation = Location(provider).apply {
                    latitude = geoPoint.latitude
                    longitude = geoPoint.longitude
                    altitude = geoPoint.altitude
                    speed = speedMps
                    this.bearing = bearing
                    accuracy = accuracyMeters
                    time = timeNow
                    elapsedRealtimeNanos = elapsedNanos

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        bearingAccuracyDegrees = 1.5f
                        speedAccuracyMetersPerSecond = 0.5f
                        verticalAccuracyMeters = 2.0f
                    }

                    // Attach realistic mock satellite & fitness telemetry in Extras
                    val extras = Bundle().apply {
                        putInt("satellites", 14) // Mimic 14 active GPS+GLONASS satellites
                        putFloat("maxCn0", 38.5f)
                        putFloat("meanCn0", 31.0f)
                        // Fitness telemetry for fitness & tracker apps
                        if (geoPoint.heartRate > 0) {
                            putInt("heart_rate", geoPoint.heartRate)
                            putInt("heartRate", geoPoint.heartRate)
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

                locationManager.setTestProviderLocation(provider, mockLocation)
            } catch (_: Exception) {
                // Ignore transient provider exceptions
            }
        }

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

    /**
     * Clean up and remove test providers
     */
    fun stopMocking() {
        for (provider in activeProviders) {
            try {
                locationManager.setTestProviderEnabled(provider, false)
                locationManager.removeTestProvider(provider)
            } catch (_: Exception) {}
        }
        activeProviders.clear()
    }
}
