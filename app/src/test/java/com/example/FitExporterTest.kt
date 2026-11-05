package com.example

import com.example.data.model.GeoPoint
import com.example.util.FitExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FitExporterTest {

    @Test
    fun testGenerateFitBinary() {
        val points = listOf(
            GeoPoint(latitude = 21.028511, longitude = 105.854444, altitude = 15.0, heartRate = 142, cadence = 168),
            GeoPoint(latitude = 21.029000, longitude = 105.855000, altitude = 16.0, heartRate = 148, cadence = 172),
            GeoPoint(latitude = 21.030000, longitude = 105.856000, altitude = 17.5, heartRate = 155, cadence = 175)
        )

        val bytes = FitExporter.generateFitBinary(points)
        
        // FIT file minimum size check: Header (14 bytes) + msgs + CRC (2 bytes)
        assertTrue(bytes.size > 14 + 2)

        // Check 14-byte header
        assertEquals(14.toByte(), bytes[0]) // Header size
        assertEquals(0x20.toByte(), bytes[1]) // Protocol 2.0
        
        // Check ".FIT" signature at bytes 8..11
        val sig = String(bytes, 8, 4, Charsets.US_ASCII)
        assertEquals(".FIT", sig)

        // Verify CRC checksum calculation
        val dataPlusHeader = bytes.copyOfRange(0, bytes.size - 2)
        val calculatedCrc = FitExporter.calculateCrc(dataPlusHeader)
        val fileCrc = ((bytes[bytes.size - 1].toInt() and 0xFF) shl 8) or (bytes[bytes.size - 2].toInt() and 0xFF)
        assertEquals(calculatedCrc, fileCrc)
    }

    @Test
    fun testCustomStartTime() {
        val points = listOf(
            GeoPoint(latitude = 21.028511, longitude = 105.854444, altitude = 15.0),
            GeoPoint(latitude = 21.030000, longitude = 105.856000, altitude = 16.0)
        )
        val customStartTime = 1700000000000L // Specific epoch
        val bytes = FitExporter.generateFitBinary(
            points = points,
            customStartTimeMillis = customStartTime,
            speedKmh = 12f
        )
        assertTrue(bytes.size > 20)
    }
}
