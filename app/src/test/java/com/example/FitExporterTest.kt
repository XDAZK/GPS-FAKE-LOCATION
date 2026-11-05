package com.example

import com.example.data.model.GeoPoint
import com.example.util.FitExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FitExporterTest {

    @Test
    fun testGenerateFitBinary() {
        val points = listOf(
            GeoPoint(latitude = 21.028511, longitude = 105.854444, altitude = 2.5),
            GeoPoint(latitude = 21.029000, longitude = 105.855000, altitude = 3.0),
            GeoPoint(latitude = 21.030000, longitude = 105.856000, altitude = 3.5)
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
            GeoPoint(latitude = 21.028511, longitude = 105.854444, altitude = 2.5),
            GeoPoint(latitude = 21.030000, longitude = 105.856000, altitude = 3.0)
        )
        val customStartTime = 1700000000000L // Specific epoch
        val bytes = FitExporter.generateFitBinary(
            points = points,
            customStartTimeMillis = customStartTime,
            speedKmh = 12f
        )
        assertTrue(bytes.size > 20)
    }

    @Test
    fun testElevationStrictlyUnder5MetersAndRealisticCadence() {
        val points = listOf(
            GeoPoint(latitude = 21.028511, longitude = 105.854444),
            GeoPoint(latitude = 21.038511, longitude = 105.864444)
        )
        val bytes = FitExporter.generateFitBinary(
            points = points,
            speedKmh = 12f
        )
        assertTrue(bytes.size > 100)

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        
        // Find the record definition (0x42)
        var recordDataStart = -1
        for (i in 14 until bytes.size - 30) {
            if (bytes[i] == 0x42.toByte() && bytes[i + 3] == 0x14.toByte()) { // Global msg 20 (record)
                // Record definition is 27 bytes long: header (1) + reserved (2) + global id (2) + num fields (1) + 7 fields * 3 bytes (21) = 27
                recordDataStart = i + 27
                break
            }
        }
        assertTrue("Record definition must be found", recordDataStart > 0)

        var recordCount = 0
        var offset = recordDataStart
        // Read records sequentially while header is 0x02
        while (offset + 21 <= bytes.size && bytes[offset] == 0x02.toByte()) {
            val altScaled = buffer.getShort(offset + 13).toInt() and 0xFFFF
            val altMeters = (altScaled / 5.0) - 500.0
            assertTrue("Altitude must be >= 0.0m, was $altMeters", altMeters >= 0.0)
            assertTrue("Altitude must be <= 5.0m, was $altMeters", altMeters <= 5.0)

            val hr = bytes[offset + 15].toInt() and 0xFF
            assertTrue("Heart rate must be realistic, was $hr", hr in 90..210)

            val cadRpm = bytes[offset + 16].toInt() and 0xFF
            // Single-leg RPM must be around 60-100 so Garmin displays SPM = RPM * 2 = 120-200
            assertTrue("Cadence RPM must be around 60-100 (for 120-200 SPM), was $cadRpm", cadRpm in 50..105)

            recordCount++
            offset += 21
        }
        assertTrue("Must generate high density records, was $recordCount", recordCount >= 30)
    }
}
