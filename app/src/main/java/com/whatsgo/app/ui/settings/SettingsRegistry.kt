package com.whatsgo.app.ui.settings

import android.os.Build
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import com.whatsgo.app.ui.settings.model.*

object SettingsRegistry {
    var actionHandlers: Map<String, () -> Unit> = emptyMap()
    val categories: List<SettingsCategory> = listOf(
        SettingsCategory(
            id = "appearance",
            title = "Appearance",
            subtitle = "Theme, dark mode, fonts",
            icon = Icons.Filled.Palette
        ),
        SettingsCategory(
            id = "chat",
            title = "Chat",
            subtitle = "Receipts",
            icon = Icons.AutoMirrored.Filled.Chat
        ),
        SettingsCategory(
            id = "storage",
            title = "Storage & Media",
            subtitle = "Downloads",
            icon = Icons.Filled.Image
        ),
        SettingsCategory(
            id = "privacy",
            title = "Privacy & Security",
            subtitle = "Last seen and online",
            icon = Icons.Filled.Lock
        ),
    )
    val all: List<SettingDefinition> = listOf(

        SettingDefinition(
            id = "dynamic_color",
            type = SettingType.TOGGLE,
            title = "Wallpaper colors",
            subtitle = "Tint the app using colors from your wallpaper",
            icon = Icons.Filled.ColorLens,
            categoryId = "appearance",
            defaultValue = true,
            enabledIf = { Build.VERSION.SDK_INT >= Build.VERSION_CODES.S },
            disabledSubtitle = "Requires Android 12 or higher"
        ),
        SettingDefinition(
            id = "dark_mode",
            type = SettingType.SINGLE_SELECT,
            title = "Dark mode",
            icon = Icons.Filled.DarkMode,
            categoryId = "appearance",
            defaultOptionKey = "system",
            options = listOf(
                SelectOption("system", "Follow system", Icons.Filled.PhoneAndroid),
                SelectOption("light",  "Light",         Icons.Filled.LightMode),
                SelectOption("dark",   "Dark",          Icons.Filled.DarkMode)
            )
        ),
        SettingDefinition(
            id = "read_receipts",
            type = SettingType.TOGGLE,
            title = "Read receipts",
            subtitle = "Let others know when you've read their messages",
            icon = Icons.Filled.DoneAll,
            categoryId = "chat",
            defaultValue = true
        ),
    )

    fun settingsFor(categoryId: String): List<SettingDefinition> =
        all.filter { it.categoryId == categoryId }

    fun categoryById(id: String): SettingsCategory? =
        categories.firstOrNull { it.id == id }

    val interactive: List<SettingDefinition>
        get() = all
}