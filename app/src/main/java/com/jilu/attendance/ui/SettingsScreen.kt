package com.jilu.attendance.ui

import android.app.DatePickerDialog
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jilu.attendance.AppContainer
import com.jilu.attendance.data.AppSettings
import com.jilu.attendance.data.BackgroundFit
import com.jilu.attendance.data.BackgroundMode
import com.jilu.attendance.data.DayOverride
import com.jilu.attendance.domain.DayType
import com.jilu.attendance.domain.DayTypeResolver
import com.jilu.attendance.domain.other
import com.jilu.attendance.reminder.ReminderScheduler
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    settings: AppSettings,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val overrides by container.dao.observeOverrides().collectAsState(initial = emptyList())
    val recordCount by container.dao.observeCount().collectAsState(initial = 0)
    var editingSlot by remember { mutableStateOf<String?>(null) }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }

    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "1.0"
    }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching { container.backup.exportTo(uri, context) }
                    .onSuccess { toast("已导出 $it 条记录") }
                    .onFailure { toast("导出失败：${it.message ?: "未知错误"}") }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) pendingImport = uri
    }

    // 用 OpenDocument 而不是 PickVisualMedia：系统文档选择器给的是带读权限的 Uri，
    // 在国产 ROM 的相册 provider 上兼容性最好。
    val pickBackgroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching { container.backgroundStore.saveFromUri(uri) }
                    .onSuccess { name ->
                        container.settingsRepo.setBackgroundImage(name, System.currentTimeMillis())
                        container.settingsRepo.setBackgroundMode(BackgroundMode.IMAGE)
                        toast("背景图已更新")
                    }
                    .onFailure {
                        toast("设置背景失败：${it.message ?: it.javaClass.simpleName}")
                    }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("返回") }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            SectionTitle("工作日")
            Text(
                "选中的星期算工作日，其余算休息日。改动只影响还没记录的日期，已记录的颜色不变。",
                fontSize = 12.sp,
                color = MutedText
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..7).forEach { iso ->
                    WeekdayToggle(
                        label = weekLabels[iso - 1],
                        selected = iso in settings.workdayOfWeek,
                        modifier = Modifier.weight(1f)
                    ) {
                        val next = if (iso in settings.workdayOfWeek) {
                            settings.workdayOfWeek - iso
                        } else {
                            settings.workdayOfWeek + iso
                        }
                        if (next.isEmpty()) {
                            toast("至少要留一天工作日")
                        } else {
                            scope.launch { container.settingsRepo.setWorkdays(next) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "当前：" + settings.workdayOfWeek.sorted()
                    .joinToString("、") { "周" + weekLabels[it - 1] },
                fontSize = 12.sp,
                color = MutedText
            )

            SectionTitle("提醒")
            Text(
                "只在判定为工作日、且当天还没有记录时才提醒。休息日不提醒。",
                fontSize = 12.sp,
                color = MutedText
            )
            ReminderRow(
                title = "上班提醒",
                enabled = settings.morningEnabled,
                time = settings.morningTime,
                onToggle = { enabled -> scope.launch { container.settingsRepo.setMorningEnabled(enabled) } },
                onEditTime = { editingSlot = ReminderScheduler.SLOT_MORNING }
            )
            ReminderRow(
                title = "下班提醒",
                enabled = settings.eveningEnabled,
                time = settings.eveningTime,
                onToggle = { enabled -> scope.launch { container.settingsRepo.setEveningEnabled(enabled) } },
                onEditTime = { editingSlot = ReminderScheduler.SLOT_EVENING }
            )

            SectionTitle("背景")
            Text(
                "选一套预设配色，或从相册挑一张图当背景。图片会自动压缩并加半透明遮罩，保证日期读得清。",
                fontSize = 12.sp,
                color = MutedText
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModePill("默认", settings.backgroundMode == BackgroundMode.DEFAULT) {
                    scope.launch { container.settingsRepo.setBackgroundMode(BackgroundMode.DEFAULT) }
                }
                ModePill("纯色", settings.backgroundMode == BackgroundMode.COLOR) {
                    scope.launch { container.settingsRepo.setBackgroundMode(BackgroundMode.COLOR) }
                }
                ModePill("图片", settings.backgroundMode == BackgroundMode.IMAGE) {
                    if (settings.backgroundImageName == null) {
                        pickBackgroundLauncher.launch(arrayOf("image/*"))
                    } else {
                        scope.launch { container.settingsRepo.setBackgroundMode(BackgroundMode.IMAGE) }
                    }
                }
            }

            if (settings.backgroundMode == BackgroundMode.COLOR) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    backgroundPresets.forEach { preset ->
                        val selected = preset.id == settings.backgroundColorId
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Brush.verticalGradient(preset.colors))
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primary else UnmarkedBorder,
                                    shape = CircleShape
                                )
                                .clickable {
                                    scope.launch {
                                        container.settingsRepo.setBackgroundColorId(preset.id)
                                        container.settingsRepo.setBackgroundMode(BackgroundMode.COLOR)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) Text("✓", fontSize = 14.sp, color = Color(0xFF1B5E20))
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "当前配色：" + presetOf(settings.backgroundColorId).label,
                    fontSize = 12.sp,
                    color = MutedText
                )
            }

            if (settings.backgroundMode == BackgroundMode.IMAGE) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModePill("完整显示", settings.backgroundFit == BackgroundFit.FIT) {
                        scope.launch { container.settingsRepo.setBackgroundFit(BackgroundFit.FIT) }
                    }
                    ModePill("铺满裁切", settings.backgroundFit == BackgroundFit.COVER) {
                        scope.launch { container.settingsRepo.setBackgroundFit(BackgroundFit.COVER) }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "完整显示：整张照片都在，比例不符的留白用同一张图的模糊版填满；铺满裁切：等比放大铺满屏幕，会裁掉照片边角。",
                    fontSize = 12.sp,
                    color = MutedText
                )

                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { pickBackgroundLauncher.launch(arrayOf("image/*")) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (settings.backgroundImageName != null) "更换图片" else "从相册选图")
                    }
                    if (settings.backgroundImageName != null) {
                        OutlinedButton(
                            onClick = {
                                val name = settings.backgroundImageName
                                scope.launch {
                                    container.settingsRepo.clearBackgroundImage()
                                    if (name != null) container.backgroundStore.delete(name)
                                    toast("已删除背景图")
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("删除图片") }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("单日覆盖", modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    val now = Calendar.getInstance()
                    DatePickerDialog(
                        context,
                        { _, year, monthIndex, dayOfMonth ->
                            val date = LocalDate.of(year, monthIndex + 1, dayOfMonth)
                            val current = DayTypeResolver.resolve(date, settings.workdayOfWeek, emptyMap())
                            scope.launch {
                                container.dao.upsertOverride(
                                    DayOverride(date.toString(), current.other().value, null)
                                )
                            }
                        },
                        now.get(Calendar.YEAR),
                        now.get(Calendar.MONTH),
                        now.get(Calendar.DAY_OF_MONTH)
                    ).show()
                }) { Text("新增") }
            }
            Text(
                "把法定节假日标成休息日、补班日标成工作日。优先级高于周规则。",
                fontSize = 12.sp,
                color = MutedText
            )
            if (overrides.isEmpty()) {
                Text("还没有单日覆盖。", fontSize = 12.sp, color = MutedText, modifier = Modifier.padding(vertical = 8.dp))
            } else {
                overrides.sortedBy { it.date }.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(item.date, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Text(
                            DayType.fromValue(item.dayType)?.label ?: item.dayType,
                            fontSize = 12.sp,
                            color = MutedText,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        TextButton(onClick = {
                            scope.launch { container.dao.deleteOverride(item.date) }
                        }) { Text("删除") }
                    }
                }
            }

            SectionTitle("备份")
            Text(
                "备份文件包含全部标记、单日覆盖和设置。导入会整体替换本地数据。",
                fontSize = 12.sp,
                color = MutedText
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { exportLauncher.launch(container.backup.defaultFileName()) },
                    modifier = Modifier.weight(1f)
                ) { Text("导出备份") }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
                    modifier = Modifier.weight(1f)
                ) { Text("导入备份") }
            }

            SectionTitle("关于")
            Text("本地记录 $recordCount 条", fontSize = 13.sp)
            Text(
                "数据只存本机，App 不申请网络与定位权限。版本 $versionName",
                fontSize = 12.sp,
                color = MutedText
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
        }
    }

    val slot = editingSlot
    if (slot != null) {
        val initial = if (slot == ReminderScheduler.SLOT_MORNING) settings.morningTime else settings.eveningTime

        var text by remember(slot) { mutableStateOf(initial) }
        var error by remember(slot) { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { editingSlot = null },
            title = { Text(if (slot == ReminderScheduler.SLOT_MORNING) "上班提醒时间" else "下班提醒时间") },
            text = {
                Column {
                    OutlinedTextField(
                        value = text,
                        onValueChange = {
                            text = it
                            error = null
                        },
                        singleLine = true,
                        label = { Text("HH:mm") }
                    )
                    val message = error
                    if (message != null) {
                        Text(message, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val parsed = ReminderScheduler.parseTime(text)
                    if (parsed == null) {
                        error = "格式不对，请填 0-23 时、0-59 分，例如 08:55"
                    } else {
                        val normalized = String.format(Locale.CHINA, "%02d:%02d", parsed.first, parsed.second)
                        scope.launch {
                            if (slot == ReminderScheduler.SLOT_MORNING) {
                                container.settingsRepo.setMorningTime(normalized)
                            } else {
                                container.settingsRepo.setEveningTime(normalized)
                            }
                        }
                        editingSlot = null
                    }
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { editingSlot = null }) { Text("取消") }
            }
        )
    }

    val importUri = pendingImport
    if (importUri != null) {
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("覆盖本地数据？") },
            text = {
                Text("导入会删除现有的 $recordCount 条记录，并整体替换为备份文件里的内容。")
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingImport = null
                    scope.launch {
                        runCatching { container.backup.importFrom(importUri, context) }
                            .onSuccess { toast("已恢复 $it 条记录") }
                            .onFailure { toast("导入失败：${it.message ?: "未知错误"}") }
                    }
                }) { Text("导入") }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun WeekdayToggle(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val background = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    val textColor = if (selected) Color.White else Color(0xFF37474F)
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(background)
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary else UnmarkedBorder,
                CircleShape
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = textColor, fontSize = 13.sp)
    }
}

@Composable
private fun ReminderRow(
    title: String,
    enabled: Boolean,
    time: String,
    onToggle: (Boolean) -> Unit,
    onEditTime: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp)
            TextButton(onClick = onEditTime, contentPadding = PaddingValues(0.dp)) {
                Text(time, fontSize = 13.sp)
            }
        }
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
}

@Composable
private fun ModePill(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val background = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    val textColor = if (selected) Color.White else Color(0xFF37474F)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(background)
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary else UnmarkedBorder,
                shape
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Text(label, color = textColor, fontSize = 13.sp)
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(top = 20.dp, bottom = 4.dp)
    )
}
