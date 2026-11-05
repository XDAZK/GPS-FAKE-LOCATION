package com.example.ui.screens

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.GeoPoint
import com.example.data.model.SimulationStatus
import com.example.ui.MainViewModel
import com.example.ui.components.SimulationSettingsSheet
import com.example.util.GeoUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val waypoints by viewModel.waypoints.collectAsState()
    val simulationProgress by viewModel.simulationProgress.collectAsState()
    val simulationConfig by viewModel.simulationConfig.collectAsState()

    var showSaveRouteDialog by remember { mutableStateOf(false) }
    var showImportGpxDialog by remember { mutableStateOf(false) }
    var showExportFitDialog by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }

    val totalDistanceMeters = GeoUtils.calculateTotalDistance(waypoints)
    val totalTimeSeconds = if (simulationConfig.speedKmh > 0) {
        ((totalDistanceMeters / ((simulationConfig.speedKmh * 1000.0) / 3600.0))).toLong()
    } else 0L

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B132B))
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Screen Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Giả Lập Lộ Trình & GPX",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "${waypoints.size} điểm dừng • ${GeoUtils.formatDistance(totalDistanceMeters)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF38BDF8)
                )
            }

            IconButton(
                onClick = { showSettingsSheet = true },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0xFF1E293B))
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Cấu hình",
                    tint = Color(0xFF34D399)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Simulation Control Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Tốc độ: ${simulationConfig.speedKmh.toInt()} km/h",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF38BDF8)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            if (simulationConfig.enableHeartRateSimulation) {
                                Text(
                                    text = "• ❤️ ${simulationProgress.currentHeartRateBpm} BPM",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFB7185),
                                    fontSize = 13.sp
                                )
                            }
                        }
                        Text(
                            text = "Ước tính di chuyển: ${GeoUtils.formatDuration(totalTimeSeconds)} • ${simulationConfig.heartRateMode.title}",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Lặp (Loop)",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Switch(
                            checked = simulationConfig.loopRoute,
                            onCheckedChange = { viewModel.updateConfig(simulationConfig.copy(loopRoute = it)) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF38BDF8))
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Action Buttons: Start / Pause / Stop
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (simulationProgress.status == SimulationStatus.IDLE) {
                        Button(
                            onClick = { viewModel.startRouteSimulation() },
                            enabled = waypoints.size >= 2,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("start_route_simulation_button")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Bắt đầu Chạy", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        if (simulationProgress.status == SimulationStatus.RUNNING || simulationProgress.status == SimulationStatus.RESTING) {
                            Button(
                                onClick = { viewModel.pauseSimulation() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Pause, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Tạm dừng")
                            }
                        } else {
                            Button(
                                onClick = { viewModel.resumeSimulation() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Tiếp tục")
                            }
                        }

                        Button(
                            onClick = { viewModel.stopMocking() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Dừng lại")
                        }
                    }

                    IconButton(
                        onClick = { showSaveRouteDialog = true },
                        enabled = waypoints.isNotEmpty(),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF334155))
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "Lưu lộ trình", tint = Color.White)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Export / Import Workout Data (FIT & GPX)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = {
                    showExportFitDialog = true
                },
                colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                    containerColor = Color(0xFF065F46),
                    contentColor = Color(0xFFA7F3D0)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1.1f)
                    .testTag("export_fit_button")
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Xuất file FIT (SPM, BPM)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            FilledTonalButton(
                onClick = {
                    val gpxFile = viewModel.exportGpxFile()
                    if (gpxFile != null) {
                        shareWorkoutFile(context, gpxFile, "application/gpx+xml", "Chia sẻ file GPX")
                    } else {
                        shareGpxText(context, viewModel.exportGpxXml())
                    }
                },
                colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                    containerColor = Color(0xFF1E293B),
                    contentColor = Color(0xFF38BDF8)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(0.9f)
                    .testTag("export_gpx_button")
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Xuất GPX", fontSize = 11.sp)
            }

            IconButton(
                onClick = { showImportGpxDialog = true },
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1E293B))
                    .testTag("import_gpx_button")
            ) {
                Icon(Icons.Default.FileUpload, contentDescription = "Nhập GPX", tint = Color(0xFFE2E8F0))
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Sample Route Templates (if empty)
        if (waypoints.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Lộ trình Mẫu (Gợi ý nhanh)",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF38BDF8),
                        fontSize = 15.sp
                    )
                    Text(
                        text = "Chọn để nạp lộ trình có sẵn hoặc chạm trên Bản đồ để tự chọn điểm:",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )

                    SampleRouteButton(
                        title = "Vòng quanh Hồ Hoàn Kiếm, Hà Nội",
                        desc = "4 điểm dừng • 1.8 km",
                        onClick = {
                            val hanoiPoints = listOf(
                                GeoPoint(21.028511, 105.854444, name = "Đền Ngọc Sơn"),
                                GeoPoint(21.031200, 105.853300, name = "Phố Hàng Đào"),
                                GeoPoint(21.027000, 105.851000, name = "Nhà thờ Lớn"),
                                GeoPoint(21.025000, 105.855000, name = "Nhà hát Lớn")
                            )
                            hanoiPoints.forEach { viewModel.addWaypoint(it.latitude, it.longitude, it.name) }
                        }
                    )

                    SampleRouteButton(
                        title = "Bến Thành → Phố đi bộ → Landmark 81, TP.HCM",
                        desc = "4 điểm dừng • 5.4 km",
                        onClick = {
                            val hcmPoints = listOf(
                                GeoPoint(10.772023, 106.698375, name = "Chợ Bến Thành"),
                                GeoPoint(10.774500, 106.702500, name = "Phố đi bộ Nguyễn Huệ"),
                                GeoPoint(10.788000, 106.711000, name = "Cầu Thủ Thiêm"),
                                GeoPoint(10.795240, 106.721833, name = "Landmark 81")
                            )
                            hcmPoints.forEach { viewModel.addWaypoint(it.latitude, it.longitude, it.name) }
                        }
                    )
                }
            }
        }

        // Waypoints List Header
        if (waypoints.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Danh sách điểm dừng (${waypoints.size})",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                TextButton(onClick = { viewModel.clearWaypoints() }) {
                    Text("Xóa tất cả", color = Color(0xFFEF4444), fontSize = 12.sp)
                }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                itemsIndexed(waypoints) { index, point ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF0284C7)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Text(
                                        text = point.name.ifEmpty { "Điểm ${index + 1}" },
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = String.format(java.util.Locale.US, "%.5f, %.5f", point.latitude, point.longitude),
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            IconButton(
                                onClick = { viewModel.removeWaypoint(index) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Xóa điểm",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Save Route Dialog
    if (showSaveRouteDialog) {
        var routeName by remember { mutableStateOf("Lộ trình mới ${waypoints.size} điểm") }
        AlertDialog(
            onDismissRequest = { showSaveRouteDialog = false },
            title = { Text("Lưu lộ trình", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = routeName,
                    onValueChange = { routeName = it },
                    label = { Text("Tên lộ trình") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (routeName.isNotBlank()) {
                            viewModel.saveRoute(routeName)
                        }
                        showSaveRouteDialog = false
                    }
                ) {
                    Text("Lưu")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveRouteDialog = false }) {
                    Text("Hủy")
                }
            }
        )
    }

    // Import GPX Dialog
    if (showImportGpxDialog) {
        var gpxInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showImportGpxDialog = false },
            title = { Text("Nhập dữ liệu GPX", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "Dán nội dung XML của file .gpx vào bên dưới:",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = gpxInput,
                        onValueChange = { gpxInput = it },
                        placeholder = { Text("<?xml version=\"1.0\"... <gpx>...") },
                        maxLines = 6,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (gpxInput.isNotBlank()) {
                            viewModel.importGpxContent(gpxInput)
                        }
                        showImportGpxDialog = false
                    }
                ) {
                    Text("Phân tích & Tải")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportGpxDialog = false }) {
                    Text("Hủy")
                }
            }
        )
    }

    // Export FIT Options Dialog (Allows typing customized start date and time directly)
    if (showExportFitDialog) {
        var inputDateText by remember {
            val nowStr = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.US).format(java.util.Date())
            mutableStateOf("01/11/2026")
        }
        var inputTimeText by remember { mutableStateOf("05:30:00") }

        // Live parsing
        val parsedStartTimeMillis: Long? = remember(inputDateText, inputTimeText) {
            val fullStr = "${inputDateText.trim()} ${inputTimeText.trim()}"
            try {
                val sdfWithSec = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.US).apply { isLenient = false }
                sdfWithSec.parse(fullStr)?.time
            } catch (_: Exception) {
                try {
                    val sdfNoSec = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.US).apply { isLenient = false }
                    sdfNoSec.parse(fullStr)?.time
                } catch (_: Exception) {
                    null
                }
            }
        }

        AlertDialog(
            onDismissRequest = { showExportFitDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    tint = Color(0xFF10B981)
                )
            },
            title = {
                Text("Xuất File Garmin FIT (.FIT)", fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(
                        text = "Nhập trực tiếp ngày giờ bắt đầu chạy để Strava/Garmin ghi nhận chính xác theo mong muốn:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Direct Date Input Field (dd/MM/yyyy)
                    OutlinedTextField(
                        value = inputDateText,
                        onValueChange = { inputDateText = it },
                        label = { Text("Ngày chạy (dd/MM/yyyy)") },
                        placeholder = { Text("Ví dụ: 01/11/2026") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Direct Time Input Field (HH:mm:ss)
                    OutlinedTextField(
                        value = inputTimeText,
                        onValueChange = { inputTimeText = it },
                        label = { Text("Giờ xuất phát (HH:mm:ss)") },
                        placeholder = { Text("Ví dụ: 05:30:00") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Quick fill shortcuts (optional convenience, fills the text fields)
                    Text("Điền nhanh ngày giờ (tùy chọn):", fontSize = 11.sp, color = Color(0xFF94A3B8))
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "01/11/2026" to { inputDateText = "01/11/2026" },
                            "Hôm nay" to {
                                inputDateText = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.US).format(java.util.Date())
                            },
                            "05:30:00" to { inputTimeText = "05:30:00" },
                            "17:45:00" to { inputTimeText = "17:45:00" }
                        ).forEach { (label, action) ->
                            androidx.compose.material3.FilterChip(
                                selected = false,
                                onClick = action,
                                label = { Text(label, fontSize = 10.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Preview Summary Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            if (parsedStartTimeMillis != null) {
                                val dateStr = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.US).format(java.util.Date(parsedStartTimeMillis))
                                Text(
                                    text = "⏰ Khởi hành: $dateStr",
                                    color = Color(0xFF34D399),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            } else {
                                Text(
                                    text = "⚠️ Định dạng ngày giờ: dd/MM/yyyy và HH:mm:ss",
                                    color = Color(0xFFF43F5E),
                                    fontSize = 11.sp
                                )
                            }
                            Text(
                                text = "⏱️ Ước tính thời gian: ${GeoUtils.formatDuration(totalTimeSeconds)} (${GeoUtils.formatDistance(totalDistanceMeters)})",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                            Text(
                                text = "⌚ Thiết bị: Garmin Forerunner 935 (S/N 3950640004)",
                                color = Color(0xFFE2E8F0),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "❤️ Nhịp tim: Biến thiên sinh lý (Khởi động -> Aerobic + HRV)",
                                color = Color(0xFFF43F5E),
                                fontSize = 11.sp
                            )
                            Text(
                                text = "👟 Guồng chân: 165 - 174 SPM • 🏔️ Độ cao: 1.0 - 5.0m",
                                color = Color(0xFF38BDF8),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val startTime = parsedStartTimeMillis ?: System.currentTimeMillis()
                        val fitFile = viewModel.exportFitFile(customStartTimeMillis = startTime)
                        if (fitFile != null) {
                            shareWorkoutFile(context, fitFile, "application/octet-stream", "Chia sẻ file Garmin FIT")
                        }
                        showExportFitDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Text("Xuất File & Chia Sẻ", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportFitDialog = false }) {
                    Text("Hủy")
                }
            }
        )
    }

    if (showSettingsSheet) {
        SimulationSettingsSheet(
            config = simulationConfig,
            onConfigChange = { viewModel.updateConfig(it) },
            onDismiss = { showSettingsSheet = false }
        )
    }
}

@Composable
private fun SampleRouteButton(title: String, desc: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(title, fontWeight = FontWeight.SemiBold, color = Color.White, fontSize = 13.sp)
            Text(desc, color = Color(0xFF38BDF8), fontSize = 11.sp)
        }
    }
}

private fun shareWorkoutFile(context: Context, file: java.io.File, mimeType: String, chooserTitle: String) {
    try {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val shareIntent = Intent.createChooser(sendIntent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(shareIntent)
    } catch (_: Exception) {}
}

private fun shareGpxText(context: Context, gpxContent: String) {
    try {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, gpxContent)
            putExtra(Intent.EXTRA_SUBJECT, "GeoSim_Route_${System.currentTimeMillis()}.gpx")
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Chia sẻ file GPX").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(shareIntent)
    } catch (_: Exception) {}
}
