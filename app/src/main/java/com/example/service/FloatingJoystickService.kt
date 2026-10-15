package com.example.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.example.data.model.GeoPoint
import com.example.ui.components.CircularJoystickView
import com.example.util.VibrationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/**
 * Floating Overlay Service providing an interactive 360° Circular Joystick.
 * Stays on screen while user switches to third-party fitness/running apps.
 */
class FloatingJoystickService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var windowManager: WindowManager? = null
    private var rootLayout: View? = null
    private var bubbleView: View? = null

    private var currentSpeedKmh = 12.0f
    private var isExpanded = true
    private var isPhysicalStepTriggerOn = true
    private lateinit var vibrationHelper: VibrationHelper

    companion object {
        const val TAG = "FloatingJoystick"
        const val CHANNEL_ID = "floating_joystick_channel"
        const val NOTIFICATION_ID = 2002

        const val ACTION_START = "com.example.service.START_OVERLAY"
        const val ACTION_STOP = "com.example.service.STOP_OVERLAY"

        var isOverlayRunning = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        vibrationHelper = VibrationHelper(this)
        createNotificationChannel()
        promoteToForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        promoteToForeground()
        val action = intent?.action ?: ACTION_START

        if (action == ACTION_STOP) {
            stopOverlay()
            return START_NOT_STICKY
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.e(TAG, "Cannot start overlay: SYSTEM_ALERT_WINDOW not granted")
            stopOverlay()
            return START_NOT_STICKY
        }

        if (rootLayout == null && bubbleView == null) {
            try {
                showFloatingControllers()
                isOverlayRunning = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start floating joystick service", e)
                stopOverlay()
                return START_NOT_STICKY
            }
        }

        return START_STICKY
    }

    private fun promoteToForeground() {
        try {
            val notification = buildForegroundNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val hasLoc = androidx.core.content.ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.ACCESS_FINE_LOCATION
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                androidx.core.content.ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                if (hasLoc) {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed promoteToForeground", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Cần điều khiển tròn nổi (Circular Joystick)",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Hiển thị cần điều khiển vòng tròn trên màn hình ứng dụng khác"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GeoSim: Cần điều khiển vòng tròn 360°")
            .setContentText("Đang chạy nổi trên màn hình. Kéo cần để di chuyển & đếm bước.")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()
    }

    @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
    private fun showFloatingControllers() {
        val wm = windowManager ?: return

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val overlayParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 220
        }

        // 1. Minimized Floating Bubble
        val bubble = FrameLayout(this).apply {
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xEE0F172A.toInt())
                setStroke(4, 0xFF38BDF8.toInt())
            }
            background = bg
            layoutParams = FrameLayout.LayoutParams(130, 130)

            val tv = TextView(context).apply {
                text = "🕹️"
                textSize = 24f
                gravity = Gravity.CENTER
            }
            addView(tv, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            visibility = View.GONE
        }
        bubbleView = bubble

        // 2. Expanded Circular Controller Pod
        val mainCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 36f
                setColor(0xF50F172A.toInt())
                setStroke(3, 0xFF38BDF8.toInt())
            }
            background = bg
            setPadding(18, 14, 18, 18)
        }

        // Header (Title, Drag Handle, Min, Close)
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(6, 2, 6, 8)
        }

        val titleTv = TextView(this).apply {
            text = "🕹️ Bánh đà 360°"
            setTextColor(0xFF38BDF8.toInt())
            textSize = 12f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val minBtn = Button(this).apply {
            text = "—"
            setTextColor(Color.WHITE)
            setBackgroundColor(0xFF334155.toInt())
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(68, 64)
        }

        val closeBtn = Button(this).apply {
            text = "✕"
            setTextColor(Color.WHITE)
            setBackgroundColor(0xFFDC2626.toInt())
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(68, 64)
        }

        header.addView(titleTv)
        header.addView(minBtn)
        header.addView(closeBtn)
        mainCard.addView(header)

        // Telemetry Row
        val telemetryTv = TextView(this).apply {
            text = "📍 12.0 km/h • 👟 165 SPM (0 bước)\n🔥 0 kcal • ❤️ 135 BPM"
            setTextColor(0xFFE2E8F0.toInt())
            textSize = 10f
            gravity = Gravity.CENTER
            setPadding(4, 2, 4, 6)
        }
        mainCard.addView(telemetryTv)

        // Hardware Step Vibration Toggle Button
        val stepVibBtn = Button(this).apply {
            text = "⚡ Kích rung đếm bước: BẬT"
            textSize = 10f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            val btnBg = GradientDrawable().apply {
                cornerRadius = 16f
                setColor(0xFF059669.toInt()) // Emerald Green
            }
            background = btnBg
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 68).apply {
                setMargins(4, 0, 4, 6)
            }
            setOnClickListener {
                isPhysicalStepTriggerOn = !isPhysicalStepTriggerOn
                if (isPhysicalStepTriggerOn) {
                    text = "⚡ Kích rung đếm bước: BẬT"
                    (background as GradientDrawable).setColor(0xFF059669.toInt())
                    vibrationHelper.pulseStepShockwave(255)
                } else {
                    text = "⚡ Kích rung đếm bước: TẮT"
                    (background as GradientDrawable).setColor(0xFF475569.toInt())
                }
                // Send toggle intent to MockLocationService
                val intent = Intent(this@FloatingJoystickService, MockLocationService::class.java).apply {
                    action = MockLocationService.ACTION_UPDATE_CONFIG
                }
                try { startService(intent) } catch (_: Exception) {}
            }
        }
        mainCard.addView(stepVibBtn)

        // Speed Preset Selector
        val speedRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 2, 0, 8)
        }

        val speedPresets = listOf(
            "🚶 5k" to 5.0f,
            "🏃 12k" to 12.0f,
            "🚴 25k" to 25.0f,
            "🚗 50k" to 50.0f
        )
        val speedButtons = mutableListOf<Button>()

        speedPresets.forEach { (label, spd) ->
            val btn = Button(this).apply {
                text = label
                textSize = 9f
                val isSelected = currentSpeedKmh == spd
                setTextColor(if (isSelected) Color.BLACK else Color.WHITE)
                val bg = GradientDrawable().apply {
                    cornerRadius = 14f
                    setColor(if (isSelected) 0xFF38BDF8.toInt() else 0xFF1E293B.toInt())
                }
                background = bg
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, 60).apply {
                    setMargins(3, 0, 3, 0)
                }
                setOnClickListener {
                    currentSpeedKmh = spd
                    speedButtons.forEach { b ->
                        val sel = b.text.toString() == label
                        b.setTextColor(if (sel) Color.BLACK else Color.WHITE)
                        (b.background as GradientDrawable).setColor(if (sel) 0xFF38BDF8.toInt() else 0xFF1E293B.toInt())
                    }
                }
            }
            speedButtons.add(btn)
            speedRow.addView(btn)
        }
        mainCard.addView(speedRow)

        // The 360° Circular Analog Joystick View
        val joystickView = CircularJoystickView(this).apply {
            layoutParams = LinearLayout.LayoutParams(380, 380).apply {
                gravity = Gravity.CENTER
            }
            onMoveListener = { angleDegrees, power ->
                handleJoystickMove(angleDegrees, power)
            }
            onReleaseListener = {
                // Movement stops smoothly
            }
        }
        mainCard.addView(joystickView)

        rootLayout = mainCard

        // Dragging logic for Expanded Card (via header)
        var initX = 0
        var initY = 0
        var touchX = 0f
        var touchY = 0f

        header.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initX = overlayParams.x
                    initY = overlayParams.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    overlayParams.x = initX + (event.rawX - touchX).toInt()
                    overlayParams.y = initY + (event.rawY - touchY).toInt()
                    try { wm.updateViewLayout(mainCard, overlayParams) } catch (_: Exception) {}
                    true
                }
                else -> false
            }
        }

        // Dragging logic for Minimized Bubble
        var bubbleInitX = 0
        var bubbleInitY = 0
        var bubbleTouchX = 0f
        var bubbleTouchY = 0f
        var isBubbleDrag = false

        bubble.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    bubbleInitX = overlayParams.x
                    bubbleInitY = overlayParams.y
                    bubbleTouchX = event.rawX
                    bubbleTouchY = event.rawY
                    isBubbleDrag = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - bubbleTouchX
                    val dy = event.rawY - bubbleTouchY
                    if (kotlin.math.abs(dx) > 10 || kotlin.math.abs(dy) > 10) {
                        isBubbleDrag = true
                    }
                    overlayParams.x = bubbleInitX + dx.toInt()
                    overlayParams.y = bubbleInitY + dy.toInt()
                    try { wm.updateViewLayout(bubble, overlayParams) } catch (_: Exception) {}
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isBubbleDrag) {
                        // Expand back to full circular controller
                        bubble.visibility = View.GONE
                        mainCard.visibility = View.VISIBLE
                        try {
                            wm.removeView(bubble)
                            wm.addView(mainCard, overlayParams)
                        } catch (_: Exception) {}
                    }
                    true
                }
                else -> false
            }
        }

        // Minimize action
        minBtn.setOnClickListener {
            mainCard.visibility = View.GONE
            bubble.visibility = View.VISIBLE
            try {
                wm.removeView(mainCard)
                wm.addView(bubble, overlayParams)
            } catch (_: Exception) {}
        }

        // Close action
        closeBtn.setOnClickListener {
            stopOverlay()
        }

        try {
            wm.addView(mainCard, overlayParams)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add overlay view", e)
            stopSelf()
            return
        }

        // Telemetry collector
        serviceScope.launch {
            MockLocationService.simulationProgress.collectLatest { progress ->
                try {
                    val spd = if (progress.currentSpeedKmh > 0) progress.currentSpeedKmh else currentSpeedKmh
                    telemetryTv.text = "📍 ${String.format(java.util.Locale.US, "%.1f", spd)} km/h • 👟 ${progress.currentCadenceSpm} SPM (${progress.totalSteps} bước)\n🔥 ${String.format(java.util.Locale.US, "%.1f", progress.caloriesBurnedKcal)} kcal • ❤️ ${progress.currentHeartRateBpm} BPM"
                } catch (_: Exception) {}
            }
        }
    }

    /**
     * Compute delta coordinates from 360° circular vector angle and dispatch nudge
     */
    private fun handleJoystickMove(angleDegrees: Float, power: Float) {
        // Distance covered in this tick (280ms interval) based on current speed
        val speedMps = (currentSpeedKmh * 1000f) / 3600f
        val stepDistMeters = (speedMps * 0.280f * power).coerceAtLeast(0.8f).toDouble()

        // Angle in radians (0° is North = +Lat, 90° is East = +Lng, 180° is South = -Lat, 270° is West = -Lng)
        val rad = Math.toRadians(angleDegrees.toDouble())
        val deltaMetersLat = stepDistMeters * cos(rad)
        val deltaMetersLng = stepDistMeters * sin(rad)

        val currentProgress = MockLocationService.simulationProgress.value
        val current = currentProgress.currentPoint ?: GeoPoint(21.028511, 105.854444, name = "Joystick")
        val latOffset = deltaMetersLat / 111111.0
        val lngOffset = deltaMetersLng / (111111.0 * cos(Math.toRadians(current.latitude)))

        val nudged = GeoPoint(
            latitude = current.latitude + latOffset,
            longitude = current.longitude + lngOffset,
            name = "Joystick"
        )

        // Pulse hardware step shockwave if enabled
        if (isPhysicalStepTriggerOn) {
            vibrationHelper.pulseStepShockwave(255)
        }

        val intent = Intent(this, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_NUDGE_LOCATION
            putExtra(MockLocationService.EXTRA_LAT, nudged.latitude)
            putExtra(MockLocationService.EXTRA_LNG, nudged.longitude)
            putExtra(MockLocationService.EXTRA_SPEED_KMH, currentSpeedKmh * power)
            putExtra(MockLocationService.EXTRA_BEARING, angleDegrees)
            putExtra(MockLocationService.EXTRA_NAME, "Joystick")
        }
        try {
            startService(intent)
        } catch (_: Exception) {}
    }

    private fun stopOverlay() {
        isOverlayRunning = false
        val wm = windowManager
        rootLayout?.let { view ->
            try { wm?.removeView(view) } catch (_: Exception) {}
            rootLayout = null
        }
        bubbleView?.let { view ->
            try { wm?.removeView(view) } catch (_: Exception) {}
            bubbleView = null
        }
        serviceScope.cancel()
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {}
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopOverlay()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
