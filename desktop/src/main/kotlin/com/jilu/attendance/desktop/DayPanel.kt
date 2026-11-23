package com.jilu.attendance.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jilu.attendance.domain.DayType
import com.jilu.attendance.domain.Outcome
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val panelFormatter = DateTimeFormatter.ofPattern("yyyy 年 M 月 d 日")
private val weekdayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

/** 右侧固定的当日面板：标记、清除、改日型。 */
@Composable
fun DayPanel(state: AppState, modifier: Modifier = Modifier) {
    val date = state.selected
    val dayType = state.dayTypeOf(date)
    val record = state.recordOf(date)
    val hasOverride = state.overrideMap.containsKey(date.toString())
    val canMark = !date.isAfter(state.today)

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f))
    ) {
        Column(Modifier.fillMaxSize().padding(22.dp)) {
            Text(dateFormatterLine(date), fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                weekdayNames[date.dayOfWeek.value - 1] + if (date == state.today) " · 今天" else "",
                fontSize = 13.sp,
                color = MutedText
            )

            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DayTypeChip(dayType)
                if (hasOverride) {
                    Text("单日覆盖", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                }
            }

            Spacer(Modifier.height(18.dp))

            if (canMark) {
                val current = Outcome.fromValue(record?.outcome)
                state.outcomesFor(date).forEach { outcome ->
                    OutcomeButton(
                        outcome = outcome,
                        selected = current == outcome,
                        onClick = { state.mark(date, outcome) }
                    )
                    Spacer(Modifier.height(8.dp))
                }
                if (record != null) {
                    TextButton(onClick = { state.clearMark(date) }) { Text("清除记录") }
                }
            } else {
                Text(
                    "未来日期不能打考勤标记，但可以先设好日型（节假日 / 补班）。",
                    fontSize = 13.sp,
                    color = MutedText
                )
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(14.dp))
            Text("日型", fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { state.setOverride(date, DayType.WORKDAY) }) { Text("设为工作日") }
                OutlinedButton(onClick = { state.setOverride(date, DayType.RESTDAY) }) { Text("设为休息日") }
            }
            if (hasOverride) {
                TextButton(onClick = { state.clearOverride(date) }) { Text("取消单日覆盖") }
            }

            Spacer(Modifier.weight(1f))
            Text(
                "方向键选日期 · 空格标记 · Esc 关闭设置",
                fontSize = 11.sp,
                color = MutedText
            )
        }
    }
}

private fun dateFormatterLine(date: LocalDate): String = panelFormatter.format(date)

@Composable
private fun DayTypeChip(dayType: DayType) {
    val color = if (dayType == DayType.WORKDAY) Color(0xFF2E7D32) else Color(0xFFEF6C00)
    Box(
        Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(dayType.label, fontSize = 12.sp, color = color, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun OutcomeButton(outcome: Outcome, selected: Boolean, onClick: () -> Unit) {
    val baseColor = when (outcome) {
        Outcome.ATTENDED -> AttendedColor
        Outcome.ABSENT -> AbsentColor
        Outcome.OVERTIME -> OvertimeColor
        Outcome.NO_OVERTIME -> NoOvertimeColor
    }
    if (selected) {
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = baseColor)
        ) { Text(outcome.label + "（再点撤销）", color = Color.White) }
    } else {
        OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(baseColor))
            Spacer(Modifier.width(8.dp))
            Text(outcome.label)
        }
    }
}
