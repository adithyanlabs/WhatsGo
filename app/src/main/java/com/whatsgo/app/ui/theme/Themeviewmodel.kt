package com.whatsgo.app.ui.theme

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ThemeViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = ThemePreferences(application)

    // This will reactively update whenever SettingsViewModel writes to the DataStore
    val settings: StateFlow<ThemeSettings> = prefs.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ThemeSettings()
    )

    fun setDynamicColorEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setDynamicColorEnabled(enabled) }
    }

    fun setDarkModeOption(option: DarkModeOption) {
        viewModelScope.launch { prefs.setDarkModeOption(option) }
    }
}