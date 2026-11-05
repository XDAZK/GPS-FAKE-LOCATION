package com.example.util

import com.example.data.model.GeoPoint
import com.example.data.model.HeartRateMode
import com.example.data.model.SimulationConfig
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Enterprise-grade Garmin FIT (Flexible and Interoperable Data Transfer) Binary Exporter.
 *
 * Implements full Garmin FIT Protocol Activity Structure required by Strava, Garmin Connect, Zwift:
 * 1. 14-byte File Header (".FIT" signature, 0x20 protocol version, Header CRC)
 * 2. File ID Message (Global Mesg 0): Type 4 (Activity), Manufacturer Garmin (1), product 1, time_created
 * 3. Event Start Message (Global Mesg 21): Event Timer (0), Type Start (0)
 * 4. Record Messages (Global Mesg 20): High-density 1Hz interpolated track
 *    - Field 253: Timestamp (uint32)
 *    - Field 0: Position Latitude (sint32, semicircles)
 *    - Field 1: Position Longitude (sint32, semicircles)
 *    - Field 2: Altitude (uint16, scale 5, offset 500) -> strictly constrained 0-5m with natural contours
 *    - Field 3: Heart Rate (uint8, bpm) -> dynamic warmup & HRV sinus oscillation (Zones 1-4)
 *    - Field 4: Cadence (uint8, single-leg RPM -> Garmin Connect running multiplies by 2 to display 160-176 SPM)
 *    - Field 6: Distance (uint32, scale 100, cm)
 * 5. Event Stop Message (Global Mesg 21): Event Timer (0), Type Stop All (9)
 * 6. Lap Message (Global Mesg 19): MANDATORY for Strava/Garmin
 *    - Sport (1 = Running), Sub-sport (0), avg_cadence (RPM), avg_hr, max_hr, calories, distance, times
 * 7. Session Message (Global Mesg 18):
 *    - Sport (1 = Running), avg_cadence (RPM), avg_hr, max_hr, avg_power (uint16, 2 bytes)
 * 8. Activity Message (Global Mesg 34): Top-level summary wrapper for Strava/Garmin API ingestion
 * 9. End-of-file CRC-16 Checksum
 */
object FitExporter {

    // FIT Epoch: 1989-12-31 00:00:00 UTC (Unix Epoch 1970 diff = 631,065,600 seconds)
    private const val FIT_EPOCH_OFFSET_SECONDS = 631065600L

    // Semicircles factor: 180 degrees = 2^31 semicircles
    private const val SEMICIRCLES_FACTOR = 2147483648.0 / 180.0

    // Garmin CRC-16 Nibble Lookup Table
    private val CRC_TABLE = intArrayOf(
        0x0000, 0xCC01, 0xD801, 0x1400, 0xF001, 0x3C00, 0x2800, 0xE401,
        0xA001, 0x6C00, 0x7800, 0xB401, 0x5000, 0x9C01, 0x8801, 0x4400
    )

    fun calculateCrc(data: ByteArray, startOffset: Int = 0, length: Int = data.size): Int {
        var crc = 0
        for (i in startOffset until (startOffset + length)) {
            val byte = data[i].toInt() and 0xFF
            var tmp = CRC_TABLE[crc and 0x0F]
            crc = (crc ushr 4) and 0x0FFF
            crc = crc xor tmp xor CRC_TABLE[byte and 0x0F]

            tmp = CRC_TABLE[crc and 0x0F]
            crc = (crc ushr 4) and 0x0FFF
            crc = crc xor tmp xor CRC_TABLE[(byte ushr 4) and 0x0F]
        }
        return crc and 0xFFFF
    }

    /**
     * Convert Unix millisecond timestamp to FIT 32-bit unsigned epoch timestamp
     */
    fun toFitTimestamp(unixMillis: Long): Long {
        val unixSeconds = unixMillis / 1000L
        val fitSeconds = unixSeconds - FIT_EPOCH_OFFSET_SECONDS
        return fitSeconds.coerceAtLeast(0L)
    }

    fun toSemicircles(degrees: Double): Int {
        return (degrees * SEMICIRCLES_FACTOR).roundToInt()
    }

    /**
     * Generate complete binary FIT file byte array from a list of route points,
     * interpolating into a smooth high-density track (1Hz) with realistic physiological
     * heart rate curves, accurate running cadence (avoiding the 2x multiplier bug in Garmin Connect),
     * and elevation strictly bounded to 0-5m with natural contours.
     */
    fun generateFitBinary(
        points: List<GeoPoint>,
        sportName: String = "running",
        customStartTimeMillis: Long? = null,
        speedKmh: Float = 12f,
        config: SimulationConfig? = null
    ): ByteArray {
        val stream = ByteArrayOutputStream()

        if (points.isEmpty()) return ByteArray(0)

        // 1. Calculate route distance
        var totalDistMeters = 0.0
        val segmentDistances = DoubleArray((points.size - 1).coerceAtLeast(1))
        val cumulativeDistances = DoubleArray(points.size)
        if (points.size > 1) {
            var acc = 0.0
            for (i in 0 until points.size - 1) {
                val d = GeoUtils.distanceBetween(points[i], points[i + 1])
                segmentDistances[i] = d
                acc += d
                cumulativeDistances[i + 1] = acc
            }
            totalDistMeters = acc
        }
        if (totalDistMeters < 5.0) {
            totalDistMeters = 1000.0 // Default 1km if single point
        }

        // 2. Realistic duration and speed
        val effectiveSpeedKmh = when {
            speedKmh > 0f -> speedKmh
            config != null && config.speedKmh > 0f -> config.speedKmh
            else -> 12f // Standard 5:00 min/km running pace
        }
        val speedMps = ((effectiveSpeedKmh * 1000.0) / 3600.0).coerceIn(1.2, 8.0)
        val isLiveTrack = points.size >= 10 && points.any { it.heartRate > 0 }
        val calculatedDurationSeconds = (totalDistMeters / speedMps).roundToInt().coerceAtLeast(points.size.coerceAtLeast(30))
        val finalDurationSeconds = if (isLiveTrack) points.size else calculatedDurationSeconds
        val numSamples = if (isLiveTrack) points.size else calculatedDurationSeconds.coerceIn(30, 7200)
        val finalDurationMillis = finalDurationSeconds * 1000L

        val startTimeMillis = customStartTimeMillis
            ?: if (points.first().time > 0) points.first().time else (System.currentTimeMillis() - finalDurationMillis)
        val endTimeMillis = startTimeMillis + finalDurationMillis

        val startFitTime = toFitTimestamp(startTimeMillis)
        val endFitTime = toFitTimestamp(endTimeMillis)

        // ==========================================
        // 1. FILE ID MESSAGE (Local Mesg 0) - Global Msg 0
        // Hardcoded device: Garmin Forerunner 935, S/N 3950640004, SW 14.1
        // ==========================================
        val fileIdDef = byteArrayOf(
            0x40.toByte(), 0x00, 0x00,
            0x00, 0x00, // Global Msg 0
            0x05,       // 5 fields
            0x00, 0x01, 0x00,           // Field 0: type (enum, 1 byte) -> 4 = Activity
            0x01, 0x02, 0x84.toByte(),   // Field 1: manufacturer (uint16, 2 bytes) -> 1 = Garmin
            0x02, 0x02, 0x84.toByte(),   // Field 2: product (uint16, 2 bytes) -> 2691 = Garmin Forerunner 935
            0x03, 0x04, 0x86.toByte(),   // Field 3: serial_number (uint32, 4 bytes) -> 3950640004
            0x04, 0x04, 0x86.toByte()    // Field 4: time_created (uint32, 4 bytes)
        )
        stream.write(fileIdDef)

        val fileIdData = ByteBuffer.allocate(1 + 1 + 2 + 2 + 4 + 4).order(ByteOrder.LITTLE_ENDIAN).apply {
            put(0x00.toByte())          // Header: Data msg, local 0
            put(0x04.toByte())          // File type: 4 (Activity)
            putShort(1.toShort())        // Manufacturer: 1 = Garmin
            putShort(2691.toShort())     // Product: 2691 = Garmin Forerunner 935
            putInt(3950640004L.toInt())  // Serial number: 3950640004
            putInt(startFitTime.toInt()) // time_created
        }.array()
        stream.write(fileIdData)

        // ==========================================
        // 1b. DEVICE INFO MESSAGE (Local Mesg 6) - Global Msg 23 (Garmin Forerunner 935)
        // ==========================================
        val deviceInfoDef = byteArrayOf(
            0x46.toByte(), 0x00, 0x00,
            0x17, 0x00, // Global Msg 23 (device_info)
            0x07,       // 7 fields
            0xFD.toByte(), 0x04, 0x86.toByte(), // Field 253: timestamp (uint32)
            0x00, 0x01, 0x02,                   // Field 0: device_index (uint8)
            0x01, 0x01, 0x02,                   // Field 1: device_type (uint8)
            0x02, 0x02, 0x84.toByte(),          // Field 2: manufacturer (uint16)
            0x03, 0x04, 0x86.toByte(),          // Field 3: serial_number (uint32)
            0x04, 0x02, 0x84.toByte(),          // Field 4: product (uint16)
            0x05, 0x02, 0x84.toByte()           // Field 5: software_version (uint16, 14.10 * 100 = 1410)
        )
        stream.write(deviceInfoDef)

        val deviceInfoData = ByteBuffer.allocate(1 + 4 + 1 + 1 + 2 + 4 + 2 + 2).order(ByteOrder.LITTLE_ENDIAN).apply {
            put(0x06.toByte())          // Header: Data msg, local 6
            putInt(startFitTime.toInt())
            put(0x00.toByte())          // Device index: 0 (Creator)
            put(0x01.toByte())          // Device type: 1
            putShort(1.toShort())        // Manufacturer: 1 (Garmin)
            putInt(3950640004L.toInt())  // Serial number: 3950640004
            putShort(2691.toShort())     // Product: 2691 (Garmin Forerunner 935)
            putShort(1410.toShort())     // Software version: 14.10 (scale 100)
        }.array()
        stream.write(deviceInfoData)

        // ==========================================
        // 2. EVENT START MESSAGE (Local Mesg 1) - Global Msg 21
        // ==========================================
        val eventDef = byteArrayOf(
            0x41.toByte(), 0x00, 0x00,
            0x15, 0x00, // Global Msg 21 (event)
            0x03,       // 3 fields
            0xFD.toByte(), 0x04, 0x86.toByte(), // Field 253: timestamp (uint32)
            0x00, 0x01, 0x00,                   // Field 0: event (enum) -> 0 = Timer
            0x01, 0x01, 0x00                    // Field 1: event_type (enum) -> 0 = Start
        )
        stream.write(eventDef)

        val eventStartData = ByteBuffer.allocate(1 + 4 + 1 + 1).order(ByteOrder.LITTLE_ENDIAN).apply {
            put(0x01.toByte())          // Header: Data msg, local 1
            putInt(startFitTime.toInt()) // Timestamp
            put(0x00.toByte())          // Event: 0 (Timer)
            put(0x00.toByte())          // Event Type: 0 (Start)
        }.array()
        stream.write(eventStartData)

        // ==========================================
        // 3. RECORD MESSAGES (Local Mesg 2) - Global Msg 20
        // High-Density 1Hz Track Points (Every 1 second)
        // Standard Garmin FIT Record Fields:
        // Field 253: timestamp (uint32)
        // Field 0: position_lat (sint32)
        // Field 1: position_long (sint32)
        // Field 2: altitude (uint16, scale 5, offset 500)
        // Field 3: heart_rate (uint8, bpm)
        // Field 4: cadence (uint8, single-leg RPM)
        // Field 5: distance (uint32, scale 100, units m) -> MANDATORY for distance charts!
        // Field 6: speed (uint16, scale 1000, units m/s) -> MANDATORY for speed charts!
        // ==========================================
        val recordDef = byteArrayOf(
            0x42.toByte(), 0x00, 0x00,
            0x14, 0x00, // Global Msg 20 (record)
            0x08,       // 8 fields
            0xFD.toByte(), 0x04, 0x86.toByte(), // Field 253: timestamp (uint32)
            0x00, 0x04, 0x85.toByte(),          // Field 0: position_lat (sint32)
            0x01, 0x04, 0x85.toByte(),          // Field 1: position_long (sint32)
            0x02, 0x02, 0x84.toByte(),          // Field 2: altitude (uint16, scale 5, offset 500)
            0x03, 0x01, 0x02,                   // Field 3: heart_rate (uint8, bpm)
            0x04, 0x01, 0x02,                   // Field 4: cadence (uint8, single-leg RPM)
            0x05, 0x04, 0x86.toByte(),          // Field 5: distance (uint32, scale 100, units m)
            0x06, 0x02, 0x84.toByte()           // Field 6: speed (uint16, scale 1000, units m/s)
        )
        stream.write(recordDef)

        // Target physiological cruise heart rate based on config or speed
        val targetCruiseBpm = when {
            config != null && config.heartRateMode != HeartRateMode.AUTO_BY_SPEED -> {
                when (config.heartRateMode) {
                    HeartRateMode.ZONE_1_WARMUP -> 105.0
                    HeartRateMode.ZONE_2_FAT_BURN -> 124.0
                    HeartRateMode.ZONE_3_AEROBIC -> 144.0
                    HeartRateMode.ZONE_4_ANAEROBIC -> 164.0
                    HeartRateMode.ZONE_5_MAX -> 182.0
                    HeartRateMode.CUSTOM_FIXED -> config.customHeartRateBpm.toDouble()
                    else -> 148.0
                }
            }
            effectiveSpeedKmh < 6.0f -> 102.0
            effectiveSpeedKmh < 9.0f -> 126.0
            effectiveSpeedKmh < 13.0f -> 148.0
            effectiveSpeedKmh < 17.0f -> 162.0
            else -> 172.0
        }

        // Base target running SPM (Steps Per Minute)
        val baseRunningSpm = when {
            config != null && !config.autoCadenceBySpeed -> config.manualCadenceSpm.toDouble()
            effectiveSpeedKmh < 6.0f -> 115.0 // Walking
            effectiveSpeedKmh < 9.5f -> 156.0 // Easy jog
            effectiveSpeedKmh < 14.0f -> 168.0 // Standard run (168 SPM)
            else -> 176.0 // Fast pace (176 SPM)
        }

        val warmupDurationSec = (numSamples * 0.14).coerceIn(45.0, 180.0)
        val warmupStartBpm = (targetCruiseBpm - 26.0).coerceAtLeast(112.0)

        var hrSum = 0L
        var hrCount = 0
        var maxHr = 0
        var spmSum = 0L
        var spmCount = 0
        var speedSumMps = 0.0
        var maxSpeedMps = 0.0
        var currentSegmentIdx = 0

        for (s in 0 until numSamples) {
            val recordTimeMillis = startTimeMillis + (s * 1000L)
            val fitTime = toFitTimestamp(recordTimeMillis)

            var lat: Double
            var lng: Double
            var altMeters: Double
            var instantHr: Int
            var instantSpm: Int
            var currentDistMeters: Double
            var instantSpeedMps: Double

            if (isLiveTrack) {
                val pt = points[s]
                lat = pt.latitude
                lng = pt.longitude
                altMeters = pt.altitude.coerceIn(1.1, 4.9)
                instantHr = if (pt.heartRate > 0) pt.heartRate else (145 + sin(s * 0.05) * 3.0).roundToInt().coerceIn(98, 195)
                instantSpm = if (pt.cadence > 0) pt.cadence else 168
                currentDistMeters = if (s < cumulativeDistances.size) cumulativeDistances[s] else totalDistMeters
                instantSpeedMps = if (s > 0) {
                    val dt = ((pt.time - points[s - 1].time) / 1000.0).coerceIn(0.5, 5.0)
                    (GeoUtils.distanceBetween(points[s - 1], pt) / dt).coerceIn(0.5, 12.0)
                } else {
                    speedMps
                }
            } else {
                val progressFraction = s.toDouble() / numSamples.toDouble()
                currentDistMeters = progressFraction * totalDistMeters

                lat = points.first().latitude
                lng = points.first().longitude

                if (points.size > 1) {
                    while (currentSegmentIdx < points.size - 2 && currentDistMeters > cumulativeDistances[currentSegmentIdx + 1]) {
                        currentSegmentIdx++
                    }
                    val segDist = segmentDistances[currentSegmentIdx]
                    val segStartDist = cumulativeDistances[currentSegmentIdx]
                    val fraction = if (segDist > 0.001) ((currentDistMeters - segStartDist) / segDist).coerceIn(0.0, 1.0) else 0.0
                    val p1 = points[currentSegmentIdx]
                    val p2 = points[currentSegmentIdx + 1]
                    lat = p1.latitude + (p2.latitude - p1.latitude) * fraction
                    lng = p1.longitude + (p2.longitude - p1.longitude) * fraction
                }

                // Elevation (Độ cao): STRICTLY 1.0m - 5.0m
                val macroHill = sin(progressFraction * Math.PI * 5.0) * 1.3
                val microHill = cos(progressFraction * Math.PI * 19.0) * 0.4
                val roadNoise = sin(s * 0.27) * 0.15
                val baseElevation = 2.8 // Center ~2.8m (within 1.0m - 5.0m rules)
                altMeters = (baseElevation + macroHill + microHill + roadNoise).coerceIn(1.1, 4.9)

                // Dynamic speed intervals (changes pace across cycles - warmup, surge, cruise, recovery)
                instantSpeedMps = (GeoUtils.getDynamicRunningSpeed(effectiveSpeedKmh, s.toLong(), true) / 3.6).coerceIn(1.0, 7.5)

                // Dynamic Heart Rate with wide physiological swing (coupled to pace surges + multi-frequency waves)
                val warmupProgress = (s.toDouble() / warmupDurationSec).coerceIn(0.0, 1.0)
                val baseHr = warmupStartBpm + (targetCruiseBpm - warmupStartBpm) * sin(warmupProgress * (Math.PI / 2.0))
                val paceSurgeRatio = (instantSpeedMps - speedMps) / speedMps.coerceAtLeast(0.5)
                val speedHrImpact = paceSurgeRatio * 18.0 // +18 BPM on surges, -12 BPM on recovery dips
                val multiWaveHrv = sin(s * 0.045) * 7.5 + cos(s * 0.018) * 5.5 + sin(s * 0.22) * 3.0
                instantHr = (baseHr + speedHrImpact + multiWaveHrv).roundToInt().coerceIn(98, 195)

                // Cadence fluctuates naturally with speed
                val cadenceOscillation = sin(s * 0.035) * 3.2 + cos(s * 0.09) * 1.8 + paceSurgeRatio * 8.0
                instantSpm = (baseRunningSpm + cadenceOscillation).roundToInt().coerceIn(120, 205)
            }

            val altScaled = ((altMeters + 500.0) * 5.0).roundToInt().coerceIn(0, 65534)
            val fitCadenceRpm = (instantSpm / 2.0).roundToInt().coerceIn(40, 110)
            val distScaled = (currentDistMeters * 100.0).roundToInt().coerceAtLeast(0)
            val speedScaled = (instantSpeedMps * 1000.0).roundToInt().coerceIn(0, 65534)

            hrSum += instantHr
            hrCount++
            if (instantHr > maxHr) maxHr = instantHr

            spmSum += instantSpm
            spmCount++

            speedSumMps += instantSpeedMps
            if (instantSpeedMps > maxSpeedMps) maxSpeedMps = instantSpeedMps

            // Write 23-byte record message: 1 (header 0x02) + 4 (timestamp) + 4 (lat) + 4 (lng) + 2 (alt) + 1 (hr) + 1 (cadence) + 4 (dist) + 2 (speed)
            val recordData = ByteBuffer.allocate(1 + 4 + 4 + 4 + 2 + 1 + 1 + 4 + 2).order(ByteOrder.LITTLE_ENDIAN).apply {
                put(0x02.toByte())                 // Header: Data msg, local 2
                putInt(fitTime.toInt())            // Timestamp (FIT epoch)
                putInt(toSemicircles(lat))         // Latitude
                putInt(toSemicircles(lng))         // Longitude
                putShort(altScaled.toShort())      // Altitude (strictly 1.1m - 4.9m)
                put(instantHr.toByte())            // Heart Rate (BPM)
                put(fitCadenceRpm.toByte())        // Cadence (single-leg RPM -> Garmin displays RPM*2 SPM!)
                putInt(distScaled)                 // Field 5: Distance (meters * 100)
                putShort(speedScaled.toShort())    // Field 6: Speed (m/s * 1000)
            }.array()
            stream.write(recordData)
        }

        // ==========================================
        // 4. EVENT STOP MESSAGE (Local Mesg 1)
        // ==========================================
        val eventStopData = ByteBuffer.allocate(1 + 4 + 1 + 1).order(ByteOrder.LITTLE_ENDIAN).apply {
            put(0x01.toByte())        // Header: Data msg, local 1
            putInt(endFitTime.toInt()) // Timestamp
            put(0x00.toByte())        // Event: 0 (Timer)
            put(0x09.toByte())        // Event Type: 9 (Stop All)
        }.array()
        stream.write(eventStopData)

        val durationSeconds = ((endTimeMillis - startTimeMillis) / 1000.0).coerceAtLeast(1.0)
        val timeScaled = (durationSeconds * 1000.0).roundToInt()
        val distScaled = (totalDistMeters * 100.0).roundToInt()
        val avgHr = if (hrCount > 0) (hrSum / hrCount).toInt() else 145
        val avgSpm = if (spmCount > 0) (spmSum / spmCount).toInt() else 168
        val avgSpeedMps = if (numSamples > 0) speedSumMps / numSamples else speedMps
        val avgSpeedScaled = (avgSpeedMps * 1000.0).roundToInt().coerceIn(0, 65534)
        val maxSpeedScaled = (maxSpeedMps.coerceAtLeast(avgSpeedMps) * 1000.0).roundToInt().coerceIn(0, 65534)

        // Garmin Connect also multiplies avg_cadence by 2 for running activities!
        val avgCadenceRpm = (avgSpm / 2.0).roundToInt().coerceIn(40, 110)
        val estimatedCalories = ((durationSeconds / 60.0) * 11.2).roundToInt().coerceIn(1, 9999)

        // ==========================================
        // 5. LAP MESSAGE (Local Mesg 3) - Global Msg 19 (MANDATORY for Strava / Garmin)
        // ==========================================
        val lapDef = byteArrayOf(
            0x43.toByte(), 0x00, 0x00,
            0x13, 0x00, // Global Msg 19 (lap)
            0x0D,       // 13 fields
            0xFD.toByte(), 0x04, 0x86.toByte(), // Field 253: timestamp
            0x02, 0x04, 0x86.toByte(),          // Field 2: start_time
            0x07, 0x04, 0x86.toByte(),          // Field 7: total_elapsed_time
            0x08, 0x04, 0x86.toByte(),          // Field 8: total_timer_time
            0x09, 0x04, 0x86.toByte(),          // Field 9: total_distance
            0x0B, 0x02, 0x84.toByte(),          // Field 11: total_calories
            0x0D, 0x02, 0x84.toByte(),          // Field 13: avg_speed (uint16, scale 1000, m/s)
            0x0E, 0x02, 0x84.toByte(),          // Field 14: max_speed (uint16, scale 1000, m/s)
            0x0F, 0x01, 0x02,                   // Field 15: avg_heart_rate
            0x10, 0x01, 0x02,                   // Field 16: max_heart_rate
            0x11, 0x01, 0x02,                   // Field 17: avg_cadence (single-leg RPM)
            0x19, 0x01, 0x00,                   // Field 25: sport (1 = Running)
            0x27, 0x01, 0x00                    // Field 39: sub_sport (0 = Generic)
        )
        stream.write(lapDef)

        val lapData = ByteBuffer.allocate(1 + 4 + 4 + 4 + 4 + 4 + 2 + 2 + 2 + 1 + 1 + 1 + 1 + 1).order(ByteOrder.LITTLE_ENDIAN).apply {
            put(0x03.toByte())                // Header: Data msg, local 3
            putInt(endFitTime.toInt())        // Timestamp
            putInt(startFitTime.toInt())      // Start time
            putInt(timeScaled)                // Total elapsed time (ms)
            putInt(timeScaled)                // Total timer time (ms)
            putInt(distScaled)                // Total distance (meters * 100)
            putShort(estimatedCalories.toShort()) // Total calories
            putShort(avgSpeedScaled.toShort()) // Avg speed (m/s * 1000)
            putShort(maxSpeedScaled.toShort()) // Max speed (m/s * 1000)
            put(avgHr.toByte())               // Avg heart rate
            put(maxHr.coerceAtLeast(avgHr).toByte()) // Max heart rate
            put(avgCadenceRpm.toByte())       // Avg cadence (single-leg RPM -> displayed as avgSpm)
            put(0x01.toByte())                // Sport: 1 = Running
            put(0x00.toByte())                // Sub-sport: 0 = Generic
        }.array()
        stream.write(lapData)

        // ==========================================
        // 6. SESSION SUMMARY MESSAGE (Local Mesg 4) - Global Msg 18
        // ==========================================
        val sessionDef = byteArrayOf(
            0x44.toByte(), 0x00, 0x00,
            0x12, 0x00, // Global Msg 18 (session)
            0x0E,       // 14 fields
            0xFD.toByte(), 0x04, 0x86.toByte(), // Field 253: timestamp
            0x02, 0x04, 0x86.toByte(),          // Field 2: start_time
            0x05, 0x01, 0x00,                   // Field 5: sport (enum, 1 = Running)
            0x06, 0x01, 0x00,                   // Field 6: sub_sport (enum, 0 = Generic)
            0x07, 0x04, 0x86.toByte(),          // Field 7: total_elapsed_time
            0x08, 0x04, 0x86.toByte(),          // Field 8: total_timer_time
            0x09, 0x04, 0x86.toByte(),          // Field 9: total_distance
            0x0B, 0x02, 0x84.toByte(),          // Field 11: total_calories
            0x0E, 0x02, 0x84.toByte(),          // Field 14: avg_speed (uint16, scale 1000, m/s)
            0x0F, 0x02, 0x84.toByte(),          // Field 15: max_speed (uint16, scale 1000, m/s)
            0x10, 0x01, 0x02,                   // Field 16: avg_heart_rate (uint8)
            0x11, 0x01, 0x02,                   // Field 17: max_heart_rate (uint8)
            0x12, 0x01, 0x02,                   // Field 18: avg_cadence (uint8, single-leg RPM)
            0x14, 0x02, 0x84.toByte()           // Field 20: avg_power (uint16, 2 bytes, 0x84 - standard FIT size!)
        )
        stream.write(sessionDef)

        val sessionData = ByteBuffer.allocate(1 + 4 + 4 + 1 + 1 + 4 + 4 + 4 + 2 + 2 + 2 + 1 + 1 + 1 + 2).order(ByteOrder.LITTLE_ENDIAN).apply {
            put(0x04.toByte())                // Header: Data msg, local 4
            putInt(endFitTime.toInt())        // Timestamp
            putInt(startFitTime.toInt())      // Start time
            put(0x01.toByte())                // Sport: 1 = Running
            put(0x00.toByte())                // Sub-sport: 0 = Generic
            putInt(timeScaled)                // Total elapsed time (ms)
            putInt(timeScaled)                // Total timer time (ms)
            putInt(distScaled)                // Total distance (meters * 100)
            putShort(estimatedCalories.toShort()) // Total calories
            putShort(avgSpeedScaled.toShort()) // Avg speed (m/s * 1000)
            putShort(maxSpeedScaled.toShort()) // Max speed (m/s * 1000)
            put(avgHr.toByte())               // Avg heart rate
            put(maxHr.coerceAtLeast(avgHr).toByte()) // Max heart rate
            put(avgCadenceRpm.toByte())       // Avg cadence (single-leg RPM -> displayed as avgSpm)
            putShort(0.toShort())             // avg_power: 0 W (uint16, 2 bytes)
        }.array()
        stream.write(sessionData)

        // ==========================================
        // 7. ACTIVITY MESSAGE (Local Mesg 5) - Global Msg 34 (REQUIRED by Strava API Ingestion)
        // ==========================================
        val activityDef = byteArrayOf(
            0x45.toByte(), 0x00, 0x00,
            0x22, 0x00, // Global Msg 34 (activity)
            0x06,       // 6 fields
            0xFD.toByte(), 0x04, 0x86.toByte(), // Field 253: timestamp
            0x00, 0x04, 0x86.toByte(),          // Field 0: total_timer_time
            0x01, 0x02, 0x84.toByte(),          // Field 1: num_sessions
            0x02, 0x01, 0x00,                   // Field 2: type (0 = Generic)
            0x03, 0x01, 0x00,                   // Field 3: event (26 = Activity)
            0x04, 0x01, 0x00                    // Field 4: event_type (1 = Stop)
        )
        stream.write(activityDef)

        val activityData = ByteBuffer.allocate(1 + 4 + 4 + 2 + 1 + 1 + 1).order(ByteOrder.LITTLE_ENDIAN).apply {
            put(0x05.toByte())          // Header: Data msg, local 5
            putInt(endFitTime.toInt())  // Timestamp
            putInt(timeScaled)          // Total timer time (ms)
            putShort(1.toShort())        // Num sessions: 1
            put(0x00.toByte())          // Type: 0 (Manual / Auto)
            put(0x1A.toByte())          // Event: 26 (Activity)
            put(0x01.toByte())          // Event Type: 1 (Stop)
        }.array()
        stream.write(activityData)

        // ==========================================
        // 8. BUILD FINAL FIT BINARY WITH HEADER & CRC
        // ==========================================
        val dataBytes = stream.toByteArray()
        val dataSize = dataBytes.size

        // 14-Byte Standard FIT Header
        val headerBuffer = ByteBuffer.allocate(14).order(ByteOrder.LITTLE_ENDIAN)
        headerBuffer.put(14.toByte())       // Header Size: 14 bytes
        headerBuffer.put(0x20.toByte())     // Protocol Version: 2.0 (0x20)
        headerBuffer.putShort(2140.toShort()) // Profile Version: 21.40
        headerBuffer.putInt(dataSize)       // Data Size
        headerBuffer.put(".FIT".toByteArray(Charsets.US_ASCII)) // Data type signature

        // Header CRC (over bytes 0..11)
        val headerBytes12 = ByteArray(12)
        System.arraycopy(headerBuffer.array(), 0, headerBytes12, 0, 12)
        val headerCrc = calculateCrc(headerBytes12)
        headerBuffer.putShort(headerCrc.toShort())

        val finalOutput = ByteArrayOutputStream()
        finalOutput.write(headerBuffer.array())
        finalOutput.write(dataBytes)

        // File CRC over (Header + Data)
        val fullFileWithoutCrc = finalOutput.toByteArray()
        val fileCrc = calculateCrc(fullFileWithoutCrc)

        val fileCrcBytes = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(fileCrc.toShort())
            .array()
        finalOutput.write(fileCrcBytes)

        return finalOutput.toByteArray()
    }

    /**
     * Save FIT binary directly to a local file
     */
    fun writeFitToFile(
        file: File,
        points: List<GeoPoint>,
        customStartTimeMillis: Long? = null,
        speedKmh: Float = 12f,
        config: SimulationConfig? = null
    ) {
        val bytes = generateFitBinary(
            points = points,
            customStartTimeMillis = customStartTimeMillis,
            speedKmh = speedKmh,
            config = config
        )
        FileOutputStream(file).use { fos ->
            fos.write(bytes)
            fos.flush()
        }
    }
}
