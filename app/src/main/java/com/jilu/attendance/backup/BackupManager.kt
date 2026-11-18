package com.jilu.attendance.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.jilu.attendance.data.AttendanceRecord
import com.jilu.attendance.data.BackgroundFit
import com.jilu.attendance.data.BackgroundMode
import com.jilu.attendance.data.DayOverride
import com.jilu.attendance.data.JiluDatabase
import com.jilu.attendance.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupManager(
    private val db: JiluDatabase,
    private val settingsRepo: SettingsRepository
) {

    fun defaultFileName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.CHINA).format(Date())
        return "考勤记录备份-$stamp.json"
    }

    /** 导出整库快照为 JSON，返回导出的记录条数。 */
    suspend fun exportTo(uri: Uri, context: Context): Int = withContext(Dispatchers.IO) {
        val dao = db.dao()
        val records = dao.getAll()
        val overrides = dao.getAllOverrides()
        val settings = settingsRepo.current()

        val root = JSONObject().apply {
            put("formatVersion", 1)
            put("exportedAt", System.currentTimeMillis())
        }

        val recordArray = JSONArray()
        records.forEach { record ->
            recordArray.put(
                JSONObject()
                    .put("date", record.date)
                    .put("dayType", record.dayType)
                    .put("outcome", record.outcome)
                    .put("markedAt", record.markedAt)
            )
        }
        root.put("records", recordArray)

        val overrideArray = JSONArray()
        overrides.forEach { item ->
            overrideArray.put(
                JSONObject()
                    .put("date", item.date)
                    .put("dayType", item.dayType)
                    .put("reason", item.reason ?: "")
            )
        }
        root.put("overrides", overrideArray)

        root.put(
            "settings", JSONObject()
                .put("workdayOfWeek", settings.workdayOfWeek.sorted().joinToString(","))
                .put("morningEnabled", settings.morningEnabled)
                .put("morningTime", settings.morningTime)
                .put("eveningEnabled", settings.eveningEnabled)
                .put("eveningTime", settings.eveningTime)
                .put("backgroundMode", settings.backgroundMode.value)
                .put("backgroundColorId", settings.backgroundColorId)
                .put("backgroundFit", settings.backgroundFit.value)
        )

        val payload = root.toString(2).toByteArray(Charsets.UTF_8)
        val stream = context.contentResolver.openOutputStream(uri)
            ?: throw IOException("无法写入所选文件")
        stream.use { it.write(payload) }

        records.size
    }

    /** 从 JSON 恢复（整体替换本地数据），返回恢复的记录条数。 */
    suspend fun importFrom(uri: Uri, context: Context): Int = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { input ->
            input.readBytes().toString(Charsets.UTF_8)
        } ?: throw IOException("无法读取所选文件")

        val root = runCatching { JSONObject(text) }.getOrElse {
            throw IOException("不是有效的备份文件")
        }

        val version = root.optInt("formatVersion", 0)
        if (version != 1) throw IOException("备份格式版本不支持：$version")

        val recordArray = root.optJSONArray("records") ?: throw IOException("备份文件缺少 records")
        val records = buildList {
            for (index in 0 until recordArray.length()) {
                val item = recordArray.getJSONObject(index)
                add(
                    AttendanceRecord(
                        date = item.getString("date"),
                        dayType = item.getString("dayType"),
                        outcome = item.getString("outcome"),
                        markedAt = item.optLong("markedAt", 0L)
                    )
                )
            }
        }

        val overrideArray = root.optJSONArray("overrides") ?: JSONArray()
        val overrides = buildList {
            for (index in 0 until overrideArray.length()) {
                val item = overrideArray.getJSONObject(index)
                add(
                    DayOverride(
                        date = item.getString("date"),
                        dayType = item.getString("dayType"),
                        reason = item.optString("reason").takeIf { it.isNotBlank() }
                    )
                )
            }
        }

        val dao = db.dao()
        db.withTransaction {
            dao.clearAll()
            dao.clearOverrides()
            dao.insertAll(records)
            dao.insertOverrides(overrides)
        }

        root.optJSONObject("settings")?.let { settings ->
            val workdays = settings.optString("workdayOfWeek", "1,2,3,4,5")
                .split(",")
                .mapNotNull { it.trim().toIntOrNull() }
                .filter { it in 1..7 }
                .toSet()
            settingsRepo.setWorkdays(workdays)
            settingsRepo.setMorningEnabled(settings.optBoolean("morningEnabled", true))
            settingsRepo.setMorningTime(settings.optString("morningTime", "08:55"))
            settingsRepo.setEveningEnabled(settings.optBoolean("eveningEnabled", true))
            settingsRepo.setEveningTime(settings.optString("eveningTime", "18:05"))
            // 背景图是二进制文件，不进 JSON；只恢复配色方案与背景模式
            if (settings.has("backgroundColorId")) {
                settingsRepo.setBackgroundColorId(settings.optString("backgroundColorId", "mint"))
            }
            if (settings.has("backgroundFit")) {
                settingsRepo.setBackgroundFit(
                    BackgroundFit.fromValue(settings.optString("backgroundFit", "fit"))
                )
            }
            if (settings.has("backgroundMode")) {
                val mode = BackgroundMode.fromValue(settings.optString("backgroundMode", "default"))
                // 备份里没有图片时不要把模式恢复成 IMAGE，否则会显示空白背景
                if (mode != BackgroundMode.IMAGE) {
                    settingsRepo.setBackgroundMode(mode)
                }
            }
        }

        records.size
    }
}
