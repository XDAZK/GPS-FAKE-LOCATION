package com.example.util

import com.example.data.model.SimulationConfig
import kotlin.random.Random

object CadenceCalculator {

    /**
     * Compute realistic Cadence (Steps Per Minute - SPM) based on movement speed
     */
    fun calculateCadence(speedKmh: Float, isResting: Boolean, config: SimulationConfig): Int {
        if (!config.enableCadenceSimulation || isResting || speedKmh < 0.8f) {
            return 0
        }

        if (!config.autoCadenceBySpeed) {
            val jitter = if (config.enableHrvFluctuation) Random.nextInt(-2, 3) else 0
            return (config.manualCadenceSpm + jitter).coerceIn(40, 240)
        }

        val baseSpm = when {
            speedKmh < 5.5f -> 95f + (speedKmh / 5.5f) * 25f // 95 - 120 SPM (Walking)
            speedKmh < 10.5f -> 125f + ((speedKmh - 5.5f) / 5.0f) * 35f // 125 - 160 SPM (Jogging)
            speedKmh < 18.0f -> 160f + ((speedKmh - 10.5f) / 7.5f) * 25f // 160 - 185 SPM (Running)
            else -> 185f + ((speedKmh - 18.0f) / 10.0f) * 15f // 185 - 200+ SPM (Sprint)
        }

        val variance = if (config.enableHrvFluctuation) Random.nextFloat() * 4f - 2f else 0f
        return (baseSpm + variance).toInt().coerceIn(0, 240)
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
