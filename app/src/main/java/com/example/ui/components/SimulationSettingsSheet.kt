package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SimulationConfig
import com.example.data.model.VibrationCadence

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimulationSettingsSheet(
    config: SimulationConfig,
    onConfigChange: (SimulationConfig) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0F172A),
        contentColor = Color(0xFFF1F5F9)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Tùy chỉnh Giả lập & Hành vi Người thật",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Điều chỉnh tốc độ, độ rung nhịp thở và ngẫu nhiên hóa thời gian dừng nghỉ",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF94A3B8)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Running Pace & Speed Presets (4:00 - 15:00 min/km)
            val currentPaceMin = com.example.util.GeoUtils.speedKmhToPaceMinutes(config.speedKmh)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Nhịp độ chạy (Pace): ${com.example.util.GeoUtils.formatPace(config.speedKmh)}",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF38BDF8),
                        fontSize = 16.sp
                    )
                    Text(
                        text = "Tương đương: ${String.format(java.util.Locale.US, "%.1f", config.speedKmh)} km/h • Dải tiêu chuẩn 4:00 - 15:00 min/km",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Pace slider from 4.0 min/km (fast 15 km/h) to 15.0 min/km (walking 4 km/h)
            Slider(
                value = currentPaceMin.toFloat(),
                onValueChange = { paceMin ->
                    val newSpeed = com.example.util.GeoUtils.paceMinutesToSpeedKmh(paceMin.toDouble())
                    onConfigChange(config.copy(speedKmh = newSpeed))
                },
                valueRange = 4.0f..15.0f,
                steps = 109,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF38BDF8),
                    activeTrackColor = Color(0xFF0284C7),
                    inactiveTrackColor = Color(0xFF334155)
                )
            )

            // Quick Pace Preset Chips (Horizontal scrolling row)
            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val pacePresets = listOf(
                    4.0 to "Pace 4:00 (15 km/h)",
                    4.5 to "Pace 4:30 (13.3 km/h)",
                    5.0 to "Pace 5:00 (12 km/h)",
                    5.5 to "Pace 5:30 (10.9 km/h)",
                    6.0 to "Pace 6:00 (10 km/h)",
                    7.0 to "Pace 7:00 (8.6 km/h)",
                    8.5 to "Pace 8:30 (7.1 km/h)",
                    10.0 to "Pace 10:00 (6 km/h)",
                    12.0 to "Pace 12:00 (5 km/h)",
                    15.0 to "Pace 15:00 (4 km/h)"
                )
                items(pacePresets.size) { idx ->
                    val (pMin, label) = pacePresets[idx]
                    val isSelected = kotlin.math.abs(currentPaceMin - pMin) < 0.25
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            val newSpeed = com.example.util.GeoUtils.paceMinutesToSpeedKmh(pMin)
                            onConfigChange(config.copy(speedKmh = newSpeed))
                        },
                        label = { Text(label, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF0284C7),
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Real Human Physics & Dynamic Intervals Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Biến thiên Tốc độ & Giai đoạn chạy (Interval)",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF34D399),
                        fontSize = 15.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Đổi tốc độ theo chu kỳ (Interval)", fontWeight = FontWeight.Medium, color = Color.White)
                            Text("Tự động tăng tốc (surge), duy trì (cruise) và thả lỏng (recovery) để biểu đồ tốc độ không bao giờ bị ngang mãi mãi", color = Color(0xFF94A3B8), fontSize = 11.sp)
                        }
                        Switch(
                            checked = config.enableIntervalPaceShifting,
                            onCheckedChange = { onConfigChange(config.copy(enableIntervalPaceShifting = it)) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF34D399))
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Biến thiên tốc độ tự nhiên vi mô", fontWeight = FontWeight.Medium, color = Color.White)
                            Text("Tự động thêm dao động bước chạy ±5-8% mô phỏng lực chân người thật", color = Color(0xFF94A3B8), fontSize = 11.sp)
                        }
                        Switch(
                            checked = config.enableNaturalSpeedVariance,
                            onCheckedChange = { onConfigChange(config.copy(enableNaturalSpeedVariance = it)) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF34D399))
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Độ nhiễu GPS vi mô (Jitter)", fontWeight = FontWeight.Medium, color = Color.White)
                            Text("Tạo sai số nhẹ tự nhiên bám sát line, không bị lệch ngang giật cục", color = Color(0xFF94A3B8), fontSize = 11.sp)
                        }
                        Switch(
                            checked = config.enableGpsJitter,
                            onCheckedChange = { onConfigChange(config.copy(enableGpsJitter = it)) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF34D399))
                        )
                    }

                    if (config.enableGpsJitter) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Độ lệch ngang tối đa:",
                                color = Color(0xFFE2E8F0),
                                fontSize = 12.sp
                            )
                            Text(
                                text = "${String.format(java.util.Locale.US, "%.1f", config.maxLateralDriftMeters)} m (tối đa 2m)",
                                color = Color(0xFF34D399),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                        Slider(
                            value = config.maxLateralDriftMeters,
                            onValueChange = { onConfigChange(config.copy(maxLateralDriftMeters = it.coerceIn(0.2f, 2.0f))) },
                            valueRange = 0.2f..2.0f,
                            steps = 17, // 0.1m step increments
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF34D399),
                                activeTrackColor = Color(0xFF10B981),
                                inactiveTrackColor = Color(0xFF334155)
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Periodic 10-Minute Rest (Replaces annoying waypoint dwell pauses)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Tự động dừng nghỉ 5-10s mỗi 10 phút", fontWeight = FontWeight.Bold, color = Color(0xFFFBBF24))
                            Text("Chạy mượt qua các mốc, chỉ tự động nghỉ ngắn 5-10s sau mỗi 10 phút", color = Color(0xFF94A3B8), fontSize = 11.sp)
                        }
                        Switch(
                            checked = config.enablePeriodicRest10m,
                            onCheckedChange = { onConfigChange(config.copy(enablePeriodicRest10m = it)) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFFBBF24))
                        )
                    }

                    if (config.enablePeriodicRest10m) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "⚡ Đã tắt dừng chờ 5s tại các mốc: Người chạy lướt thẳng liên tục và chạy vòng lặp (Loop).",
                            color = Color(0xFF34D399),
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))



            // Heart Rate Simulation Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("❤️", fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Giả Lập Nhịp Tim (BPM)",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF43F5E),
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "Mô phỏng nhịp tim sinh học và xuất vào tệp GPX",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Switch(
                            checked = config.enableHeartRateSimulation,
                            onCheckedChange = { onConfigChange(config.copy(enableHeartRateSimulation = it)) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFF43F5E))
                        )
                    }

                    if (config.enableHeartRateSimulation) {
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Chế độ Nhịp Tim: ${config.heartRateMode.title}",
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFFDA4AF),
                            fontSize = 13.sp
                        )
                        Text(
                            text = config.heartRateMode.description,
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Modes Selector
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            com.example.data.model.HeartRateMode.values().forEach { mode ->
                                FilterChip(
                                    selected = config.heartRateMode == mode,
                                    onClick = { onConfigChange(config.copy(heartRateMode = mode)) },
                                    label = { Text(mode.title, fontSize = 12.sp) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFFBE123C),
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }

                        if (config.heartRateMode == com.example.data.model.HeartRateMode.CUSTOM_FIXED) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Nhịp tim cố định: ${config.customHeartRateBpm} BPM",
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Slider(
                                value = config.customHeartRateBpm.toFloat(),
                                onValueChange = { onConfigChange(config.copy(customHeartRateBpm = it.toInt())) },
                                valueRange = 40f..220f,
                                steps = 179,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFFF43F5E),
                                    activeTrackColor = Color(0xFFE11D48),
                                    inactiveTrackColor = Color(0xFF334155)
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // HRV Fluctuation Switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Biến thiên nhịp tim tự nhiên (HRV)", color = Color.White, fontSize = 13.sp)
                                Text("Tạo dao động ±2-4 BPM sinh lý tự nhiên như tim người sống", color = Color(0xFF94A3B8), fontSize = 11.sp)
                            }
                            Switch(
                                checked = config.enableHrvFluctuation,
                                onCheckedChange = { onConfigChange(config.copy(enableHrvFluctuation = it)) },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFF43F5E))
                            )
                        }


                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Cadence (SPM) & Calories (Kcal) Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("👟", fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Nhịp Chạy (SPM) & Tiêu Thụ Calo",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF38BDF8),
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "Fake số bước chân, nhịp SPM và calo xuất ra tệp GPX",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Switch(
                            checked = config.enableCadenceSimulation,
                            onCheckedChange = { onConfigChange(config.copy(enableCadenceSimulation = it)) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF38BDF8))
                        )
                    }

                    if (config.enableCadenceSimulation) {
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Tự động tính SPM theo vận tốc", color = Color.White, fontSize = 13.sp)
                                Text("Đi bộ: 100-120 spm • Chạy: 160-185 spm", color = Color(0xFF94A3B8), fontSize = 11.sp)
                            }
                            Switch(
                                checked = config.autoCadenceBySpeed,
                                onCheckedChange = { onConfigChange(config.copy(autoCadenceBySpeed = it)) },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF38BDF8))
                            )
                        }

                        if (!config.autoCadenceBySpeed) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Nhịp chạy thủ công: ${config.manualCadenceSpm} SPM",
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Slider(
                                value = config.manualCadenceSpm.toFloat(),
                                onValueChange = { onConfigChange(config.copy(manualCadenceSpm = it.toInt())) },
                                valueRange = 80f..220f,
                                steps = 139,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF38BDF8),
                                    activeTrackColor = Color(0xFF0284C7),
                                    inactiveTrackColor = Color(0xFF334155)
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // User Weight Slider for accurate calories calculation
                        Text(
                            text = "Cân nặng vận động viên: ${config.userWeightKg.toInt()} kg (để tính Calo)",
                            color = Color(0xFFFDE68A),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Slider(
                            value = config.userWeightKg,
                            onValueChange = { onConfigChange(config.copy(userWeightKg = it)) },
                            valueRange = 40f..120f,
                            steps = 79,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFFFBBF24),
                                activeTrackColor = Color(0xFFD97706),
                                inactiveTrackColor = Color(0xFF334155)
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Route Loop Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E293B), RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Lặp lại lộ trình chạy ngược về (Loop Con Lắc)", fontWeight = FontWeight.SemiBold, color = Color.White)
                    Text("Khi chạy tới điểm cuối sẽ tự động chạy ngược lại về điểm xuất phát (Out-and-Back), không bị dịch chuyển đột ngột.", color = Color(0xFF94A3B8), fontSize = 11.sp)
                }
                Switch(
                    checked = config.loopRoute,
                    onCheckedChange = { onConfigChange(config.copy(loopRoute = it)) },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF38BDF8))
                )
            }
        }
    }
}

@Composable
private fun SpeedChip(label: String, speed: Float, currentSpeed: Float, onClick: () -> Unit) {
    val isSelected = (currentSpeed - speed).let { if (it < 0) -it else it } < 1f
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { Text(label, fontSize = 11.sp) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Color(0xFF0284C7),
            selectedLabelColor = Color.White
        )
    )
}
