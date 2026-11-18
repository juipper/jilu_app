package com.jilu.attendance.domain

import java.time.LocalDate

/** 日型只有两类：工作日 / 休息日。法定节假日与调休靠单日覆盖表达。 */
enum class DayType(val value: String, val label: String) {
    WORKDAY("workday", "工作日"),
    RESTDAY("restday", "休息日");

    companion object {
        fun fromValue(value: String?): DayType? = entries.firstOrNull { it.value == value }
    }
}

/** 一天的标记结果。与 [DayType] 强绑定：工作日只能 attended/absent，休息日只能 overtime/no_overtime。 */
enum class Outcome(val value: String, val label: String) {
    ATTENDED("attended", "上班了"),
    ABSENT("absent", "没上班"),
    OVERTIME("overtime", "加班了"),
    NO_OVERTIME("no_overtime", "没加班");

    companion object {
        fun fromValue(value: String?): Outcome? = entries.firstOrNull { it.value == value }
    }
}

/** 该日型下可选的两种标记。 */
fun DayType.outcomes(): List<Outcome> =
    if (this == DayType.WORKDAY) listOf(Outcome.ATTENDED, Outcome.ABSENT)
    else listOf(Outcome.OVERTIME, Outcome.NO_OVERTIME)

fun DayType.other(): DayType = if (this == DayType.WORKDAY) DayType.RESTDAY else DayType.WORKDAY

object DayTypeResolver {

    /**
     * 判定某个日期生效的日型。
     *
     * 优先级：单日覆盖 > 周规则 > 兜底（未命中周规则即休息日）。
     *
     * @param workdayOfWeek ISO 星期数字集合（1 = 周一 … 7 = 周日）
     * @param overrides 日期（yyyy-MM-dd） → 日型 value 的覆盖表
     */
    fun resolve(
        date: LocalDate,
        workdayOfWeek: Set<Int>,
        overrides: Map<String, String>
    ): DayType {
        DayType.fromValue(overrides[date.toString()])?.let { return it }
        return if (date.dayOfWeek.value in workdayOfWeek) DayType.WORKDAY else DayType.RESTDAY
    }
}
