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
    val defaultSaveFolder: String = "Documents/paperpilot",
    val ocrLanguage: String = "English",
    val defaultImageQuality: Int = 85,
    val keepOriginalFiles: Boolean = true,
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
        val DEFAULT_SAVE_FOLDER = stringPreferencesKey("default_save_folder")
        val OCR_LANGUAGE = stringPreferencesKey("ocr_language")
        val DEFAULT_IMAGE_QUALITY = intPreferencesKey("default_image_quality")
        val KEEP_ORIGINAL_FILES = booleanPreferencesKey("keep_original_files")
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
            defaultSaveFolder = prefs[Keys.DEFAULT_SAVE_FOLDER] ?: "Documents/paperpilot",
            ocrLanguage = prefs[Keys.OCR_LANGUAGE] ?: "English",
            defaultImageQuality = prefs[Keys.DEFAULT_IMAGE_QUALITY] ?: 85,
            keepOriginalFiles = prefs[Keys.KEEP_ORIGINAL_FILES] ?: true,
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

    suspend fun recordToolUsage(toolKey: String) {
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
        }
    }
}
