package com.yanxing.agent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dagger.hilt.android.AndroidEntryPoint
import com.yanxing.agent.ui.AgentApp
import com.yanxing.agent.ui.theme.YanxingTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            YanxingTheme {
                AgentApp(initialText = intent?.getStringExtra(EXTRA_QUICK_TEXT))
            }
        }
    }

    companion object {
        /** 悬浮窗快捷输入传递的文本 */
        const val EXTRA_QUICK_TEXT = "quick_text"
    }
}
