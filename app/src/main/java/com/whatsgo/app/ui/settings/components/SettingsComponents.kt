package com.whatsgo.app.ui.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.whatsgo.app.ui.settings.model.SettingDefinition

@Composable
fun TonalIcon(
    icon: ImageVector,
    bg: Color = MaterialTheme.colorScheme.secondaryContainer,
    tint: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    size: Int = 40
) {
    Surface(
        Modifier.size(size.dp),
        shape = RoundedCornerShape(12.dp),
        color = bg
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(22.dp), tint)
        }
    }
}
@Composable
fun ProfileCard(
    name: String,
    about: String,
    phone: String,
    avatarUri: String?,
    onEditClick: () -> Unit
) {
    ElevatedCard(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.elevatedCardElevation(2.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onEditClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                Modifier.size(64.dp), CircleShape,
                MaterialTheme.colorScheme.primaryContainer
            ) {
                if (!avatarUri.isNullOrBlank()) {
                    coil3.compose.AsyncImage(
                        model = avatarUri,
                        contentDescription = "Profile photo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            Icons.Filled.Person, "Profile",
                            Modifier.size(36.dp),
                            MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    name.ifEmpty { "Your name" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    about.ifEmpty { "Hey there! I'm using WhatsGo" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    phone,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            IconButton(
                onEditClick,
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
            ) {
                Icon(
                    Icons.Filled.Create, "Edit",
                    Modifier.size(20.dp),
                    MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
fun CategoryCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(1.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TonalIcon(icon)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Filled.ChevronRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun ToggleCard(
    def: SettingDefinition,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val isEnabled = def.enabledIf?.invoke() ?: true
    val subtitle = if (!isEnabled && def.disabledSubtitle.isNotBlank())
        def.disabledSubtitle else def.subtitle

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(1.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            def.icon?.let { TonalIcon(it) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    def.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = checked && isEnabled,
                onCheckedChange = onCheckedChange,
                enabled = isEnabled
            )
        }
    }
}

@Composable
fun SingleSelectCard(
    def: SettingDefinition,
    selectedKey: String,
    onOptionSelected: (String) -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(1.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                def.icon?.let {
                    TonalIcon(
                        it,
                        MaterialTheme.colorScheme.tertiaryContainer,
                        MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
                Spacer(Modifier.width(14.dp))
                Text(
                    def.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(Modifier.padding(horizontal = 4.dp))
            Spacer(Modifier.height(8.dp))

            Column(Modifier.selectableGroup()) {
                def.options.forEach { opt ->
                    val selected = selectedKey == opt.key
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .selectable(
                                selected = selected,
                                onClick = { onOptionSelected(opt.key) },
                                role = Role.RadioButton
                            )
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        opt.icon?.let {
                            Icon(
                                it, null, Modifier.size(20.dp),
                                tint = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(10.dp))
                        }
                        Text(
                            opt.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ActionCard(
    def: SettingDefinition,
    onClick: () -> Unit
) {
    val container = if (def.isDestructive)
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
    else
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)

    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(1.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = container)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            def.icon?.let {
                TonalIcon(
                    it,
                    bg = if (def.isDestructive) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.secondaryContainer,
                    tint = if (def.isDestructive) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    def.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (def.isDestructive) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    def.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (def.isDestructive)
                        MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Filled.ChevronRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun LogoutDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        icon = {
            Surface(
                Modifier.size(56.dp), CircleShape,
                MaterialTheme.colorScheme.errorContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Person, null,
                        Modifier.size(28.dp),
                        MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        },
        title = {
            Text(
                "Log out?",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Text(
                "Are you sure you want to log out? You will stop receiving " +
                        "messages until you sign back in.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Button(
                onConfirm,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) { Text("Log out") }
        },
        dismissButton = {
            TextButton(onDismiss, shape = RoundedCornerShape(12.dp)) {
                Text("Cancel")
            }
        }
    )
}