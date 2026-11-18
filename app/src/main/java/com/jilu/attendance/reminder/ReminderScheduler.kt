package com.jilu.attendance.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.jilu.attendance.data.AppSettings
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

object ReminderScheduler {

    const val ACTION_REMIND = "com.jilu.attendance.action.REMIND"
    const val EXTRA_SLOT = "slot"
    const val SLOT_MORNING = "morning"
    const val SLOT_EVENING = "evening"

    fun rescheduleAll(context: Context, settings: AppSettings) {
        scheduleSlot(context, SLOT_MORNING, settings.morningTime, settings.morningEnabled)
        scheduleSlot(context, SLOT_EVENING, settings.eveningTime, settings.eveningEnabled)
    }

    fun scheduleSlot(context: Context, slot: String, timeText: String, enabled: Boolean) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = pendingIntent(context, slot)
        alarmManager.cancel(pendingIntent)
        if (!enabled) return

        val (hour, minute) = parseTime(timeText) ?: return
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now(zone)
        var next = LocalDate.now(zone).atTime(hour, minute)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val triggerAt = next.atZone(zone).toInstant().toEpochMilli()

        try {
            val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                alarmManager.canScheduleExactAlarms()
            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
        } catch (security: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    /** 解析 "HH:mm"，非法返回 null。 */
    fun parseTime(text: String): Pair<Int, Int>? {
        val parts = text.trim().split(":")
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour to minute
    }

    private fun pendingIntent(context: Context, slot: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_REMIND
            putExtra(EXTRA_SLOT, slot)
        }
        val requestCode = if (slot == SLOT_MORNING) 1 else 2
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
