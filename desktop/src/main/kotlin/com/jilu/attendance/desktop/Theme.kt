package com.jilu.attendance.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val AttendedColor = Color(0xFF2E7D32)
val AbsentColor = Color(0xFF9E9E9E)
val OvertimeColor = Color(0xFFEF6C00)
val NoOvertimeColor = Color(0xFFCFD8DC)
val UnmarkedBorder = Color(0xFFE0E0E0)
val MutedText = Color(0xFF757575)

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E7D32),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8E6C9),
    onPrimaryContainer = Color(0xFF1B5E20),
    secondary = Color(0xFFEF6C00),
    onSecondary = Color.White,
    surface = Color(0xFFFFFFFF),
    background = Color(0xFFF7F8F7)
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightColors, content = content)
}

data class BackgroundPreset(val id: String, val label: String, val colors: List<Color>)

val backgroundPresets = listOf(
    BackgroundPreset("mint", "薄荷", listOf(Color(0xFFE8F5E9), Color(0xFFF1F8E9))),
    BackgroundPreset("sky", "天空", listOf(Color(0xFFE3F2FD), Color(0xFFEDE7F6))),
    BackgroundPreset("sand", "沙丘", listOf(Color(0xFFFFF8E1), Color(0xFFFBE9E7))),
    BackgroundPreset("rose", "樱粉", listOf(Color(0xFFFCE4EC), Color(0xFFFFF3E0))),
    BackgroundPreset("clay", "灰白", listOf(Color(0xFFF5F5F5), Color(0xFFECEFF1)))
)

fun presetOf(id: String): BackgroundPreset =
    backgroundPresets.firstOrNull { it.id == id } ?: backgroundPresets.first()

val weekLabels = listOf("一", "二", "三", "四", "五", "六", "日")
