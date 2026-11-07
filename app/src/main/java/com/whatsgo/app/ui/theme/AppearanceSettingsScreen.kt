package com.whatsgo.app.ui.theme

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettingsScreen(
    onNavigateUp: () -> Unit,
    viewModel: ThemeViewModel = viewModel()
) {
    val settings by viewModel.settings.collectAsState()
    val supportsDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Appearance") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Navigate up")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            val isDynamicEnabled = settings.dynamicColorEnabled && supportsDynamicColor

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = supportsDynamicColor) {
                        viewModel.setDynamicColorEnabled(!settings.dynamicColorEnabled)
                    }
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.ColorLens,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Wallpaper colors",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (supportsDynamicColor) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (supportsDynamicColor) {
                            "Tint the app using colors from your wallpaper"
                        } else {
                            "Requires Android 12 or higher"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isDynamicEnabled,
                    onCheckedChange = { viewModel.setDynamicColorEnabled(it) },
                    enabled = supportsDynamicColor
                )
            }

            HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)

            Text(
                text = "Theme",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )

            data class ThemeOption(
                val mode: DarkModeOption,
                val label: String,
                val icon: androidx.compose.ui.graphics.vector.ImageVector
            )

            val themeOptions = listOf(
                ThemeOption(DarkModeOption.SYSTEM, "Follow system", Icons.Filled.PhoneAndroid),
                ThemeOption(DarkModeOption.LIGHT, "Light", Icons.Filled.LightMode),
                ThemeOption(DarkModeOption.DARK, "Dark", Icons.Filled.DarkMode)
            )

            themeOptions.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setDarkModeOption(option.mode) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = option.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    RadioButton(
                        selected = settings.darkModeOption == option.mode,
                        onClick = { viewModel.setDarkModeOption(option.mode) }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}