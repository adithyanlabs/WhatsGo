package com.whatsgo.app.ui.user

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.whatsgo.app.ui.user.components.*

@Composable
fun ProfileSettingsScreen(
    onBack: () -> Unit,
    viewModel: ProfileViewModel = viewModel()
) {
    val profile by viewModel.profile.collectAsState()
    var editingField by remember { mutableStateOf<String?>(null) }

    when (editingField) {
        "name" -> ProfileEditDialog(
            title = "Your name",
            currentValue = profile.name,
            hint = "Enter your name",
            maxLength = 30,
            onSave = { viewModel.updateName(it) },
            onDismiss = { editingField = null }
        )
        "about" -> ProfileEditDialog(
            title = "About",
            currentValue = profile.about,
            hint = "Tell us about yourself",
            maxLength = 140,
            onSave = { viewModel.updateAbout(it) },
            onDismiss = { editingField = null }
        )
        "username" -> ProfileEditDialog(
            title = "Username",
            currentValue = profile.username,
            hint = "@username",
            maxLength = 25,
            onSave = { viewModel.updateUsername(it) },
            onDismiss = { editingField = null }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 100.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))

        ProfileAvatar(
            avatarUri = profile.avatarUri,
            name = profile.name,
            onAvatarClick = { /* TODO: image picker */ },
            size = 120
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = profile.name.ifEmpty { "Your name" },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = profile.username.ifEmpty { "@username" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(28.dp))

        ProfileSectionLabel("Personal info")
        Spacer(Modifier.height(8.dp))

        ProfileFieldCard(
            icon = Icons.Filled.Person,
            label = "Name",
            value = profile.name,
            onEditClick = { editingField = "name" }
        )

        Spacer(Modifier.height(12.dp))

        ProfileFieldCard(
            icon = Icons.Filled.Info,
            label = "About",
            value = profile.about,
            onEditClick = { editingField = "about" },
            iconBg = MaterialTheme.colorScheme.tertiaryContainer,
            iconTint = MaterialTheme.colorScheme.onTertiaryContainer
        )

        Spacer(Modifier.height(12.dp))

        ProfileFieldCard(
            icon = Icons.Filled.AlternateEmail,
            label = "Username",
            value = profile.username,
            onEditClick = { editingField = "username" },
            iconBg = MaterialTheme.colorScheme.primaryContainer,
            iconTint = MaterialTheme.colorScheme.onPrimaryContainer
        )

        Spacer(Modifier.height(24.dp))


        ProfileSectionLabel("Contact info")
        Spacer(Modifier.height(8.dp))

        ProfileFieldCard(
            icon = Icons.Filled.Phone,
            label = "Phone number",
            value = profile.phone,
            editable = false,
            onEditClick = {}
        )

        Spacer(Modifier.height(24.dp))

    }
}