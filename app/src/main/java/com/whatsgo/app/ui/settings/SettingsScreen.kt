package com.whatsgo.app.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.whatsgo.app.ui.settings.components.CategoryCard
import com.whatsgo.app.ui.settings.components.ProfileCard
import com.whatsgo.app.ui.theme.ThemeViewModel

@Composable
fun SettingsScreen(
    onNavigateToCategory: (String) -> Unit,
    onLogout: () -> Unit,
    viewModel: ThemeViewModel = viewModel(),
    profileViewModel: com.whatsgo.app.ui.user.ProfileViewModel = viewModel()
) {
    val profile by profileViewModel.profile.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        ProfileCard(
            name = profile.name,
            about = profile.about,
            phone = profile.phone,
            avatarUri = profile.avatarUri,
            onEditClick = {
                SettingsRegistry.actionHandlers["edit_profile"]?.invoke()
            }
        )

        Spacer(Modifier.height(24.dp))

        Text(
            "Settings",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Category cards
        SettingsRegistry.categories.forEach { cat ->
            CategoryCard(
                icon = cat.icon,
                title = cat.title,
                subtitle = cat.subtitle,
                onClick = { onNavigateToCategory(cat.id) }
            )
            Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.height(16.dp))

        // Logout
        OutlinedButton(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            ),
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
            )
        ) {
            Icon(Icons.AutoMirrored.Filled.Logout, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Log out", fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "WhatsGo v0.1.0-alpha",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}