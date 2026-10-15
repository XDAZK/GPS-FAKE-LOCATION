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
