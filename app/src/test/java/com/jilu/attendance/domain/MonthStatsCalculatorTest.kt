package com.jilu.attendance.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class MonthStatsCalculatorTest {

    private val weekdays = setOf(1, 2, 3, 4, 5)
    private val month = YearMonth.of(2026, 11)
    private val today = LocalDate.of(2026, 11, 17)

    @Test
    fun `weekday rule marks monday_to_friday as workday`() {
        val monday = LocalDate.of(2026, 11, 16)
        val sunday = LocalDate.of(2026, 11, 15)
        assertEquals(DayType.WORKDAY, DayTypeResolver.resolve(monday, weekdays, emptyMap()))
        assertEquals(DayType.RESTDAY, DayTypeResolver.resolve(sunday, weekdays, emptyMap()))
    }

    @Test
    fun `single day override wins over week rule`() {
        val saturday = LocalDate.of(2026, 11, 21)
        val overrides = mapOf("2026-11-21" to DayType.WORKDAY.value)
        assertEquals(DayType.WORKDAY, DayTypeResolver.resolve(saturday, weekdays, overrides))
    }

    @Test
    fun `unknown override value falls back to week rule`() {
        val monday = LocalDate.of(2026, 11, 16)
        val overrides = mapOf("2026-11-16" to "holiday")
        assertEquals(DayType.WORKDAY, DayTypeResolver.resolve(monday, weekdays, overrides))
    }

    @Test
    fun `stats count attended absent overtime and unmarked separately`() {
        val overrides = mapOf(
            "2026-11-21" to DayType.WORKDAY.value,  // 补班周六
            "2026-11-24" to DayType.RESTDAY.value   // 请假/节日
        )
        val outcomes = mapOf(
            "2026-11-16" to Outcome.ATTENDED.value,
            "2026-11-17" to Outcome.ATTENDED.value,
            "2026-11-18" to Outcome.ABSENT.value,
            "2026-11-15" to Outcome.OVERTIME.value,
            "2026-11-22" to Outcome.NO_OVERTIME.value
        )

        val stats = MonthStatsCalculator.compute(month, today, weekdays, overrides, outcomes)

        // 11 月共 30 天：周一至周五共 21 天，
        // - 11-21 周六被覆盖为工作日 => +1
        // - 11-24 周二被覆盖为休息日 => -1
        assertEquals(21, stats.expectedWorkdays)
        assertEquals(2, stats.attended)
        assertEquals(1, stats.absent)
        assertEquals(1, stats.overtime)
    }

    @Test
    fun `unmarked past days are counted`() {
        val stats = MonthStatsCalculator.compute(month, today, weekdays, emptyMap(), emptyMap())
        // 2026-11-01 是周日；11-01 之前没有工作日。
        // 11-02(一) 到 11-17(二) 之间的工作日：11-02,03,04,05,06,09,10,11,12,13,16,17 = 12 天
        assertEquals(12, stats.unmarkedPastWorkdays)
        assertEquals(0, stats.attended)
        assertEquals(0, stats.overtime)
    }
}
