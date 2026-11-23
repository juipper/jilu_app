package com.jilu.attendance.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * 与 Android 端完全一致的备份格式（formatVersion = 1）。
 *
 * 结构：formatVersion / exportedAt / records[] / overrides[] / settings{}
 * 桌面端导入安卓的备份、安卓导入桌面端的备份，双向可用。
 */
object BackupCodec {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun encode(snapshot: JiluSnapshot): String {
        val root = buildJsonObject {
            put("formatVersion", 1)
            put("exportedAt", snapshot.exportedAt)
            putJsonArray("records") {
                snapshot.records.forEach { record ->
                    add(
                        buildJsonObject {
                            put("date", record.date)
                            put("dayType", record.dayType)
                            put("outcome", record.outcome)
                            put("markedAt", record.markedAt)
                        }
                    )
                }
            }
            putJsonArray("overrides") {
                snapshot.overrides.forEach { item ->
                    add(
                        buildJsonObject {
                            put("date", item.date)
                            put("dayType", item.dayType)
                            put("reason", item.reason ?: "")
                        }
                    )
                }
            }
            putJsonObject("settings") {
                val s = snapshot.settings
                put("workdayOfWeek", s.workdayOfWeek.sorted().joinToString(","))
                put("morningEnabled", s.morningEnabled)
                put("morningTime", s.morningTime)
                put("eveningEnabled", s.eveningEnabled)
                put("eveningTime", s.eveningTime)
                put("backgroundMode", s.backgroundMode.value)
                put("backgroundColorId", s.backgroundColorId)
                put("backgroundFit", s.backgroundFit.value)
            }
        }
        return json.encodeToString(JsonElement.serializer(), root)
    }

    fun decode(text: String): JiluSnapshot {
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrElse {
            throw IllegalArgumentException("不是有效的备份文件")
        }

        val version = root["formatVersion"]?.jsonPrimitive?.intOrNull ?: 0
        if (version != 1) throw IllegalArgumentException("备份格式版本不支持：$version")

        val recordArray = root["records"]?.jsonArray
            ?: throw IllegalArgumentException("备份文件缺少 records")
        val records = recordArray.map { element ->
            val item = element.jsonObject
            AttendanceRecord(
                date = item.string("date"),
                dayType = item.string("dayType"),
                outcome = item.string("outcome"),
                markedAt = item["markedAt"]?.jsonPrimitive?.longOrNull ?: 0L
            )
        }

        val overrideArray = (root["overrides"] as? JsonArray) ?: JsonArray(emptyList())
        val overrides = overrideArray.map { element ->
            val item = element.jsonObject
            DayOverride(
                date = item.string("date"),
                dayType = item.string("dayType"),
                reason = item.content("reason").takeIf { it.isNotBlank() }
            )
        }

        val settingsJson = root["settings"]?.jsonObject
        val workdays = settingsJson?.content("workdayOfWeek")
            ?.split(",")
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.filter { it in 1..7 }
            ?.toSet()
            ?.takeIf { it.isNotEmpty() }
            ?: setOf(1, 2, 3, 4, 5)

        val settings = JiluSettings(
            workdayOfWeek = workdays,
            morningEnabled = settingsJson?.get("morningEnabled")?.jsonPrimitive?.booleanOrNull ?: true,
            morningTime = (settingsJson?.content("morningTime") ?: "").takeIf { it.isNotBlank() } ?: "08:55",
            eveningEnabled = settingsJson?.get("eveningEnabled")?.jsonPrimitive?.booleanOrNull ?: true,
            eveningTime = (settingsJson?.content("eveningTime") ?: "").takeIf { it.isNotBlank() } ?: "18:05",
            backgroundMode = BackgroundMode.fromValue(settingsJson?.content("backgroundMode")),
            backgroundColorId = (settingsJson?.content("backgroundColorId") ?: "").takeIf { it.isNotBlank() } ?: "mint",
            backgroundFit = BackgroundFit.fromValue(settingsJson?.content("backgroundFit"))
        )

        val exportedAt = root["exportedAt"]?.jsonPrimitive?.longOrNull ?: 0L
        return JiluSnapshot(records = records, overrides = overrides, settings = settings, exportedAt = exportedAt)
    }

    /** 取字符串字段，缺失抛错（records / overrides 的必填字段）。 */
    private fun JsonObject.string(key: String): String {
        val value = content(key)
        if (value.isEmpty()) throw IllegalArgumentException("备份文件缺少字段：$key")
        return value
    }

    /** 取字符串字段，缺失返回空串（可选字段与 settings）。 */
    private fun JsonObject?.content(key: String): String {
        val primitive = this?.get(key) as? JsonPrimitive ?: return ""
        return primitive.content
    }
}
