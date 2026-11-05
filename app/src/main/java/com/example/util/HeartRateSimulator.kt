package com.example.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.data.model.HeartRateMode
import com.example.data.model.SimulationConfig
import kotlin.math.roundToInt
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
     * Compute realistic Heart Rate (BPM) based on speed, mode, and natural HRV with
     * rich physiological wave dynamics (respiration RSA, Mayer blood-pressure waves, cardiac drift)
     * so heart rate oscillates across a realistic range (10-30 BPM range) rather than being flat.
     */
    fun calculateBpm(
        speedKmh: Float,
        isResting: Boolean,
        config: SimulationConfig,
        secondsElapsed: Long = 0L
    ): Int {
        if (!config.enableHeartRateSimulation) return 72

        val targetBpm: Float = if (isResting || speedKmh < 0.5f) {
            // Resting state with slight breathing oscillation
            val restWave = (kotlin.math.sin(secondsElapsed * 0.25) * 3.0).toFloat()
            68f + restWave
        } else {
            when (config.heartRateMode) {
                HeartRateMode.AUTO_BY_SPEED -> {
                    when {
                        speedKmh < 4.5f -> 88f + (speedKmh / 4.5f) * 18f // 88 - 106 BPM (Walking)
                        speedKmh < 9.5f -> 108f + ((speedKmh - 4.5f) / 5.0f) * 28f // 108 - 136 BPM (Jogging/Zone 2)
                        speedKmh < 15.0f -> 136f + ((speedKmh - 9.5f) / 5.5f) * 28f // 136 - 164 BPM (Running/Zone 3-4)
                        speedKmh < 22.0f -> 164f + ((speedKmh - 15.0f) / 7.0f) * 20f // 164 - 184 BPM (Sprint/Interval)
                        else -> 178f
                    }
                }
                HeartRateMode.ZONE_1_WARMUP -> 102f
                HeartRateMode.ZONE_2_FAT_BURN -> 124f
                HeartRateMode.ZONE_3_AEROBIC -> 144f
                HeartRateMode.ZONE_4_ANAEROBIC -> 164f
                HeartRateMode.ZONE_5_MAX -> 182f
                HeartRateMode.CUSTOM_FIXED -> config.customHeartRateBpm.toFloat()
            }
        }

        // Smooth physiological ramp towards target (12% per second)
        previousBpm += (targetBpm - previousBpm) * 0.12f

        // Multi-frequency biological cardiac waves:
        // 1. Respiration Sinus Arrhythmia (RSA, ~15 breaths/min, period 4-5s): ±4 to 6 BPM
        val t = secondsElapsed.toDouble()
        val rsaWave = kotlin.math.sin(t * 0.35) * 4.8

        // 2. Mayer blood-pressure waves (period ~10-15s): ±5 to 8 BPM
        val mayerWave = kotlin.math.cos(t * 0.08) * 6.5

        // 3. Long-term cardiac interval drift (period ~60s): ±5 to 7 BPM
        val intervalDrift = kotlin.math.sin(t * 0.02) * 5.2

        // 4. Natural HRV micro-noise
        val microNoise = if (config.enableHrvFluctuation) {
            (Random.nextFloat() * 6f - 3f)
        } else 0f

        val totalOscillation = if (config.enableHrvFluctuation) {
            (rsaWave + mayerWave + intervalDrift + microNoise).toFloat()
        } else {
            (rsaWave * 0.5 + mayerWave * 0.5).toFloat()
        }

        val finalBpm = (previousBpm + totalOscillation).roundToInt().coerceIn(45, 215)
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
