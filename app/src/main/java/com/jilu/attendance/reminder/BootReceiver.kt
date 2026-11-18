package com.jilu.attendance.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jilu.attendance.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = AppContainer.get(appContext).settingsRepo.current()
                ReminderScheduler.rescheduleAll(appContext, settings)
            } catch (throwable: Throwable) {
                // 重启后重排失败不阻塞；下次进入 App 会再次重排
            } finally {
                pendingResult.finish()
            }
        }
    }
}
