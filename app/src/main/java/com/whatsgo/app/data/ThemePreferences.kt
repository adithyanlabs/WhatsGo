package com.whatsgo.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Creates one DataStore file named "settings" for the whole app.
private val Context.dataStore by preferencesDataStore(name = "settings")

object ThemePreferences {
    private val DARK_THEME_KEY = booleanPreferencesKey("dark_theme")

    // Emits the saved value every time it changes, and survives app restarts / process death.
    fun isDarkThemeFlow(context: Context): Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[DARK_THEME_KEY] ?: false }

    suspend fun setDarkTheme(context: Context, isDark: Boolean) {
        context.dataStore.edit { prefs -> prefs[DARK_THEME_KEY] = isDark }
    }
}