package com.yanxing.agent.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ===== 马卡龙主题调色板 =====
// 亮色：米色底 + 粉彩（珊瑚粉 / 薰衣草紫 / 薄荷绿）
private val MacaronLightColors = lightColorScheme(
    primary = Color(0xFFC97A82),          // 温柔珊瑚粉（主行动色）
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF4D6DA),  // 奶油粉容器
    onPrimaryContainer = Color(0xFF5C2A30),
    secondary = Color(0xFF9C8AC9),         // 薰衣草紫
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE6DFF5), // 淡紫容器
    onSecondaryContainer = Color(0xFF3A2D5C),
    tertiary = Color(0xFF7FA89B),          // 薄荷绿
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD6E8E2), // 淡薄荷容器
    onTertiaryContainer = Color(0xFF22473C),
    background = Color(0xFFFAF6EF),        // 米色背景
    onBackground = Color(0xFF3E3833),      // 暖深棕文字
    surface = Color(0xFFFCFAF6),           // 卡片米白
    onSurface = Color(0xFF3E3833),
    surfaceVariant = Color(0xFFF0E9DF),    // 输入框/略深米色
    onSurfaceVariant = Color(0xFF6B6259),
    surfaceTint = Color(0xFFC97A82),
    error = Color(0xFFC97A7A),             // 柔和玫红
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF5DADA),
    onErrorContainer = Color(0xFF5C2424),
    outline = Color(0xFFA99E92),           // 米灰描边
    outlineVariant = Color(0xFFE4DBD0),
    inverseSurface = Color(0xFF3E3832),
    inverseOnSurface = Color(0xFFFCFAF6),
    inversePrimary = Color(0xFFF4D6E0),
)

// 深色：暖棕深底 + 柔和粉彩（暗色马卡龙）
private val MacaronDarkColors = darkColorScheme(
    primary = Color(0xFFE3A5AD),          // 亮珊瑚粉
    onPrimary = Color(0xFF4A2026),
    primaryContainer = Color(0xFF6E3A42),
    onPrimaryContainer = Color(0xFFF6DAE0),
    secondary = Color(0xFFB9A6D8),        // 薰衣草紫
    onSecondary = Color(0xFF352A4F),
    secondaryContainer = Color(0xFF5A4A7A),
    onSecondaryContainer = Color(0xFFEBE2FA),
    tertiary = Color(0xFF8FBCAB),         // 薄荷绿
    onTertiary = Color(0xFF1F4036),
    tertiaryContainer = Color(0xFF3E5F53),
    onTertiaryContainer = Color(0xFFDCEFE7),
    background = Color(0xFF262220),       // 暖深棕背景
    onBackground = Color(0xFFE8E0D8),
    surface = Color(0xFF2E2A27),          // 卡片深暖色
    onSurface = Color(0xFFE8E0D8),
    surfaceVariant = Color(0xFF3D3833),
    onSurfaceVariant = Color(0xFFC0B6AC),
    surfaceTint = Color(0xFFE3A5AD),
    error = Color(0xFFD98C8C),
    onError = Color(0xFF4A1E1E),
    errorContainer = Color(0xFF6E3434),
    onErrorContainer = Color(0xFFF8D9D9),
    outline = Color(0xFF8D837A),
    outlineVariant = Color(0xFF49423C),
    inverseSurface = Color(0xFFE8E0D8),
    inverseOnSurface = Color(0xFF2E2A27),
    inversePrimary = Color(0xFFC97A82),
)

@Composable
fun YanxingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) MacaronDarkColors else MacaronLightColors,
        content = content,
    )
}
