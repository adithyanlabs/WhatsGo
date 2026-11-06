// ui/settings/model/SettingModels.kt
package com.whatsgo.app.ui.settings.model

import androidx.compose.ui.graphics.vector.ImageVector

enum class SettingType {
    TOGGLE,
    SINGLE_SELECT,
    ACTION
}

data class SelectOption(
    val key: String,
    val label: String,
    val icon: ImageVector? = null
)

data class SettingDefinition(
    val id: String,
    val type: SettingType,
    val title: String,
    val subtitle: String = "",
    val icon: ImageVector? = null,
    val categoryId: String = "",

    // TOGGLE
    val defaultValue: Boolean = false,
    val enabledIf: (() -> Boolean)? = null,
    val disabledSubtitle: String = "",

    // SINGLE_SELECT
    val options: List<SelectOption> = emptyList(),
    val defaultOptionKey: String = "",

    // ACTION
    val isDestructive: Boolean = false
)

data class SettingsCategory(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val route: String = "settings/$id"
)