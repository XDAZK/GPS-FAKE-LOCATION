package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditLocation
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.model.GeoPoint
import com.example.data.model.SimulationStatus
import com.example.ui.MainViewModel
import com.example.ui.components.CircularJoystickView
import com.example.ui.components.DevModeBanner
import com.example.ui.components.MapWebView
import com.example.ui.components.SimulationSettingsSheet
import com.example.util.GeoUtils
import kotlin.math.cos
import kotlin.math.sin

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

    var showSettingsSheet by remember { mutableStateOf(false) }
    var showCoordinatesDialog by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var showJoystick by remember { mutableStateOf(false) }
    var isPinMode by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        // Interactive OpenStreetMap Primary Viewer
        MapWebView(
            currentPoint = simulationProgress.currentPoint ?: selectedPoint,
            waypoints = waypoints,
            isMocking = simulationProgress.isMockActive,
            isPinMode = isPinMode,
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

        // Top Overlay: Dev Mode Warning Banner & Search/Control Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 10.dp)
        ) {
            DevModeBanner(
                isDevModeEnabled = isDevModeEnabled,
                isMockPermitted = isMockPermitted,
                onRefreshCheck = { viewModel.refreshDevStatus() }
            )

            // Location Bar
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
                            fontSize = 12.sp
                        )
                    }

                    // Search Button
                    IconButton(
                        onClick = { showSearchDialog = true },
                        modifier = Modifier.testTag("search_location_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Tìm kiếm",
                            tint = Color(0xFF38BDF8)
                        )
                    }

                    // Coordinates input Button
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
                            tint = Color(0xFF34D399)
                        )
                    }
                }
            }

            // Pin Mode Active Info Banner
            AnimatedVisibility(visible = isPinMode) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xF01E3A8A)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Text("📍", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Chế độ ghim lộ trình (${waypoints.size} điểm)",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "Chạm bản đồ để ghim điểm 1, 2, 3... (Chạm vào điểm để xóa)",
                                    color = Color(0xFFBAE6FD),
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (waypoints.size >= 2) {
                                Button(
                                    onClick = { viewModel.startRouteSimulation() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("Chạy ngay", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            if (waypoints.isNotEmpty()) {
                                TextButton(
                                    onClick = { viewModel.clearWaypoints() },
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("Xóa ghim", color = Color(0xFFFCA5A5), fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Live Simulation HUD Card (Visible when running or resting)
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

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Live Simulated Heart Rate Monitor
                            Text(
                                text = "❤️ ${simulationProgress.currentHeartRateBpm} BPM",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFB7185),
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${String.format(java.util.Locale.US, "%.1f", simulationProgress.currentSpeedKmh)} km/h",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF38BDF8),
                                fontSize = 13.sp
                            )
                        }
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
                                text = "Còn: ${GeoUtils.formatDistance(simulationProgress.remainingDistanceMeters)} • ETA: ${GeoUtils.formatDuration(simulationProgress.remainingTimeSeconds)}",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                            Text(
                                text = "👟 ${simulationProgress.currentCadenceSpm} SPM (${simulationProgress.totalSteps} bước) • 🔥 ${String.format(java.util.Locale.US, "%.1f", simulationProgress.caloriesBurnedKcal)} kcal",
                                color = Color(0xFF38BDF8),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Row {
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
                                    contentDescription = "Dừng lại",
                                    tint = Color(0xFFEF4444)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Interactive On-Screen 360° Circular Joystick Controller
        AnimatedVisibility(
            visible = showJoystick,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = 90.dp)
        ) {
            var inAppSpeed by remember { mutableStateOf(12.0f) }

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xF50F172A)),
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF38BDF8)),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header with close
                    Row(
                        modifier = Modifier.width(200.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🕹️ Bánh đà 360°",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF38BDF8),
                            fontSize = 12.sp
                        )
                        IconButton(
                            onClick = { showJoystick = false },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Đóng", tint = Color.Gray, modifier = Modifier.size(16.dp))
                        }
                    }

                    // Live telemetry tag
                    Text(
                        text = "👟 ${simulationProgress.currentCadenceSpm} SPM • ${simulationProgress.totalSteps} bước\n🔥 ${simulationProgress.caloriesBurnedKcal.toInt()} kcal • ❤️ ${simulationProgress.currentHeartRateBpm} BPM",
                        color = Color(0xFFE2E8F0),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Step Haptic Shockwave Toggle Button
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (simulationConfig.enablePhysicalStepVibration) Color(0xFF059669) else Color(0xFF475569),
                        onClick = {
                            viewModel.updateConfig(
                                simulationConfig.copy(enablePhysicalStepVibration = !simulationConfig.enablePhysicalStepVibration)
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .height(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = if (simulationConfig.enablePhysicalStepVibration) "⚡ Tự đếm bước: BẬT" else "⚡ Tự đếm bước: TẮT",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Speed selection chips
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("🚶 5k" to 5f, "🏃 12k" to 12f, "🚴 25k" to 25f, "🚗 50k" to 50f).forEach { (lbl, spd) ->
                            val isSel = inAppSpeed == spd
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSel) Color(0xFF0284C7) else Color(0xFF1E293B),
                                onClick = { inAppSpeed = spd }
                            ) {
                                Text(
                                    text = lbl,
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Circular Joystick Analog Component
                    AndroidView(
                        factory = { ctx: android.content.Context ->
                            CircularJoystickView(ctx).apply {
                                onMoveListener = { angleDegrees: Float, power: Float ->
                                    val speedMps = (inAppSpeed * 1000f) / 3600f
                                    val stepDistMeters = (speedMps * 0.280f * power).coerceAtLeast(0.8f).toDouble()
                                    val rad = Math.toRadians(angleDegrees.toDouble())
                                    val deltaLat = stepDistMeters * cos(rad)
                                    val deltaLng = stepDistMeters * sin(rad)
                                    viewModel.nudgeLocation(deltaLat, deltaLng, inAppSpeed * power)
                                }
                            }
                        },
                        modifier = Modifier.size(170.dp)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Button to launch system overlay over other apps
                    TextButton(
                        onClick = { viewModel.toggleOverlayJoystick() },
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("Mở nổi trên app khác ↗", color = Color(0xFFFBBF24), fontSize = 10.sp)
                    }
                }
            }
        }

        // Right-side Floating Action Buttons
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 90.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.End
        ) {
            // Toggle Joystick Button
            FloatingActionButton(
                onClick = { showJoystick = !showJoystick },
                containerColor = if (showJoystick) Color(0xFF0284C7) else Color(0xFF1E293B),
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier
                    .size(44.dp)
                    .testTag("toggle_joystick_button")
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "Joystick",
                    modifier = Modifier.size(20.dp)
                )
            }

            // Floating Overlay Joystick over other apps
            FloatingActionButton(
                onClick = { viewModel.toggleOverlayJoystick() },
                containerColor = Color(0xFF0F172A),
                contentColor = Color(0xFFFBBF24),
                shape = CircleShape,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("toggle_overlay_joystick_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.SportsEsports,
                    contentDescription = "Bật cần điều khiển nổi trên màn hình"
                )
            }

            // Toggle Pin Mode Button
            FloatingActionButton(
                onClick = { isPinMode = !isPinMode },
                containerColor = if (isPinMode) Color(0xFF0284C7) else Color(0xFF0F172A),
                contentColor = if (isPinMode) Color.White else Color(0xFF38BDF8),
                shape = CircleShape,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("toggle_pin_mode_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.Route,
                    contentDescription = if (isPinMode) "Tắt chế độ ghim" else "Bật chế độ ghim điểm"
                )
            }

            // Quick Run Pinned Route Button
            if (waypoints.size >= 2 && simulationProgress.status == SimulationStatus.IDLE) {
                Button(
                    onClick = { viewModel.startRouteSimulation() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.testTag("start_pinned_route_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Chạy lộ trình (${waypoints.size})", fontWeight = FontWeight.Bold)
                }
            }

            // Main Action Button: Start / Stop Point Mock
            if (!simulationProgress.isMockActive) {
                Button(
                    onClick = { viewModel.startPointMock() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.testTag("start_mock_location_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Fake vị trí này", fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = { viewModel.stopMocking() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.testTag("stop_mock_location_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Dừng Fake", fontWeight = FontWeight.Bold)
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

    // Simulation Settings BottomSheet
    if (showSettingsSheet) {
        SimulationSettingsSheet(
            config = simulationConfig,
            onConfigChange = { viewModel.updateConfig(it) },
            onDismiss = { showSettingsSheet = false }
        )
    }
}
