package com.jilu.attendance.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jilu.attendance.domain.DayType
import com.jilu.attendance.domain.MonthStats
import com.jilu.attendance.domain.MonthStatsCalculator
import com.jilu.attendance.domain.Outcome
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun AppContent(state: AppState) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 18.dp)) {
        Header(state)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxSize().weight(1f)) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                StatsLine(state)
                Spacer(Modifier.height(10.dp))
                CalendarArea(state, Modifier.weight(1f).fillMaxWidth())
                Spacer(Modifier.height(4.dp))
                HorizontalDivider(color = Color(0x33000000))
                Legend()
            }
            Spacer(Modifier.width(28.dp))
            DayPanel(state, Modifier.width(340.dp).fillMaxHeight())
        }
        StatusLine(state)
    }

    if (state.settingsOpen) {
        SettingsOverlay(state)
    }
}

@Composable
private fun Header(state: AppState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("考勤记录", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1B2B22))
        Spacer(Modifier.width(24.dp))
        TextButton(onClick = { state.month = state.month.minusMonths(1) }) { Text("上月") }
        Text(
            "${state.month.year} 年 ${state.month.monthValue} 月",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
        TextButton(onClick = { state.month = state.month.plusMonths(1) }) { Text("下月") }
        TextButton(onClick = {
            val now = YearMonth.now()
            state.month = now
            state.selected = LocalDate.now()
        }) { Text("回到本月") }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = { state.settingsOpen = true }) { Text("设置") }
    }
}

@Composable
private fun StatsLine(state: AppState) {
    val stats: MonthStats = remember(state.month, state.settings.workdayOfWeek, state.overrides, state.records, state.today) {
        MonthStatsCalculator.compute(
            month = state.month,
            today = state.today,
            workdayOfWeek = state.settings.workdayOfWeek,
            overrides = state.overrideMap,
            outcomes = state.outcomeMap
        )
    }
    Column {
        Text(
            "出勤 ${stats.attended} / 应出勤 ${stats.expectedWorkdays} 天 · 加班 ${stats.overtime} 天",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "未记录：工作日 ${stats.unmarkedPastWorkdays} 天 · 休息日 ${stats.unmarkedPastRestdays} 天",
            fontSize = 12.sp,
            color = MutedText
        )
    }
}

/** 月历区域：格子尺寸与间距按可用宽高自动计算，总是铺满这块区域。 */
@Composable
private fun CalendarArea(state: AppState, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val gapY = 6.dp
        val headerHeight = 20.dp
        val maxCellByWidth = (maxWidth - gapY * 6) / 7
        val maxCellByHeight = (maxHeight - headerHeight - gapY * 5) / 6
        val cell: Dp = minOf(maxCellByWidth, maxCellByHeight).coerceAtLeast(24.dp)
        // 被高度限制时，把横向余量摊到列间距里，避免右边留白
        val gapX: Dp = if (cell < maxCellByWidth) {
            ((maxWidth - cell * 7) / 6).coerceAtMost(48.dp)
        } else {
            gapY
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            WeekHeader(cell, gapX)
            Spacer(Modifier.height(gapY))
            CalendarGrid(state, cell, gapX)
        }
    }
}

@Composable
private fun WeekHeader(cell: Dp, gapX: Dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(gapX)) {
        weekLabels.forEach { label ->
            Text(
                label,
                modifier = Modifier.width(cell),
                color = MutedText,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun CalendarGrid(state: AppState, cell: Dp, gapX: Dp) {
    val month = state.month
    val daysInMonth = month.lengthOfMonth()
    val leadingBlanks = month.atDay(1).dayOfWeek.value - 1
    val rows = (leadingBlanks + daysInMonth + 6) / 7

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(rows) { rowIndex ->
            Row(horizontalArrangement = Arrangement.spacedBy(gapX)) {
                repeat(7) { columnIndex ->
                    val dayNumber = rowIndex * 7 + columnIndex - leadingBlanks + 1
                    if (dayNumber in 1..daysInMonth) {
                        val date = month.atDay(dayNumber)
                        DayCell(
                            date = date,
                            size = cell,
                            isToday = date == state.today,
                            isSelected = date == state.selected,
                            isFuture = date.isAfter(state.today),
                            dayType = state.dayTypeOf(date),
                            outcome = Outcome.fromValue(state.outcomeMap[date.toString()]),
                            onClick = { state.selected = date }
                        )
                    } else {
                        Spacer(Modifier.size(cell))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    size: Dp,
    isToday: Boolean,
    isSelected: Boolean,
    isFuture: Boolean,
    dayType: DayType,
    outcome: Outcome?,
    onClick: () -> Unit
) {
    val background = when (outcome) {
        Outcome.ATTENDED -> AttendedColor
        Outcome.ABSENT -> AbsentColor
        Outcome.OVERTIME -> OvertimeColor
        Outcome.NO_OVERTIME -> NoOvertimeColor
        null -> Color.Transparent
    }
    val textColor = when {
        outcome == null -> if (isFuture) MutedText.copy(alpha = 0.55f) else Color(0xFF37474F)
        outcome == Outcome.NO_OVERTIME -> Color(0xFF37474F)
        else -> Color.White
    }
    val borderColor = when {
        isSelected -> MaterialTheme.colorScheme.secondary
        isToday -> MaterialTheme.colorScheme.primary
        else -> UnmarkedBorder
    }

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .border(width = if (isSelected || isToday) 2.dp else 1.dp, color = borderColor, shape = CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            date.dayOfMonth.toString(),
            color = textColor,
            fontSize = (size.value * 0.28f).coerceIn(11f, 22f).sp,
            fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun Legend() {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LegendItem(AttendedColor, "出勤")
        LegendItem(AbsentColor, "没上班")
        LegendItem(OvertimeColor, "加班")
        LegendItem(NoOvertimeColor, "没加班")
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(label, fontSize = 11.sp, color = MutedText, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
private fun StatusLine(state: AppState) {
    if (state.status.isNotBlank()) {
        Spacer(Modifier.height(8.dp))
        Text(state.status, fontSize = 12.sp, color = Color(0xFF455A64))
    }
}
