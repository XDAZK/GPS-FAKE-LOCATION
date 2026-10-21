package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddLocation
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.EditLocation
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.GeoPoint
import com.example.data.model.SimulationStatus
import com.example.ui.MainViewModel
import com.example.ui.components.DevModeBanner
import com.example.ui.components.NativeOsmdroidMapView
import com.example.ui.components.SimulationSettingsSheet
import com.example.util.GeoUtils

@Composable
fun MapScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val selectedPoint by viewModel.selectedPoint.collectAsState()
    val waypoints by viewModel.waypoints.collectAsState()
    val simulationProgress by viewModel.simulationProgress.collectAsState()
    val simulationConfig by viewModel.simulationConfig.collectAsState()
    val isDevModeEnabled by viewModel.isDevModeEnabled.collectAsState()
    val isMockPermitted by viewModel.isMockPermitted.collectAsState()
    val isLocatingRealGps by viewModel.isLocatingRealGps.collectAsState()

    var showSettingsSheet by remember { mutableStateOf(false) }
    var showCoordinatesDialog by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var showSaveRouteDialog by remember { mutableStateOf(false) }
    var isRouteMode by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        // Native OpenStreetMap with Osmdroid
        NativeOsmdroidMapView(
            currentPoint = simulationProgress.currentPoint ?: selectedPoint,
            waypoints = waypoints,
            isMocking = simulationProgress.isMockActive,
            isPinMode = isRouteMode,
            onMapClick = { lat, lng ->
                viewModel.selectLocation(lat, lng)
            },
            onAddWaypoint = { lat, lng ->
                viewModel.addWaypoint(lat, lng)
            },
            onRemoveWaypoint = { idx ->
                viewModel.removeWaypoint(idx)
            }
        )

        // Top Area: Status Banner, Search & Coords
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 10.dp)
        ) {
            val isBatteryIgnored by viewModel.isBatteryOptimizationIgnored.collectAsState()
            DevModeBanner(
                isDevModeEnabled = isDevModeEnabled,
                isMockPermitted = isMockPermitted,
                onRefreshCheck = { viewModel.refreshDevStatus() },
                isBatteryOptimizationIgnored = isBatteryIgnored,
                onRequestIgnoreBatteryOptimization = { viewModel.requestIgnoreBatteryOptimization() }
            )

            // Location Bar Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xEE0F172A)),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (simulationProgress.isMockActive) Color(0xFFEF4444) else Color(0xFF10B981))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (simulationProgress.isMockActive) "ĐANG ẨN VỊ TRÍ THẬT" else "VỊ TRÍ GPS THẬT",
                                color = if (simulationProgress.isMockActive) Color(0xFFF87171) else Color(0xFF34D399),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = selectedPoint.name.ifEmpty { "Vị trí đã chọn" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1
                        )
                        Text(
                            text = String.format(
                                java.util.Locale.US,
                                "Lat: %.5f  •  Lng: %.5f",
                                selectedPoint.latitude,
                                selectedPoint.longitude
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF38BDF8),
                            fontSize = 11.sp
                        )
                    }

                    // Get Real Location Button
                    IconButton(
                        onClick = { viewModel.fetchRealLocation(moveToCenter = true, showFeedback = true) },
                        modifier = Modifier.testTag("get_real_location_button")
                    ) {
                        if (isLocatingRealGps) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color(0xFF10B981),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.GpsFixed,
                                contentDescription = "Lấy vị trí GPS thật",
                                tint = Color(0xFF10B981)
                            )
                        }
                    }

                    // Search Presets Button
                    IconButton(
                        onClick = { showSearchDialog = true },
                        modifier = Modifier.testTag("search_location_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Tìm kiếm nhanh",
                            tint = Color(0xFF38BDF8)
                        )
                    }

                    // Input Coordinates Directly Button
                    IconButton(
                        onClick = { showCoordinatesDialog = true },
                        modifier = Modifier.testTag("enter_coordinates_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.EditLocation,
                            contentDescription = "Nhập tọa độ",
                            tint = Color(0xFF38BDF8)
                        )
                    }

                    // Settings Button
                    IconButton(
                        onClick = { showSettingsSheet = true },
                        modifier = Modifier.testTag("open_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Cài đặt giả lập",
                            tint = Color(0xFF94A3B8)
                        )
                    }
                }
            }

            // Mode Selector: Single Point vs Route Mode
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xEE0F172A),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (!isRouteMode) Color(0xFF0284C7) else Color.Transparent,
                        onClick = { isRouteMode = false },
                        modifier = Modifier.height(30.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        ) {
                            Text(
                                text = "📍 1 Điểm cố định",
                                color = if (!isRouteMode) Color.White else Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                fontWeight = if (!isRouteMode) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isRouteMode) Color(0xFF0284C7) else Color.Transparent,
                        onClick = { isRouteMode = true },
                        modifier = Modifier.height(30.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        ) {
                            Text(
                                text = if (waypoints.isNotEmpty()) "🗺️ Lộ trình (${waypoints.size})" else "🗺️ Tạo Lộ trình",
                                color = if (isRouteMode) Color.White else Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                fontWeight = if (isRouteMode) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }

        // Route Management Panel (Appears when Route Mode is active)
        AnimatedVisibility(
            visible = isRouteMode && simulationProgress.status == SimulationStatus.IDLE,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 90.dp, start = 12.dp, end = 12.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xF50F172A)),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8)),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Header with instructions and quick buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Route,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Lộ trình: ${waypoints.size} điểm (${String.format(java.util.Locale.US, "%.1f km", GeoUtils.calculateTotalDistance(waypoints) / 1000.0)})",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            // Reverse order
                            if (waypoints.size >= 2) {
                                IconButton(
                                    onClick = { viewModel.reverseWaypoints() },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SwapVert,
                                        contentDescription = "Đảo chiều",
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            // Save Route
                            if (waypoints.size >= 2) {
                                IconButton(
                                    onClick = { showSaveRouteDialog = true },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Save,
                                        contentDescription = "Lưu lộ trình",
                                        tint = Color(0xFF34D399),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            // Clear all waypoints
                            if (waypoints.isNotEmpty()) {
                                IconButton(
                                    onClick = { viewModel.clearWaypoints() },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Xóa hết",
                                        tint = Color(0xFFF87171),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "💡 Chạm bất kỳ đâu trên bản đồ hoặc bấm '+ Thêm điểm tâm' để thêm mốc di chuyển.",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )

                    // Waypoints Horizontal Scroll List
                    if (waypoints.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            contentPadding = PaddingValues(vertical = 2.dp)
                        ) {
                            itemsIndexed(waypoints) { idx, wp ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF1E293B),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF475569))
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "${idx + 1}. ${wp.name}",
                                            color = Color(0xFFE2E8F0),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        IconButton(
                                            onClick = { viewModel.removeWaypoint(idx) },
                                            modifier = Modifier.size(18.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Xóa điểm này",
                                                tint = Color(0xFFFCA5A5),
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Speed selector shortcuts
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "🚶 5 km/h" to 5f,
                            "🏃 12 km/h" to 12f,
                            "🛵 30 km/h" to 30f,
                            "🚗 50 km/h" to 50f
                        ).forEach { (label, speed) ->
                            val isSelected = simulationConfig.speedKmh == speed
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) Color(0xFF0284C7) else Color(0xFF1E293B),
                                onClick = { viewModel.updateConfig(simulationConfig.copy(speedKmh = speed)) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Actions Row: Add Current Center vs Start Simulation
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Add center of map button
                        OutlinedButton(
                            onClick = {
                                viewModel.addWaypoint(selectedPoint.latitude, selectedPoint.longitude, selectedPoint.name)
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                        ) {
                            Icon(Icons.Default.AddLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("+ Thêm điểm tâm", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        // Start Route Button
                        Button(
                            onClick = { viewModel.startRouteSimulation() },
                            enabled = waypoints.size >= 2,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF10B981),
                                disabledContainerColor = Color(0xFF334155)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1.2f)
                                .height(42.dp)
                                .testTag("start_pinned_route_button")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Chạy lộ trình (${waypoints.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Live Simulation Running HUD (Active during route simulation)
        AnimatedVisibility(
            visible = simulationProgress.status != SimulationStatus.IDLE,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 90.dp, start = 12.dp, end = 12.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xF20F172A)),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (simulationProgress.status) {
                                            SimulationStatus.RUNNING -> Color(0xFF10B981)
                                            SimulationStatus.RESTING -> Color(0xFFFBBF24)
                                            SimulationStatus.PAUSED -> Color(0xFFEF4444)
                                            else -> Color.Gray
                                        }
                                    )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when (simulationProgress.status) {
                                    SimulationStatus.RUNNING -> "Đang di chuyển (${simulationProgress.progressPercent}%)"
                                    SimulationStatus.RESTING -> "Dừng nghỉ ngẫu nhiên: còn ${simulationProgress.pauseRemainingSeconds}s"
                                    SimulationStatus.PAUSED -> "Tạm dừng (${simulationProgress.progressPercent}%)"
                                    else -> "Sẵn sàng"
                                },
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 13.sp
                            )
                        }

                        Text(
                            text = "${String.format(java.util.Locale.US, "%.1f", simulationProgress.currentSpeedKmh)} km/h",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF38BDF8),
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Progress Bar
                    LinearProgressIndicator(
                        progress = { simulationProgress.progressPercent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = Color(0xFF06B6D4),
                        trackColor = Color(0xFF334155)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Còn lại: ${GeoUtils.formatDistance(simulationProgress.remainingDistanceMeters)}",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Thời gian ước tính: ${GeoUtils.formatDuration(simulationProgress.remainingTimeSeconds)}",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (simulationProgress.status == SimulationStatus.RUNNING || simulationProgress.status == SimulationStatus.RESTING) {
                                IconButton(
                                    onClick = { viewModel.pauseSimulation() },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Pause,
                                        contentDescription = "Tạm dừng",
                                        tint = Color(0xFFFBBF24)
                                    )
                                }
                            } else if (simulationProgress.status == SimulationStatus.PAUSED) {
                                IconButton(
                                    onClick = { viewModel.resumeSimulation() },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = "Tiếp tục",
                                        tint = Color(0xFF10B981)
                                    )
                                }
                            }
                            IconButton(
                                onClick = { viewModel.stopMocking() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.Stop,
                                    contentDescription = "Dừng và khôi phục vị trí thật",
                                    tint = Color(0xFFEF4444)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bottom Action Island: Single Point Mock & Real GPS Toggle
        if (!isRouteMode && simulationProgress.status == SimulationStatus.IDLE) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xF20F172A),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (simulationProgress.isMockActive) Color(0xFFEF4444) else Color(0xFF0284C7)
                ),
                shadowElevation = 10.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 90.dp, start = 16.dp, end = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!simulationProgress.isMockActive) {
                        // Start Spoofing Single Point
                        Button(
                            onClick = { viewModel.startPointMock() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.testTag("start_mock_location_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Bắt đầu Fake vị trí này", fontWeight = FontWeight.Bold)
                        }

                        // Locate Real GPS Button
                        OutlinedButton(
                            onClick = { viewModel.fetchRealLocation(moveToCenter = true, showFeedback = true) },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.testTag("fetch_real_gps_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Về vị trí thật", color = Color(0xFF10B981), fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        // Stop Spoofing & Immediately Return to Real Physical GPS Location
                        Button(
                            onClick = { viewModel.stopMocking() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.testTag("stop_mock_location_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("TẮT ẨN VỊ TRÍ (Về GPS thật)", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Coordinates Input Dialog
    if (showCoordinatesDialog) {
        var inputLat by remember { mutableStateOf(selectedPoint.latitude.toString()) }
        var inputLng by remember { mutableStateOf(selectedPoint.longitude.toString()) }
        var inputName by remember { mutableStateOf(selectedPoint.name) }

        AlertDialog(
            onDismissRequest = { showCoordinatesDialog = false },
            title = { Text("Nhập tọa độ trực tiếp", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = inputLat,
                        onValueChange = { inputLat = it },
                        label = { Text("Vĩ độ (Latitude)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = inputLng,
                        onValueChange = { inputLng = it },
                        label = { Text("Kinh độ (Longitude)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = inputName,
                        onValueChange = { inputName = it },
                        label = { Text("Tên địa điểm (tùy chọn)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val lat = inputLat.toDoubleOrNull()
                        val lng = inputLng.toDoubleOrNull()
                        if (lat != null && lng != null) {
                            viewModel.selectLocation(lat, lng, inputName)
                        }
                        showCoordinatesDialog = false
                    }
                ) {
                    Text("Chuyển đến")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCoordinatesDialog = false }) {
                    Text("Hủy")
                }
            }
        )
    }

    // Quick Preset Locations Dialog
    if (showSearchDialog) {
        val presets = listOf(
            GeoPoint(21.028511, 105.854444, name = "Hồ Hoàn Kiếm, Hà Nội"),
            GeoPoint(10.772023, 106.698375, name = "Chợ Bến Thành, TP.HCM"),
            GeoPoint(16.061099, 108.223000, name = "Cầu Rồng, Đà Nẵng"),
            GeoPoint(10.795240, 106.721833, name = "Landmark 81, TP.HCM"),
            GeoPoint(35.689487, 139.691706, name = "Shinjuku, Tokyo, Nhật Bản"),
            GeoPoint(48.858370, 2.294481, name = "Tháp Eiffel, Paris, Pháp"),
            GeoPoint(40.758896, -73.985130, name = "Times Square, New York, Mỹ"),
            GeoPoint(1.286389, 103.854722, name = "Marina Bay Sands, Singapore")
        )

        AlertDialog(
            onDismissRequest = { showSearchDialog = false },
            title = { Text("Chọn nhanh địa điểm nổi tiếng", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    presets.forEach { preset ->
                        Surface(
                            onClick = {
                                viewModel.selectLocation(preset.latitude, preset.longitude, preset.name)
                                showSearchDialog = false
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Transparent,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.MyLocation,
                                    contentDescription = null,
                                    tint = Color(0xFF0284C7),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(preset.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text(
                                        "${preset.latitude}, ${preset.longitude}",
                                        color = Color.Gray,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSearchDialog = false }) {
                    Text("Đóng")
                }
            }
        )
    }

    // Save Route Dialog
    if (showSaveRouteDialog) {
        var routeNameInput by remember {
            mutableStateOf("Lộ trình ${waypoints.size} điểm - ${java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}")
        }

        AlertDialog(
            onDismissRequest = { showSaveRouteDialog = false },
            title = { Text("Lưu lộ trình này", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "Nhập tên để lưu lại lộ trình gồm ${waypoints.size} điểm mốc:",
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = routeNameInput,
                        onValueChange = { routeNameInput = it },
                        label = { Text("Tên lộ trình") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (routeNameInput.isNotBlank()) {
                            viewModel.saveRoute(routeNameInput.trim())
                            showSaveRouteDialog = false
                        }
                    }
                ) {
                    Text("Lưu lại")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveRouteDialog = false }) {
                    Text("Hủy")
                }
            }
        )
    }

    // Simulation Settings BottomSheet
    if (showSettingsSheet) {
        SimulationSettingsSheet(
            config = simulationConfig,
            onConfigChange = { viewModel.updateConfig(it) },
            onDismiss = { showSettingsSheet = false }
        )
    }
}
