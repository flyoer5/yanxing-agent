package com.yanxing.agent.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// ===== 马卡龙主题调色板 =====
// 亮色：米色底 + 粉彩（珊瑚粉 / 薰衣草紫 / 薄荷绿）
private val MacaronLightColors = lightColorScheme(
    primary = 0xFFC97A82,          // 温柔珊瑚粉（主行动色）
    onPrimary = 0xFFFFFFFF,
    primaryContainer = 0xFFF4D6DA,  // 奶油粉容器
    onPrimaryContainer = 0xFF5C2A30,
    secondary = 0xFF9C8AC9,         // 薰衣草紫
    onSecondary = 0xFFFFFFFF,
    secondaryContainer = 0xFFE6DFF5, // 淡紫容器
    onSecondaryContainer = 0xFF3A2D5C,
    tertiary = 0xFF7FA89B,          // 薄荷绿
    onTertiary = 0xFFFFFFFF,
    tertiaryContainer = 0xFFD6E8E2, // 淡薄荷容器
    onTertiaryContainer = 0xFF22473C,
    background = 0xFFFAF6EF,        // 米色背景
    onBackground = 0xFF3E3833,      // 暖深棕文字
    surface = 0xFFFCFAF6,           // 卡片米白
    onSurface = 0xFF3E3833,
    surfaceVariant = 0xFFF0E9DF,    // 输入框/略深米色
    onSurfaceVariant = 0xFF6B6259,
    surfaceTint = 0xFFC97A82,
    error = 0xFFC97A7A,             // 柔和玫红
    onError = 0xFFFFFFFF,
    errorContainer = 0xFFF5DADA,
    onErrorContainer = 0xFF5C2424,
    outline = 0xFFA99E92,           // 米灰描边
    outlineVariant = 0xFFE4DBD0,
    inverseSurface = 0xFF3E3832,
    inverseOnSurface = 0xFFFCFAF6,
    inversePrimary = 0xFFF4D6E0,
)

// 深色：暖棕深底 + 柔和粉彩（暗色马卡龙）
private val MacaronDarkColors = darkColorScheme(
    primary = 0xFFE3A5AD,          // 亮珊瑚粉
    onPrimary = 0xFF4A2026,
    primaryContainer = 0xFF6E3A42,
    onPrimaryContainer = 0xFFF6DAE0,
    secondary = 0xFFB9A6D8,        // 薰衣草紫
    onSecondary = 0xFF352A4F,
    secondaryContainer = 0xFF5A4A7A,
    onSecondaryContainer = 0xFFEBE2FA,
    tertiary = 0xFF8FBCAB,         // 薄荷绿
    onTertiary = 0xFF1F4036,
    tertiaryContainer = 0xFF3E5F53,
    onTertiaryContainer = 0xFFDCEFE7,
    background = 0xFF262220,       // 暖深棕背景
    onBackground = 0xFFE8E0D8,
    surface = 0xFF2E2A27,          // 卡片深暖色
    onSurface = 0xFFE8E0D8,
    surfaceVariant = 0xFF3D3833,
    onSurfaceVariant = 0xFFC0B6AC,
    surfaceTint = 0xFFE3A5AD,
    error = 0xFFD98C8C,
    onError = 0xFF4A1E1E,
    errorContainer = 0xFF6E3434,
    onErrorContainer = 0xFFF8D9D9,
    outline = 0xFF8D837A,
    outlineVariant = 0xFF49423C,
    inverseSurface = 0xFFE8E0D8,
    inverseOnSurface = 0xFF2E2A27,
    inversePrimary = 0xFFC97A82,
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
