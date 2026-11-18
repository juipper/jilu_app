package com.jilu.attendance.domain

import java.time.LocalDate
import java.time.YearMonth

data class MonthStats(
    /** 本月判定为工作日的日期总数（含未来日期） */
    val expectedWorkdays: Int = 0,
    /** 本月「上班了」天数 */
    val attended: Int = 0,
    /** 本月「没上班」天数 */
    val absent: Int = 0,
    /** 本月已过去但未记录的工作日数 */
    val unmarkedPastWorkdays: Int = 0,
    /** 本月已过去的休息日数 */
    val pastRestdays: Int = 0,
    /** 本月「加班了」天数 */
    val overtime: Int = 0,
    /** 本月已过去但未记录的休息日数 */
    val unmarkedPastRestdays: Int = 0
)

object MonthStatsCalculator {

    fun compute(
        month: YearMonth,
        today: LocalDate,
        workdayOfWeek: Set<Int>,
        overrides: Map<String, String>,
        outcomes: Map<String, String>
    ): MonthStats {
        var expectedWorkdays = 0
        var attended = 0
        var absent = 0
        var unmarkedPastWorkdays = 0
        var pastRestdays = 0
        var overtime = 0
        var unmarkedPastRestdays = 0

        for (day in 1..month.lengthOfMonth()) {
            val date = month.atDay(day)
            val dayType = DayTypeResolver.resolve(date, workdayOfWeek, overrides)
            val outcome = outcomes[date.toString()]
            val isPastOrToday = !date.isAfter(today)

            if (dayType == DayType.WORKDAY) {
                expectedWorkdays++
                when (outcome) {
                    Outcome.ATTENDED.value -> attended++
                    Outcome.ABSENT.value -> absent++
                    else -> if (isPastOrToday) unmarkedPastWorkdays++
                }
            } else {
                if (isPastOrToday) pastRestdays++
                when (outcome) {
                    Outcome.OVERTIME.value -> overtime++
                    Outcome.NO_OVERTIME.value -> Unit
                    else -> if (isPastOrToday) unmarkedPastRestdays++
                }
            }
        }

        return MonthStats(
            expectedWorkdays = expectedWorkdays,
            attended = attended,
            absent = absent,
            unmarkedPastWorkdays = unmarkedPastWorkdays,
            pastRestdays = pastRestdays,
            overtime = overtime,
            unmarkedPastRestdays = unmarkedPastRestdays
        )
    }
}
