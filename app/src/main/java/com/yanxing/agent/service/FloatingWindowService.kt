package com.yanxing.agent.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.yanxing.agent.MainActivity
import com.yanxing.agent.R

/**
 * 悬浮窗服务：
 * - 显示可拖动的悬浮球
 * - 点击展开迷你面板（文字输入 + 语音 + 打开主界面）
 */
class FloatingWindowService : Service() {

    private lateinit var windowManager: WindowManager
    private var floatView: View? = null
    private var panelView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var voiceInput: VoiceInputController? = null
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        showFloatingBall()
    }

    override fun onDestroy() {
        voiceInput?.release()
        voiceInput = null
        removeFloatingViews()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 来自通知的"停止"操作
        if (intent?.getBooleanExtra(EXTRA_STOP, false) == true) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    // ============ 悬浮球 ============

    @SuppressLint("ClickableViewAccessibility")
    private fun showFloatingBall() {
        if (floatView != null) return
        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val ball = inflater.inflate(R.layout.floating_ball, null) as FrameLayout
        floatView = ball

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 200
        }

        // 拖动逻辑
        ball.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isDragging = false
                    initialX = layoutParams!!.x
                    initialY = layoutParams!!.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (kotlin.math.abs(event.rawX - initialTouchX) > 10 ||
                        kotlin.math.abs(event.rawY - initialTouchY) > 10
                    ) {
                        isDragging = true
                        layoutParams!!.x = clamp(initialX + (event.rawX - initialTouchX).toInt(), 0, resources.displayMetrics.widthPixels - ball.width)
                        layoutParams!!.y = clamp(initialY + (event.rawY - initialTouchY).toInt(), 0, resources.displayMetrics.heightPixels - ball.height)
                        windowManager.updateViewLayout(ball, layoutParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) togglePanel()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    // 拖拽被系统打断时复位，避免下次 UP 被误判为点击
                    isDragging = false
                    true
                }
                else -> false
            }
        }

        try {
            windowManager.addView(ball, layoutParams)
        } catch (e: Exception) {
            // 悬浮窗权限被收回等服务重启场景，addView 抛 BadTokenException 必崩
            floatView = null
            stopSelf()
        }
    }

    private fun clamp(value: Int, min: Int, max: Int): Int = value.coerceIn(min, max.coerceAtLeast(min))

    // ============ 迷你面板 ============

    private fun togglePanel() {
        if (panelView == null) showPanel() else hidePanel()
    }

    @SuppressLint("InflateParams")
    private fun showPanel() {
        if (panelView != null) return
        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val panel = inflater.inflate(R.layout.floating_panel, null) as LinearLayout
        panelView = panel

        val panelParams = WindowManager.LayoutParams(
            (resources.displayMetrics.widthPixels * 0.85f).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            // 不能带 FLAG_NOT_FOCUSABLE：面板里的 EditText 需要焦点和软键盘；
            // FLAG_NOT_TOUCH_MODAL 让面板外的触摸穿透，不挡住底下的应用
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 100
        }

        // 标题
        panel.findViewById<TextView>(R.id.panel_title).text = "言行 Agent 快捷面板"
        // 关闭按钮
        panel.findViewById<ImageButton>(R.id.panel_close).setOnClickListener { hidePanel() }
        // 打开主界面
        panel.findViewById<Button>(R.id.panel_open).setOnClickListener {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        }
        // 发送文本
        val input = panel.findViewById<EditText>(R.id.panel_input)
        // 语音输入：识别结果直接填入输入框，由用户确认后再发送
        val voiceButton = panel.findViewById<ImageButton>(R.id.panel_voice)
        voiceButton.setOnClickListener { startVoiceInput(input, voiceButton) }
        panel.findViewById<Button>(R.id.panel_send).setOnClickListener {
            val text = input.text.toString().trim()
            if (text.isNotEmpty()) {
                // 通过 Intent 把文本交给主界面处理
                val intent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra(MainActivity.EXTRA_QUICK_TEXT, text)
                }
                startActivity(intent)
                input.text.clear()
                hidePanel()
            }
        }

        // 点击面板外部时收起（配合 FLAG_WATCH_OUTSIDE_TOUCH）
        panel.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                hidePanel()
                true
            } else {
                false
            }
        }

        try {
            windowManager.addView(panel, panelParams)
        } catch (e: Exception) {
            panelView = null
        }
    }

    /**
     * 悬浮窗内的语音识别。
     * 悬浮窗不是 Activity，无法弹权限申请，未授权时引导用户到主界面处理。
     */
    private fun startVoiceInput(input: EditText, button: ImageButton) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            toast("请先在主界面授予录音权限")
            return
        }
        val controller = voiceInput ?: VoiceInputController(this).also { voiceInput = it }
        controller.start(
            onResult = { text ->
                val existing = input.text.toString().trim()
                input.setText(if (existing.isEmpty()) text else "$existing $text")
                input.setSelection(input.text.length)
            },
            onError = { message -> toast(message) },
            onStateChanged = { listening ->
                button.isEnabled = !listening
                button.alpha = if (listening) 0.4f else 1f
                if (listening) toast("请开始说话…")
            },
        )
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun hidePanel() {
        voiceInput?.cancel()
        panelView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        panelView = null
    }

    private fun removeFloatingViews() {
        floatView?.let { view -> runCatching { windowManager.removeView(view) } }
        panelView?.let { view -> runCatching { windowManager.removeView(view) } }
        floatView = null
        panelView = null
    }

    // ============ 通知 ============

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "悬浮窗服务",
                NotificationManager.IMPORTANCE_LOW,
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, FloatingWindowService::class.java).apply {
            putExtra(EXTRA_STOP, true)
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val openIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            1,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("言行 Agent")
                .setContentText("悬浮窗已开启")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentIntent(openPendingIntent)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "停止", stopPendingIntent)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("言行 Agent")
                .setContentText("悬浮窗已开启")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentIntent(openPendingIntent)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "停止", stopPendingIntent)
                .build()
        }
    }

    companion object {
        private const val CHANNEL_ID = "floating_window"
        private const val NOTIFICATION_ID = 1001
        private const val EXTRA_STOP = "extra_stop"

        /** 检查悬浮窗权限 */
        fun hasOverlayPermission(context: Context): Boolean =
            Settings.canDrawOverlays(context)

        /** 启动悬浮窗服务 */
        fun start(context: Context) {
            val intent = Intent(context, FloatingWindowService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** 停止悬浮窗服务 */
        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingWindowService::class.java))
        }
    }
}
