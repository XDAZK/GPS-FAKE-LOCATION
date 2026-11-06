package com.example.util

import com.example.data.model.SimulationConfig
import kotlin.random.Random

object CadenceCalculator {

    /**
     * Compute realistic Cadence (Steps Per Minute - SPM) based on movement speed.
     * Realistic human rhythm:
     * - Does NOT oscillate up and down like a clockwork sine wave ("không lên xuống đều như tăm")
     * - Under normal steady running, only fluctuates slightly by 3-4 SPM ("chỉ lệch 3-4")
     * - Occasional recovery dip: every ~2 minutes, relaxes by ~10 SPM for ~10 seconds ("lâu lâu lệch 10 như nghỉ ngơi")
     */
    fun calculateCadence(
        speedKmh: Float,
        isResting: Boolean,
        config: SimulationConfig,
        secondsElapsed: Long = 0L
    ): Int {
        if (!config.enableCadenceSimulation || isResting || speedKmh < 0.8f) {
            return 0
        }

        val baseSpm = if (!config.autoCadenceBySpeed) {
            config.manualCadenceSpm.toFloat()
        } else {
            when {
                speedKmh < 5.5f -> 100f + (speedKmh / 5.5f) * 20f // 100 - 120 SPM (Walking)
                speedKmh < 10.5f -> 135f + ((speedKmh - 5.5f) / 5.0f) * 25f // 135 - 160 SPM (Jogging)
                speedKmh < 18.0f -> 160f + ((speedKmh - 10.5f) / 7.5f) * 25f // 160 - 185 SPM (Running)
                else -> 185f + ((speedKmh - 18.0f) / 10.0f) * 15f // 185 - 200 SPM (Sprint)
            }
        }

        val t = secondsElapsed.toDouble()

        // 1. Normal human stride steady fluctuation: only deviates by ±3 to 4 SPM ("chỉ lệch 3-4")
        val normalDeviation = if (config.enableHrvFluctuation) {
            // Smooth natural drift between -3.5 and +3.5 SPM
            (kotlin.math.sin(t * 0.18) * 2.2 + kotlin.math.cos(t * 0.07) * 1.3).toFloat()
        } else {
            0f
        }

        // 2. Occasional recovery dip ("lâu lâu lệch 10 như nghỉ ngơi")
        // Occurs every ~120s (from second 95 to 108 of the 120s window)
        val cycleTime = (secondsElapsed % 120L).toDouble()
        val restDip = if (t >= 50.0 && cycleTime in 95.0..108.0) {
            val dipProgress = (cycleTime - 95.0) / 13.0
            (-10.0 * kotlin.math.sin(dipProgress * Math.PI)).toFloat() // Dips down by ~10 SPM
        } else {
            0f
        }

        val finalSpm = (baseSpm + normalDeviation + restDip).toInt().coerceIn(40, 240)
        return finalSpm
    }

    /**
     * Compute calories burned for 1 second of exercise based on METs and user weight
     */
    fun computeCaloriesDeltaKcal(speedKmh: Float, isResting: Boolean, weightKg: Float): Double {
        if (isResting || speedKmh < 0.5f) {
            // Resting basal metabolic rate: MET 1.2
            return (1.2 * 3.5 * weightKg / 200.0) / 60.0
        }

        val met = when {
            speedKmh < 5.0f -> 3.5 // Walking
            speedKmh < 9.0f -> 7.0 // Moderate Jogging
            speedKmh < 13.0f -> 10.2 // Running
            speedKmh < 17.0f -> 12.8 // Fast Running
            else -> 15.0 // Sprint / High intensity
        }

        // Kcal per minute = MET * 3.5 * weightKg / 200 -> Kcal per second = / 60
        return (met * 3.5 * weightKg / 200.0) / 60.0
    }
}
