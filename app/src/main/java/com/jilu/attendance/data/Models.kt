package com.jilu.attendance.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 一天的考勤标记。未记录的日子没有行。
 *
 * [dayType] 记录当时生效的日型快照（workday / restday），
 * 这样之后修改周规则不会让历史记录变色。
 */
@Entity(tableName = "attendance_record")
data class AttendanceRecord(
    @PrimaryKey val date: String,
    val dayType: String,
    val outcome: String,
    val markedAt: Long
)

/** 针对某个具体日期的日型覆盖（法定节假日、调休补班）。 */
@Entity(tableName = "day_override")
data class DayOverride(
    @PrimaryKey val date: String,
    val dayType: String,
    val reason: String? = null
)
