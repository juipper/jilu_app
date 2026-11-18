package com.jilu.attendance.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jilu.attendance.data.AttendanceRecord
import com.jilu.attendance.domain.DayType
import com.jilu.attendance.domain.Outcome
import com.jilu.attendance.domain.other
import com.jilu.attendance.domain.outcomes
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DaySheet(
    date: LocalDate,
    dayType: DayType,
    record: AttendanceRecord?,
    hasOverride: Boolean,
    canMark: Boolean,
    onMark: (Outcome) -> Unit,
    onClearMark: () -> Unit,
    onSetOverride: (DayType) -> Unit,
    onClearOverride: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "${date.year} 年 ${date.monthValue} 月 ${date.dayOfMonth} 日 ${weekdayLabel(date)}",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = dayType.label,
                color = MutedText,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp)
            )

            Spacer(Modifier.height(16.dp))

            if (canMark) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val options = dayType.outcomes()
                    options.forEach { outcome ->
                        val selected = record?.outcome == outcome.value
                        OutlinedButton(
                            onClick = { onMark(outcome) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (selected) dayColor(outcome) else Color.Transparent,
                                contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Text(outcome.label)
                        }
                    }
                }
            } else {
                Text("未来日期不能标记考勤。", color = MutedText, fontSize = 13.sp)
            }

            record?.let { item ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "记录于 ${timeLabel(item.markedAt)}",
                    color = MutedText,
                    fontSize = 12.sp
                )
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()

            TextButton(onClick = { onSetOverride(dayType.other()) }) {
                Text("把这一天改为${dayType.other().label}")
            }
            if (hasOverride) {
                TextButton(onClick = onClearOverride) { Text("清除单日覆盖（回到周规则）") }
            }
            if (record != null) {
                TextButton(onClick = onClearMark) { Text("清除这条标记") }
            }
        }
    }
}

internal fun dayColor(outcome: Outcome): Color = when (outcome) {
    Outcome.ATTENDED -> AttendedColor
    Outcome.ABSENT -> AbsentColor
    Outcome.OVERTIME -> OvertimeColor
    Outcome.NO_OVERTIME -> NoOvertimeColor
}

internal fun weekdayLabel(date: LocalDate): String {
    val names = listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")
    return names[date.dayOfWeek.value - 1]
}

internal fun timeLabel(millis: Long): String =
    SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(millis))
