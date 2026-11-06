package com.whatsgo.app.ui.theme

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.whatsgo.app.ui.settings.data.appSettingsDataStore // <-- Import the shared instance
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class DarkModeOption { SYSTEM, LIGHT, DARK }

data class ThemeSettings(
    val dynamicColorEnabled: Boolean = false,
    val darkModeOption: DarkModeOption = DarkModeOption.SYSTEM
)

private val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
private val KEY_DARK_MODE = stringPreferencesKey("dark_mode")

class ThemePreferences(private val context: Context) {

    // Use the shared DataStore instance. DO NOT create a new preferencesDataStore here!
    private val ds: DataStore<Preferences> = context.appSettingsDataStore

    val settingsFlow: Flow<ThemeSettings> = ds.data.map { prefs ->
        ThemeSettings(
            dynamicColorEnabled = prefs[KEY_DYNAMIC_COLOR] ?: false,
            darkModeOption = prefs[KEY_DARK_MODE]?.let { prefValue ->
                runCatching { DarkModeOption.valueOf(prefValue.uppercase()) }.getOrNull()
            } ?: DarkModeOption.SYSTEM
        )
    }

    suspend fun setDynamicColorEnabled(enabled: Boolean) {
        ds.edit { it[KEY_DYNAMIC_COLOR] = enabled }
    }

    suspend fun setDarkModeOption(option: DarkModeOption) {
        ds.edit { it[KEY_DARK_MODE] = option.name.lowercase() }
    }
}