package com.jilu.attendance

import android.content.Context
import com.jilu.attendance.backup.BackupManager
import com.jilu.attendance.data.AttendanceDao
import com.jilu.attendance.data.BackgroundImageStore
import com.jilu.attendance.data.JiluDatabase
import com.jilu.attendance.data.SettingsRepository

class AppContainer private constructor(context: Context) {

    val db: JiluDatabase = JiluDatabase.get(context)

    val dao: AttendanceDao = db.dao()

    val settingsRepo: SettingsRepository = SettingsRepository(context)

    val backgroundStore: BackgroundImageStore = BackgroundImageStore(context)

    val backup: BackupManager = BackupManager(db, settingsRepo)

    companion object {
        @Volatile
        private var instance: AppContainer? = null

        fun get(context: Context): AppContainer =
            instance ?: synchronized(this) {
                instance ?: AppContainer(context.applicationContext).also { instance = it }
            }
    }
}
