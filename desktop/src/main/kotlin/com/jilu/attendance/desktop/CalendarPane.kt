package com.jilu.attendance.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jilu.attendance.domain.DayType
import com.jilu.attendance.domain.MonthStats
import com.jilu.attendance.domain.MonthStatsCalculator
import com.jilu.attendance.domain.Outcome
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private val dateFormatter = DateTimeFormatter.ofPattern("yyyy 年 M 月 d 日")

@Composable
fun AppContent(state: AppState) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 18.dp)) {
        Header(state)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxSize().weight(1f)) {
            Column(Modifier.weight(1f)) {
                StatsLine(state)
                Spacer(Modifier.height(14.dp))
                WeekHeader(Modifier.width(380.dp))
                Spacer(Modifier.height(6.dp))
                CalendarGrid(state, Modifier.width(380.dp))
                Spacer(Modifier.weight(1f))
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

@Composable
private fun WeekHeader(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth()) {
        weekLabels.forEach { label ->
            Text(
                label,
                modifier = Modifier.weight(1f),
                color = MutedText,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun CalendarGrid(state: AppState, modifier: Modifier = Modifier) {
    val month = state.month
    val daysInMonth = month.lengthOfMonth()
    val leadingBlanks = month.atDay(1).dayOfWeek.value - 1
    val rows = ((leadingBlanks + daysInMonth) + 6) / 7

    Column(modifier.fillMaxWidth()) {
        repeat(rows) { rowIndex ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { columnIndex ->
                    val cellIndex = rowIndex * 7 + columnIndex
                    val dayNumber = cellIndex - leadingBlanks + 1
                    Box(Modifier.weight(1f)) {
                        if (dayNumber in 1..daysInMonth) {
                            val date = month.atDay(dayNumber)
                            DayCell(
                                date = date,
                                isToday = date == state.today,
                                isSelected = date == state.selected,
                                isFuture = date.isAfter(state.today),
                                dayType = state.dayTypeOf(date),
                                outcome = Outcome.fromValue(state.outcomeMap[date.toString()]),
                                onClick = { state.selected = date }
                            )
                        } else {
                            Spacer(Modifier.aspectRatio(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
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
            .padding(3.dp)
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(background)
            .border(width = if (isSelected || isToday) 2.dp else 1.dp, color = borderColor, shape = CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            date.dayOfMonth.toString(),
            color = textColor,
            fontSize = 15.sp,
            fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun Legend() {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
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
