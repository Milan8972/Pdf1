package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.AccentPreset
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "paperpilot_prefs")

data class UserSettings(
    val onboardingComplete: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accentPreset: AccentPreset = AccentPreset.CORAL,
    val askMobileData: Boolean = true,
    val defaultSaveFolder: String = "Documents/paperpilot",
    val ocrLanguage: String = "English",
    val defaultImageQuality: Int = 85,
    val keepOriginalFiles: Boolean = true,
    val profileName: String = "Local Workspace",
    val profileEmail: String = "",
    val isPro: Boolean = false,
    val dailyHeavyToolCount: Int = 0,
    val lastUsageDate: String = "",
    val toolUsageCsv: String = ""
) {
    fun usageMap(): Map<String, Int> {
        if (toolUsageCsv.isBlank()) return emptyMap()
        return toolUsageCsv.split(",").mapNotNull { entry ->
            val parts = entry.split(":")
            if (parts.size == 2) parts[0] to (parts[1].toIntOrNull() ?: 0) else null
        }.toMap()
    }
}

class SettingsStore(private val context: Context) {
    private object Keys {
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACCENT_PRESET = stringPreferencesKey("accent_preset")
        val ASK_MOBILE_DATA = booleanPreferencesKey("ask_mobile_data")
        val DEFAULT_SAVE_FOLDER = stringPreferencesKey("default_save_folder")
        val OCR_LANGUAGE = stringPreferencesKey("ocr_language")
        val DEFAULT_IMAGE_QUALITY = intPreferencesKey("default_image_quality")
        val KEEP_ORIGINAL_FILES = booleanPreferencesKey("keep_original_files")
        val PROFILE_NAME = stringPreferencesKey("profile_name")
        val PROFILE_EMAIL = stringPreferencesKey("profile_email")
        val IS_PRO = booleanPreferencesKey("is_pro")
        val DAILY_HEAVY_COUNT = intPreferencesKey("daily_heavy_count")
        val LAST_USAGE_DATE = stringPreferencesKey("last_usage_date")
        val TOOL_USAGE_CSV = stringPreferencesKey("tool_usage_csv")
    }

    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        UserSettings(
            onboardingComplete = prefs[Keys.ONBOARDING_COMPLETE] ?: false,
            themeMode = runCatching {
                ThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name)
            }.getOrDefault(ThemeMode.SYSTEM),
            accentPreset = runCatching {
                AccentPreset.valueOf(prefs[Keys.ACCENT_PRESET] ?: AccentPreset.CORAL.name)
            }.getOrDefault(AccentPreset.CORAL),
            askMobileData = prefs[Keys.ASK_MOBILE_DATA] ?: true,
            defaultSaveFolder = prefs[Keys.DEFAULT_SAVE_FOLDER] ?: "Documents/paperpilot",
            ocrLanguage = prefs[Keys.OCR_LANGUAGE] ?: "English",
            defaultImageQuality = prefs[Keys.DEFAULT_IMAGE_QUALITY] ?: 85,
            keepOriginalFiles = prefs[Keys.KEEP_ORIGINAL_FILES] ?: true,
            profileName = prefs[Keys.PROFILE_NAME] ?: "Local Workspace",
            profileEmail = prefs[Keys.PROFILE_EMAIL] ?: "",
            isPro = prefs[Keys.IS_PRO] ?: false,
            dailyHeavyToolCount = prefs[Keys.DAILY_HEAVY_COUNT] ?: 0,
            lastUsageDate = prefs[Keys.LAST_USAGE_DATE] ?: "",
            toolUsageCsv = prefs[Keys.TOOL_USAGE_CSV] ?: ""
        )
    }

    suspend fun setOnboardingComplete(done: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_COMPLETE] = done }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setAccentPreset(preset: AccentPreset) {
        context.dataStore.edit { it[Keys.ACCENT_PRESET] = preset.name }
    }

    suspend fun setAskMobileData(value: Boolean) {
        context.dataStore.edit { it[Keys.ASK_MOBILE_DATA] = value }
    }

    suspend fun setDefaultSaveFolder(folder: String) {
        context.dataStore.edit { it[Keys.DEFAULT_SAVE_FOLDER] = folder }
    }

    suspend fun setOcrLanguage(lang: String) {
        context.dataStore.edit { it[Keys.OCR_LANGUAGE] = lang }
    }

    suspend fun setDefaultImageQuality(quality: Int) {
        context.dataStore.edit { it[Keys.DEFAULT_IMAGE_QUALITY] = quality }
    }

    suspend fun setKeepOriginalFiles(keep: Boolean) {
        context.dataStore.edit { it[Keys.KEEP_ORIGINAL_FILES] = keep }
    }

    suspend fun setLocalProfile(name: String, email: String) {
        context.dataStore.edit {
            it[Keys.PROFILE_NAME] = name.ifBlank { "Local Workspace" }
            it[Keys.PROFILE_EMAIL] = email.trim()
        }
    }

    suspend fun setProEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.IS_PRO] = enabled }
    }

    suspend fun recordToolUsage(toolKey: String, isHeavy: Boolean, todayKey: String) {
        context.dataStore.edit { prefs ->
            val currentMap = (prefs[Keys.TOOL_USAGE_CSV] ?: "")
                .split(",")
                .mapNotNull { entry ->
                    val p = entry.split(":")
                    if (p.size == 2) p[0] to (p[1].toIntOrNull() ?: 0) else null
                }
                .toMap()
                .toMutableMap()
            currentMap[toolKey] = (currentMap[toolKey] ?: 0) + 1
            prefs[Keys.TOOL_USAGE_CSV] = currentMap.entries.joinToString(",") { "${it.key}:${it.value}" }

            if (isHeavy) {
                val prevDate = prefs[Keys.LAST_USAGE_DATE] ?: ""
                val count = if (prevDate == todayKey) (prefs[Keys.DAILY_HEAVY_COUNT] ?: 0) + 1 else 1
                prefs[Keys.LAST_USAGE_DATE] = todayKey
                prefs[Keys.DAILY_HEAVY_COUNT] = count
            }
        }
    }
}
