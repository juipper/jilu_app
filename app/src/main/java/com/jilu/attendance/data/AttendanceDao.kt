package com.jilu.attendance.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AttendanceDao {

    // ---- 考勤记录 ----

    @Query("SELECT * FROM attendance_record WHERE date LIKE :monthPrefix || '%'")
    fun observeMonth(monthPrefix: String): Flow<List<AttendanceRecord>>

    @Query("SELECT * FROM attendance_record")
    fun observeAll(): Flow<List<AttendanceRecord>>

    @Query("SELECT COUNT(*) FROM attendance_record")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM attendance_record")
    suspend fun getAll(): List<AttendanceRecord>

    @Query("SELECT * FROM attendance_record WHERE date = :date LIMIT 1")
    suspend fun get(date: String): AttendanceRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: AttendanceRecord)

    @Insert
    suspend fun insertAll(records: List<AttendanceRecord>)

    @Query("DELETE FROM attendance_record WHERE date = :date")
    suspend fun delete(date: String)

    @Query("DELETE FROM attendance_record")
    suspend fun clearAll()

    // ---- 单日覆盖 ----

    @Query("SELECT * FROM day_override")
    fun observeOverrides(): Flow<List<DayOverride>>

    @Query("SELECT * FROM day_override WHERE date LIKE :monthPrefix || '%'")
    fun observeOverridesInMonth(monthPrefix: String): Flow<List<DayOverride>>

    @Query("SELECT * FROM day_override")
    suspend fun getAllOverrides(): List<DayOverride>

    @Query("SELECT * FROM day_override WHERE date = :date LIMIT 1")
    suspend fun getOverride(date: String): DayOverride?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOverride(dayOverride: DayOverride)

    @Insert
    suspend fun insertOverrides(overrides: List<DayOverride>)

    @Query("DELETE FROM day_override WHERE date = :date")
    suspend fun deleteOverride(date: String)

    @Query("DELETE FROM day_override")
    suspend fun clearOverrides()
}
