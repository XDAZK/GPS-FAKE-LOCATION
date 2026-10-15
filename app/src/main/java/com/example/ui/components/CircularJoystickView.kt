package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * High-performance 360° Circular Virtual Analog Joystick View.
 * Provides smooth 360° directional vector steering, deflection power,
 * cardinal arrow taps, and continuous tick dispatching when held.
 */
class CircularJoystickView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var onMoveListener: ((angleDegrees: Float, power: Float) -> Unit)? = null
    var onReleaseListener: (() -> Unit)? = null

    // Touch and geometric state
    private var centerX = 0f
    private var centerY = 0f
    private var baseRadius = 0f
    private var knobRadius = 0f
    private var currentKnobX = 0f
    private var currentKnobY = 0f
    private var isDragging = false

    private var currentAngleDeg = 0f
    private var currentPower = 0f

    // Paints
    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xEE0F172A.toInt() // Dark Slate Glass
    }

    private val baseBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        color = 0xFF38BDF8.toInt() // Neon Cyan
    }

    private val gridRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = 0x4438BDF8.toInt()
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF94A3B8.toInt()
        textSize = 28f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val activeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF38BDF8.toInt()
        textSize = 30f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val knobBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = 0xFFFFFFFF.toInt()
    }

    private val knobCenterDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFFFFFFFF.toInt()
    }

    // Continuous tick loop when held
    private val handler = Handler(Looper.getMainLooper())
    private val tickRunnable = object : Runnable {
        override fun run() {
            if (isDragging && currentPower > 0.15f) {
                onMoveListener?.invoke(currentAngleDeg, currentPower)
                handler.postDelayed(this, 280L)
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f
        baseRadius = min(centerX, centerY) - 8f
        knobRadius = baseRadius * 0.36f
        currentKnobX = centerX
        currentKnobY = centerY
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw Base Circle
        canvas.drawCircle(centerX, centerY, baseRadius, basePaint)
        canvas.drawCircle(centerX, centerY, baseRadius, baseBorderPaint)

        // Concentric Guides
        canvas.drawCircle(centerX, centerY, baseRadius * 0.40f, gridRingPaint)
        canvas.drawCircle(centerX, centerY, baseRadius * 0.72f, gridRingPaint)

        // Crosshairs / Compass Guides
        canvas.drawLine(centerX - baseRadius + 15f, centerY, centerX + baseRadius - 15f, centerY, gridRingPaint)
        canvas.drawLine(centerX, centerY - baseRadius + 15f, centerX, centerY + baseRadius - 15f, gridRingPaint)

        // Cardinal Direction Markers
        canvas.drawText("▲ B", centerX, centerY - baseRadius + 36f, if (isDragging && (currentAngleDeg in 315f..360f || currentAngleDeg in 0f..45f)) activeTextPaint else textPaint)
        canvas.drawText("▼ N", centerX, centerY + baseRadius - 16f, if (isDragging && currentAngleDeg in 135f..225f) activeTextPaint else textPaint)
        canvas.drawText("T ◀", centerX - baseRadius + 36f, centerY + 10f, if (isDragging && currentAngleDeg in 225f..315f) activeTextPaint else textPaint)
        canvas.drawText("▶ Đ", centerX + baseRadius - 36f, centerY + 10f, if (isDragging && currentAngleDeg in 45f..135f) activeTextPaint else textPaint)

        // Glowing Knob with Radial Gradient
        val gradient = RadialGradient(
            currentKnobX, currentKnobY, knobRadius,
            intArrayOf(0xFF06B6D4.toInt(), 0xFF0284C7.toInt(), 0xFF1E3A8A.toInt()),
            floatArrayOf(0f, 0.65f, 1f),
            Shader.TileMode.CLAMP
        )
        knobPaint.shader = gradient
        canvas.drawCircle(currentKnobX, currentKnobY, knobRadius, knobPaint)
        canvas.drawCircle(currentKnobX, currentKnobY, knobRadius, knobBorderPaint)
        canvas.drawCircle(currentKnobX, currentKnobY, knobRadius * 0.22f, knobCenterDotPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                isDragging = true
                val dx = event.x - centerX
                val dy = event.y - centerY
                val dist = sqrt(dx * dx + dy * dy)
                val maxDist = baseRadius - knobRadius * 0.5f

                if (dist <= maxDist) {
                    currentKnobX = event.x
                    currentKnobY = event.y
                } else {
                    currentKnobX = centerX + (dx / dist) * maxDist
                    currentKnobY = centerY + (dy / dist) * maxDist
                }

                currentPower = (dist / maxDist).coerceIn(0f, 1f)

                // Calculate heading angle in degrees (0° = North, 90° = East, 180° = South, 270° = West)
                var angle = Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble())).toFloat()
                if (angle < 0f) angle += 360f
                currentAngleDeg = angle

                invalidate()

                handler.removeCallbacks(tickRunnable)
                if (currentPower > 0.15f) {
                    onMoveListener?.invoke(currentAngleDeg, currentPower)
                    handler.postDelayed(tickRunnable, 280L)
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                currentKnobX = centerX
                currentKnobY = centerY
                currentPower = 0f
                handler.removeCallbacks(tickRunnable)
                invalidate()
                onReleaseListener?.invoke()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacks(tickRunnable)
    }
}
