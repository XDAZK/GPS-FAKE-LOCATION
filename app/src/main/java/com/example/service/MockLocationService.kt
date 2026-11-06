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
import android.os.PowerManager
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
    private var wakeLock: PowerManager.WakeLock? = null

    private lateinit var mockLocationManager: MockLocationManager
    private lateinit var heartRateSimulator: HeartRateSimulator
    private lateinit var notificationManager: NotificationManager

    private var accumulatedSteps = 0.0
    private var accumulatedCalories = 0.0

    private var routeCurrentWaypointIdx = 0
    private var routeIsReversing = false
    private var simulationElapsedSeconds = 0L
    private var routeCurrentPos: GeoPoint? = null
    private var routeLegProgressMeters = 0.0
    private var routeDistanceCovered = 0.0
    private var runnerLateralOffsetMeters = 0.0
    private var smoothedRunningSpeedKmh = 0f
    private var speedOrganicNoise = 0.0
    private var nextRestAtElapsedSeconds = 0L
    private val activeTrackList = ArrayList<GeoPoint>()

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

        private val _recordedTrack = MutableStateFlow<List<GeoPoint>>(emptyList())
        val recordedTrack = _recordedTrack.asStateFlow()

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

    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GeoSim::MockLocationWakeLock").apply {
                    setReferenceCounted(false)
                }
            }
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(24 * 60 * 60 * 1000L) // up to 24h
            }
        } catch (_: Exception) {}
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            // Android OS restarted the service after low-memory kill (START_STICKY).
            // Resume the persisted active mock command instead of stopping!
            val savedPoint = MockStateStore.getActivePoint(this)
            if (savedPoint != null) {
                if (MockStateStore.isRouteActive(this) && _routeWaypoints.value.size >= 2) {
                    startRouteSimulation()
                } else {
                    startMockingSinglePoint(savedPoint)
                }
                return START_STICKY
            } else {
                stopSelf()
                return START_NOT_STICKY
            }
        }

        val action = intent.action ?: return START_NOT_STICKY

        when (action) {
            ACTION_START_POINT -> {
                val lat = intent.getDoubleExtra(EXTRA_LAT, 0.0)
                val lng = intent.getDoubleExtra(EXTRA_LNG, 0.0)
                val name = intent.getStringExtra(EXTRA_NAME) ?: "Vị trí đã chọn"
                val point = GeoPoint(lat, lng, name = name)
                MockStateStore.setActivePoint(this, point, isRoute = false)
                startMockingSinglePoint(point)
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

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // User swiped the app away from Recent Apps:
        // Intentionally keep MockLocationService running in the background!
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
        acquireWakeLock()
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

                val jitteredPoint = GeoUtils.applyGpsJitter(point, currentConf.enableGpsJitter, currentConf.maxLateralDriftMeters)
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
                // 250ms high frequency like elsewhere (ensures real GPS fix never surfaces in cache)
                delay(250L)
            }
        }
    }

    private fun startRouteSimulation(isResume: Boolean = false) {
        acquireWakeLock()
        val waypoints = _routeWaypoints.value
        if (waypoints.size < 2) return

        val config = _activeConfig.value
        mockLocationManager.startMocking(config.isolateRealLocation)

        simulationJob?.cancel()

        if (!isResume || routeCurrentPos == null) {
            routeCurrentWaypointIdx = 0
            routeIsReversing = false
            simulationElapsedSeconds = 0L
            routeCurrentPos = waypoints[0]
            routeLegProgressMeters = 0.0
            routeDistanceCovered = 0.0
            runnerLateralOffsetMeters = 0.0
            val startSpeed = if (config.enablePaceRange) {
                GeoUtils.paceMinutesToSpeedKmh(config.maxPaceMinPerKm.toDouble())
            } else {
                (config.speedKmh * 0.72f).coerceAtLeast(4.0f)
            }
            smoothedRunningSpeedKmh = startSpeed
            speedOrganicNoise = 0.0
            accumulatedSteps = 0.0
            accumulatedCalories = 0.0
            val minRestSec = config.minRestIntervalMinutes * 60L
            val maxRestSec = (config.maxRestIntervalMinutes + 1) * 60L
            nextRestAtElapsedSeconds = Random.nextLong(minRestSec.coerceAtLeast(180L), maxRestSec.coerceAtLeast(360L))
            heartRateSimulator.reset(initialBpm = 75f)
            CadenceCalculator.reset()
            synchronized(activeTrackList) {
                activeTrackList.clear()
                _recordedTrack.value = emptyList()
            }
        }

        val initialBpm = heartRateSimulator.calculateBpm(config.speedKmh, isResting = false, config = config, secondsElapsed = simulationElapsedSeconds)
        val initialNotif = buildNotification(
            title = "GeoSim: Đang chạy lộ trình (${waypoints.size} điểm) • ❤️ ${initialBpm} BPM",
            content = "Pace: ${GeoUtils.formatPace(config.speedKmh)} | ${config.speedKmh.toInt()} km/h",
            progressPercent = _simulationProgress.value.progressPercent
        )
        startForegroundCompat(NOTIFICATION_ID, initialNotif)

        simulationJob = serviceScope.launch {
            val totalDistance = GeoUtils.calculateTotalDistance(waypoints)

            while (isActive) {
                val currentConfig = _activeConfig.value

                // If loopRoute is disabled, check if completed
                if (!currentConfig.loopRoute) {
                    if (!routeIsReversing && routeCurrentWaypointIdx >= waypoints.size - 1) break
                    if (routeIsReversing && routeCurrentWaypointIdx <= 0) break
                }

                // Leg boundaries depending on whether running forward or in reverse (out-and-back)
                val startOfLeg: GeoPoint
                val nextWaypoint: GeoPoint
                if (!routeIsReversing) {
                    startOfLeg = waypoints[routeCurrentWaypointIdx]
                    nextWaypoint = waypoints[routeCurrentWaypointIdx + 1]
                } else {
                    startOfLeg = waypoints[routeCurrentWaypointIdx]
                    nextWaypoint = waypoints[routeCurrentWaypointIdx - 1]
                }

                val legDistance = GeoUtils.distanceBetween(startOfLeg, nextWaypoint)
                val bearing = GeoUtils.bearingBetween(startOfLeg, nextWaypoint)

                while (isActive && routeLegProgressMeters < legDistance) {
                    simulationElapsedSeconds++
                    val activeConf = _activeConfig.value

                    // Dynamic pace calculation: either dynamic across Pace Range (e.g. 4:00 - 15:00 min/km)
                    // or single pace with smooth initial warmup
                    val targetBaseSpeed = if (activeConf.enablePaceRange) {
                        GeoUtils.getPaceRangeDynamicSpeed(
                            distanceCoveredMeters = routeDistanceCovered,
                            secondsElapsed = simulationElapsedSeconds,
                            minPaceMinPerKm = activeConf.minPaceMinPerKm,
                            maxPaceMinPerKm = activeConf.maxPaceMinPerKm,
                            enableWarmup = true
                        )
                    } else {
                        val warmupFactor = if (simulationElapsedSeconds < 50L) {
                            (0.72 + 0.28 * (simulationElapsedSeconds / 50.0)).toFloat()
                        } else 1.0f
                        (activeConf.speedKmh * warmupFactor)
                    }

                    // Human physical acceleration/deceleration momentum (avoids sharp cliff drops!)
                    // Deceleration is gradual (~0.08 km/h per sec), taking 50-70s to slowly descend
                    smoothedRunningSpeedKmh = GeoUtils.smoothTransitionSpeed(
                        currentSpeedKmh = smoothedRunningSpeedKmh,
                        targetSpeedKmh = targetBaseSpeed,
                        maxAccelPerSec = 0.12f,
                        maxDecelPerSec = 0.08f
                    )

                    // Non-periodic organic speed drift (AR-1 process, ±0.18 km/h, no sine wave comb teeth)
                    if (activeConf.enableNaturalSpeedVariance) {
                        speedOrganicNoise = speedOrganicNoise * 0.94 + Random.nextDouble(-0.025, 0.025)
                        speedOrganicNoise = speedOrganicNoise.coerceIn(-0.18, 0.18)
                    } else {
                        speedOrganicNoise = 0.0
                    }

                    val currentSpeed = (smoothedRunningSpeedKmh + speedOrganicNoise.toFloat()).coerceAtLeast(1.0f)

                    // Human cardiac response with physiological lag
                    val currentBpm = heartRateSimulator.calculateBpm(
                        currentSpeed,
                        isResting = false,
                        config = activeConf,
                        secondsElapsed = simulationElapsedSeconds
                    )

                    val currentCadence = CadenceCalculator.calculateCadence(
                        currentSpeed,
                        isResting = false,
                        config = activeConf,
                        secondsElapsed = simulationElapsedSeconds
                    )

                    val calDelta = CadenceCalculator.computeCaloriesDeltaKcal(
                        currentSpeed,
                        isResting = false,
                        weightKg = activeConf.userWeightKg
                    )
                    accumulatedCalories += calDelta
                    if (currentCadence > 0) {
                        accumulatedSteps += (currentCadence / 60.0)
                    }

                    // Distance traveled in this 1 second step
                    val stepMeters = (currentSpeed * 1000.0) / 3600.0
                    routeLegProgressMeters += stepMeters
                    routeDistanceCovered += stepMeters
                    if (routeLegProgressMeters > legDistance) {
                        routeLegProgressMeters = legDistance
                    }

                    val fraction = if (legDistance > 0.001) (routeLegProgressMeters / legDistance).coerceIn(0.0, 1.0) else 1.0
                    val interpolatedPoint = GeoUtils.interpolate(startOfLeg, nextWaypoint, fraction)
                    routeCurrentPos = interpolatedPoint

                    // Smooth lateral drift along the line: strictly limited to maxLateralDriftMeters (max 2.0m)
                    if (activeConf.enableGpsJitter) {
                        val maxDrift = activeConf.maxLateralDriftMeters.coerceIn(0.2f, 2.0f).toDouble()
                        val targetOffset = Random.nextDouble(-maxDrift, maxDrift)
                        val delta = (targetOffset - runnerLateralOffsetMeters) * 0.08
                        runnerLateralOffsetMeters = (runnerLateralOffsetMeters + delta.coerceIn(-0.15, 0.15)).coerceIn(-maxDrift, maxDrift)
                    } else {
                        runnerLateralOffsetMeters = 0.0
                    }

                    // Realistic undulating road elevation (strictly under 5m, total ascent max 7m)
                    val naturalAltitude = GeoUtils.calculateNaturalAltitude(routeDistanceCovered)

                    val jitteredPoint = if (activeConf.enableGpsJitter) {
                        GeoUtils.applySmoothRunnerOffset(
                            point = interpolatedPoint,
                            bearingDeg = bearing,
                            lateralOffsetMeters = runnerLateralOffsetMeters,
                            maxLateralMeters = activeConf.maxLateralDriftMeters
                        ).copy(altitude = naturalAltitude, heartRate = currentBpm, cadence = currentCadence)
                    } else {
                        interpolatedPoint.copy(altitude = naturalAltitude, heartRate = currentBpm, cadence = currentCadence)
                    }

                    mockLocationManager.pushMockLocation(
                        geoPoint = jitteredPoint,
                        speedKmh = currentSpeed,
                        bearing = bearing,
                        caloriesKcal = accumulatedCalories,
                        steps = accumulatedSteps.toLong()
                    )

                    val recordedPoint = jitteredPoint.copy(
                        time = System.currentTimeMillis(),
                        altitude = naturalAltitude
                    )
                    synchronized(activeTrackList) {
                        activeTrackList.add(recordedPoint)
                        _recordedTrack.value = activeTrackList.toList()
                    }

                    // Calculate 1km (1000m) Lap progression
                    val currentLap = (routeDistanceCovered / 1000.0).toInt() + 1
                    val lapDistanceCovered = routeDistanceCovered % 1000.0
                    val lapPercent = ((lapDistanceCovered / 1000.0) * 100).toInt().coerceIn(0, 100)

                    val singlePassDist = if (totalDistance > 0) totalDistance else 1000.0
                    val legFractionOfTotal = (routeDistanceCovered % singlePassDist)
                    val percent = ((legFractionOfTotal / singlePassDist) * 100).toInt().coerceIn(0, 100)
                    val remainingLegDist = (singlePassDist - legFractionOfTotal).coerceAtLeast(0.0)
                    val remainingSeconds = if (currentSpeed > 0) (remainingLegDist / ((currentSpeed * 1000.0) / 3600.0)).toLong() else 0L

                    _simulationProgress.value = SimulationProgress(
                        status = SimulationStatus.RUNNING,
                        currentPoint = jitteredPoint,
                        currentWaypointIndex = routeCurrentWaypointIdx,
                        totalWaypoints = waypoints.size,
                        progressPercent = percent,
                        currentSpeedKmh = currentSpeed,
                        currentHeartRateBpm = currentBpm,
                        currentCadenceSpm = currentCadence,
                        totalSteps = accumulatedSteps.toLong(),
                        caloriesBurnedKcal = accumulatedCalories,
                        distanceCoveredMeters = routeDistanceCovered,
                        remainingDistanceMeters = remainingLegDist,
                        remainingTimeSeconds = remainingSeconds,
                        isMockActive = true,
                        isLoopReversing = routeIsReversing,
                        currentLapNumber = currentLap,
                        currentLapDistanceMeters = lapDistanceCovered,
                        lapTargetMeters = 1000.0
                    )

                    val dirBadge = if (routeIsReversing) " [Vòng về] " else " "
                    val notif = buildNotification(
                        title = "GeoSim: Lap $currentLap (${GeoUtils.formatDistance(lapDistanceCovered)} / 1km) • ❤️ ${currentBpm} BPM",
                        content = "Tổng: ${GeoUtils.formatDistance(routeDistanceCovered)}$dirBadge| Pace: ${GeoUtils.formatPace(currentSpeed)} | 👟 ${currentCadence} SPM",
                        progressPercent = lapPercent
                    )
                    notificationManager.notify(NOTIFICATION_ID, notif)

                    delay(1000L)

                    // Randomized rest pause (10-20s duration, randomized interval e.g. every 6-12 minutes)
                    if (activeConf.enablePeriodicRest10m && simulationElapsedSeconds >= nextRestAtElapsedSeconds) {
                        val restDurationSec = Random.nextInt(
                            activeConf.minPauseSeconds.coerceAtLeast(10),
                            (activeConf.maxPauseSeconds + 1).coerceAtLeast(20)
                        ) // 10 to 20 seconds
                        for (sec in restDurationSec downTo 1) {
                            if (!isActive) break

                            val restingBpm = heartRateSimulator.calculateBpm(
                                0f,
                                isResting = true,
                                config = activeConf,
                                secondsElapsed = simulationElapsedSeconds
                            )

                            _simulationProgress.value = _simulationProgress.value.copy(
                                status = SimulationStatus.RESTING,
                                pauseRemainingSeconds = sec,
                                currentSpeedKmh = 0f,
                                currentHeartRateBpm = restingBpm,
                                currentCadenceSpm = 0,
                                distanceCoveredMeters = routeDistanceCovered,
                                isLoopReversing = routeIsReversing
                            )

                            mockLocationManager.pushMockLocation(
                                geoPoint = jitteredPoint.copy(heartRate = restingBpm, cadence = 0),
                                speedKmh = 0f,
                                bearing = bearing,
                                caloriesKcal = accumulatedCalories,
                                steps = accumulatedSteps.toLong()
                            )

                            val restNotif = buildNotification(
                                title = "GeoSim: Tạm nghỉ ${restDurationSec}s ngẫu nhiên • ❤️ ${restingBpm} BPM",
                                content = "Đã chạy: ${GeoUtils.formatDistance(routeDistanceCovered)} | Nghỉ: ${sec}s...",
                                progressPercent = lapPercent
                            )
                            notificationManager.notify(NOTIFICATION_ID, restNotif)

                            delay(1000L)
                        }
                        smoothedRunningSpeedKmh = 2.5f // Smooth acceleration back up after rest
                        val minRestSec = activeConf.minRestIntervalMinutes * 60L
                        val maxRestSec = (activeConf.maxRestIntervalMinutes + 1) * 60L
                        val nextIntervalSec = Random.nextLong(minRestSec.coerceAtLeast(180L), maxRestSec.coerceAtLeast(360L))
                        nextRestAtElapsedSeconds = simulationElapsedSeconds + nextIntervalSec
                    }
                }

                routeCurrentPos = nextWaypoint
                routeLegProgressMeters = 0.0

                // Advance or reverse waypoint index (Chạy xuyên suốt qua mốc, không dừng chờ 5s)
                if (!routeIsReversing) {
                    routeCurrentWaypointIdx++
                    if (routeCurrentWaypointIdx >= waypoints.size - 1) {
                        // Reached the end point!
                        if (currentConfig.loopRoute) {
                            // Reverse direction: run backward from last point back to start
                            routeIsReversing = true
                        } else {
                            break
                        }
                    }
                } else {
                    routeCurrentWaypointIdx--
                    if (routeCurrentWaypointIdx <= 0) {
                        // Reached the start point!
                        if (currentConfig.loopRoute) {
                            // Forward direction: run forward again
                            routeIsReversing = false
                        } else {
                            break
                        }
                    }
                }
            }

            // Completed
            _simulationProgress.value = _simulationProgress.value.copy(
                status = SimulationStatus.IDLE,
                progressPercent = 100,
                distanceCoveredMeters = routeDistanceCovered,
                remainingDistanceMeters = 0.0,
                currentSpeedKmh = 0f,
                pauseRemainingSeconds = 0,
                isLoopReversing = false
            )

            val finishNotif = buildNotification(
                title = "GeoSim: Đã hoàn thành lộ trình!",
                content = "Tổng quãng đường: ${GeoUtils.formatDistance(routeDistanceCovered)}",
                progressPercent = 100,
                isPaused = true
            )
            notificationManager.notify(NOTIFICATION_ID, finishNotif)
        }
    }

    private fun pauseSimulation() {
        simulationJob?.cancel()
        _simulationProgress.value = _simulationProgress.value.copy(
            status = SimulationStatus.PAUSED,
            currentSpeedKmh = 0f
        )

        // Hold paused position so device GPS doesn't leak physical location
        val pausedPoint = _simulationProgress.value.currentPoint ?: routeCurrentPos
        if (pausedPoint != null) {
            simulationJob = serviceScope.launch {
                while (isActive) {
                    mockLocationManager.pushMockLocation(
                        geoPoint = pausedPoint,
                        speedKmh = 0f,
                        bearing = 0f,
                        caloriesKcal = accumulatedCalories,
                        steps = accumulatedSteps.toLong()
                    )
                    delay(500L)
                }
            }
        }

        val percent = _simulationProgress.value.progressPercent
        val notif = buildNotification(
            title = "GeoSim: Đã tạm dừng tại điểm",
            content = "Tiến độ: $percent% - Nhấn tiếp tục để tiếp tục lộ trình",
            progressPercent = percent,
            isPaused = true
        )
        notificationManager.notify(NOTIFICATION_ID, notif)
    }

    private fun resumeSimulation() {
        startRouteSimulation(isResume = true)
    }

    private fun stopMockingService() {
        simulationJob?.cancel()
        releaseWakeLock()
        MockStateStore.clearActive(this)
        mockLocationManager.stopMocking()

        routeCurrentPos = null
        routeCurrentWaypointIdx = 0
        routeIsReversing = false
        simulationElapsedSeconds = 0L
        routeLegProgressMeters = 0.0
        routeDistanceCovered = 0.0

        _simulationProgress.value = SimulationProgress(
            status = SimulationStatus.IDLE,
            isMockActive = false
        )

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
        MockStateStore.clearActive(this)
        stopMockingService()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
