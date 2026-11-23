package com.jilu.attendance.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jilu.attendance.domain.DayType
import com.jilu.attendance.domain.DayTypeResolver
import com.jilu.attendance.domain.Outcome
import com.jilu.attendance.domain.outcomes
import com.jilu.attendance.model.AttendanceRecord
import com.jilu.attendance.model.BackupCodec
import com.jilu.attendance.model.BackgroundFit
import com.jilu.attendance.model.BackgroundMode
import com.jilu.attendance.model.DayOverride
import com.jilu.attendance.model.JiluSettings
import com.jilu.attendance.model.JiluSnapshot
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import java.time.YearMonth

/** 桌面端数据目录：Windows 用 %APPDATA%\jilu_app，macOS 用 ~/Library/Application Support/jilu_app。 */
object AppPaths {

    private val dir: Path by lazy {
        val os = System.getProperty("os.name").lowercase()
        val base = if (os.contains("mac")) {
            Paths.get(System.getProperty("user.home"), "Library", "Application Support")
        } else {
            Paths.get(System.getenv("APPDATA") ?: System.getProperty("user.home"))
        }
        base.resolve("jilu_app").also { Files.createDirectories(it) }
    }

    val dataFile: Path get() = dir.resolve("data.json")
    val backgroundFile: Path get() = dir.resolve("background.img")

    fun displayPath(): String = dataFile.toString()
}

/**
 * 桌面端的全部状态：设置 + 记录 + 覆盖。
 * 每次变更立即落盘为与安卓同格式的 JSON（data.json）。
 */
class AppState {

    var settings by mutableStateOf(JiluSettings())
        private set

    var records by mutableStateOf<List<AttendanceRecord>>(emptyList())
        private set

    var overrides by mutableStateOf<List<DayOverride>>(emptyList())
        private set

    var month by mutableStateOf(YearMonth.now())
    var selected by mutableStateOf(LocalDate.now())
    var settingsOpen by mutableStateOf(false)
    var status by mutableStateOf("")

    /** 当前背景图字节；null 表示没有图片 */
    var backgroundBytes by mutableStateOf<ByteArray?>(null)
        private set

    val today: LocalDate get() = LocalDate.now()

    val overrideMap: Map<String, String> get() = overrides.associate { it.date to it.dayType }
    val outcomeMap: Map<String, String> get() = records.associate { it.date to it.outcome }

    fun dayTypeOf(date: LocalDate): DayType =
        DayTypeResolver.resolve(date, settings.workdayOfWeek, overrideMap)

    fun recordOf(date: LocalDate): AttendanceRecord? =
        records.firstOrNull { it.date == date.toString() }

    fun load() {
        val file = AppPaths.dataFile
        if (Files.exists(file)) {
            runCatching {
                val snapshot = BackupCodec.decode(Files.readString(file))
                settings = snapshot.settings
                records = snapshot.records
                overrides = snapshot.overrides
            }.onFailure { status = "读取本地数据失败：${it.message}" }
        }
        reloadBackground()
    }

    /** 重新读取背景图；没有图片或模式不是 IMAGE 时为空。 */
    private fun reloadBackground() {
        val file = AppPaths.backgroundFile
        backgroundBytes = if (settings.backgroundMode == BackgroundMode.IMAGE && Files.exists(file)) {
            runCatching { Files.readAllBytes(file) }.getOrNull()
        } else {
            null
        }
    }

    private fun snapshot(): JiluSnapshot =
        JiluSnapshot(records = records, overrides = overrides, settings = settings, exportedAt = System.currentTimeMillis())

    private fun persist() {
        runCatching {
            val tmp = AppPaths.dataFile.resolveSibling("data.json.tmp")
            Files.writeString(tmp, BackupCodec.encode(snapshot()))
            Files.move(tmp, AppPaths.dataFile, StandardCopyOption.REPLACE_EXISTING)
        }.onFailure { status = "保存失败：${it.message}" }
    }

    // ---- 标记 ----

    fun mark(date: LocalDate, outcome: Outcome) {
        if (date.isAfter(today)) {
            status = "未来日期不能打考勤标记"
            return
        }
        val dayType = dayTypeOf(date)
        val existing = recordOf(date)
        if (existing?.outcome == outcome.value) {
            records = records.filterNot { it.date == date.toString() }
        } else {
            records = records.filterNot { it.date == date.toString() } + AttendanceRecord(
                date = date.toString(),
                dayType = dayType.value,
                outcome = outcome.value,
                markedAt = System.currentTimeMillis()
            )
        }
        persist()
    }

    fun clearMark(date: LocalDate) {
        records = records.filterNot { it.date == date.toString() }
        persist()
    }

    // ---- 日型覆盖 ----

    fun setOverride(date: LocalDate, dayType: DayType) {
        overrides = overrides.filterNot { it.date == date.toString() } +
            DayOverride(date.toString(), dayType.value, null)
        // 日型变了，旧标记与新日型不匹配，一并清掉
        val existing = recordOf(date)
        if (existing != null && existing.dayType != dayType.value) {
            records = records.filterNot { it.date == date.toString() }
        }
        persist()
    }

    fun clearOverride(date: LocalDate) {
        overrides = overrides.filterNot { it.date == date.toString() }
        persist()
    }

    // ---- 设置 ----

    fun setWorkdays(workdays: Set<Int>) {
        if (workdays.isEmpty()) {
            status = "至少要选一个工作日"
            return
        }
        settings = settings.copy(workdayOfWeek = workdays)
        persist()
    }

    fun setBackground(mode: BackgroundMode) {
        settings = settings.copy(backgroundMode = mode)
        reloadBackground()
        persist()
    }

    fun setBackgroundColor(colorId: String) {
        settings = settings.copy(backgroundMode = BackgroundMode.COLOR, backgroundColorId = colorId)
        reloadBackground()
        persist()
    }

    fun setBackgroundFit(fit: BackgroundFit) {
        settings = settings.copy(backgroundFit = fit)
        persist()
    }

    /** 把外部图片拷进数据目录，切成 IMAGE 模式。 */
    fun importBackground(source: Path): Boolean {
        return runCatching {
            Files.copy(source, AppPaths.backgroundFile, StandardCopyOption.REPLACE_EXISTING)
            settings = settings.copy(
                backgroundMode = BackgroundMode.IMAGE,
                backgroundImageVersion = System.currentTimeMillis()
            )
            reloadBackground()
            persist()
            true
        }.getOrElse {
            status = "读取图片失败：${it.message}"
            false
        }
    }

    fun removeBackground() {
        runCatching { Files.deleteIfExists(AppPaths.backgroundFile) }
        settings = settings.copy(backgroundMode = BackgroundMode.DEFAULT, backgroundImageVersion = 0L)
        reloadBackground()
        persist()
    }

    // ---- 备份 ----

    fun exportTo(target: Path): Boolean = runCatching {
        Files.writeString(target, BackupCodec.encode(snapshot()))
        status = "已导出 ${records.size} 条记录"
        true
    }.getOrElse {
        status = "导出失败：${it.message}"
        false
    }

    fun importFrom(source: Path): Boolean = runCatching {
        val snapshot = BackupCodec.decode(Files.readString(source))
        records = snapshot.records
        overrides = snapshot.overrides
        // 备份里的背景图只是二进制文件，不进 JSON：若原本是图片模式则退回默认，避免空白背景
        settings = snapshot.settings.copy(
            backgroundMode = if (snapshot.settings.backgroundMode == BackgroundMode.IMAGE) {
                BackgroundMode.DEFAULT
            } else {
                snapshot.settings.backgroundMode
            }
        )
        reloadBackground()
        persist()
        status = "已导入 ${records.size} 条记录（整体替换）"
        true
    }.getOrElse {
        status = "导入失败：${it.message}"
        false
    }

    fun outcomesFor(date: LocalDate): List<Outcome> = dayTypeOf(date).outcomes()
}
