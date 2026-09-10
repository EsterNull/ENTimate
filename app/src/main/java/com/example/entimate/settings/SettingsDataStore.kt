package com.example.entimate.settings

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

object SettingsKeys {
    val THEME_PRESET = stringPreferencesKey("theme_preset")
    val DARK_MODE = stringPreferencesKey("dark_mode")
    val CUSTOM_COLOR = longPreferencesKey("custom_color")
    val CUSTOM_BG_COLOR = longPreferencesKey("custom_bg_color")
    val CUSTOM_SECONDARY_COLOR = longPreferencesKey("custom_secondary_color")
    val DATE_FORMAT = stringPreferencesKey("date_format")
    val TUTORIAL_SEEN = booleanPreferencesKey("tutorial_seen")
    val CURRENT_FOLDER_ID = longPreferencesKey("current_folder_id")
    val PENDING_UPDATE_VERSION = stringPreferencesKey("pending_update_version")
    val SHOW_DISCHARGED = booleanPreferencesKey("show_discharged")
}

data class ThemeSettings(
    val preset: String = "tokyonight",
    val darkMode: String = "system",
    val customColor: Long = 0xFF6750A4,
    val customBg: Long = 0L,
    val customSecondary: Long = 0L,
    val dateFormat: String = "dd.MM.yyyy",
    val showDischarged: Boolean = false,
)

class SettingsDataStore(private val context: Context) {
    val settingsFlow: Flow<ThemeSettings> = context.dataStore.data.map { prefs ->
        ThemeSettings(
            preset = prefs[SettingsKeys.THEME_PRESET] ?: "tokyonight",
            darkMode = prefs[SettingsKeys.DARK_MODE] ?: "system",
            customColor = prefs[SettingsKeys.CUSTOM_COLOR] ?: 0xFF6750A4,
            customBg = prefs[SettingsKeys.CUSTOM_BG_COLOR] ?: 0L,
            customSecondary = prefs[SettingsKeys.CUSTOM_SECONDARY_COLOR] ?: 0L,
            dateFormat = prefs[SettingsKeys.DATE_FORMAT] ?: "dd.MM.yyyy",
            showDischarged = prefs[SettingsKeys.SHOW_DISCHARGED] ?: false,
        )
    }

    suspend fun update(
        preset: String? = null,
        darkMode: String? = null,
        customColor: Long? = null,
        customBg: Long? = null,
        customSecondary: Long? = null,
        dateFormat: String? = null,
        showDischarged: Boolean? = null,
    ) {
        context.dataStore.edit { prefs ->
            preset?.let { prefs[SettingsKeys.THEME_PRESET] = it }
            darkMode?.let { prefs[SettingsKeys.DARK_MODE] = it }
            customColor?.let { prefs[SettingsKeys.CUSTOM_COLOR] = it }
            customBg?.let { prefs[SettingsKeys.CUSTOM_BG_COLOR] = it }
            customSecondary?.let { prefs[SettingsKeys.CUSTOM_SECONDARY_COLOR] = it }
            dateFormat?.let { prefs[SettingsKeys.DATE_FORMAT] = it }
            showDischarged?.let { prefs[SettingsKeys.SHOW_DISCHARGED] = it }
        }
    }

    suspend fun setTutorialSeen() {
        context.dataStore.edit { prefs -> prefs[SettingsKeys.TUTORIAL_SEEN] = true }
    }

    fun isTutorialSeen(): Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[SettingsKeys.TUTORIAL_SEEN] ?: false }

    fun currentFolderIdFlow(): Flow<Long> =
        context.dataStore.data.map { prefs -> prefs[SettingsKeys.CURRENT_FOLDER_ID] ?: 1L }

    suspend fun setCurrentFolderId(id: Long) {
        context.dataStore.edit { prefs -> prefs[SettingsKeys.CURRENT_FOLDER_ID] = id }
    }

    fun pendingUpdateVersionFlow(): Flow<String?> =
        context.dataStore.data.map { prefs -> prefs[SettingsKeys.PENDING_UPDATE_VERSION] }

    suspend fun setPendingUpdateVersion(version: String) {
        context.dataStore.edit { prefs -> prefs[SettingsKeys.PENDING_UPDATE_VERSION] = version }
    }

    suspend fun clearPendingUpdateVersion() {
        context.dataStore.edit { prefs -> prefs.remove(SettingsKeys.PENDING_UPDATE_VERSION) }
    }
}
