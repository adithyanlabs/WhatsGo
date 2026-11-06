// ui/settings/SettingsCategoryScreen.kt
package com.whatsgo.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.whatsgo.app.ui.settings.components.ActionCard
import com.whatsgo.app.ui.settings.components.SingleSelectCard
import com.whatsgo.app.ui.settings.components.ToggleCard
import com.whatsgo.app.ui.settings.model.SettingType

// ✅ No Scaffold, no TopAppBar — the parent handles that
@Composable
fun SettingsCategoryContent(
    categoryId: String,
    viewModel: SettingsViewModel = viewModel()
) {
    val settings = SettingsRegistry.settingsFor(categoryId)
    val toggles by viewModel.toggles.collectAsState()
    val selects by viewModel.selects.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 100.dp)  // space above floating bottom nav
    ) {
        Spacer(Modifier.height(12.dp))

        settings.forEach { def ->
            when (def.type) {
                SettingType.TOGGLE -> ToggleCard(
                    def = def,
                    checked = toggles[def.id] ?: def.defaultValue,
                    onCheckedChange = { viewModel.setToggle(def.id, it) }
                )
                SettingType.SINGLE_SELECT -> SingleSelectCard(
                    def = def,
                    selectedKey = selects[def.id] ?: def.defaultOptionKey,
                    onOptionSelected = { viewModel.setSelect(def.id, it) }
                )
                SettingType.ACTION -> ActionCard(
                    def = def,
                    onClick = {
                        SettingsRegistry.actionHandlers[def.id]?.invoke()
                    }
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}