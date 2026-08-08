package com.yanxing.agent.service

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.TextView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 悬浮窗主题配色（纯数据，可由纯函数生成，便于单元测试）。
 */
data class OverlayColors(
    val cardBackground: Int,
    val titleText: Int,
    val bodyText: Int,
    val secondaryText: Int,
    val successText: Int,
    val failureText: Int,
    val stopButtonBackground: Int,
    val undoButtonBackground: Int,
    val buttonText: Int,
    val disabledBackground: Int,
)

/**
 * 根据深色模式生成悬浮窗配色（无 Android 运行时依赖，纯逻辑可测）。
 */
fun resolveOverlayColors(darkMode: Boolean): OverlayColors =
    if (darkMode) {
        OverlayColors(
            cardBackground = 0xFF2A2A2A.toInt(),
            titleText = 0xFFF5F5F5.toInt(),
            bodyText = 0xFFE0E0E0.toInt(),
            secondaryText = 0xFF9E9E9E.toInt(),
            successText = 0xFF66BB6A.toInt(),
            failureText = 0xFFEF5350.toInt(),
            stopButtonBackground = 0xFFC62828.toInt(),
            undoButtonBackground = 0xFF1565C0.toInt(),
            buttonText = 0xFFFFFFFF.toInt(),
            disabledBackground = 0xFF616161.toInt(),
        )
    } else {
        OverlayColors(
            cardBackground = 0xFFFFFFFF.toInt(),
            titleText = 0xFF000000.toInt(),
            bodyText = 0xFF444444.toInt(),
            secondaryText = 0xFF757575.toInt(),
            successText = 0xFF2E7D32.toInt(),
            failureText = 0xFFC62828.toInt(),
            stopButtonBackground = 0xFFC62828.toInt(),
            undoButtonBackground = 0xFF1565C0.toInt(),
            buttonText = 0xFFFFFFFF.toInt(),
            disabledBackground = 0xFF757575.toInt(),
        )
    }

/**
 * 悬浮窗水平边缘吸附：返回 ACTION_UP 时应设置的水平坐标。
 * gravity 为 TOP|END，x 表示距右边缘的偏移；0 = 贴右，screenWidth-windowWidth = 贴左。
 */
fun resolveSnapX(currentX: Int, screenWidth: Int, windowWidth: Int): Int {
    val maxX = (screenWidth - windowWidth).coerceAtLeast(0)
    val midX = maxX / 2
    return if (currentX < midX) 0 else maxX
}

/**
 * 悬浮窗实时进度显示
 * 显示执行状态、成功/失败计数、控制按钮
 */
class FloatingProgressOverlay(private val context: Context) {

    companion object {
        /** 悬浮窗提示条的显示时长（毫秒） */
        const val NOTICE_DURATION_MS = 3_000L
    }
    
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
    private var isVisible = false
    private var currentView: View? = null
    private var currentParams: android.view.WindowManager.LayoutParams? = null
    private var progressTextView: TextView? = null
    private var successCountTextView: TextView? = null
    private var failedCountTextView: TextView? = null
    private var currentTaskTextView: TextView? = null
    private var lastResultTextView: TextView? = null
    private var noticeTextView: TextView? = null
    private var controlButtonView: android.widget.Button? = null
    private var undoButtonView: android.widget.Button? = null
    /** 用户点击"停止执行"时的回调，由 ChatViewModel 注入 */
    var onStopRequested: (() -> Unit)? = null

    /** 用户点击"撤销上一个动作"的回调 */
    var onUndoRequested: (() -> Unit)? = null

    // 内部状态流
    private val _currentState = MutableStateFlow(CheckboxState())
    val currentState: StateFlow<CheckboxState> = _currentState.asStateFlow()
    
    data class CheckboxState(
        val total: Int = 0,
        val success: Int = 0,
        val failed: Int = 0,
        val currentStep: String = "",
        val actionModeEnabled: Boolean = false,
        val stopped: Boolean = false, // 用户已请求停止
        val lastResult: String = "",        // 最近一次执行结果的消息
        val lastResultIsSuccess: Boolean = true, // 最近一次结果是否成功（用于着色）
        val notice: String? = null,          // 悬浮窗内短期提示（如撤销完成/无法撤销）
    )

    /**
     * 展示最近一次执行结果（成功绿 / 失败红）。
     * @param success 结果是否成功，决定文本颜色
     */
    fun showResult(message: String, success: Boolean) {
        _currentState.value = _currentState.value.copy(
            lastResult = message,
            lastResultIsSuccess = success,
        )
        updateUI()
    }
    
    init {
        updateUI()
    }
    
    /**
     * 更新总任务数
     */
    fun setTotalActions(total: Int) {
        _currentState.value = _currentState.value.copy(total = total)
        updateUI()
    }
    
    /**
     * 更新当前步骤描述
     */
    fun setCurrentAction(actionDesc: String?) {
        _currentState.value = _currentState.value.copy(currentStep = actionDesc ?: "")
        updateUI()
    }
    
    /**
     * 增加成功计数
     */
    fun incrementSuccess() {
        _currentState.value = _currentState.value.copy(success = _currentState.value.success + 1)
        updateUI()
    }
    
    /**
     * 增加失败计数
     */
    fun incrementFailed() {
        _currentState.value = _currentState.value.copy(failed = _currentState.value.failed + 1)
        updateUI()
    }
    
    /**
     * 标记为已停止：按钮置灰并显示"已停止"，等待执行链路收尾
     */
    fun markStopped() {
        _currentState.value = _currentState.value.copy(stopped = true, currentStep = "已停止")
        updateUI()
    }

    /**
     * 开始新任务时重置计数与停止标记
     */
    fun resetProgress() {
        _currentState.value = CheckboxState(actionModeEnabled = _currentState.value.actionModeEnabled)
        updateUI()
    }
    
    /**
     * 设置行动模式开关状态
     */
    fun setActionModeEnabled(enabled: Boolean) {
        _currentState.value = _currentState.value.copy(actionModeEnabled = enabled)
        if (!enabled && isVisible) {
            destroyView()
        } else if (enabled && !isVisible) {
            createView()
        }
    }
    
    /**
     * 隐藏悬浮窗
     */
    fun hide() {
        if (isVisible) destroyView()
    }
    
    /**
     * 显示悬浮窗
     */
    fun show() {
        if (!isVisible) createView()
    }
    
    /**
     * 销毁悬浮窗
     */
    private fun destroyView() {
        try {
            if (isVisible) {
                currentView?.let { windowManager.removeView(it) }
                currentView = null
                currentParams = null
                isVisible = false
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 显示悬浮窗内短期提示（如"撤销完成"、"无法撤销"等）。
     * 悬浮窗无法弹出系统 Toast，改用窗口内提示文本替代，3 秒后自动消退。
     */
    fun toast(message: String) {
        Log.i("YanxingToast", message)
        _currentState.value = _currentState.value.copy(notice = message)
        updateUI()
        val currentViewRef = currentView
        currentViewRef?.postDelayed({
            if (_currentState.value.notice == message) {
                _currentState.value = _currentState.value.copy(notice = null)
                updateUI()
            }
        }, NOTICE_DURATION_MS)
    }

    /** 立即清除当前提示 */
    fun clearNotice() {
        _currentState.value = _currentState.value.copy(notice = null)
        updateUI()
    }
    
    private fun isDarkMode(): Boolean {
        val nightModeFlags = context.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    /**
     * 创建并显示悬浮窗
     */
    private fun createView() {
        try {
            destroyView() // 先移除旧的

            val colors = resolveOverlayColors(isDarkMode())

            val cardView = android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setPadding(16, 12, 16, 12)
                background = GradientDrawable().apply {
                    setColor(colors.cardBackground)
                    cornerRadius = 12f
                }
                elevation = 8f
            }
            
            val dragStart = FloatArray(2)
            val windowStart = IntArray(2)
            cardView.setOnTouchListener { _, event ->
                val params = currentParams ?: return@setOnTouchListener false
                when (event.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        dragStart[0] = event.rawX
                        dragStart[1] = event.rawY
                        windowStart[0] = params.x
                        windowStart[1] = params.y
                        true
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        params.x = windowStart[0] + (dragStart[0] - event.rawX).toInt()
                        params.y = windowStart[1] + (event.rawY - dragStart[1]).toInt()
                        windowManager.updateViewLayout(cardView, params)
                        true
                    }
                    android.view.MotionEvent.ACTION_UP -> {
                        // 松手后水平吸附到最近边缘
                        val screenWidth = context.resources.displayMetrics.widthPixels
                        params.x = resolveSnapX(params.x, screenWidth, params.width)
                        windowManager.updateViewLayout(cardView, params)
                        true
                    }
                    else -> false
                }
            }
            
            val titleText = TextView(context).apply {
                text = "替我行动"
                textSize = 16f
                setTextColor(colors.titleText)
                setPadding(16, 12, 16, 12)
            }
            
            val statusText = TextView(context).apply {
                id = View.generateViewId()
                text = "准备就绪"
                textSize = 14f
                setTextColor(colors.secondaryText)
                setPadding(16, 0, 16, 0)
            }
            
            val progressText = TextView(context).apply {
                text = "0 / 0"
                textSize = 18f
                setTextColor(colors.bodyText)
                setPadding(0, 4, 0, 4)
                setBold()
            }
            progressTextView = progressText
            
            val successCountText = TextView(context).apply {
                text = "✓ 成功：0"
                textSize = 14f
                setTextColor(colors.successText)
                setPadding(0, 2, 0, 2)
            }
            successCountTextView = successCountText
            
            val failedCountText = TextView(context).apply {
                text = "✗ 失败：0"
                textSize = 14f
                setTextColor(colors.failureText)
                setPadding(0, 2, 0, 2)
            }
            failedCountTextView = failedCountText
            
            val currentTaskText = TextView(context).apply {
                text = "等待执行..."
                textSize = 13f
                setTextColor(colors.bodyText)
                setPadding(0, 4, 0, 8)
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            currentTaskTextView = currentTaskText

            val lastResultText = TextView(context).apply {
                text = "结果：—"
                textSize = 13f
                setTextColor(colors.secondaryText)
                setPadding(0, 2, 0, 8)
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            lastResultTextView = lastResultText

            val noticeText = TextView(context).apply {
                text = ""
                textSize = 13f
                setPadding(0, 2, 0, 8)
                visibility = View.GONE
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            noticeTextView = noticeText

            // 停止按钮
            val controlButton = android.widget.Button(context).apply {
                text = "停止执行"
                textSize = 14f
                setTextColor(colors.buttonText)
                setBackgroundColor(colors.stopButtonBackground)
                setPadding(16, 8, 16, 8)
                contentDescription = "停止替我行动执行"
            }
            controlButtonView = controlButton

            controlButton.setOnClickListener {
                if (_currentState.value.stopped) return@setOnClickListener
                markStopped()
                onStopRequested?.invoke()
            }

            // 撤销按钮（隐藏直到有可撤销动作）
            val undoButton = android.widget.Button(context).apply {
                text = "撤销上一个"
                textSize = 13f
                setTextColor(colors.buttonText)
                setBackgroundColor(colors.undoButtonBackground)
                setPadding(16, 6, 16, 6)
                visibility = View.GONE
                contentDescription = "撤销上一个操作"
            }
            undoButtonView = undoButton
            undoButton.setOnClickListener { onUndoRequested?.invoke() }

            cardView.addView(titleText)
            cardView.addView(statusText)
            cardView.addView(progressText)
            cardView.addView(successCountText)
            cardView.addView(failedCountText)
            cardView.addView(currentTaskText)
            cardView.addView(lastResultText)
            cardView.addView(noticeText)
            cardView.addView(controlButton)
            cardView.addView(undoButton)
            currentView = cardView
            
            // 添加到 Window
            val params = android.view.WindowManager.LayoutParams().apply {
                type = android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                flags = android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                format = PixelFormat.TRANSPARENT
                width = 320
                height = 400
                gravity = Gravity.TOP or Gravity.END
                x = 0
                y = 100
            }
            
            currentParams = params
            windowManager.addView(cardView, params)
            isVisible = true
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * 更新 UI（从状态流读取）
     */
    private fun updateUI() {
        val state = _currentState.value
        val colors = resolveOverlayColors(isDarkMode())

        currentTaskTextView?.text = state.currentStep.ifBlank { "等待执行..." }
        if (state.lastResult.isBlank()) {
            lastResultTextView?.text = "结果：—"
            lastResultTextView?.setTextColor(colors.secondaryText)
        } else {
            lastResultTextView?.text = "结果：${state.lastResult}"
            lastResultTextView?.setTextColor(
                if (state.lastResultIsSuccess) colors.successText else colors.failureText,
            )
        }
        progressTextView?.text = "进度：${state.success + state.failed} / ${state.total}"
        // 短期提示条：有 notice 显示，无则隐藏
        noticeTextView?.apply {
            if (state.notice.isNullOrBlank()) {
                text = ""
                visibility = View.GONE
            } else {
                text = "ℹ️ ${state.notice}"
                setTextColor(colors.secondaryText)
                visibility = View.VISIBLE
            }
        }
        successCountTextView?.text = "✓ 成功：${state.success}"
        failedCountTextView?.text = "✗ 失败：${state.failed}"
        controlButtonView?.apply {
            visibility = View.VISIBLE
            isEnabled = !state.stopped
            text = if (state.stopped) "已停止" else "停止执行"
            setBackgroundColor(
                if (state.stopped) colors.disabledBackground else colors.stopButtonBackground,
            )
        }
    }

    /** 设置撤销按钮可见性（用于显示可用计数） */
    fun setUndoButton(visible: Boolean) {
        undoButtonView?.visibility = if (visible) View.VISIBLE else View.GONE
    }
}

// 扩展函数：设置粗体
private fun TextView.setBold() {
    setTypeface(null, android.graphics.Typeface.BOLD)
}