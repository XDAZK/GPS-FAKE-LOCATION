package com.example.util

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Utility to reliably fetch the user's REAL physical device GPS location.
 * Uses Google Play Services FusedLocationProviderClient first,
 * with fallbacks to Android framework LocationManager (GPS & Network).
 */
object RealLocationProvider {

    @SuppressLint("MissingPermission")
    suspend fun getRealCurrentLocation(context: Context): Location? {
        // Try with 6-second timeout so UI never hangs
        return withTimeoutOrNull(6000L) {
            // 1. Try Google Play Services Fused Location
            val fusedLocation = tryGetFusedLocation(context)
            if (fusedLocation != null && isValidLocation(fusedLocation)) {
                return@withTimeoutOrNull fusedLocation
            }

            // 2. Try Android Framework LocationManager
            val frameworkLocation = tryGetFrameworkLocation(context)
            if (frameworkLocation != null && isValidLocation(frameworkLocation)) {
                return@withTimeoutOrNull frameworkLocation
            }

            // 3. Fallback to any last known location
            getLastKnownLocation(context)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun tryGetFusedLocation(context: Context): Location? {
        return try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            val cts = CancellationTokenSource()

            suspendCancellableCoroutine { continuation ->
                fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                    .addOnSuccessListener { loc ->
                        continuation.resume(loc)
                    }
                    .addOnFailureListener {
                        continuation.resume(null)
                    }

                continuation.invokeOnCancellation {
                    cts.cancel()
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun tryGetFrameworkLocation(context: Context): Location? {
        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
            val provider = when {
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                else -> null
            } ?: return null

            suspendCancellableCoroutine { continuation ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        try {
                            lm.removeUpdates(this)
                        } catch (_: Exception) {}
                        if (continuation.isActive) {
                            continuation.resume(location)
                        }
                    }
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                }

                try {
                    lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                } catch (_: Exception) {
                    if (continuation.isActive) continuation.resume(null)
                }

                continuation.invokeOnCancellation {
                    try {
                        lm.removeUpdates(listener)
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    @SuppressLint("MissingPermission")
    fun getLastKnownLocation(context: Context): Location? {
        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            var bestLoc: Location? = null
            for (p in providers) {
                try {
                    if (lm.isProviderEnabled(p)) {
                        val loc = lm.getLastKnownLocation(p)
                        if (loc != null) {
                            if (bestLoc == null || loc.time > bestLoc.time) {
                                bestLoc = loc
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
            bestLoc
        } catch (_: Exception) {
            null
        }
    }

    private fun isValidLocation(loc: Location): Boolean {
        return loc.latitude != 0.0 && loc.longitude != 0.0 &&
                loc.latitude in -90.0..90.0 && loc.longitude in -180.0..180.0
    }
}
