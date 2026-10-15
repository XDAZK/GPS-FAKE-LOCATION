package com.example.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.data.model.VibrationCadence

class VibrationHelper(context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    /**
     * Sharp, high-intensity mechanical shockwave pulse (55-65ms at 255 peak amplitude)
     * specifically tuned to stimulate the phone's internal MEMS hardware accelerometer.
     * This creates a physical G-force impulse (>1.3G) detected by Android's StepDetector
     * and running apps (Strava, Nike Run Club, Viettel, Pedometer, etc.) so that they
     * count steps and cadence (SPM) continuously without requiring the user to shake
     * the phone by hand!
     */
    fun pulseStepShockwave(strength: Int = 255) {
        if (vibrator == null || !vibrator.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val clamped = strength.coerceIn(1, 255)
                val effect = VibrationEffect.createOneShot(55L, clamped)
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(55L)
            }
        } catch (_: Exception) {
            // Ignore if vibration fails on specific hardware
        }
    }

    /**
     * Trigger a gentle haptic pulse corresponding to breathing phase or step cadence
     */
    fun pulseBreathing(cadence: VibrationCadence) {
        if (vibrator == null || !vibrator.hasVibrator()) return
        if (cadence == VibrationCadence.OFF) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val duration = when (cadence) {
                    VibrationCadence.BREATHING_RELAXED -> 50L
                    VibrationCadence.BREATHING_FAST -> 40L
                    VibrationCadence.RUNNING_CADENCE -> 45L
                    VibrationCadence.OFF -> 0L
                }
                if (duration > 0) {
                    val effect = VibrationEffect.createOneShot(duration, 160)
                    vibrator.vibrate(effect)
                }
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(40)
            }
        } catch (_: Exception) {}
    }

    fun stop() {
        try {
            vibrator?.cancel()
        } catch (_: Exception) {}
    }
}
