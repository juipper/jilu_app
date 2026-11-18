package com.jilu.attendance.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jilu.attendance.AppContainer
import com.jilu.attendance.domain.DayType
import com.jilu.attendance.domain.DayTypeResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val slot = intent.getStringExtra(ReminderScheduler.EXTRA_SLOT)
            ?: ReminderScheduler.SLOT_MORNING
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val container = AppContainer.get(appContext)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = container.settingsRepo.current()
                val today = LocalDate.now()
                val override = container.dao.getOverride(today.toString())
                val overrides = if (override != null) {
                    mapOf(today.toString() to override.dayType)
                } else {
                    emptyMap()
                }
                val dayType = DayTypeResolver.resolve(today, settings.workdayOfWeek, overrides)
                val record = container.dao.get(today.toString())

                if (dayType == DayType.WORKDAY && record == null) {
                    Notifications.show(
                        appContext,
                        "今天还没记录考勤",
                        "点一下，记下今天「上班了」或「没上班」。"
                    )
                }

                val timeText = if (slot == ReminderScheduler.SLOT_MORNING) {
                    settings.morningTime
                } else {
                    settings.eveningTime
                }
                val enabled = if (slot == ReminderScheduler.SLOT_MORNING) {
                    settings.morningEnabled
                } else {
                    settings.eveningEnabled
                }
                ReminderScheduler.scheduleSlot(appContext, slot, timeText, enabled)
            } catch (throwable: Throwable) {
                // 提醒失败不应影响下一次调度，静默吞掉
            } finally {
                pendingResult.finish()
            }
        }
    }
}
