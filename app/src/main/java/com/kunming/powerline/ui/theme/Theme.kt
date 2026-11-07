package com.kunming.powerline.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** 主色：红色系强调色 #B71C1C */
val PowerRed = Color(0xFFB71C1C)
val PowerRedDark = Color(0xFF8B0000)
val PowerRedLight = Color(0xFFE53935)
/** 浅米色弹窗背景 */
val CreamBackground = Color(0xFFFDF7F0)
val CreamSurface = Color(0xFFFBF3EA)
/** 深色半透明浮层 */
val OverlayDark = Color(0xE6101418)
val OverlayDarkSoft = Color(0xCC13181E)
val TextOnDark = Color(0xFFF2F2F2)
val HintGray = Color(0xFF9AA0A6)

private val DarkColors = darkColorScheme(
    primary = PowerRedLight,
    onPrimary = Color.White,
    primaryContainer = PowerRed,
    secondary = Color(0xFFFFB74D),
    background = Color(0xFF0E1116),
    surface = OverlayDarkSoft,
    onSurface = TextOnDark,
    surfaceVariant = Color(0xFF1E252D),
    onSurfaceVariant = Color(0xFFD0D6DE),
    error = Color(0xFFEF5350)
)

private val LightColors = lightColorScheme(
    primary = PowerRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFCDD2),
    onPrimaryContainer = Color(0xFF5F0000),
    secondary = Color(0xFFF57C00),
    background = CreamBackground,
    surface = Color.White,
    onSurface = Color(0xFF1C1B1B)
)

/**
 * 浅色面板配色（管理页 / 导入页 / 设置页统一使用）
 *
 * 这些页面是浅米色底，必须使用一套浅色 Material 配色，
 * 否则 OutlinedTextField / AlertDialog 等组件会继承全局深色主题的
 * 浅色文字，出现「白字白底看不清」。
 */
val PanelBg = Color(0xFFFBF6EE)        // 面板底色
val PanelCard = Color(0xFFFFFDF8)      // 卡片 / 输入框底色
val PanelLine = Color(0xFFE8DFD2)      // 分割线 / 边框
val PanelText = Color(0xFF2B2B2B)      // 主文字
val PanelTextSub = Color(0xFF7A6F66)   // 次要文字
val PanelTextHint = Color(0xFFA2968A)  // 提示文字

private val LightPanelColors = lightColorScheme(
    primary = PowerRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE2E0),
    onPrimaryContainer = Color(0xFF5F0000),
    secondary = Color(0xFFEF6C00),
    onSecondary = Color.White,
    background = PanelBg,
    onBackground = PanelText,
    surface = PanelCard,
    onSurface = PanelText,
    surfaceVariant = Color(0xFFF3ECE1),
    onSurfaceVariant = PanelTextSub,
    outline = Color(0xFFCDBFB0),
    outlineVariant = PanelLine,
    error = Color(0xFFC62828),
    onError = Color.White
)

/** 浅色面板作用域：包在浅色底页面外层，让 Material 组件用浅色配色 */
@Composable
fun LightPanelTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightPanelColors, typography = AppTypography, content = content)
}
private val AppTypography = Typography(
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium)
)

@Composable
fun PowerLineTheme(
    darkTheme: Boolean = true, // 工业风默认深色浮层
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content
    )
}
