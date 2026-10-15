package com.example.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.data.model.HeartRateMode
import com.example.data.model.SimulationConfig
import kotlin.random.Random

class HeartRateSimulator(context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private var previousBpm: Float = 72f

    /**
     * Compute realistic Heart Rate (BPM) based on speed, mode, and natural HRV
     */
    fun calculateBpm(speedKmh: Float, isResting: Boolean, config: SimulationConfig): Int {
        if (!config.enableHeartRateSimulation) return 72

        val targetBpm: Float = if (isResting || speedKmh < 0.5f) {
            // Resting state
            68f
        } else {
            when (config.heartRateMode) {
                HeartRateMode.AUTO_BY_SPEED -> {
                    when {
                        speedKmh < 4.5f -> 85f + (speedKmh / 4.5f) * 15f // 85 - 100 BPM (Walking)
                        speedKmh < 10f -> 105f + ((speedKmh - 4.5f) / 5.5f) * 25f // 105 - 130 BPM (Jogging/Zone 2)
                        speedKmh < 18f -> 132f + ((speedKmh - 10f) / 8f) * 28f // 132 - 160 BPM (Fast running/Zone 3-4)
                        speedKmh < 30f -> 160f + ((speedKmh - 18f) / 12f) * 22f // 160 - 182 BPM (Sprint/Hard cycling)
                        else -> 175f // Driving or high speed sustained aerobic
                    }
                }
                HeartRateMode.ZONE_1_WARMUP -> 100f
                HeartRateMode.ZONE_2_FAT_BURN -> 122f
                HeartRateMode.ZONE_3_AEROBIC -> 142f
                HeartRateMode.ZONE_4_ANAEROBIC -> 162f
                HeartRateMode.ZONE_5_MAX -> 182f
                HeartRateMode.CUSTOM_FIXED -> config.customHeartRateBpm.toFloat()
            }
        }

        // Smooth physiological ramp (heart rate doesn't jump instantly, it ramps up/down smoothly)
        previousBpm += (targetBpm - previousBpm) * 0.15f

        // Natural HRV (Heart Rate Variability) noise: ±2 BPM
        val hrvNoise = if (config.enableHrvFluctuation) {
            Random.nextFloat() * 4f - 2f
        } else 0f

        val finalBpm = (previousBpm + hrvNoise).toInt().coerceIn(40, 220)
        return finalBpm
    }

    /**
     * Trigger a realistic "lub-dub" heartbeat haptic pulse
     */
    fun pulseHeartbeat() {
        if (vibrator == null || !vibrator.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // S1 ("lub") 25ms, gap 60ms, S2 ("dub") 35ms
                val timings = longArrayOf(0, 25, 60, 35)
                val amplitudes = intArrayOf(0, 80, 0, 110)
                val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 25, 60, 35), -1)
            }
        } catch (_: Exception) {}
    }
}
