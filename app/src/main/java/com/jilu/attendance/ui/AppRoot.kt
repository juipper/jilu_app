package com.jilu.attendance.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.jilu.attendance.AppContainer
import com.jilu.attendance.data.AppSettings
import com.jilu.attendance.reminder.Notifications
import com.jilu.attendance.reminder.ReminderScheduler

@Composable
fun AppRoot(container: AppContainer) {
    val settings by container.settingsRepo.settings.collectAsState(initial = AppSettings())
    val context = LocalContext.current
    var showSettings by remember { mutableStateOf(false) }

    // 设置一有变化就重排提醒（启动、改工作日、改时间、开关都会走到这里）
    LaunchedEffect(settings) {
        Notifications.ensureChannel(context)
        ReminderScheduler.rescheduleAll(context, settings)
    }

    BackHandler(enabled = showSettings) { showSettings = false }

    if (showSettings) {
        SettingsScreen(
            container = container,
            settings = settings,
            onBack = { showSettings = false }
        )
    } else {
        CalendarScreen(
            container = container,
            settings = settings,
            onOpenSettings = { showSettings = true }
        )
    }
}
