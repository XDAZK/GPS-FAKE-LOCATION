package com.example.ui.components

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.model.GeoPoint

class MapBridge(
    private val onMapClick: (Double, Double) -> Unit,
    private val onAddWaypoint: (Double, Double) -> Unit,
    private val onRemoveWaypoint: (Int) -> Unit,
    private val onMapLoaded: () -> Unit
) {
    @JavascriptInterface
    fun onLocationSelected(lat: Double, lng: Double) {
        Handler(Looper.getMainLooper()).post {
            onMapClick(lat, lng)
        }
    }

    @JavascriptInterface
    fun onWaypointAdded(lat: Double, lng: Double) {
        Handler(Looper.getMainLooper()).post {
            onAddWaypoint(lat, lng)
        }
    }

    @JavascriptInterface
    fun onWaypointRemoved(index: Int) {
        Handler(Looper.getMainLooper()).post {
            onRemoveWaypoint(index)
        }
    }

    @JavascriptInterface
    fun onMapReady() {
        Handler(Looper.getMainLooper()).post {
            onMapLoaded()
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MapWebView(
    currentPoint: GeoPoint?,
    waypoints: List<GeoPoint>,
    isMocking: Boolean,
    isPinMode: Boolean,
    onMapClick: (Double, Double) -> Unit,
    onAddWaypoint: (Double, Double) -> Unit,
    onRemoveWaypoint: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isMapInitialized by remember { mutableStateOf(false) }

    val bridge = remember(onMapClick, onAddWaypoint, onRemoveWaypoint) {
        MapBridge(
            onMapClick = onMapClick,
            onAddWaypoint = onAddWaypoint,
            onRemoveWaypoint = onRemoveWaypoint,
            onMapLoaded = { isMapInitialized = true }
        )
    }

    var webViewRef: WebView? = remember { null }

    // Read bundled Leaflet CSS & JS from local assets for 100% offline-ready reliability
    val leafletCss = remember {
        try {
            context.assets.open("leaflet/leaflet.css").bufferedReader().use { it.readText() }
        } catch (_: Exception) { "" }
    }
    val leafletJs = remember {
        try {
            context.assets.open("leaflet/leaflet.js").bufferedReader().use { it.readText() }
        } catch (_: Exception) { "" }
    }

    val htmlContent = remember(leafletCss, leafletJs) {
        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8" />
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
            <style>
                $leafletCss
            </style>
            <style>
                html, body {
                    height: 100%;
                    width: 100%;
                    margin: 0;
                    padding: 0;
                    background: #0B132B;
                    overflow: hidden;
                    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                }
                #map {
                    position: absolute;
                    top: 0;
                    left: 0;
                    right: 0;
                    bottom: 0;
                    width: 100%;
                    height: 100%;
                    background: #0F172A;
                    z-index: 1;
                }
                .custom-marker {
                    background-color: #06B6D4;
                    border: 3px solid #FFFFFF;
                    border-radius: 50%;
                    width: 24px;
                    height: 24px;
                    box-shadow: 0 0 14px rgba(6, 182, 212, 0.9);
                }
                .mock-active-marker {
                    background-color: #10B981;
                    border: 3px solid #FFFFFF;
                    border-radius: 50%;
                    width: 28px;
                    height: 28px;
                    box-shadow: 0 0 18px rgba(16, 185, 129, 1);
                    animation: pulse 1.5s infinite;
                }
                @keyframes pulse {
                    0% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(16, 185, 129, 0.7); }
                    70% { transform: scale(1.15); box-shadow: 0 0 0 14px rgba(16, 185, 129, 0); }
                    100% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(16, 185, 129, 0); }
                }
                .wp-badge {
                    background: #2563EB;
                    color: white;
                    font-size: 11px;
                    font-weight: bold;
                    border-radius: 14px;
                    padding: 4px 8px;
                    border: 2px solid white;
                    box-shadow: 0 2px 8px rgba(0,0,0,0.6);
                    text-align: center;
                    cursor: pointer;
                }
                .leaflet-control-layers {
                    background: rgba(15, 23, 42, 0.95) !important;
                    color: #FFFFFF !important;
                    border-radius: 12px !important;
                    border: 1px solid rgba(56, 189, 248, 0.4) !important;
                    font-size: 12px !important;
                    box-shadow: 0 4px 12px rgba(0,0,0,0.5) !important;
                }
                .leaflet-control-layers-base label {
                    margin-bottom: 4px;
                    display: block;
                    cursor: pointer;
                }
                .leaflet-bar a {
                    background-color: #0F172A !important;
                    color: #FFFFFF !important;
                    border-color: #334155 !important;
                }
                .leaflet-bar a:hover {
                    background-color: #1E293B !important;
                    color: #38BDF8 !important;
                }
                #pin-mode-banner {
                    display: none;
                    position: absolute;
                    top: 10px;
                    left: 50%;
                    transform: translateX(-50%);
                    background: rgba(2, 132, 199, 0.95);
                    color: white;
                    padding: 6px 16px;
                    border-radius: 20px;
                    font-size: 12px;
                    font-weight: bold;
                    z-index: 1000;
                    box-shadow: 0 4px 12px rgba(0,0,0,0.4);
                    pointer-events: none;
                }
            </style>
            <script>
                $leafletJs
            </script>
        </head>
        <body>
            <div id="pin-mode-banner">📍 Chạm bản đồ để ghim điểm</div>
            <div id="map"></div>

            <script>
                var map = null;
                var isPinModeActive = false;
                var currentMarker = null;
                var waypointMarkers = [];
                var routePolyline = null;
                var currentLat = 21.028511;
                var currentLng = 105.854444;

                function initLeafletMap() {
                    if (map !== null) return;
                    if (typeof L === 'undefined') {
                        console.error("Leaflet not available");
                        return;
                    }

                    try {
                        map = L.map('map', {
                            zoomControl: true,
                            attributionControl: false
                        }).setView([currentLat, currentLng], 15);

                        // 1. Google Maps Roadmap (100% reliable global raster, loads everywhere)
                        var googleRoadmap = L.tileLayer('https://mt1.google.com/vt/lyrs=m&x={x}&y={y}&z={z}', {
                            maxZoom: 20
                        }).addTo(map);

                        // 2. OpenStreetMap Voyager (CartoDB clean raster)
                        var cartoVoyager = L.tileLayer('https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png', {
                            maxZoom: 19,
                            subdomains: 'abcd'
                        });

                        // 3. OpenStreetMap Chuẩn (OSM standard)
                        var osmStandard = L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
                            maxZoom: 19
                        });

                        // 4. Google Maps Vệ tinh (Satellite Hybrid)
                        var googleHybrid = L.tileLayer('https://mt1.google.com/vt/lyrs=y&x={x}&y={y}&z={z}', {
                            maxZoom: 20
                        });

                        // 5. Bản đồ Ban đêm (CartoDB Dark Matter)
                        var cartoDark = L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png', {
                            maxZoom: 19,
                            subdomains: 'abcd'
                        });

                        var baseMaps = {
                            "Google Maps (Khuyên dùng)": googleRoadmap,
                            "OpenStreetMap (Voyager)": cartoVoyager,
                            "OpenStreetMap Chuẩn": osmStandard,
                            "Google Vệ tinh": googleHybrid,
                            "Bản đồ Ban đêm": cartoDark
                        };
                        L.control.layers(baseMaps, null, { position: 'topright' }).addTo(map);

                        // Map click event
                        map.on('click', function(e) {
                            var clickLat = e.latlng.lat;
                            var clickLng = e.latlng.lng;

                            if (isPinModeActive) {
                                if (window.AndroidBridge) {
                                    window.AndroidBridge.onWaypointAdded(clickLat, clickLng);
                                }
                            } else {
                                updateCurrentLocation(clickLat, clickLng, false);
                                if (window.AndroidBridge) {
                                    window.AndroidBridge.onLocationSelected(clickLat, clickLng);
                                }
                            }
                        });

                        // Auto size invalidation whenever map container resizes
                        if (window.ResizeObserver) {
                            new ResizeObserver(function() {
                                if (map) map.invalidateSize();
                            }).observe(document.getElementById('map'));
                        }

                        // Force refresh sizing across multiple frames
                        setTimeout(function() { if (map) map.invalidateSize(); }, 100);
                        setTimeout(function() { if (map) map.invalidateSize(); }, 400);
                        setTimeout(function() { if (map) map.invalidateSize(); }, 1000);

                        if (window.AndroidBridge) {
                            window.AndroidBridge.onMapReady();
                        }
                    } catch (err) {
                        console.error("Map init error:", err);
                    }
                }

                function setPinMode(active) {
                    isPinModeActive = active;
                    var banner = document.getElementById('pin-mode-banner');
                    if (banner) {
                        banner.style.display = active ? 'block' : 'none';
                    }
                    var mapEl = document.getElementById('map');
                    if (mapEl) {
                        mapEl.style.cursor = active ? 'crosshair' : 'grab';
                    }
                }

                function updateCurrentLocation(lat, lng, isMock) {
                    currentLat = lat;
                    currentLng = lng;
                    if (!map) return;

                    var iconClass = isMock ? 'mock-active-marker' : 'custom-marker';
                    var icon = L.divIcon({ className: iconClass, iconSize: [26, 26], iconAnchor: [13, 13] });
                    if (!currentMarker) {
                        currentMarker = L.marker([lat, lng], { icon: icon }).addTo(map);
                    } else {
                        currentMarker.setLatLng([lat, lng]);
                        currentMarker.setIcon(icon);
                    }
                }

                function centerMap(lat, lng, zoom) {
                    currentLat = lat;
                    currentLng = lng;
                    if (map) {
                        map.setView([lat, lng], zoom || map.getZoom());
                        map.invalidateSize();
                    }
                }

                function setWaypoints(pointsJson) {
                    if (!map) return;
                    waypointMarkers.forEach(function(m) { map.removeLayer(m); });
                    waypointMarkers = [];
                    if (routePolyline) { map.removeLayer(routePolyline); routePolyline = null; }

                    try {
                        var latlngs = [];
                        var points = JSON.parse(pointsJson);
                        points.forEach(function(p, idx) {
                            var latlng = [p.lat, p.lng];
                            latlngs.push(latlng);
                            var badgeIcon = L.divIcon({
                                className: 'wp-badge',
                                html: '' + (idx + 1),
                                iconSize: [26, 26],
                                iconAnchor: [13, 13]
                            });
                            var marker = L.marker(latlng, { icon: badgeIcon }).addTo(map);
                            marker.on('click', function(e) {
                                L.DomEvent.stopPropagation(e);
                                if (window.AndroidBridge) {
                                    window.AndroidBridge.onWaypointRemoved(idx);
                                }
                            });
                            waypointMarkers.push(marker);
                        });

                        if (latlngs.length > 1) {
                            routePolyline = L.polyline(latlngs, {
                                color: '#06B6D4',
                                weight: 5,
                                opacity: 0.9,
                                dashArray: '8, 8'
                            }).addTo(map);
                        }
                    } catch (e) {
                        console.error("setWaypoints error", e);
                    }
                }

                window.addEventListener('resize', function() { if (map) map.invalidateSize(); });
                document.addEventListener('DOMContentLoaded', initLeafletMap);
                window.addEventListener('load', initLeafletMap);
                initLeafletMap();
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    AndroidView(
        factory = { ctx ->
            object : WebView(ctx) {
                override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
                    super.onSizeChanged(w, h, ow, oh)
                    if (w > 0 && h > 0) {
                        post {
                            evaluateJavascript("if (window.map) { window.map.invalidateSize(); }", null)
                        }
                    }
                }
            }.apply {
                webViewRef = this
                @Suppress("DEPRECATION")
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    allowContentAccess = true
                    allowFileAccess = true
                    allowFileAccessFromFileURLs = true
                    allowUniversalAccessFromFileURLs = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    userAgentString = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0 Mobile Safari/537.36 GeoSim/1.0"
                }
                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        view?.evaluateJavascript("initLeafletMap();", null)
                        currentPoint?.let { pt ->
                            view?.evaluateJavascript(
                                "updateCurrentLocation(${pt.latitude}, ${pt.longitude}, $isMocking); centerMap(${pt.latitude}, ${pt.longitude}, 15); setPinMode($isPinMode);",
                                null
                            )
                        }
                    }
                }
                addJavascriptInterface(bridge, "AndroidBridge")
                loadDataWithBaseURL("https://tile.openstreetmap.org/", htmlContent, "text/html", "UTF-8", null)
            }
        },
        update = { webView ->
            webViewRef = webView
        },
        modifier = modifier.fillMaxSize()
    )

    // Update location marker dynamically when currentPoint changes
    LaunchedEffect(currentPoint?.latitude, currentPoint?.longitude, isMocking, isMapInitialized) {
        currentPoint?.let { pt ->
            webViewRef?.evaluateJavascript(
                "updateCurrentLocation(${pt.latitude}, ${pt.longitude}, $isMocking);",
                null
            )
        }
    }

    // Toggle Pin Mode in JS
    LaunchedEffect(isPinMode, isMapInitialized) {
        webViewRef?.evaluateJavascript("setPinMode($isPinMode);", null)
    }

    // Update waypoints and polyline on map
    LaunchedEffect(waypoints, isMapInitialized) {
        val jsonArray = waypoints.joinToString(prefix = "[", postfix = "]") {
            """{"lat":${it.latitude},"lng":${it.longitude}}"""
        }
        webViewRef?.evaluateJavascript(
            "setWaypoints('$jsonArray');",
            null
        )
    }
}
