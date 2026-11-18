package com.jilu.attendance.ui

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
