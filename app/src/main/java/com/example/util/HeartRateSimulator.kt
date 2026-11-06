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
    private var hrvDrift: Double = 0.0

    fun reset(initialBpm: Float = 72f) {
        previousBpm = initialBpm
        hrvDrift = 0.0
    }

    /**
     * Compute realistic Heart Rate (BPM) based on speed, mode, and cardiac lag.
     * Uses physiological cardiac inertia:
     * - Rising rate (~3.5%/s): takes 30-40s to reach higher heart rate zone
     * - Recovery rate (~1.6%/s): takes 60-90s to slowly recover down, preventing sudden cliff drops
     * - Organic AR(1) HRV noise: non-periodic natural biological variation, eliminates comb/barcode teeth
     */
    fun calculateBpm(
        speedKmh: Float,
        isResting: Boolean,
        config: SimulationConfig,
        secondsElapsed: Long = 0L
    ): Int {
        if (!config.enableHeartRateSimulation) return 72

        val targetBpm: Float = if (isResting || speedKmh < 0.5f) {
            74f // Realistic standing recovery heart rate
        } else {
            // Gradual cardiac drift due to body heat & fatigue (+0 to +7 BPM over 1.5h)
            val fatigueDrift = ((secondsElapsed / 1800.0).coerceAtMost(3.0) * 2.2f).toFloat()

            val speedBase = when (config.heartRateMode) {
                HeartRateMode.AUTO_BY_SPEED -> {
                    when {
                        speedKmh < 4.5f -> 86f + (speedKmh / 4.5f) * 18f // 86 - 104 BPM (Walking)
                        speedKmh < 8.5f -> 104f + ((speedKmh - 4.5f) / 4.0f) * 24f // 104 - 128 BPM (Jogging/Zone 2)
                        speedKmh < 13.0f -> 128f + ((speedKmh - 8.5f) / 4.5f) * 30f // 128 - 158 BPM (Running/Zone 3-4)
                        speedKmh < 18.0f -> 158f + ((speedKmh - 13.0f) / 5.0f) * 24f // 158 - 182 BPM (Sprint/Interval)
                        else -> 182f
                    }
                }
                HeartRateMode.ZONE_1_WARMUP -> 102f
                HeartRateMode.ZONE_2_FAT_BURN -> 124f
                HeartRateMode.ZONE_3_AEROBIC -> 144f
                HeartRateMode.ZONE_4_ANAEROBIC -> 164f
                HeartRateMode.ZONE_5_MAX -> 182f
                HeartRateMode.CUSTOM_FIXED -> config.customHeartRateBpm.toFloat()
            }
            speedBase + fatigueDrift
        }

        // Physiological cardiac inertia (asymmetric: slower recovery than ramp up)
        val cardiacLagRate = if (targetBpm >= previousBpm) 0.040f else 0.022f
        previousBpm += (targetBpm - previousBpm) * cardiacLagRate

        // Non-periodic organic HRV noise (AR-1 / Ornstein-Uhlenbeck process, ±2.5 BPM)
        if (config.enableHrvFluctuation) {
            hrvDrift = hrvDrift * 0.92 + (Random.nextDouble(-0.35, 0.35))
            hrvDrift = hrvDrift.coerceIn(-2.5, 2.5)
        } else {
            hrvDrift = 0.0
        }

        // Occasional gentle rest dip every ~120s
        val t = secondsElapsed.toDouble()
        val cycleTime = (secondsElapsed % 120L).toDouble()
        val restDip = if (t >= 50.0 && cycleTime in 95.0..108.0) {
            val dipProgress = (cycleTime - 95.0) / 13.0
            (-7.0 * kotlin.math.sin(dipProgress * Math.PI)).toFloat()
        } else {
            0f
        }

        val finalBpm = (previousBpm + hrvDrift.toFloat() + restDip).roundToInt().coerceIn(45, 215)
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
