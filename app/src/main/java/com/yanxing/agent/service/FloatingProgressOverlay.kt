package com.yanxing.agent.service

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.TextView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 悬浮窗实时进度显示
 * 显示执行状态、成功/失败计数、控制按钮
 */
class FloatingProgressOverlay(private val context: Context) {
    
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
    private var isVisible = false
    private var currentView: View? = null
    private var currentParams: android.view.WindowManager.LayoutParams? = null
    private var progressTextView: TextView? = null
    private var successCountTextView: TextView? = null
    private var failedCountTextView: TextView? = null
    private var currentTaskTextView: TextView? = null
    private var controlButtonView: android.widget.Button? = null

    /** 用户点击"停止执行"时的回调，由 ChatViewModel 注入 */
    var onStopRequested: (() -> Unit)? = null

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
    )
    
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
     * 创建并显示悬浮窗
     */
    private fun createView() {
        try {
            destroyView() // 先移除旧的
            
            val cardView = android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setPadding(16, 12, 16, 12)
                background = GradientDrawable().apply {
                    setColor(android.graphics.Color.WHITE)
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
                    else -> false
                }
            }
            
            val titleText = TextView(context).apply {
                text = "替我行动"
                textSize = 16f
                setTextColor(context.getColor(android.R.color.black))
                setPadding(16, 12, 16, 12)
            }
            
            val statusText = TextView(context).apply {
                id = View.generateViewId()
                text = "准备就绪"
                textSize = 14f
                setTextColor(context.getColor(android.R.color.darker_gray))
                setPadding(16, 0, 16, 0)
            }
            
            val progressText = TextView(context).apply {
                text = "0 / 0"
                textSize = 18f
                setTextColor(android.graphics.Color.DKGRAY)
                setPadding(0, 4, 0, 4)
                setBold()
            }
            progressTextView = progressText
            
            val successCountText = TextView(context).apply {
                text = "✓ 成功：0"
                textSize = 14f
                setTextColor(context.getColor(android.R.color.holo_green_dark))
                setPadding(0, 2, 0, 2)
            }
            successCountTextView = successCountText
            
            val failedCountText = TextView(context).apply {
                text = "✗ 失败：0"
                textSize = 14f
                setTextColor(context.getColor(android.R.color.holo_red_dark))
                setPadding(0, 2, 0, 2)
            }
            failedCountTextView = failedCountText
            
            val currentTaskText = TextView(context).apply {
                text = "等待执行..."
                textSize = 13f
                setTextColor(context.getColor(android.R.color.black))
                setPadding(0, 4, 0, 8)
            }
            currentTaskTextView = currentTaskText
            
            val controlButton = android.widget.Button(context).apply {
                text = "停止执行"
                textSize = 14f
                setTextColor(context.getColor(android.R.color.white))
                setBackgroundColor(context.getColor(android.R.color.holo_red_dark))
                setPadding(16, 8, 16, 8)
                contentDescription = "停止替我行动执行"
            }
            controlButtonView = controlButton

            controlButton.setOnClickListener {
                if (_currentState.value.stopped) return@setOnClickListener
                markStopped()
                onStopRequested?.invoke()
            }

            cardView.addView(titleText)
            cardView.addView(statusText)
            cardView.addView(progressText)
            cardView.addView(successCountText)
            cardView.addView(failedCountText)
            cardView.addView(currentTaskText)
            cardView.addView(controlButton)
            currentView = cardView
            
            // 添加到 Window
            val params = android.view.WindowManager.LayoutParams().apply {
                type = android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                flags = android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                format = PixelFormat.TRANSPARENT
                width = 320
                height = 360
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
        
        currentTaskTextView?.text = state.currentStep.ifBlank { "等待执行..." }
        progressTextView?.text = "进度：${state.success + state.failed} / ${state.total}"
        successCountTextView?.text = "✓ 成功：${state.success}"
        failedCountTextView?.text = "✗ 失败：${state.failed}"
        controlButtonView?.apply {
            visibility = View.VISIBLE
            isEnabled = !state.stopped
            text = if (state.stopped) "已停止" else "停止执行"
            setBackgroundColor(
                context.getColor(
                    if (state.stopped) android.R.color.darker_gray else android.R.color.holo_red_dark,
                ),
            )
        }
    }
}

// 扩展函数：设置粗体
private fun TextView.setBold() {
    setTypeface(null, android.graphics.Typeface.BOLD)
}