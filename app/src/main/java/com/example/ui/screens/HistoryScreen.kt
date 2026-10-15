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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.data.local.MockHistoryEntity
import com.example.data.local.SavedRouteEntity
import com.example.ui.MainViewModel
import com.example.util.GeoUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    viewModel: MainViewModel,
    onNavigateToMap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val historyList by viewModel.history.collectAsState()
    val savedRoutes by viewModel.savedRoutes.collectAsState()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Vị trí đã Fake (${historyList.size})", "Lộ trình đã lưu (${savedRoutes.size})")

    val dateFormat = remember { SimpleDateFormat("HH:mm - dd/MM/yyyy", Locale.getDefault()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B132B))
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Lịch Sử & Lưu Trữ",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Lưu lại nhật ký vị trí đã mô phỏng và các tuyến đường",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF94A3B8)
                )
            }

            if (selectedTabIndex == 0 && historyList.isNotEmpty()) {
                TextButton(onClick = { viewModel.clearAllHistory() }) {
                    Text("Xóa lịch sử", color = Color(0xFFEF4444), fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Custom Tabs
        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = Color(0xFF1E293B),
            contentColor = Color(0xFF38BDF8),
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                    color = Color(0xFF38BDF8)
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = {
                        Text(
                            text = title,
                            fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTabIndex == index) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                            fontSize = 13.sp
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (selectedTabIndex == 0) {
            // Location History Tab
            if (historyList.isEmpty()) {
                EmptyState(
                    title = "Chưa có lịch sử vị trí",
                    desc = "Khi bạn bắt đầu Fake GPS hoặc di chuyển theo lộ trình, vị trí sẽ được tự động lưu lại ở đây."
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(historyList, key = { it.id }) { item ->
                        HistoryItemCard(
                            item = item,
                            dateFormat = dateFormat,
                            onTeleport = {
                                viewModel.teleportToHistory(item)
                                onNavigateToMap()
                            },
                            onDelete = { viewModel.deleteHistory(item.id) }
                        )
                    }
                }
            }
        } else {
            // Saved Routes Tab
            if (savedRoutes.isEmpty()) {
                EmptyState(
                    title = "Chưa có lộ trình nào được lưu",
                    desc = "Tạo các điểm dừng ở tab 'Lộ trình' rồi nhấn biểu tượng Lưu để lưu giữ tuyến đường thường đi."
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(savedRoutes, key = { it.id }) { route ->
                        SavedRouteCard(
                            route = route,
                            dateFormat = dateFormat,
                            onLoad = {
                                viewModel.loadSavedRoute(route)
                                onNavigateToMap()
                            },
                            onDelete = { viewModel.deleteRoute(route.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryItemCard(
    item: MockHistoryEntity,
    dateFormat: SimpleDateFormat,
    onTeleport: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (item.mode == "ROUTE") Color(0xFF0369A1) else Color(0xFF059669)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (item.mode == "ROUTE") Icons.Default.Route else Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = item.name,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 14.sp
                    )
                    Text(
                        text = String.format(Locale.US, "%.5f, %.5f", item.latitude, item.longitude),
                        color = Color(0xFF38BDF8),
                        fontSize = 12.sp
                    )
                    Text(
                        text = dateFormat.format(Date(item.timestamp)),
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }
            }

            Row {
                Button(
                    onClick = onTeleport,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("teleport_button")
                ) {
                    Text("Tới đây", fontSize = 12.sp)
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Xóa",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SavedRouteCard(
    route: SavedRouteEntity,
    dateFormat: SimpleDateFormat,
    onLoad: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = route.name,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 15.sp
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Xóa lộ trình",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${route.waypointCount} điểm dừng • ${GeoUtils.formatDistance(route.totalDistanceMeters)}",
                color = Color(0xFF38BDF8),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Tạo ngày: ${dateFormat.format(Date(route.createdAt))}",
                color = Color(0xFF94A3B8),
                fontSize = 11.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onLoad,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Nạp lộ trình này & Chạy", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun EmptyState(title: String, desc: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.History,
            contentDescription = null,
            tint = Color(0xFF475569),
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFCBD5E1)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = desc,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF64748B),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}
