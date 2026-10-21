package com.example.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.FavoritePlaceEntity
import com.example.data.local.MockDatabase
import com.example.data.local.MockHistoryEntity
import com.example.data.local.SavedRouteEntity
import com.example.data.model.GeoPoint
import com.example.data.model.SimulationConfig
import com.example.data.model.SimulationProgress
import com.example.data.model.SimulationStatus
import com.example.data.repository.MockLocationRepository
import com.example.service.MockLocationManager
import com.example.service.MockLocationService
import com.example.util.GeoUtils
import com.example.util.GpxExporter
import com.example.util.RealLocationProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = MockDatabase.getInstance(application)
    val repository = MockLocationRepository(db.mockDao())
    private val mockLocationManager = MockLocationManager(application)

    // Current targeted/selected point (Default: Hồ Hoàn Kiếm, Hà Nội or user's real GPS)
    private val _selectedPoint = MutableStateFlow(
        GeoPoint(latitude = 21.028511, longitude = 105.854444, name = "Đang tải vị trí...")
    )
    val selectedPoint: StateFlow<GeoPoint> = _selectedPoint.asStateFlow()

    // Real device physical GPS point (when not spoofing)
    private val _realLocationPoint = MutableStateFlow<GeoPoint?>(null)
    val realLocationPoint: StateFlow<GeoPoint?> = _realLocationPoint.asStateFlow()

    // Is currently fetching real location
    private val _isLocatingRealGps = MutableStateFlow(false)
    val isLocatingRealGps: StateFlow<Boolean> = _isLocatingRealGps.asStateFlow()

    // Waypoints for Route Simulation
    private val _waypoints = MutableStateFlow<List<GeoPoint>>(emptyList())
    val waypoints: StateFlow<List<GeoPoint>> = _waypoints.asStateFlow()

    // Simulation Config
    private val _simulationConfig = MutableStateFlow(SimulationConfig())
    val simulationConfig: StateFlow<SimulationConfig> = _simulationConfig.asStateFlow()

    // Simulation Progress (bound to MockLocationService)
    val simulationProgress: StateFlow<SimulationProgress> = MockLocationService.simulationProgress

    // Developer mode and mock permission status
    private val _isDevModeEnabled = MutableStateFlow(false)
    val isDevModeEnabled: StateFlow<Boolean> = _isDevModeEnabled.asStateFlow()

    private val _isMockPermitted = MutableStateFlow(false)
    val isMockPermitted: StateFlow<Boolean> = _isMockPermitted.asStateFlow()

    // History & Saved Routes
    val history: StateFlow<List<MockHistoryEntity>> = repository.history.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val savedRoutes: StateFlow<List<SavedRouteEntity>> = repository.savedRoutes.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val favorites: StateFlow<List<FavoritePlaceEntity>> = repository.favorites.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    // Toast/Snackbar notification message
    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    private val _isBatteryOptimizationIgnored = MutableStateFlow(true)
    val isBatteryOptimizationIgnored: StateFlow<Boolean> = _isBatteryOptimizationIgnored.asStateFlow()

    init {
        refreshDevStatus()
        fetchRealLocation(moveToCenter = true, showFeedback = false)
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    fun showMessage(msg: String) {
        _userMessage.value = msg
    }

    fun refreshDevStatus() {
        _isDevModeEnabled.value = mockLocationManager.isDeveloperOptionsEnabled()
        _isMockPermitted.value = mockLocationManager.isMockLocationPermitted()
        checkBatteryOptimization()
    }

    fun checkBatteryOptimization() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val pm = getApplication<Application>().getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
            _isBatteryOptimizationIgnored.value = pm?.isIgnoringBatteryOptimizations(getApplication<Application>().packageName) ?: true
        } else {
            _isBatteryOptimizationIgnored.value = true
        }
    }

    fun requestIgnoreBatteryOptimization() {
        val context = getApplication<Application>()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            try {
                val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                try {
                    val intent = Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {}
            }
        }
    }

    fun selectLocation(lat: Double, lng: Double, name: String = "") {
        val label = if (name.isNotEmpty()) name else String.format(java.util.Locale.US, "%.5f, %.5f", lat, lng)
        _selectedPoint.value = GeoPoint(latitude = lat, longitude = lng, name = label)
    }

    fun addWaypoint(lat: Double, lng: Double, name: String = "") {
        val count = _waypoints.value.size + 1
        val label = if (name.isNotEmpty()) name else "Điểm $count"
        val newPoint = GeoPoint(latitude = lat, longitude = lng, name = label)
        val updated = _waypoints.value + newPoint
        _waypoints.value = updated
        MockLocationService.setWaypoints(updated)
        _userMessage.value = "Đã thêm điểm dừng $count"
    }

    fun removeWaypoint(index: Int) {
        if (index in _waypoints.value.indices) {
            val updated = _waypoints.value.toMutableList().apply { removeAt(index) }
            _waypoints.value = updated
            MockLocationService.setWaypoints(updated)
        }
    }

    fun clearWaypoints() {
        _waypoints.value = emptyList()
        MockLocationService.setWaypoints(emptyList())
        _userMessage.value = "Đã xóa toàn bộ điểm lộ trình"
    }

    fun updateConfig(newConfig: SimulationConfig) {
        _simulationConfig.value = newConfig
        MockLocationService.updateConfig(newConfig)
    }

    /**
     * Start spoofing a single location immediately
     */
    fun startPointMock() {
        val point = _selectedPoint.value
        val context = getApplication<Application>()
        val intent = Intent(context, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_START_POINT
            putExtra(MockLocationService.EXTRA_LAT, point.latitude)
            putExtra(MockLocationService.EXTRA_LNG, point.longitude)
            putExtra(MockLocationService.EXTRA_NAME, point.name)
        }
        context.startService(intent)

        viewModelScope.launch {
            repository.recordHistory(point.latitude, point.longitude, point.name, "POINT")
        }
        _userMessage.value = "Đã kích hoạt vị trí giả lập: ${point.name}"
    }

    /**
     * Start automated route simulation along all waypoints
     */
    fun startRouteSimulation() {
        val wps = _waypoints.value
        if (wps.size < 2) {
            _userMessage.value = "Vui lòng chọn ít nhất 2 điểm để tạo lộ trình di chuyển"
            return
        }

        val context = getApplication<Application>()
        MockLocationService.setWaypoints(wps)
        MockLocationService.updateConfig(_simulationConfig.value)

        val intent = Intent(context, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_START_ROUTE
        }
        context.startService(intent)

        viewModelScope.launch {
            val first = wps.first()
            val last = wps.last()
            repository.recordHistory(
                first.latitude,
                first.longitude,
                "Lộ trình: ${first.name} → ${last.name}",
                "ROUTE"
            )
        }
        _userMessage.value = "Bắt đầu giả lập lộ trình ${wps.size} điểm"
    }

    fun pauseSimulation() {
        val context = getApplication<Application>()
        val intent = Intent(context, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_PAUSE
        }
        context.startService(intent)
    }

    fun resumeSimulation() {
        val context = getApplication<Application>()
        val intent = Intent(context, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_RESUME
        }
        context.startService(intent)
    }

    fun stopMocking() {
        val context = getApplication<Application>()
        val intent = Intent(context, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_STOP
        }
        context.startService(intent)
        mockLocationManager.stopMocking()
        _userMessage.value = "Đã tắt giả lập. Đang lấy lại vị trí GPS thật..."
        fetchRealLocation(moveToCenter = true, showFeedback = true)
    }

    /**
     * Retrieve the user's REAL physical device GPS location via GPS/Network hardware
     */
    fun fetchRealLocation(moveToCenter: Boolean = true, showFeedback: Boolean = true) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            _isLocatingRealGps.value = true
            try {
                val realLoc = RealLocationProvider.getRealCurrentLocation(context)
                if (realLoc != null) {
                    val point = GeoPoint(
                        latitude = realLoc.latitude,
                        longitude = realLoc.longitude,
                        altitude = realLoc.altitude,
                        name = "Vị trí GPS thật của máy"
                    )
                    _realLocationPoint.value = point
                    if (moveToCenter || !simulationProgress.value.isMockActive) {
                        _selectedPoint.value = point
                    }
                    if (showFeedback) {
                        _userMessage.value = String.format(
                            java.util.Locale.US,
                            "Đã lấy đúng vị trí thật: %.5f, %.5f",
                            realLoc.latitude,
                            realLoc.longitude
                        )
                    }
                } else {
                    if (showFeedback) {
                        _userMessage.value = "Không thể lấy tín hiệu GPS thật. Vui lòng bật Vị trí (GPS) trên thiết bị."
                    }
                }
            } catch (e: Exception) {
                if (showFeedback) {
                    _userMessage.value = "Lỗi đọc GPS: ${e.message}"
                }
            } finally {
                _isLocatingRealGps.value = false
            }
        }
    }

    /**
     * Reverse order of route waypoints
     */
    fun reverseWaypoints() {
        val list = _waypoints.value
        if (list.size >= 2) {
            val reversed = list.reversed()
            _waypoints.value = reversed
            MockLocationService.setWaypoints(reversed)
            _userMessage.value = "Đã đảo chiều lộ trình"
        }
    }

    /**
     * Joystick / D-pad nudge movement (North, South, East, West)
     */
    fun nudgeLocation(deltaMetersLat: Double, deltaMetersLng: Double, speedKmh: Float = 12f) {
        val current = simulationProgress.value.currentPoint ?: _selectedPoint.value
        val latOffset = deltaMetersLat / 111111.0
        val lngOffset = deltaMetersLng / (111111.0 * Math.cos(Math.toRadians(current.latitude)))
        val bearing = when {
            deltaMetersLat > 0 -> 0f
            deltaMetersLat < 0 -> 180f
            deltaMetersLng > 0 -> 90f
            else -> 270f
        }
        val nudged = GeoPoint(
            latitude = current.latitude + latOffset,
            longitude = current.longitude + lngOffset,
            name = "Vị trí Joystick"
        )
        _selectedPoint.value = nudged

        val context = getApplication<Application>()
        val intent = Intent(context, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_NUDGE_LOCATION
            putExtra(MockLocationService.EXTRA_LAT, nudged.latitude)
            putExtra(MockLocationService.EXTRA_LNG, nudged.longitude)
            putExtra(MockLocationService.EXTRA_SPEED_KMH, speedKmh)
            putExtra(MockLocationService.EXTRA_BEARING, bearing)
            putExtra(MockLocationService.EXTRA_NAME, "Joystick")
        }
        try {
            context.startService(intent)
        } catch (_: Exception) {}
    }

    /**
     * Save current route waypoints to Room Database
     */
    fun saveRoute(name: String) {
        val wps = _waypoints.value
        if (wps.isEmpty()) return
        viewModelScope.launch {
            repository.saveRoute(name, wps)
            _userMessage.value = "Đã lưu lộ trình: $name"
        }
    }

    fun loadSavedRoute(route: SavedRouteEntity) {
        val points = repository.parseRouteWaypoints(route.waypointsJson)
        if (points.isNotEmpty()) {
            _waypoints.value = points
            MockLocationService.setWaypoints(points)
            _selectedPoint.value = points.first()
            _userMessage.value = "Đã tải lộ trình: ${route.name}"
        }
    }

    fun deleteRoute(id: Long) {
        viewModelScope.launch {
            repository.deleteRoute(id)
            _userMessage.value = "Đã xóa lộ trình"
        }
    }

    fun teleportToHistory(item: MockHistoryEntity) {
        selectLocation(item.latitude, item.longitude, item.name)
        startPointMock()
    }

    fun deleteHistory(id: Long) {
        viewModelScope.launch {
            repository.deleteHistory(id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearHistory()
            _userMessage.value = "Đã xóa toàn bộ lịch sử"
        }
    }

    /**
     * Generate GPX XML from current waypoints or active simulation track
     */
    fun exportGpxXml(): String {
        val points = if (_waypoints.value.isNotEmpty()) _waypoints.value else listOf(_selectedPoint.value)
        return GpxExporter.exportToGpx("GeoSim_Route_${System.currentTimeMillis()}", points)
    }

    /**
     * Import GPX XML and replace waypoints
     */
    fun importGpxContent(xml: String) {
        val parsed = GpxExporter.parseGpx(xml)
        if (parsed.isNotEmpty()) {
            _waypoints.value = parsed
            MockLocationService.setWaypoints(parsed)
            _selectedPoint.value = parsed.first()
            _userMessage.value = "Đã nhập thành công ${parsed.size} điểm từ file GPX"
        } else {
            _userMessage.value = "Không tìm thấy dữ liệu tọa độ hợp lệ trong file GPX"
        }
    }
}
