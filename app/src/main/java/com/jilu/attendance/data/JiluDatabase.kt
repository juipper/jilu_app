package com.jilu.attendance.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [AttendanceRecord::class, DayOverride::class],
    version = 1,
    exportSchema = false
)
abstract class JiluDatabase : RoomDatabase() {

    abstract fun dao(): AttendanceDao

    companion object {
        private const val DB_NAME = "jilu.db"

        @Volatile
        private var instance: JiluDatabase? = null

        fun get(context: Context): JiluDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    JiluDatabase::class.java,
                    DB_NAME
                ).build().also { instance = it }
            }
    }
}
