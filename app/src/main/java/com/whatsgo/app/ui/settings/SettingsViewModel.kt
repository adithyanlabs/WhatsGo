// ui/settings/SettingsViewModel.kt
package com.whatsgo.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.whatsgo.app.ui.settings.data.SettingsRepository
import com.whatsgo.app.ui.settings.model.SettingType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    val repo = SettingsRepository(app)

    private val _toggles = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val toggles: StateFlow<Map<String, Boolean>> = _toggles.asStateFlow()

    private val _selects = MutableStateFlow<Map<String, String>>(emptyMap())
    val selects: StateFlow<Map<String, String>> = _selects.asStateFlow()

    init {
        SettingsRegistry.interactive.forEach { def ->
            viewModelScope.launch {
                when (def.type) {
                    SettingType.TOGGLE ->
                        repo.observeBoolean(def.id, def.defaultValue).collect { v ->
                            _toggles.update { it + (def.id to v) }
                        }
                    SettingType.SINGLE_SELECT ->
                        repo.observeString(def.id, def.defaultOptionKey).collect { v ->
                            _selects.update { it + (def.id to v) }
                        }
                    else -> {}
                }
            }
        }
    }

    fun setToggle(id: String, value: Boolean) {
        viewModelScope.launch { repo.setBoolean(id, value) }
    }

    fun setSelect(id: String, key: String) {
        viewModelScope.launch { repo.setString(id, key) }
    }
}