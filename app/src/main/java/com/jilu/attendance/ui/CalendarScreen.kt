package com.jilu.attendance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jilu.attendance.AppContainer
import com.jilu.attendance.data.AppSettings
import com.jilu.attendance.data.AttendanceRecord
import com.jilu.attendance.data.DayOverride
import com.jilu.attendance.domain.DayType
import com.jilu.attendance.domain.DayTypeResolver
import com.jilu.attendance.domain.MonthStatsCalculator
import com.jilu.attendance.domain.Outcome
import com.jilu.attendance.domain.other
import com.jilu.attendance.domain.outcomes
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    container: AppContainer,
    settings: AppSettings,
    onOpenSettings: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val today = LocalDate.now()
    var month by remember { mutableStateOf(YearMonth.now()) }
    var sheetDate by remember { mutableStateOf<LocalDate?>(null) }

    val recordsFlow = remember(month) { container.dao.observeMonth(month.toString()) }
    val records by recordsFlow.collectAsState(initial = emptyList())
    val overridesFlow = remember(month) { container.dao.observeOverridesInMonth(month.toString()) }
    val overrides by overridesFlow.collectAsState(initial = emptyList())

    val overrideMap = remember(overrides) { overrides.associate { it.date to it.dayType } }
    val outcomeMap = remember(records) { records.associate { it.date to it.outcome } }
    val stats = remember(month, settings.workdayOfWeek, overrideMap, outcomeMap, today) {
        MonthStatsCalculator.compute(month, today, settings.workdayOfWeek, overrideMap, outcomeMap)
    }

    fun dayTypeOf(date: LocalDate): DayType =
        DayTypeResolver.resolve(date, settings.workdayOfWeek, overrideMap)

    fun mark(date: LocalDate, outcome: Outcome) {
        if (date.isAfter(today)) return
        val dayType = dayTypeOf(date)
        val existing = records.firstOrNull { it.date == date.toString() }
        scope.launch {
            if (existing?.outcome == outcome.value) {
                container.dao.delete(date.toString())
            } else {
                container.dao.upsert(
                    AttendanceRecord(
                        date = date.toString(),
                        dayType = dayType.value,
                        outcome = outcome.value,
                        markedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    fun clearMark(date: LocalDate) {
        scope.launch { container.dao.delete(date.toString()) }
    }

    fun setOverride(date: LocalDate, dayType: DayType) {
        scope.launch {
            container.dao.upsertOverride(DayOverride(date.toString(), dayType.value, null))
            val existing = container.dao.get(date.toString())
            // 日型变了，旧标记与新日型不匹配，一并清掉，避免出现「休息日却记着上班了」
            if (existing != null && existing.dayType != dayType.value) {
                container.dao.delete(date.toString())
            }
        }
    }

    fun clearOverride(date: LocalDate) {
        scope.launch { container.dao.deleteOverride(date.toString()) }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("考勤记录") },
                actions = {
                    TextButton(onClick = onOpenSettings) { Text("设置") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                )
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            BackgroundLayer(settings = settings, modifier = Modifier.fillMaxSize())
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { month = month.minusMonths(1) }) { Text("上月") }
                Text(
                    text = "${month.year} 年 ${month.monthValue} 月",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(onClick = { month = month.plusMonths(1) }) { Text("下月") }
            }

            Text(
                text = "出勤 ${stats.attended} / 应出勤 ${stats.expectedWorkdays} 天 · 加班 ${stats.overtime} 天",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "未记录：工作日 ${stats.unmarkedPastWorkdays} 天 · 休息日 ${stats.unmarkedPastRestdays} 天",
                color = MutedText,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp)
            )

            Spacer(Modifier.height(12.dp))

            WeekHeader()
            Spacer(Modifier.height(4.dp))
            CalendarGrid(
                month = month,
                today = today,
                workdayOfWeek = settings.workdayOfWeek,
                overrideMap = overrideMap,
                outcomeMap = outcomeMap,
                onDayClick = { sheetDate = it }
            )

            Spacer(Modifier.weight(1f))
            HorizontalDivider()
            Legend()
            }
        }
    }

    val selected = sheetDate
    if (selected != null) {
        DaySheet(
            date = selected,
            dayType = dayTypeOf(selected),
            record = records.firstOrNull { it.date == selected.toString() },
            hasOverride = overrideMap.containsKey(selected.toString()),
            canMark = !selected.isAfter(today),
            onMark = { outcome -> mark(selected, outcome) },
            onClearMark = { clearMark(selected) },
            onSetOverride = { dayType -> setOverride(selected, dayType) },
            onClearOverride = { clearOverride(selected) },
            onDismiss = { sheetDate = null }
        )
    }
}

@Composable
private fun WeekHeader() {
    Row(Modifier.fillMaxWidth()) {
        weekLabels.forEach { label ->
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                color = MutedText,
                fontSize = 12.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun CalendarGrid(
    month: YearMonth,
    today: LocalDate,
    workdayOfWeek: Set<Int>,
    overrideMap: Map<String, String>,
    outcomeMap: Map<String, String>,
    onDayClick: (LocalDate) -> Unit
) {
    val daysInMonth = month.lengthOfMonth()
    val leadingBlanks = month.atDay(1).dayOfWeek.value - 1
    val rows = ((leadingBlanks + daysInMonth) + 6) / 7

    Column(Modifier.fillMaxWidth()) {
        repeat(rows) { rowIndex ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { columnIndex ->
                    val cellIndex = rowIndex * 7 + columnIndex
                    val dayNumber = cellIndex - leadingBlanks + 1
                    Box(modifier = Modifier.weight(1f)) {
                        if (dayNumber in 1..daysInMonth) {
                            val date = month.atDay(dayNumber)
                            val dayType = DayTypeResolver.resolve(date, workdayOfWeek, overrideMap)
                            val outcome = Outcome.fromValue(outcomeMap[date.toString()])
                            DayCell(
                                date = date,
                                isToday = date == today,
                                isFuture = date.isAfter(today),
                                dayType = dayType,
                                outcome = outcome,
                                onClick = { onDayClick(date) }
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

    Box(
        modifier = Modifier
            .padding(2.dp)
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(background)
            .border(
                width = if (isToday) 2.dp else 1.dp,
                color = if (isToday) MaterialTheme.colorScheme.primary else UnmarkedBorder,
                shape = CircleShape
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            color = textColor,
            fontSize = 14.sp,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun Legend() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
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
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(label, fontSize = 11.sp, color = MutedText, modifier = Modifier.padding(start = 4.dp))
    }
}

internal val weekLabels = listOf("一", "二", "三", "四", "五", "六", "日")
