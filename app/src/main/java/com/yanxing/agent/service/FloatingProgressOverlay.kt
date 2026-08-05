package com.yanxing.agent.service

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.widget.TextView
import androidx.cardview.widget.CardView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 悬浮窗实时进度显示
 * 显示执行状态、成功/失败计数、控制按钮
 */
class FloatingProgressOverlay(context: Context) {
    
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
    private var isVisible = false
    
    // 内部状态流
    private val _currentState = MutableStateFlow(CheckboxState())
    val currentState: StateFlow<CheckboxState> = _currentState.asStateFlow()
    
    data class CheckboxState(
        val total: Int = 0,
        val success: Int = 0,
        val failed: Int = 0,
        val currentStep: String = "",
        val isPaused: Boolean = false,
        val actionModeEnabled: Boolean = false,
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
    fun incrementFailed() {
        _currentState.value = _currentState.value.copy(failed = _currentState.value.failed + 1)
        updateUI()
    }
    
    /**
     * 切换暂停状态
     */
    fun togglePause() {
        val newState = !_currentState.value.isPaused
        _currentState.value = _currentState.value.copy(isPaused = newState)
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
            if (isVisible && _currentView != null) {
                windowManager.removeView(_currentView)
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
            
            val cardView = CardView(context).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).also {
                    it.width = 320
                    it.height = 360
                }
                radius = 12f
                setCardBackgroundColor(context.getColor(android.R.color.white))
                elevation = 8f
            }
            
            val layout = View(context).apply {
                setBackgroundColor(context.getColor(android.R.color.transparent))
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
                id = View.generateViewId()
                text = "0 / 0"
                textSize = 18f
                setTextColor(context.getColor(R.color.primary))
                setPadding(16, 4, 16, 4)
                setBold()
            }
            
            val statsContainer = View(context).apply {
                setBackgroundColor(context.getColor(android.R.color.transparent))
            }
            
            val successCountText = TextView(context).apply {
                id = View.generateViewId()
                text = "✓ 成功：0"
                textSize = 14f
                setTextColor(context.getColor(android.R.color.holo_green_light))
                setPadding(16, 2, 16, 2)
            }
            
            val failedCountText = TextView(context).apply {
                id = View.generateViewId()
                text = "✗ 失败：0"
                textSize = 14f
                setTextColor(context.getColor(android.R.color.holo_red_light))
                setPadding(16, 2, 16, 2)
            }
            
            val currentTaskText = TextView(context).apply {
                id = View.generateViewId()
                text = "等待执行..."
                textSize = 13f
                setTextColor(context.getColor(android.R.color.black))
                setPadding(16, 4, 16, 8)
            }
            
            val controlButton = android.widget.Button(context).apply {
                id = View.generateViewId()
                text = "暂停"
                textSize = 14f
                setTextColor(context.getColor(android.R.color.white))
                setBackgroundColor(context.getColor(android.R.color.holo_blue_light))
                setPadding(16, 8, 16, 8)
                visibility = View.GONE // 初始隐藏
            }
            
            controlButton.setOnClickListener {
                togglePause()
                updateControlButtonText()
            }
            
            // 触摸处理 - 拖拽功能
            layout.setOnTouchListener { _, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        // 记录起始位置
                        // TODO: 实现拖拽逻辑
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        // 更新窗口位置
                        // TODO: 实现拖拽逻辑
                    }
                }
                false
            }
            
            // 添加布局到卡片
            val contentLayout = android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setPadding(0, 0, 0, 0)
            }
            
            contentLayout.addView(titleText)
            contentLayout.addView(statusText)
            contentLayout.addView(progressText)
            contentLayout.addView(statsContainer)
            contentLayout.addView(successCountText)
            contentLayout.addView(failedCountText)
            contentLayout.addView(currentTaskText)
            contentLayout.addView(controlButton)
            
            cardView.addView(contentLayout)
            _currentView = cardView
            
            // 添加到 Window
            val params = android.view.WindowManager.LayoutParams().apply {
                type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                format = PixelFormat.TRANSPARENT
                width = 320
                height = 360
                gravity = Gravity.TOP or Gravity.END
                x = 0
                y = 100
            }
            
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
        
        // 查找并更新所有 TextView
        _currentView?.let { view ->
            view.findViewById<TextView>(R.id.currentStep)?.text = state.currentStep
            view.findViewById<TextView>(R.id.progressText)?.text = 
                "进度：${state.success + state.failed} / ${state.total}"
            view.findViewById<TextView>(R.id.successCountText)?.text = 
                "✓ 成功：${state.success}"
            view.findViewById<TextView>(R.id.failedCountText)?.text = 
                "✗ 失败：${state.failed}"
            view.findViewById<View>(R.id.controlButton)?.visibility = 
                if (state.isPaused) View.VISIBLE else View.GONE
        }
    }
    
    /**
     * 更新控制按钮文字
     */
    private fun updateControlButtonText() {
        _currentView?.let { view ->
            view.findViewById<android.widget.Button>(R.id.controlButton)?.text = 
                if (_currentState.value.isPaused) "继续" else "暂停"
        }
    }
    
    companion object {
        @Volatile private var _currentView: View? = null
    }
}

// 扩展函数：设置粗体
private fun TextView.setBold() {
    setTypeface(null, android.graphics.Typeface.BOLD)
}