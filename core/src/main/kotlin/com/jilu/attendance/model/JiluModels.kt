package com.jilu.attendance.model

/** 一条考勤记录。未记录的日子没有条目。 */
data class AttendanceRecord(
    val date: String,
    val dayType: String,
    val outcome: String,
    val markedAt: Long = 0L
)

/** 针对某个具体日期的日型覆盖（法定节假日、调休补班）。 */
data class DayOverride(
    val date: String,
    val dayType: String,
    val reason: String? = null
)

enum class BackgroundMode(val value: String) {
    DEFAULT("default"),
    COLOR("color"),
    IMAGE("image");

    companion object {
        fun fromValue(value: String?): BackgroundMode =
            entries.firstOrNull { it.value == value } ?: DEFAULT
    }
}

enum class BackgroundFit(val value: String) {
    /** 铺满屏幕、裁掉超出部分 */
    COVER("cover"),

    /** 整张图完整显示，空白用同一张图的模糊版填满 */
    FIT("fit");

    companion object {
        fun fromValue(value: String?): BackgroundFit =
            entries.firstOrNull { it.value == value } ?: FIT
    }
}

/**
 * 与 Android 端一致的设置项。桌面端目前只用到 workdayOfWeek 与背景三项，
 * 早/晚提醒字段保留下来只为备份往返不丢数据（桌面版不发提醒）。
 */
data class JiluSettings(
    val workdayOfWeek: Set<Int> = setOf(1, 2, 3, 4, 5),
    val morningEnabled: Boolean = true,
    val morningTime: String = "08:55",
    val eveningEnabled: Boolean = true,
    val eveningTime: String = "18:05",
    val backgroundMode: BackgroundMode = BackgroundMode.DEFAULT,
    val backgroundColorId: String = "mint",
    val backgroundFit: BackgroundFit = BackgroundFit.FIT,
    /** 本地背景图版本号，仅桌面端使用，不进备份 */
    val backgroundImageVersion: Long = 0L
)

/** 一份完整的备份快照，和 Android 的备份 JSON 一一对应。 */
data class JiluSnapshot(
    val records: List<AttendanceRecord> = emptyList(),
    val overrides: List<DayOverride> = emptyList(),
    val settings: JiluSettings = JiluSettings(),
    val exportedAt: Long = 0L
) {
    fun outcomeMap(): Map<String, String> = records.associate { it.date to it.outcome }
    fun overrideMap(): Map<String, String> = overrides.associate { it.date to it.dayType }
}
