package com.example.util

import com.example.data.model.GeoPoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

object GeoUtils {

    private const val EARTH_RADIUS_METERS = 6371000.0

    /**
     * Calculate Haversine distance in meters between two coordinates
     */
    fun distanceBetween(p1: GeoPoint, p2: GeoPoint): Double {
        val lat1Rad = Math.toRadians(p1.latitude)
        val lat2Rad = Math.toRadians(p2.latitude)
        val deltaLatRad = Math.toRadians(p2.latitude - p1.latitude)
        val deltaLngRad = Math.toRadians(p2.longitude - p1.longitude)

        val a = sin(deltaLatRad / 2) * sin(deltaLatRad / 2) +
                cos(lat1Rad) * cos(lat2Rad) *
                sin(deltaLngRad / 2) * sin(deltaLngRad / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return EARTH_RADIUS_METERS * c
    }

    /**
     * Calculate bearing in degrees (0..360) from p1 to p2
     */
    fun bearingBetween(p1: GeoPoint, p2: GeoPoint): Float {
        val lat1Rad = Math.toRadians(p1.latitude)
        val lat2Rad = Math.toRadians(p2.latitude)
        val deltaLngRad = Math.toRadians(p2.longitude - p1.longitude)

        val y = sin(deltaLngRad) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(deltaLngRad)

        val bearingRad = atan2(y, x)
        val bearingDeg = Math.toDegrees(bearingRad)
        return ((bearingDeg + 360) % 360).toFloat()
    }

    /**
     * Interpolate a point along a line between start and target at fraction (0.0 to 1.0)
     */
    fun interpolate(start: GeoPoint, target: GeoPoint, fraction: Double): GeoPoint {
        val lat = start.latitude + (target.latitude - start.latitude) * fraction
        val lng = start.longitude + (target.longitude - start.longitude) * fraction
        val alt = start.altitude + (target.altitude - start.altitude) * fraction
        return GeoPoint(lat, lng, alt)
    }

    /**
     * Adds natural micro-jitter to mimic realistic GPS hardware inaccuracies (~1-2.5 meters)
     */
    fun applyGpsJitter(point: GeoPoint, enable: Boolean): GeoPoint {
        if (!enable) return point
        // 1 meter is approx 0.000009 degrees latitude
        val jitterMeters = 1.5
        val latOffset = (Random.nextDouble(-jitterMeters, jitterMeters) / 111111.0)
        val lngOffset = (Random.nextDouble(-jitterMeters, jitterMeters) / (111111.0 * cos(Math.toRadians(point.latitude))))
        val altOffset = Random.nextDouble(-0.8, 0.8)

        return GeoPoint(
            latitude = point.latitude + latOffset,
            longitude = point.longitude + lngOffset,
            altitude = point.altitude + altOffset
        )
    }

    /**
     * Convert speed in km/h to running pace in minutes per km (min/km)
     */
    fun speedKmhToPaceMinutes(speedKmh: Float): Double {
        return if (speedKmh > 0.1f) (60.0 / speedKmh).coerceIn(4.0, 15.0) else 15.0
    }

    /**
     * Convert running pace in minutes per km to speed in km/h
     */
    fun paceMinutesToSpeedKmh(paceMin: Double): Float {
        return if (paceMin > 0.1) (60.0 / paceMin.coerceIn(4.0, 15.0)).toFloat() else 4.0f
    }

    /**
     * Format speed as Pace string (e.g. "05:30 /km")
     */
    fun formatPace(speedKmh: Float): String {
        if (speedKmh <= 0.5f) return "--:-- /km"
        val paceSec = (3600.0 / speedKmh).toInt()
        val m = paceSec / 60
        val s = paceSec % 60
        return String.format(java.util.Locale.US, "%02d:%02d /km", m, s)
    }

    fun formatPaceFromMinutes(paceMinutes: Double): String {
        val totalSec = (paceMinutes * 60.0).toInt()
        val m = totalSec / 60
        val s = totalSec % 60
        return String.format(java.util.Locale.US, "%02d:%02d /km", m, s)
    }

    /**
     * Human-like running speed simulation that dynamically shifts pace across
     * interval cycles (warmup, surge/pickup, steady cruise, ease/recovery)
     * so that the speed never stays on a flat line.
     */
    fun getDynamicRunningSpeed(
        baseSpeedKmh: Float,
        secondsElapsed: Long,
        enableVariance: Boolean = true
    ): Float {
        if (baseSpeedKmh <= 0.5f) return baseSpeedKmh
        if (!enableVariance) return baseSpeedKmh

        val t = secondsElapsed.toDouble()

        // 1. Warmup transition in the first 45 seconds (starts at 82% and ramps to 100%)
        val warmupFactor = if (t < 45.0) {
            0.82 + 0.18 * (t / 45.0)
        } else {
            1.0
        }

        // 2. Macro Interval Wave (period ~75s, amplitude ±15%): alternates between pickup and recovery
        val intervalWave = sin(t * (2.0 * Math.PI / 75.0)) * 0.15

        // 3. Secondary Wave (period ~31s, amplitude ±7%): short bursts / traffic & terrain checks
        val shortBurstWave = cos(t * (2.0 * Math.PI / 31.0)) * 0.07

        // 4. Cadence/Footstrike micro fluctuation (period ~7s, amplitude ±3%)
        val strideMicroWave = sin(t * 0.9) * 0.03

        val combinedFactor = (warmupFactor + intervalWave + shortBurstWave + strideMicroWave)
            .coerceIn(0.68, 1.35)

        return (baseSpeedKmh * combinedFactor).toFloat().coerceIn(3.0f, 22.0f)
    }

    /**
     * Fluctuates speed naturally by ±5% to ±8% to simulate human walking/driving
     */
    fun getHumanizedSpeed(baseSpeedKmh: Float, enable: Boolean): Float {
        if (!enable || baseSpeedKmh <= 0) return baseSpeedKmh
        val varianceFactor = 1.0f + (Random.nextFloat() * 0.14f - 0.07f) // -7% to +7%
        return (baseSpeedKmh * varianceFactor).coerceAtLeast(1.0f)
    }

    /**
     * Calculate total distance of a route with multiple points
     */
    fun calculateTotalDistance(points: List<GeoPoint>): Double {
        if (points.size < 2) return 0.0
        var total = 0.0
        for (i in 0 until points.size - 1) {
            total += distanceBetween(points[i], points[i + 1])
        }
        return total
    }

    /**
     * Format distance nicely in m or km
     */
    fun formatDistance(meters: Double): String {
        return if (meters >= 1000) {
            String.format(java.util.Locale.US, "%.2f km", meters / 1000)
        } else {
            String.format(java.util.Locale.US, "%.0f m", meters)
        }
    }

    /**
     * Format time nicely (hh:mm:ss or mm:ss)
     */
    fun formatDuration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) {
            String.format(java.util.Locale.US, "%02d:%02d:%02d", h, m, s)
        } else {
            String.format(java.util.Locale.US, "%02d:%02d", m, s)
        }
    }
}
