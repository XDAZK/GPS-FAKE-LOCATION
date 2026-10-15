package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.GeoPoint
import com.example.data.model.SimulationConfig
import com.example.data.model.SimulationProgress
import com.example.data.model.SimulationStatus
import com.example.util.CadenceCalculator
import com.example.util.GeoUtils
import com.example.util.HeartRateSimulator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

class MockLocationService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var simulationJob: Job? = null

    private lateinit var mockLocationManager: MockLocationManager
    private lateinit var heartRateSimulator: HeartRateSimulator
    private lateinit var notificationManager: NotificationManager

    private var accumulatedSteps = 0.0
    private var accumulatedCalories = 0.0

    companion object {
        const val CHANNEL_ID = "mock_location_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START_POINT = "com.example.service.START_POINT"
        const val ACTION_START_ROUTE = "com.example.service.START_ROUTE"
        const val ACTION_PAUSE = "com.example.service.PAUSE"
        const val ACTION_RESUME = "com.example.service.RESUME"
        const val ACTION_STOP = "com.example.service.STOP"
        const val ACTION_UPDATE_CONFIG = "com.example.service.UPDATE_CONFIG"
        const val ACTION_NUDGE_LOCATION = "com.example.service.NUDGE_LOCATION"

        const val EXTRA_LAT = "extra_lat"
        const val EXTRA_LNG = "extra_lng"
        const val EXTRA_NAME = "extra_name"
        const val EXTRA_SPEED_KMH = "extra_speed_kmh"
        const val EXTRA_BEARING = "extra_bearing"

        private val _simulationProgress = MutableStateFlow(SimulationProgress())
        val simulationProgress = _simulationProgress.asStateFlow()

        private val _routeWaypoints = MutableStateFlow<List<GeoPoint>>(emptyList())
        val routeWaypoints = _routeWaypoints.asStateFlow()

        private val _activeConfig = MutableStateFlow(SimulationConfig())
        val activeConfig = _activeConfig.asStateFlow()

        fun setWaypoints(points: List<GeoPoint>) {
            _routeWaypoints.value = points
        }

        fun updateConfig(config: SimulationConfig) {
            _activeConfig.value = config
        }
    }

    override fun onCreate() {
        super.onCreate()
        mockLocationManager = MockLocationManager(this)
        heartRateSimulator = HeartRateSimulator(this)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        when (action) {
            ACTION_START_POINT -> {
                val lat = intent.getDoubleExtra(EXTRA_LAT, 0.0)
                val lng = intent.getDoubleExtra(EXTRA_LNG, 0.0)
                val name = intent.getStringExtra(EXTRA_NAME) ?: "Vị trí đã chọn"
                startMockingSinglePoint(GeoPoint(lat, lng, name = name))
            }
            ACTION_NUDGE_LOCATION -> {
                val lat = intent.getDoubleExtra(EXTRA_LAT, 0.0)
                val lng = intent.getDoubleExtra(EXTRA_LNG, 0.0)
                val speedKmh = intent.getFloatExtra(EXTRA_SPEED_KMH, 12f)
                val bearing = intent.getFloatExtra(EXTRA_BEARING, 0f)
                val name = intent.getStringExtra(EXTRA_NAME) ?: "Joystick"
                handleJoystickNudge(lat, lng, speedKmh, bearing, name)
            }
            ACTION_START_ROUTE -> {
                startRouteSimulation()
            }
            ACTION_PAUSE -> {
                pauseSimulation()
            }
            ACTION_RESUME -> {
                resumeSimulation()
            }
            ACTION_STOP -> {
                stopMockingService()
            }
            ACTION_UPDATE_CONFIG -> {
                // Config already updated via static StateFlow
            }
        }

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "GeoSim GPS Simulation",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Hiển thị tiến độ và trạng thái giả lập GPS"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(
        title: String,
        content: String,
        progressPercent: Int = -1,
        isPaused: Boolean = false
    ): Notification {
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            mainIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val pauseResumeIntent = Intent(this, MockLocationService::class.java).apply {
            this.action = if (isPaused) ACTION_RESUME else ACTION_PAUSE
        }
        val pauseResumePendingIntent = PendingIntent.getService(
            this,
            1,
            pauseResumeIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, MockLocationService::class.java).apply {
            this.action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            2,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setContentIntent(contentPendingIntent)
            .setOnlyAlertOnce(true)
            .addAction(
                if (isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                if (isPaused) "Tiếp tục" else "Tạm dừng",
                pauseResumePendingIntent
            )
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dừng lại", stopPendingIntent)

        if (progressPercent in 0..100) {
            builder.setProgress(100, progressPercent, false)
        }

        return builder.build()
    }

    private fun startForegroundCompat(notificationId: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(notificationId, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(notificationId, notification)
        }
    }

    private fun handleJoystickNudge(lat: Double, lng: Double, speedKmh: Float, bearing: Float, name: String) {
        val config = _activeConfig.value
        mockLocationManager.startMocking(config.isolateRealLocation)

        val currentPoint = GeoPoint(lat, lng, name = name)
        val currentCadence = CadenceCalculator.calculateCadence(speedKmh, isResting = false, config = config)
        val calDelta = CadenceCalculator.computeCaloriesDeltaKcal(speedKmh, isResting = false, weightKg = config.userWeightKg)
        accumulatedCalories += calDelta
        if (currentCadence > 0) {
            accumulatedSteps += (currentCadence / 60.0).coerceAtLeast(1.0)
        }
        val currentBpm = heartRateSimulator.calculateBpm(speedKmh, isResting = false, config = config)
        val pointWithFitness = currentPoint.copy(heartRate = currentBpm, cadence = currentCadence)

        mockLocationManager.pushMockLocation(
            geoPoint = pointWithFitness,
            speedKmh = speedKmh,
            bearing = bearing,
            caloriesKcal = accumulatedCalories,
            steps = accumulatedSteps.toLong()
        )

        _simulationProgress.value = SimulationProgress(
            status = SimulationStatus.RUNNING,
            currentPoint = pointWithFitness,
            progressPercent = 100,
            currentSpeedKmh = speedKmh,
            currentHeartRateBpm = currentBpm,
            currentCadenceSpm = currentCadence,
            totalSteps = accumulatedSteps.toLong(),
            caloriesBurnedKcal = accumulatedCalories,
            isMockActive = true
        )

        val notification = buildNotification(
            title = "GeoSim: Cần điều khiển • ❤️ ${currentBpm} BPM",
            content = "Tốc độ: ${speedKmh.toInt()} km/h | 👟 ${currentCadence} SPM | 🔥 ${accumulatedCalories.toInt()} kcal"
        )
        startForegroundCompat(NOTIFICATION_ID, notification)
    }

    private fun startMockingSinglePoint(point: GeoPoint) {
        val config = _activeConfig.value
        mockLocationManager.startMocking(config.isolateRealLocation)

        simulationJob?.cancel()

        val notification = buildNotification(
            title = "GeoSim: Đang giữ vị trí cố định",
            content = "${point.name} (${String.format(java.util.Locale.US, "%.5f, %.5f", point.latitude, point.longitude)})"
        )
        startForegroundCompat(NOTIFICATION_ID, notification)

        simulationJob = serviceScope.launch {
            while (isActive) {
                val currentConf = _activeConfig.value
                val isSimulatingExercise = currentConf.speedKmh > 0f && currentConf.enableCadenceSimulation
                val currentSpeed = if (isSimulatingExercise) currentConf.speedKmh else 0f
                val currentBpm = heartRateSimulator.calculateBpm(currentSpeed, isResting = !isSimulatingExercise, config = currentConf)
                val currentCadence = if (isSimulatingExercise) CadenceCalculator.calculateCadence(currentSpeed, isResting = false, config = currentConf) else 0
                val calDelta = if (isSimulatingExercise) CadenceCalculator.computeCaloriesDeltaKcal(currentSpeed, isResting = false, weightKg = currentConf.userWeightKg) else 0.0
                accumulatedCalories += calDelta
                if (currentCadence > 0) {
                    accumulatedSteps += (currentCadence / 60.0)
                }

                val jitteredPoint = GeoUtils.applyGpsJitter(point, currentConf.enableGpsJitter)
                    .copy(heartRate = currentBpm, cadence = currentCadence)
                mockLocationManager.pushMockLocation(
                    geoPoint = jitteredPoint,
                    speedKmh = currentSpeed,
                    bearing = 0f,
                    caloriesKcal = accumulatedCalories,
                    steps = accumulatedSteps.toLong()
                )

                _simulationProgress.value = SimulationProgress(
                    status = SimulationStatus.RUNNING,
                    currentPoint = jitteredPoint,
                    progressPercent = 100,
                    currentSpeedKmh = currentSpeed,
                    currentHeartRateBpm = currentBpm,
                    currentCadenceSpm = currentCadence,
                    totalSteps = accumulatedSteps.toLong(),
                    caloriesBurnedKcal = accumulatedCalories,
                    isMockActive = true
                )
                delay(1000L)
            }
        }
    }

    private fun startRouteSimulation() {
        val waypoints = _routeWaypoints.value
        if (waypoints.size < 2) return

        val config = _activeConfig.value
        mockLocationManager.startMocking(config.isolateRealLocation)

        simulationJob?.cancel()

        val initialBpm = heartRateSimulator.calculateBpm(config.speedKmh, isResting = false, config = config)
        val initialNotif = buildNotification(
            title = "GeoSim: Bắt đầu lộ trình (${waypoints.size} điểm) • ❤️ ${initialBpm} BPM",
            content = "Tiến độ: 0% | Tốc độ: ${config.speedKmh.toInt()} km/h | Nhịp tim: $initialBpm BPM",
            progressPercent = 0
        )
        startForegroundCompat(NOTIFICATION_ID, initialNotif)

        simulationJob = serviceScope.launch {
            var currentWaypointIdx = 0
            var currentPos = waypoints[0]
            val totalDistance = GeoUtils.calculateTotalDistance(waypoints)
            var distanceCovered = 0.0

            while (isActive && currentWaypointIdx < waypoints.size - 1) {
                val nextWaypoint = waypoints[currentWaypointIdx + 1]
                val legDistance = GeoUtils.distanceBetween(currentPos, nextWaypoint)
                var legProgressMeters = 0.0

                val bearing = GeoUtils.bearingBetween(currentPos, nextWaypoint)

                while (isActive && legProgressMeters < legDistance) {
                    val currentConfig = _activeConfig.value
                    val currentSpeed = GeoUtils.getHumanizedSpeed(
                        currentConfig.speedKmh,
                        currentConfig.enableNaturalSpeedVariance
                    )
                    val currentBpm = heartRateSimulator.calculateBpm(
                        currentSpeed,
                        isResting = false,
                        config = currentConfig
                    )
                    val currentCadence = CadenceCalculator.calculateCadence(
                        currentSpeed,
                        isResting = false,
                        config = currentConfig
                    )
                    val calDelta = CadenceCalculator.computeCaloriesDeltaKcal(
                        currentSpeed,
                        isResting = false,
                        weightKg = currentConfig.userWeightKg
                    )
                    accumulatedCalories += calDelta
                    if (currentCadence > 0) {
                        accumulatedSteps += (currentCadence / 60.0)
                    }

                    // Meters per 1000ms
                    val stepMeters = (currentSpeed * 1000.0) / 3600.0

                    legProgressMeters += stepMeters
                    distanceCovered += stepMeters
                    if (legProgressMeters > legDistance) {
                        legProgressMeters = legDistance
                    }

                    val fraction = (legProgressMeters / legDistance).coerceIn(0.0, 1.0)
                    val interpolatedPoint = GeoUtils.interpolate(currentPos, nextWaypoint, fraction)
                    val jitteredPoint = GeoUtils.applyGpsJitter(interpolatedPoint, currentConfig.enableGpsJitter)
                        .copy(heartRate = currentBpm, cadence = currentCadence)

                    mockLocationManager.pushMockLocation(
                        geoPoint = jitteredPoint,
                        speedKmh = currentSpeed,
                        bearing = bearing,
                        caloriesKcal = accumulatedCalories,
                        steps = accumulatedSteps.toLong()
                    )

                    val remainingDist = (totalDistance - distanceCovered).coerceAtLeast(0.0)
                    val percent = if (totalDistance > 0) ((distanceCovered / totalDistance) * 100).toInt().coerceIn(0, 100) else 0
                    val remainingSeconds = if (currentSpeed > 0) ((remainingDist / ((currentSpeed * 1000.0) / 3600.0))).toLong() else 0L

                    _simulationProgress.value = SimulationProgress(
                        status = SimulationStatus.RUNNING,
                        currentPoint = jitteredPoint,
                        currentWaypointIndex = currentWaypointIdx,
                        totalWaypoints = waypoints.size,
                        progressPercent = percent,
                        currentSpeedKmh = currentSpeed,
                        currentHeartRateBpm = currentBpm,
                        currentCadenceSpm = currentCadence,
                        totalSteps = accumulatedSteps.toLong(),
                        caloriesBurnedKcal = accumulatedCalories,
                        remainingDistanceMeters = remainingDist,
                        remainingTimeSeconds = remainingSeconds,
                        isMockActive = true
                    )

                    val notif = buildNotification(
                        title = "GeoSim: Đang di chuyển ${percent}% • ❤️ ${currentBpm} BPM",
                        content = "Tốc độ: ${String.format(java.util.Locale.US, "%.1f", currentSpeed)} km/h | 👟 ${currentCadence} SPM | 🔥 ${accumulatedCalories.toInt()} kcal",
                        progressPercent = percent
                    )
                    notificationManager.notify(NOTIFICATION_ID, notif)

                    delay(1000L)
                }

                currentPos = nextWaypoint
                currentWaypointIdx++

                // Randomized rest / dwell time at waypoint
                val currentConfig = _activeConfig.value
                if (currentConfig.enableRandomPause && currentWaypointIdx < waypoints.size) {
                    val pauseDurationSec = if (currentConfig.maxPauseSeconds > currentConfig.minPauseSeconds) {
                        Random.nextInt(currentConfig.minPauseSeconds, currentConfig.maxPauseSeconds + 1)
                    } else {
                        currentConfig.minPauseSeconds
                    }

                    for (sec in pauseDurationSec downTo 1) {
                        if (!isActive) break

                        val restingBpm = heartRateSimulator.calculateBpm(0f, isResting = true, config = currentConfig)

                        _simulationProgress.value = _simulationProgress.value.copy(
                            status = SimulationStatus.RESTING,
                            pauseRemainingSeconds = sec,
                            currentSpeedKmh = 0f,
                            currentHeartRateBpm = restingBpm
                        )

                        mockLocationManager.pushMockLocation(
                            geoPoint = GeoUtils.applyGpsJitter(currentPos, currentConfig.enableGpsJitter).copy(heartRate = restingBpm),
                            speedKmh = 0f,
                            bearing = bearing,
                            caloriesKcal = accumulatedCalories,
                            steps = accumulatedSteps.toLong()
                        )

                        val notif = buildNotification(
                            title = "GeoSim: Tạm nghỉ ngẫu nhiên tại điểm $currentWaypointIdx • ❤️ ${restingBpm} BPM",
                            content = "Nghỉ an toàn: còn ${sec}s... | Tiến độ: ${_simulationProgress.value.progressPercent}%",
                            progressPercent = _simulationProgress.value.progressPercent
                        )
                        notificationManager.notify(NOTIFICATION_ID, notif)

                        delay(1000L)
                    }
                }

                // If at the end and loopRoute is enabled, reset to start
                if (currentWaypointIdx >= waypoints.size - 1 && currentConfig.loopRoute) {
                    currentWaypointIdx = 0
                    currentPos = waypoints[0]
                    distanceCovered = 0.0
                }
            }

            // Completed
            _simulationProgress.value = _simulationProgress.value.copy(
                status = SimulationStatus.IDLE,
                progressPercent = 100,
                remainingDistanceMeters = 0.0,
                currentSpeedKmh = 0f,
                pauseRemainingSeconds = 0
            )

            val finishNotif = buildNotification(
                title = "GeoSim: Đã hoàn thành lộ trình!",
                content = "Tổng quãng đường: ${GeoUtils.formatDistance(totalDistance)}",
                progressPercent = 100,
                isPaused = true
            )
            notificationManager.notify(NOTIFICATION_ID, finishNotif)
        }
    }

    private fun pauseSimulation() {
        simulationJob?.cancel()
        _simulationProgress.value = _simulationProgress.value.copy(status = SimulationStatus.PAUSED)

        val percent = _simulationProgress.value.progressPercent
        val notif = buildNotification(
            title = "GeoSim: Đã tạm dừng",
            content = "Tiến độ hiện tại: $percent% - Nhấn tiếp tục để chạy",
            progressPercent = percent,
            isPaused = true
        )
        notificationManager.notify(NOTIFICATION_ID, notif)
    }

    private fun resumeSimulation() {
        startRouteSimulation()
    }

    private fun stopMockingService() {
        simulationJob?.cancel()
        mockLocationManager.stopMocking()

        _simulationProgress.value = SimulationProgress(
            status = SimulationStatus.IDLE,
            isMockActive = false
        )

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopMockingService()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
