package com.example.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.VibrationCadence
import com.example.ui.MainViewModel
import com.example.ui.components.openDeveloperSettings

@Composable
fun ShieldScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDevModeEnabled by viewModel.isDevModeEnabled.collectAsState()
    val isMockPermitted by viewModel.isMockPermitted.collectAsState()
    val config by viewModel.simulationConfig.collectAsState()

    val securityScore = (if (isDevModeEnabled) 20 else 0) +
            (if (isMockPermitted) 25 else 0) +
            (if (config.isolateRealLocation) 20 else 0) +
            (if (config.enableNaturalSpeedVariance) 15 else 0) +
            (if (config.enableGpsJitter) 10 else 0) +
            (if (config.enableRandomPause) 10 else 0)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B132B))
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
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
                    text = "Khiên Bảo Mật & Ẩn Vị Trí",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Bảo vệ danh tính & chống phát hiện vị trí mô phỏng",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF94A3B8)
                )
            }

            IconButton(
                onClick = { viewModel.refreshDevStatus() },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0xFF1E293B))
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Làm mới kiểm tra",
                    tint = Color(0xFF38BDF8)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Security Status Score Card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (securityScore >= 80) Color(0xFF064E3B) else Color(0xFF451A03)
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(if (securityScore >= 80) Color(0xFF10B981) else Color(0xFFF59E0B)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (securityScore >= 80) Icons.Default.Shield else Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = "Mức độ Bảo vệ: $securityScore / 100%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = if (securityScore >= 80)
                            "Tối ưu: Đã cô lập vị trí thực và kích hoạt các thuật toán mô phỏng người thật."
                        else
                            "Cần chú ý: Cần cấp quyền mô phỏng trong Cài đặt nhà phát triển để hoạt động.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (securityScore >= 80) Color(0xFFA7F3D0) else Color(0xFFFDE68A),
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Android System Dev Mode Requirements Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Quyền Hệ Thống Bắt Buộc (Android Dev Mode)",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF38BDF8),
                    fontSize = 15.sp
                )
                Text(
                    text = "Để Android cho phép phát sóng GPS giả lập đến các ứng dụng khác:",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                StatusRow(
                    title = "Chế độ Nhà phát triển (Developer Mode)",
                    desc = if (isDevModeEnabled) "Đã bật trên thiết bị" else "Chưa kích hoạt (Nhấn 7 lần vào Build Number)",
                    isOk = isDevModeEnabled
                )

                Spacer(modifier = Modifier.height(8.dp))

                StatusRow(
                    title = "Ứng dụng vị trí mô phỏng (Mock App)",
                    desc = if (isMockPermitted) "Đã chọn GeoSim trong Developer Options" else "Chưa được chọn làm app mô phỏng",
                    isOk = isMockPermitted
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { openDeveloperSettings(context) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("open_dev_options_button")
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Mở Cài đặt Nhà phát triển")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Real Location Isolation Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Ẩn Vị Trí Thật (Cô Lập GPS & Network)",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981),
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Ghi đè cả cổng GPS phần cứng lẫn định vị Wifi/Cell Tower để các ứng dụng khác không thể đọc được tọa độ thật.",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                    }

                    Switch(
                        checked = config.isolateRealLocation,
                        onCheckedChange = { viewModel.updateConfig(config.copy(isolateRealLocation = it)) },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF10B981))
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Vệ tinh mô phỏng: 14 vệ tinh GPS/GLONASS • Độ chính xác: 3.2m",
                        color = Color(0xFFCBD5E1),
                        fontSize = 11.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Realism Engine Details Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Thuật Toán Chạy Như Người Thật",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF59E0B),
                    fontSize = 15.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                SwitchFeatureRow(
                    title = "Biến thiên vận tốc tự nhiên (±5-8%)",
                    desc = "Người thật không bao giờ đi đúng 20.0000 km/h đều tăm tắp",
                    checked = config.enableNaturalSpeedVariance,
                    onCheckedChange = { viewModel.updateConfig(config.copy(enableNaturalSpeedVariance = it)) }
                )

                Spacer(modifier = Modifier.height(10.dp))

                SwitchFeatureRow(
                    title = "Độ nhiễu vi mô Anten GPS (1-2m)",
                    desc = "Mô phỏng sai số khí quyển tự nhiên của anten thu vệ tinh",
                    checked = config.enableGpsJitter,
                    onCheckedChange = { viewModel.updateConfig(config.copy(enableGpsJitter = it)) }
                )

                Spacer(modifier = Modifier.height(10.dp))

                SwitchFeatureRow(
                    title = "Ngẫu nhiên hóa thời gian dừng nghỉ (${config.minPauseSeconds}-${config.maxPauseSeconds}s)",
                    desc = "Mô phỏng dừng đèn đỏ, ngã tư hoặc dừng nghe điện thoại",
                    checked = config.enableRandomPause,
                    onCheckedChange = { viewModel.updateConfig(config.copy(enableRandomPause = it)) }
                )

                Spacer(modifier = Modifier.height(10.dp))

                SwitchFeatureRow(
                    title = "Giả lập độ rung điện thoại (Nhịp thở thật)",
                    desc = "Phát xung rung haptic nhẹ nhàng đồng bộ nhịp thở hoặc bước chân: ${config.vibrationPattern.label}",
                    checked = config.vibrationPattern != VibrationCadence.OFF,
                    onCheckedChange = { enabled ->
                        viewModel.updateConfig(
                            config.copy(
                                vibrationPattern = if (enabled) VibrationCadence.BREATHING_RELAXED else VibrationCadence.OFF
                            )
                        )
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                SwitchFeatureRow(
                    title = "Giả lập nhịp tim sinh học (BPM)",
                    desc = "Tự động biến thiên theo vận tốc & lưu vào track GPX: ${config.heartRateMode.title}",
                    checked = config.enableHeartRateSimulation,
                    onCheckedChange = { enabled ->
                        viewModel.updateConfig(config.copy(enableHeartRateSimulation = enabled))
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun StatusRow(title: String, desc: String, isOk: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (isOk) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            tint = if (isOk) Color(0xFF10B981) else Color(0xFFF59E0B),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(title, fontWeight = FontWeight.SemiBold, color = Color.White, fontSize = 13.sp)
            Text(desc, color = if (isOk) Color(0xFFA7F3D0) else Color(0xFFFDE68A), fontSize = 11.sp)
        }
    }
}

@Composable
private fun SwitchFeatureRow(
    title: String,
    desc: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = Color.White, fontSize = 13.sp)
            Text(desc, color = Color(0xFF94A3B8), fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF38BDF8))
        )
    }
}
