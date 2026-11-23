package com.jilu.attendance.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jilu.attendance.model.BackgroundFit
import com.jilu.attendance.model.BackgroundMode
import java.io.File

/** 设置浮层：工作日规则、背景、备份与数据位置。 */
@Composable
fun SettingsOverlay(state: AppState) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.28f))
            .clickable { state.settingsOpen = false },
        contentAlignment = Alignment.Center
    ) {
        Card(
            Modifier
                .width(560.dp)
                .height(620.dp)
                .clickable(enabled = false) { },
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFDFDFC))
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(26.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("设置", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { state.settingsOpen = false }) { Text("关闭") }
                }

                Spacer(Modifier.height(10.dp))
                SectionTitle("工作日（周规则）")
                Text("未单独设置的日子按这里判定；单日覆盖优先。", fontSize = 12.sp, color = MutedText)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    weekLabels.forEachIndexed { index, label ->
                        val day = index + 1
                        val active = day in state.settings.workdayOfWeek
                        val bg = if (active) MaterialTheme.colorScheme.primary else Color(0xFFEDEFEE)
                        val fg = if (active) Color.White else Color(0xFF455A64)
                        Box(
                            Modifier
                                .clip(CircleShape)
                                .background(bg)
                                .clickable {
                                    val next = state.settings.workdayOfWeek.toMutableSet()
                                    if (active) next.remove(day) else next.add(day)
                                    state.setWorkdays(next)
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) { Text("周$label", fontSize = 12.sp, color = fg) }
                    }
                }

                Spacer(Modifier.height(18.dp))
                HorizontalDivider()
                Spacer(Modifier.height(14.dp))

                SectionTitle("背景")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModeChip("默认", state.settings.backgroundMode == BackgroundMode.DEFAULT) {
                        state.setBackground(BackgroundMode.DEFAULT)
                    }
                    ModeChip("图片", state.settings.backgroundMode == BackgroundMode.IMAGE) {
                        val file = Dialogs.chooseImage()
                        if (file != null) state.importBackground(file.toPath())
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("预设配色", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    backgroundPresets.forEach { preset ->
                        val active = state.settings.backgroundMode == BackgroundMode.COLOR &&
                            state.settings.backgroundColorId == preset.id
                        Column(
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { state.setBackgroundColor(preset.id) }
                                .border(
                                    width = if (active) 2.dp else 1.dp,
                                    color = if (active) MaterialTheme.colorScheme.primary else Color(0xFFE0E0E0),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .padding(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                Modifier
                                    .size(56.dp, 32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Brush.verticalGradient(preset.colors))
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(preset.label, fontSize = 11.sp, color = MutedText)
                        }
                    }
                }

                if (state.settings.backgroundMode == BackgroundMode.IMAGE) {
                    Spacer(Modifier.height(10.dp))
                    Text("图片适配", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ModeChip("完整显示", state.settings.backgroundFit == BackgroundFit.FIT) {
                            state.setBackgroundFit(BackgroundFit.FIT)
                        }
                        ModeChip("铺满裁切", state.settings.backgroundFit == BackgroundFit.COVER) {
                            state.setBackgroundFit(BackgroundFit.COVER)
                        }
                        TextButton(onClick = { state.removeBackground() }) { Text("移除图片") }
                    }
                    Text(
                        "完整显示：整张图都在，留白用同一张图的模糊版铺底；铺满裁切：等比放大铺满，会裁掉边角。",
                        fontSize = 12.sp,
                        color = MutedText
                    )
                }

                Spacer(Modifier.height(18.dp))
                HorizontalDivider()
                Spacer(Modifier.height(14.dp))

                SectionTitle("数据与备份")
                Text(AppPaths.displayPath(), fontSize = 11.sp, color = MutedText)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val file = Dialogs.saveBackup()
                        if (file != null) state.exportTo(file.toPath())
                    }) { Text("导出 JSON") }
                    OutlinedButton(onClick = {
                        val file = Dialogs.openBackup()
                        if (file != null) state.importFrom(file.toPath())
                    }) { Text("导入 JSON") }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "导入为整体替换。备份格式与安卓端一致，两个平台可互相导入。",
                    fontSize = 12.sp,
                    color = MutedText
                )

                Spacer(Modifier.height(18.dp))
                HorizontalDivider()
                Spacer(Modifier.height(14.dp))
                SectionTitle("说明")
                Text(
                    "桌面版不发提醒；背景图只存本机数据目录，不进备份。",
                    fontSize = 12.sp,
                    color = MutedText
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun ModeChip(label: String, active: Boolean, onClick: () -> Unit) {
    val bg = if (active) MaterialTheme.colorScheme.primary else Color.White
    val fg = if (active) Color.White else Color(0xFF37474F)
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .border(1.dp, if (active) MaterialTheme.colorScheme.primary else Color(0xFFDDDDDD), RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) { Text(label, fontSize = 13.sp, color = fg) }
}

/** 原生文件对话框。Compose Desktop 运行在 AWT 事件线程上，可直接同步调用。 */
object Dialogs {

    fun chooseImage(): File? {
        val chooser = javax.swing.JFileChooser().apply {
            dialogTitle = "选择背景图片"
            fileSelectionMode = javax.swing.JFileChooser.FILES_ONLY
            fileFilter = javax.swing.filechooser.FileNameExtensionFilter(
                "图片 (jpg, jpeg, png, webp, bmp, gif)",
                "jpg", "jpeg", "png", "webp", "bmp", "gif"
            )
        }
        return if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
    }

    fun saveBackup(): File? {
        val stamp = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")
            .format(java.time.LocalDateTime.now())
        val chooser = javax.swing.JFileChooser().apply {
            dialogTitle = "导出备份"
            selectedFile = File("考勤记录备份-$stamp.json")
            fileFilter = javax.swing.filechooser.FileNameExtensionFilter("JSON 备份", "json")
        }
        if (chooser.showSaveDialog(null) != javax.swing.JFileChooser.APPROVE_OPTION) return null
        val picked = chooser.selectedFile
        return if (picked.extension.equals("json", ignoreCase = true)) picked
        else File(picked.absolutePath + ".json")
    }

    fun openBackup(): File? {
        val chooser = javax.swing.JFileChooser().apply {
            dialogTitle = "导入备份"
            fileFilter = javax.swing.filechooser.FileNameExtensionFilter("JSON 备份", "json")
        }
        return if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
    }
}
