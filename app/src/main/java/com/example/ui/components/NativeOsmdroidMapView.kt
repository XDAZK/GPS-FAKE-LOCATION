package com.example.ui.components

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddLocation
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.data.model.GeoPoint
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/**
 * 100% Native OpenStreetMap Component powered by Osmdroid SDK
 * (Modeled after 0xfnzero/gps-locator implementation).
 *
 * Provides:
 * - Direct native map rendering (NO WebView, NO JavaScript crashes)
 * - Ultra-responsive smooth touch panning & pinch-to-zoom
 * - Center Pin target picker (Google Maps / Uber style)
 * - Tap-to-locate & Animated camera movement
 * - Interactive Waypoint polyline routing & markers
 * - GPS centering button & Zoom controls
 */
@Composable
fun NativeOsmdroidMapView(
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
    val lifecycleOwner = LocalLifecycleOwner.current

    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var centerLat by remember { mutableDoubleStateOf(currentPoint?.latitude ?: 21.028511) }
    var centerLng by remember { mutableDoubleStateOf(currentPoint?.longitude ?: 105.854444) }
    var currentTileSourceIndex by remember { mutableStateOf(0) }

    val tileSources = listOf(
        "Chuẩn (MAPNIK)" to TileSourceFactory.MAPNIK,
        "Vệ tinh / Địa hình (TOPO)" to TileSourceFactory.OpenTopo
    )

    // Manage lifecycle of MapView (onResume / onPause)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapViewRef?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapViewRef?.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapViewRef?.onDetach()
        }
    }

    // Camera animation when currentPoint changes externally (search or navigation)
    LaunchedEffect(currentPoint?.latitude, currentPoint?.longitude) {
        val pt = currentPoint ?: return@LaunchedEffect
        mapViewRef?.let { map ->
            val target = org.osmdroid.util.GeoPoint(pt.latitude, pt.longitude)
            val currentCenter = map.mapCenter
            val distLat = Math.abs(currentCenter.latitude - target.latitude)
            val distLng = Math.abs(currentCenter.longitude - target.longitude)
            if (distLat > 0.0001 || distLng > 0.0001) {
                map.controller.animateTo(target)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Native Osmdroid MapView wrapped in AndroidView
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(tileSources[currentTileSourceIndex].second)
                    setMultiTouchControls(true)
                    isTilesScaledToDpi = true
                    minZoomLevel = 3.0
                    maxZoomLevel = 20.0
                    controller.setZoom(16.0)

                    val initPt = currentPoint ?: GeoPoint(21.028511, 105.854444)
                    controller.setCenter(org.osmdroid.util.GeoPoint(initPt.latitude, initPt.longitude))

                    // Map scroll & zoom listener (updates center coordinates)
                    addMapListener(object : MapListener {
                        override fun onScroll(event: ScrollEvent?): Boolean {
                            centerLat = mapCenter.latitude
                            centerLng = mapCenter.longitude
                            return false
                        }

                        override fun onZoom(event: ZoomEvent?): Boolean {
                            centerLat = mapCenter.latitude
                            centerLng = mapCenter.longitude
                            return false
                        }
                    })

                    // Map tap listener
                    overlays.add(
                        MapEventsOverlay(object : MapEventsReceiver {
                            override fun singleTapConfirmedHelper(point: org.osmdroid.util.GeoPoint): Boolean {
                                if (isPinMode) {
                                    onAddWaypoint(point.latitude, point.longitude)
                                } else {
                                    controller.animateTo(point)
                                    centerLat = point.latitude
                                    centerLng = point.longitude
                                    onMapClick(point.latitude, point.longitude)
                                }
                                return true
                            }

                            override fun longPressHelper(point: org.osmdroid.util.GeoPoint): Boolean {
                                onAddWaypoint(point.latitude, point.longitude)
                                return true
                            }
                        })
                    )

                    mapViewRef = this
                }
            },
            update = { mapView ->
                // Update tile source if changed
                val desiredTileSource = tileSources[currentTileSourceIndex].second
                if (mapView.tileProvider.tileSource != desiredTileSource) {
                    mapView.setTileSource(desiredTileSource)
                }

                // Manage overlays (Polyline route and Waypoint Markers)
                // Filter out non-event overlays before re-adding
                val eventOverlays = mapView.overlays.filterIsInstance<MapEventsOverlay>()
                mapView.overlays.clear()
                mapView.overlays.addAll(eventOverlays)

                // 1. Polyline for Route
                if (waypoints.size >= 2) {
                    val polyline = Polyline(mapView).apply {
                        setPoints(waypoints.map { org.osmdroid.util.GeoPoint(it.latitude, it.longitude) })
                        outlinePaint.color = android.graphics.Color.parseColor("#0284C7")
                        outlinePaint.strokeWidth = 10f
                    }
                    mapView.overlays.add(polyline)
                }

                // 2. Waypoint Markers
                waypoints.forEachIndexed { index, wp ->
                    val marker = Marker(mapView).apply {
                        position = org.osmdroid.util.GeoPoint(wp.latitude, wp.longitude)
                        title = "${index + 1}. ${wp.name}"
                        snippet = "Chạm để xóa điểm mốc này"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        setOnMarkerClickListener { _, _ ->
                            onRemoveWaypoint(index)
                            true
                        }
                    }
                    mapView.overlays.add(marker)
                }

                // 3. Active Mocking Navigation Marker
                if (isMocking && currentPoint != null) {
                    val mockMarker = Marker(mapView).apply {
                        position = org.osmdroid.util.GeoPoint(currentPoint.latitude, currentPoint.longitude)
                        title = "🚀 Đang giả lập GPS: ${currentPoint.name}"
                        snippet = "Vị trí: ${String.format(java.util.Locale.US, "%.5f", currentPoint.latitude)}, ${String.format(java.util.Locale.US, "%.5f", currentPoint.longitude)}"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    }
                    mapView.overlays.add(mockMarker)
                }

                mapView.invalidate()
            },
            modifier = Modifier.fillMaxSize()
        )

        // Center Pin overlay (Uber / Google Maps style)
        // Shows when not in waypoint adding mode
        if (!isPinMode && !isMocking) {
            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_map_pin),
                    contentDescription = "Tâm bản đồ chọn vị trí",
                    modifier = Modifier
                        .size(52.dp)
                        .offset(y = (-26).dp)
                )
            }
        }

        // Top-left Map Tile Layer Switcher
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xDD0F172A),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 16.dp, top = 88.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                IconButton(
                    onClick = {
                        currentTileSourceIndex = (currentTileSourceIndex + 1) % tileSources.size
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = "Đổi lớp bản đồ",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = tileSources[currentTileSourceIndex].first.split(" ").first(),
                    color = Color(0xFFE2E8F0),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(end = 4.dp)
                )
            }
        }

        // Center Coordinate Indicator & Quick Select Button (at bottom center of map)
        if (!isMocking) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xF20F172A),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8)),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 96.dp)
                    .testTag("map_center_selector")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = null,
                        tint = Color(0xFFE11D48),
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        Text(
                            text = "${String.format(java.util.Locale.US, "%.5f", centerLat)}, ${String.format(java.util.Locale.US, "%.5f", centerLng)}",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isPinMode) "Chạm bản đồ để thả điểm mốc" else "Kéo bản đồ để chọn tọa độ tâm",
                            color = Color(0xFF94A3B8),
                            fontSize = 10.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF0284C7),
                        onClick = {
                            if (isPinMode) {
                                onAddWaypoint(centerLat, centerLng)
                            } else {
                                onMapClick(centerLat, centerLng)
                            }
                        },
                        modifier = Modifier.height(32.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp)
                        ) {
                            Icon(
                                imageVector = if (isPinMode) Icons.Default.AddLocation else Icons.Default.Check,
                                contentDescription = "Chọn",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isPinMode) "+ Mốc" else "Chọn điểm",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Right-hand Quick Map Controls (Zoom In, Zoom Out, Center GPS)
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Zoom In (+)
            FloatingActionButton(
                onClick = { mapViewRef?.controller?.zoomIn() },
                containerColor = Color(0xEE1E293B),
                contentColor = Color.White,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(4.dp),
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Phóng to", modifier = Modifier.size(20.dp))
            }

            // Zoom Out (-)
            FloatingActionButton(
                onClick = { mapViewRef?.controller?.zoomOut() },
                containerColor = Color(0xEE1E293B),
                contentColor = Color.White,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(4.dp),
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Thu nhỏ", modifier = Modifier.size(20.dp))
            }

            // Animate to Current Location / Selected Location
            FloatingActionButton(
                onClick = {
                    val target = currentPoint ?: GeoPoint(21.028511, 105.854444)
                    mapViewRef?.controller?.animateTo(org.osmdroid.util.GeoPoint(target.latitude, target.longitude))
                },
                containerColor = Color(0xEE0284C7),
                contentColor = Color.White,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(4.dp),
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "Về vị trí chọn", modifier = Modifier.size(20.dp))
            }
        }
    }
}
