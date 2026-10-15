package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DevModeBanner(
    isDevModeEnabled: Boolean,
    isMockPermitted: Boolean,
    onRefreshCheck: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showGuideDialog by remember { mutableStateOf(false) }

    val needsAttention = !isDevModeEnabled || !isMockPermitted

    AnimatedVisibility(visible = needsAttention, modifier = modifier) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF451A03), // Deep warning amber/dark brown
                contentColor = Color(0xFFFEF3C7)
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .clickable { showGuideDialog = true }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Cảnh báo quyền",
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (!isDevModeEnabled) "Chưa bật Tùy chọn Nhà phát triển" else "Chưa chọn GeoSim làm app mô phỏng",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFDE68A)
                        )
                        Text(
                            text = "Nhấn để xem hướng dẫn & mở cài đặt hệ thống",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFCD34D),
                            fontSize = 11.sp
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Xem",
                    tint = Color(0xFFFDE68A)
                )
            }
        }
    }

    if (showGuideDialog) {
        AlertDialog(
            onDismissRequest = { showGuideDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8)
                )
            },
            title = {
                Text(
                    text = "Bắt buộc: Kích hoạt Vị trí mô phỏng",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Để fake vị trí và giả lập lộ trình mượt mà như người thật, Android bắt buộc người dùng chọn GeoSim trong Developer Options:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    StepItem(
                        step = "1",
                        title = "Bật Chế độ Nhà phát triển (nếu chưa bật)",
                        desc = "Vào Cài đặt > Thông tin điện thoại > Nhấn liên tục 7 lần vào 'Số phiên bản' (Build number)."
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    StepItem(
                        step = "2",
                        title = "Mở Tùy chọn cho nhà phát triển",
                        desc = "Vào Cài đặt > Hệ thống > Tùy chọn cho nhà phát triển (Developer options)."
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    StepItem(
                        step = "3",
                        title = "Chọn GeoSim",
                        desc = "Tìm mục 'Chọn ứng dụng vị trí mô phỏng' (Select mock location app) và chọn GeoSim."
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        openDeveloperSettings(context)
                        showGuideDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                ) {
                    Text("Mở Cài đặt Nhà phát triển")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        onRefreshCheck()
                        showGuideDialog = false
                    }
                ) {
                    Text("Kiểm tra lại")
                }
            }
        )
    }
}

@Composable
private fun StepItem(step: String, title: String, desc: String) {
    Row(verticalAlignment = Alignment.Top) {
        Column(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(Color(0xFF0369A1)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = step,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }
    }
}

fun openDeveloperSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        try {
            val fallback = Intent(Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(fallback)
        } catch (_: Exception) {}
    }
}
