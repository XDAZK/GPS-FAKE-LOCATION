package com.example.util

import com.example.data.model.GeoPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object GpxExporter {

    private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    /**
     * Build standard GPX 1.1 XML string from route coordinates with Garmin Heart Rate extension
     */
    fun exportToGpx(
        trackName: String,
        points: List<GeoPoint>,
        creator: String = "GeoSim Mock GPS"
    ): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""").append("\n")
        sb.append("""<gpx version="1.1" creator="$creator" xmlns="http://www.topografix.com/GPX/1/1" xmlns:gpxtpx="http://www.garmin.com/xmlschemas/TrackPointExtension/v1" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd http://www.garmin.com/xmlschemas/TrackPointExtension/v1 http://www.garmin.com/xmlschemas/TrackPointExtensionv1.xsd">""").append("\n")
        
        sb.append("  <metadata>\n")
        sb.append("    <name>").append(escapeXml(trackName)).append("</name>\n")
        sb.append("    <time>").append(isoDateFormat.format(Date())).append("</time>\n")
        sb.append("  </metadata>\n")

        // Waypoints for reference
        points.forEachIndexed { index, pt ->
            sb.append("  <wpt lat=\"${pt.latitude}\" lon=\"${pt.longitude}\">\n")
            sb.append("    <ele>${String.format(Locale.US, "%.1f", pt.altitude)}</ele>\n")
            sb.append("    <name>${escapeXml(if (pt.name.isNotEmpty()) pt.name else "WP ${index + 1}")}</name>\n")
            sb.append("  </wpt>\n")
        }

        // Track segment
        sb.append("  <trk>\n")
        sb.append("    <name>").append(escapeXml(trackName)).append("</name>\n")
        sb.append("    <trkseg>\n")

        var currentTime = System.currentTimeMillis() - (points.size * 5000L)
        points.forEach { pt ->
            sb.append("      <trkpt lat=\"${pt.latitude}\" lon=\"${pt.longitude}\">\n")
            sb.append("        <ele>${String.format(Locale.US, "%.1f", pt.altitude)}</ele>\n")
            sb.append("        <time>${isoDateFormat.format(Date(if (pt.time > 0) pt.time else currentTime))}</time>\n")
            if (pt.heartRate > 0 || pt.cadence > 0) {
                sb.append("        <extensions>\n")
                sb.append("          <gpxtpx:TrackPointExtension>\n")
                if (pt.heartRate > 0) {
                    sb.append("            <gpxtpx:hr>${pt.heartRate}</gpxtpx:hr>\n")
                }
                if (pt.cadence > 0) {
                    sb.append("            <gpxtpx:cad>${pt.cadence}</gpxtpx:cad>\n")
                }
                sb.append("          </gpxtpx:TrackPointExtension>\n")
                sb.append("        </extensions>\n")
            }
            sb.append("      </trkpt>\n")
            currentTime += 5000L
        }

        sb.append("    </trkseg>\n")
        sb.append("  </trk>\n")
        sb.append("</gpx>")

        return sb.toString()
    }

    /**
     * Parse a GPX XML string into a list of GeoPoints with heart rate
     */
    fun parseGpx(gpxContent: String): List<GeoPoint> {
        val points = mutableListOf<GeoPoint>()
        val trkptRegex = Regex("""<(?:trkpt|wpt)\s+lat=["']([-0-9.]+)["']\s+lon=["']([-0-9.]+)["'][^>]*>(.*?)</(?:trkpt|wpt)>""", RegexOption.DOT_MATCHES_ALL)
        val trkptSelfClosingRegex = Regex("""<(?:trkpt|wpt)\s+lat=["']([-0-9.]+)["']\s+lon=["']([-0-9.]+)["']\s*/>""")
        val eleRegex = Regex("""<ele>([-0-9.]+)</ele>""")
        val nameRegex = Regex("""<name>(.*?)</name>""")
        val hrRegex = Regex("""<(?:gpxtpx:)?hr>(\d+)</(?:gpxtpx:)?hr>""")

        for (match in trkptRegex.findAll(gpxContent)) {
            val lat = match.groupValues[1].toDoubleOrNull() ?: continue
            val lon = match.groupValues[2].toDoubleOrNull() ?: continue
            val body = match.groupValues[3]
            val ele = eleRegex.find(body)?.groupValues?.get(1)?.toDoubleOrNull() ?: 15.0
            val name = nameRegex.find(body)?.groupValues?.get(1) ?: ""
            val hr = hrRegex.find(body)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            points.add(GeoPoint(latitude = lat, longitude = lon, altitude = ele, name = name, heartRate = hr))
        }

        if (points.isEmpty()) {
            for (match in trkptSelfClosingRegex.findAll(gpxContent)) {
                val lat = match.groupValues[1].toDoubleOrNull() ?: continue
                val lon = match.groupValues[2].toDoubleOrNull() ?: continue
                points.add(GeoPoint(latitude = lat, longitude = lon))
            }
        }

        return points
    }

    private fun escapeXml(str: String): String {
        return str.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
