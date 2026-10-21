package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.ui.MainViewModel
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.MapScreen
import com.example.ui.screens.RouteScreen
import com.example.ui.screens.ShieldScreen
import com.example.ui.theme.MyApplicationTheme

enum class NavTab(val title: String, val selectedIcon: ImageVector, val unselectedIcon: ImageVector, val tag: String) {
    MAP("Bản đồ", Icons.Filled.Map, Icons.Outlined.Map, "nav_tab_map"),
    ROUTE("Lộ trình & GPX", Icons.Filled.Route, Icons.Outlined.Route, "nav_tab_route"),
    HISTORY("Lịch sử", Icons.Filled.History, Icons.Outlined.History, "nav_tab_history"),
    SHIELD("Bảo mật", Icons.Filled.Security, Icons.Outlined.Security, "nav_tab_shield")
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize OpenStreetMap Osmdroid Configuration
        val osmConfig = org.osmdroid.config.Configuration.getInstance()
        osmConfig.load(this, getSharedPreferences("osmdroid_prefs", MODE_PRIVATE))
        // Set valid custom userAgentValue AFTER load() so OpenStreetMap tile servers allow downloads
        osmConfig.userAgentValue = "GeoSim-GPS-Mock/2.0 (Android; ${packageName})"
        val osmBasePath = java.io.File(cacheDir, "osmdroid").apply { mkdirs() }
        val osmTileCache = java.io.File(osmBasePath, "tiles").apply { mkdirs() }
        osmConfig.osmdroidBasePath = osmBasePath
        osmConfig.osmdroidTileCache = osmTileCache

        setContent {
            MyApplicationTheme {
                MainApp(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshDevStatus()
    }
}

@Composable
fun MainApp(viewModel: MainViewModel) {
    val context = LocalContext.current
    var currentTabIndex by remember { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }
    val userMessage by viewModel.userMessage.collectAsState()

    // Request permissions launcher
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshDevStatus()
    }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            permissionsLauncher.launch(needed.toTypedArray())
        }
    }

    // Show Snackbars when userMessage is triggered
    LaunchedEffect(userMessage) {
        userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearUserMessage()
        }
    }

    // Handle back button on secondary tabs
    if (currentTabIndex != 0) {
        BackHandler {
            currentTabIndex = 0
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF0F172A),
                contentColor = Color(0xFFF1F5F9),
                tonalElevation = 8.dp
            ) {
                NavTab.values().forEachIndexed { index, tab ->
                    val isSelected = currentTabIndex == index
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { currentTabIndex = index },
                        icon = {
                            Icon(
                                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.title
                            )
                        },
                        label = { Text(tab.title) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF0F172A),
                            selectedTextColor = Color(0xFF38BDF8),
                            indicatorColor = Color(0xFF38BDF8),
                            unselectedIconColor = Color(0xFF94A3B8),
                            unselectedTextColor = Color(0xFF94A3B8)
                        ),
                        modifier = Modifier.testTag(tab.tag)
                    )
                }
            }
        }
    ) { innerPadding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)

        when (currentTabIndex) {
            0 -> MapScreen(viewModel = viewModel, modifier = contentModifier)
            1 -> RouteScreen(viewModel = viewModel, modifier = contentModifier)
            2 -> HistoryScreen(
                viewModel = viewModel,
                onNavigateToMap = { currentTabIndex = 0 },
                modifier = contentModifier
            )
            3 -> ShieldScreen(viewModel = viewModel, modifier = contentModifier)
        }
    }
}
