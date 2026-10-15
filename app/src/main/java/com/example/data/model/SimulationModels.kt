package com.example.data.model

import java.io.Serializable

data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 15.0,
    val name: String = "",
    val time: Long = System.currentTimeMillis(),
    val heartRate: Int = 0, // Heart rate in BPM for GPX export
    val cadence: Int = 0    // Cadence in SPM for GPX export
) : Serializable

enum class SimulationStatus {
    IDLE,
    RUNNING,
    PAUSED,
    RESTING // Paused for randomized dwell time at waypoint
}

enum class HeartRateMode(val title: String, val minBpm: Int, val maxBpm: Int, val description: String) {
    AUTO_BY_SPEED("Tự động theo vận tốc", 65, 185, "Tự động tăng giảm nhịp tim dựa trên tốc độ di chuyển thật"),
    ZONE_1_WARMUP("Zone 1: Khởi động / Phục hồi", 90, 110, "90 - 110 BPM (Khởi động nhẹ nhàng)"),
    ZONE_2_FAT_BURN("Zone 2: Đốt mỡ & Sức bền", 111, 130, "111 - 130 BPM (Vùng chạy dẻo dai cơ bản)"),
    ZONE_3_AEROBIC("Zone 3: Hiếu khí (Aerobic)", 131, 150, "131 - 150 BPM (Tăng cường tim mạch & sức bền)"),
    ZONE_4_ANAEROBIC("Zone 4: Kỵ khí (Anaerobic)", 151, 170, "151 - 170 BPM (Luyện tập cường độ cao)"),
    ZONE_5_MAX("Zone 5: Cực đại (VO2 Max)", 171, 195, "171 - 195 BPM (Bứt tốc đỉnh điểm)"),
    CUSTOM_FIXED("Tùy chỉnh thủ công", 40, 220, "Cố định nhịp tim theo ý muốn")
}

data class SimulationConfig(
    val speedKmh: Float = 25f, // Speed in km/h
    val enableNaturalSpeedVariance: Boolean = true, // ±5-8% human speed variance
    val enableGpsJitter: Boolean = true, // 1-2m natural GPS drift
    val enableRandomPause: Boolean = true, // Random rest at waypoints
    val minPauseSeconds: Int = 5,
    val maxPauseSeconds: Int = 20,
    val enableBreathingVibration: Boolean = true, // Phone vibration synced to breathing/footsteps
    val vibrationPattern: VibrationCadence = VibrationCadence.BREATHING_RELAXED,
    val loopRoute: Boolean = false,
    val isolateRealLocation: Boolean = true, // Override both GPS & Network
    // Heart Rate Simulation features
    val enableHeartRateSimulation: Boolean = true,
    val heartRateMode: HeartRateMode = HeartRateMode.AUTO_BY_SPEED,
    val customHeartRateBpm: Int = 135,
    val enableHeartbeatVibration: Boolean = false, // Haptic heartbeat pulsing
    val enableHrvFluctuation: Boolean = true, // Natural Heart Rate Variability ±2-4 BPM
    // Cadence & Calories & Steps
    val enableCadenceSimulation: Boolean = true,
    val autoCadenceBySpeed: Boolean = true,
    val manualCadenceSpm: Int = 165,
    val userWeightKg: Float = 68f,
    // Mechanical step shockwave trigger (stimulates hardware accelerometer so tracking apps count steps without shaking)
    val enablePhysicalStepVibration: Boolean = true,
    val stepVibrationStrength: Int = 255
)

enum class VibrationCadence(val label: String, val inhaleMs: Long, val exhaleMs: Long) {
    BREATHING_RELAXED("Nhịp thở 4-4s (Thư giãn)", 4000L, 4000L),
    BREATHING_FAST("Nhịp thở 2-2s (Thể thao)", 2000L, 2000L),
    RUNNING_CADENCE("Nhịp bước chạy (160 spm)", 375L, 375L),
    OFF("Tắt rung nhịp thở", 0L, 0L)
}

data class SimulationProgress(
    val status: SimulationStatus = SimulationStatus.IDLE,
    val currentPoint: GeoPoint? = null,
    val currentWaypointIndex: Int = 0,
    val totalWaypoints: Int = 0,
    val progressPercent: Int = 0,
    val currentSpeedKmh: Float = 0f,
    val currentHeartRateBpm: Int = 72,
    val currentCadenceSpm: Int = 0,
    val totalSteps: Long = 0L,
    val caloriesBurnedKcal: Double = 0.0,
    val remainingDistanceMeters: Double = 0.0,
    val remainingTimeSeconds: Long = 0,
    val pauseRemainingSeconds: Int = 0,
    val isMockActive: Boolean = false,
    val isStepTriggerActive: Boolean = true
)
