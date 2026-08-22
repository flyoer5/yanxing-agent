package com.yanxing.agent

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dagger.hilt.android.AndroidEntryPoint
import com.yanxing.agent.ui.AgentApp
import com.yanxing.agent.ui.theme.YanxingTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // 悬浮窗快捷文本：Activity 复用时不走 onCreate，必须经 onNewIntent 更新，
    // 否则来自悬浮窗的第二次及以后输入会丢失
    private var quickText by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        quickText = intent?.getStringExtra(EXTRA_QUICK_TEXT)
        setContent {
            YanxingTheme {
                AgentApp(initialText = quickText)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_QUICK_TEXT)?.let { quickText = it }
    }

    companion object {
        /** 悬浮窗快捷输入传递的文本 */
        const val EXTRA_QUICK_TEXT = "quick_text"
    }
}
