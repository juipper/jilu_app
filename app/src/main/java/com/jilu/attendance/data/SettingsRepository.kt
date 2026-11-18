package com.jilu.attendance.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** 背景页的三种来源：默认、预设配色、自定义图片。 */
enum class BackgroundMode(val value: String) {
    DEFAULT("default"),
    COLOR("color"),
    IMAGE("image");

    companion object {
        fun fromValue(value: String?): BackgroundMode =
            entries.firstOrNull { it.value == value } ?: DEFAULT
    }
}

/** 自定义图片在屏幕上的适配方式。 */
enum class BackgroundFit(val value: String) {
    /** 铺满屏幕、裁掉超出部分（不会留白，但会裁掉照片边角） */
    COVER("cover"),

    /** 整张照片完整显示，空白区域用同一张图的模糊版填满 */
    FIT("fit");

    companion object {
        fun fromValue(value: String?): BackgroundFit =
            entries.firstOrNull { it.value == value } ?: FIT
    }
}

data class AppSettings(
    val workdayOfWeek: Set<Int> = setOf(1, 2, 3, 4, 5),
    val morningEnabled: Boolean = true,
    val morningTime: String = "08:55",
    val eveningEnabled: Boolean = true,
    val eveningTime: String = "18:05",
    val backgroundMode: BackgroundMode = BackgroundMode.DEFAULT,
    val backgroundColorId: String = "mint",
    val backgroundImageName: String? = null,
    val backgroundImageUpdatedAt: Long = 0L,
    val backgroundFit: BackgroundFit = BackgroundFit.FIT
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val WORKDAYS = stringPreferencesKey("workday_of_week")
        val MORNING_ENABLED = booleanPreferencesKey("reminder_morning_enabled")
        val MORNING_TIME = stringPreferencesKey("reminder_morning_time")
        val EVENING_ENABLED = booleanPreferencesKey("reminder_evening_enabled")
        val EVENING_TIME = stringPreferencesKey("reminder_evening_time")
        val BACKGROUND_MODE = stringPreferencesKey("background_mode")
        val BACKGROUND_COLOR_ID = stringPreferencesKey("background_color_id")
        val BACKGROUND_IMAGE_NAME = stringPreferencesKey("background_image_name")
        val BACKGROUND_IMAGE_UPDATED_AT = longPreferencesKey("background_image_updated_at")
        val BACKGROUND_FIT = stringPreferencesKey("background_fit")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        val rawWorkdays = prefs[Keys.WORKDAYS] ?: "1,2,3,4,5"
        AppSettings(
            workdayOfWeek = rawWorkdays.split(",")
                .mapNotNull { it.trim().toIntOrNull() }
                .filter { it in 1..7 }
                .toSet(),
            morningEnabled = prefs[Keys.MORNING_ENABLED] ?: true,
            morningTime = prefs[Keys.MORNING_TIME] ?: "08:55",
            eveningEnabled = prefs[Keys.EVENING_ENABLED] ?: true,
            eveningTime = prefs[Keys.EVENING_TIME] ?: "18:05",
            backgroundMode = BackgroundMode.fromValue(prefs[Keys.BACKGROUND_MODE]),
            backgroundColorId = prefs[Keys.BACKGROUND_COLOR_ID] ?: "mint",
            backgroundImageName = prefs[Keys.BACKGROUND_IMAGE_NAME]?.takeIf { it.isNotBlank() },
            backgroundImageUpdatedAt = prefs[Keys.BACKGROUND_IMAGE_UPDATED_AT] ?: 0L,
            backgroundFit = BackgroundFit.fromValue(prefs[Keys.BACKGROUND_FIT])
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setWorkdays(workdays: Set<Int>) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.WORKDAYS] = workdays.sorted().joinToString(",")
        }
    }

    suspend fun setMorningEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[Keys.MORNING_ENABLED] = enabled }
    }

    suspend fun setMorningTime(time: String) {
        context.settingsDataStore.edit { prefs -> prefs[Keys.MORNING_TIME] = time }
    }

    suspend fun setEveningEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[Keys.EVENING_ENABLED] = enabled }
    }

    suspend fun setEveningTime(time: String) {
        context.settingsDataStore.edit { prefs -> prefs[Keys.EVENING_TIME] = time }
    }

    suspend fun setBackgroundMode(mode: BackgroundMode) {
        context.settingsDataStore.edit { prefs -> prefs[Keys.BACKGROUND_MODE] = mode.value }
    }

    suspend fun setBackgroundColorId(colorId: String) {
        context.settingsDataStore.edit { prefs -> prefs[Keys.BACKGROUND_COLOR_ID] = colorId }
    }

    suspend fun setBackgroundFit(fit: BackgroundFit) {
        context.settingsDataStore.edit { prefs -> prefs[Keys.BACKGROUND_FIT] = fit.value }
    }

    suspend fun setBackgroundImage(name: String, updatedAt: Long) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.BACKGROUND_IMAGE_NAME] = name
            prefs[Keys.BACKGROUND_IMAGE_UPDATED_AT] = updatedAt
        }
    }

    suspend fun clearBackgroundImage() {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.BACKGROUND_IMAGE_NAME] = ""
            prefs[Keys.BACKGROUND_IMAGE_UPDATED_AT] = System.currentTimeMillis()
            if (prefs[Keys.BACKGROUND_MODE] == BackgroundMode.IMAGE.value) {
                prefs[Keys.BACKGROUND_MODE] = BackgroundMode.DEFAULT.value
            }
        }
    }
}
