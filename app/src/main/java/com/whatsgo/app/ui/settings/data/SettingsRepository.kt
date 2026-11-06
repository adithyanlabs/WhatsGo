package com.whatsgo.app.ui.settings.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.whatsgo.app.ui.settings.SettingsRegistry
import com.whatsgo.app.ui.settings.model.SettingType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// 1. REMOVE 'private' and rename to 'appSettingsDataStore' so it can be shared
val Context.appSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "whatsgo_settings"
)

class SettingsRepository(private val context: Context) {

    // 2. Update this reference to use the new name
    private val ds = context.appSettingsDataStore

    fun observeBoolean(id: String, default: Boolean = false): Flow<Boolean> =
        ds.data.map { it[booleanPreferencesKey(id)] ?: default }

    fun observeString(id: String, default: String = ""): Flow<String> =
        ds.data.map { it[stringPreferencesKey(id)] ?: default }

    suspend fun getBoolean(id: String, default: Boolean = false): Boolean =
        ds.data.first()[booleanPreferencesKey(id)] ?: default

    suspend fun getString(id: String, default: String = ""): String =
        ds.data.first()[stringPreferencesKey(id)] ?: default

    suspend fun setBoolean(id: String, value: Boolean) {
        ds.edit { it[booleanPreferencesKey(id)] = value }
    }

    suspend fun setString(id: String, value: String) {
        ds.edit { it[stringPreferencesKey(id)] = value }
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun <T> get(id: String): T {
        val def = SettingsRegistry.interactive.firstOrNull { it.id == id }
            ?: throw IllegalArgumentException("Unknown setting: $id")
        return when (def.type) {
            SettingType.TOGGLE        -> getBoolean(id, def.defaultValue) as T
            SettingType.SINGLE_SELECT -> getString(id, def.defaultOptionKey) as T
            else -> throw UnsupportedOperationException("'$id' is not a value type")
        }
    }
}