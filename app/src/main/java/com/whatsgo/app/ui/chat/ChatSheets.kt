package com.whatsgo.app.ui.chat

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whatsgo.app.ui.chat.models.ChatProfile
import com.whatsgo.app.ui.chat.models.MediaOption
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.produceState
import androidx.compose.runtime.produceState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.whatsgo.app.core.db.ChannelEntity
import java.util.Locale
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.platform.LocalContext
import com.whatsgo.app.core.GroupInfoData
import com.whatsgo.app.core.GroupMember
import java.text.SimpleDateFormat
import java.util.Date
import androidx.compose.animation.animateContentSize
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaPickerSheet(onDismiss: () -> Unit, onOptionSelected: (MediaOption) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val cs = MaterialTheme.colorScheme

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = cs.surfaceContainerLow,
        contentWindowInsets = { WindowInsets.systemBars },
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .width(32.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(cs.onSurfaceVariant.copy(alpha = 0.4f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Actions",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = cs.onSurface,
                modifier = Modifier.padding(bottom = 20.dp, start = 4.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(MediaOption.entries.toList()) { option ->
                    CompactActionItem(option = option, onClick = { onOptionSelected(option) })
                }
            }
        }
    }
}

@Composable
private fun CompactActionItem(option: MediaOption, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "itemScale"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(option.color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = option.icon,
                contentDescription = option.label,
                tint = option.color,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(Modifier.height(10.dp))

        Text(
            text = option.label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 14.sp
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatProfileSheet(
    profile: ChatProfile,
    onDismiss: () -> Unit,
    fetchAbout: suspend () -> String
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val cs = MaterialTheme.colorScheme

    val about by produceState(initialValue = "", key1 = profile.name) {
        value = fetchAbout()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = cs.surfaceContainerLow,
        contentWindowInsets = { WindowInsets.systemBars },
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .width(32.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(cs.onSurfaceVariant.copy(alpha = 0.4f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val avatarFile = profile.avatarPath?.let { java.io.File(it) }
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(cs.primaryContainer)
                    .padding(3.dp)
                    .clip(CircleShape)
                    .background(cs.onPrimaryContainer.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                if (avatarFile != null && avatarFile.exists()) {
                    coil3.compose.AsyncImage(
                        model = avatarFile,
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                } else {
                    Text(
                        text = profile.avatarLetter,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = cs.onPrimaryContainer
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = profile.name,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = cs.onSurface
            )

            if (profile.phone.isNotBlank()) {
                Text(
                    text = profile.phone,
                    fontSize = 14.sp,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(Modifier.height(28.dp))

            ProfileActionCard(
                icon = Icons.Filled.Info,
                title = "About",
                subtitle = about.ifBlank { "Hey there! I am using WhatsGo." },
                onClick = { }
            )
        }
    }
}
@Composable
private fun ProfileActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .scale(if (isPressed) 0.98f else 1f)
            .clip(RoundedCornerShape(24.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(24.dp),
        color = cs.surfaceContainer,
        tonalElevation = if (isPressed) 0.dp else 1.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(cs.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = cs.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = cs.onSurface,
                    letterSpacing = 0.2.sp
                )
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = cs.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

fun formatCount(n: Long): String = when {
    n >= 1_000_000 -> String.format(Locale.US, "%.1fM", n / 1_000_000.0).replace(".0M", "M")
    n >= 1_000 -> String.format(Locale.US, "%.1fK", n / 1_000.0).replace(".0K", "K")
    else -> n.toString()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelProfileSheet(
    name: String,
    avatarPath: String?,
    channel: ChannelEntity?,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val cs = MaterialTheme.colorScheme

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = cs.surfaceContainerLow,
        contentWindowInsets = { WindowInsets.systemBars },
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .width(32.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(cs.onSurfaceVariant.copy(alpha = 0.4f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val avatarFile = avatarPath?.let { java.io.File(it) }
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(cs.primaryContainer)
                    .padding(3.dp)
                    .clip(CircleShape)
                    .background(cs.onPrimaryContainer.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                if (avatarFile != null && avatarFile.exists()) {
                    coil3.compose.AsyncImage(
                        model = avatarFile,
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                } else {
                    Text(
                        text = name.take(1).uppercase(),
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = cs.onPrimaryContainer
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = name,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = cs.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (channel?.verified == true) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Filled.Verified,
                        contentDescription = "Verified",
                        tint = cs.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Text(
                text = if (channel != null) "Channel · ${formatCount(channel.subscribers)} followers" else "Channel",
                fontSize = 14.sp,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(Modifier.height(28.dp))

            ExpandableInfoCard(
                icon = Icons.Filled.Info,
                title = "Description",
                text = channel?.description?.takeIf { it.isNotBlank() } ?: "No description"
            )

            Spacer(Modifier.height(12.dp))

            ProfileActionCard(
                icon = Icons.Filled.People,
                title = "Followers",
                subtitle = channel?.subscribers?.let { String.format(Locale.US, "%,d", it) } ?: "—",
                onClick = { }
            )

            Spacer(Modifier.height(12.dp))

            val muted = channel?.muted == true
            ProfileActionCard(
                icon = if (muted) Icons.Filled.NotificationsOff else Icons.Filled.Notifications,
                title = "Notifications",
                subtitle = if (muted) "Muted" else "On",
                onClick = { }
            )
        }
    }
}

private fun timerLabel(sec: Long): String = when {
    sec <= 0L -> "Off"
    sec <= 86_400L -> "24 hours"
    sec <= 604_800L -> "7 days"
    sec <= 7_776_000L -> "90 days"
    else -> "${sec / 86_400L} days"
}

@Composable
private fun InfoCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(24.dp),
        color = cs.surfaceContainer,
        tonalElevation = 1.dp
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(cs.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = cs.primary, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = cs.onSurface)
                Text(subtitle, fontSize = 13.sp, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

@Composable
private fun MemberRow(m: GroupMember) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(cs.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                m.name.firstOrNull()?.uppercase() ?: "?",
                color = cs.onSecondaryContainer, fontWeight = FontWeight.Bold, fontSize = 16.sp
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            if (m.isMe) "You" else m.name,
            fontSize = 15.sp, fontWeight = FontWeight.Medium, color = cs.onSurface,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (m.isAdmin) {
            Surface(shape = RoundedCornerShape(8.dp), color = cs.primaryContainer) {
                Text(
                    if (m.isSuperAdmin) "Owner" else "Admin",
                    fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    color = cs.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupProfileSheet(
    name: String,
    avatarPath: String?,
    info: GroupInfoData?,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val dateFmt = remember { SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault()) }
    fun date(sec: Long) = dateFmt.format(Date(sec * 1000))

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = cs.surfaceContainerLow,
        contentWindowInsets = { WindowInsets.systemBars },
        dragHandle = {
            Box(
                Modifier.padding(top = 12.dp, bottom = 8.dp).width(32.dp).height(4.dp)
                    .clip(RoundedCornerShape(2.dp)).background(cs.onSurfaceVariant.copy(alpha = 0.4f))
            )
        }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                val avatarFile = avatarPath?.let { java.io.File(it) }
                Box(
                    Modifier.size(96.dp).clip(CircleShape).background(cs.primaryContainer)
                        .padding(3.dp).clip(CircleShape)
                        .background(cs.onPrimaryContainer.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (avatarFile != null && avatarFile.exists()) {
                        coil3.compose.AsyncImage(
                            model = avatarFile, contentDescription = null,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                        )
                    } else {
                        Text(
                            name.take(1).uppercase(), fontSize = 36.sp,
                            fontWeight = FontWeight.Bold, color = cs.onPrimaryContainer
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    name, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                    color = cs.onSurface, textAlign = TextAlign.Center
                )
                Text(
                    if (info != null) "Group · ${info.memberCount} members" else "Group",
                    fontSize = 14.sp, color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(Modifier.height(24.dp))
            }

            if (info == null) {
                item { CircularProgressIndicator(Modifier.padding(24.dp)) }
                return@LazyColumn
            }

            item {
                val by = buildString {
                    if (info.descriptionBy.isNotBlank()) append("By ${info.descriptionBy}")
                    if (info.descriptionAt > 0) {
                        if (isNotEmpty()) append(" · ")
                        append(date(info.descriptionAt))
                    }
                }
                ExpandableInfoCard(
                    icon = Icons.Filled.Info,
                    title = "Description",
                    text = info.description.ifBlank { "No description" },
                    footer = by
                )
                Spacer(Modifier.height(12.dp))
            }

            if (info.createdAt > 0) item {
                InfoCard(
                    Icons.Filled.Event, "Created",
                    buildString {
                        append(date(info.createdAt))
                        if (info.createdBy.isNotBlank()) append(" · by ${info.createdBy}")
                    }
                )
                Spacer(Modifier.height(12.dp))
            }

            item {
                Text(
                    "Group settings", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    color = cs.primary,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp)
                )
                InfoCard(Icons.Filled.Chat, "Send messages", if (info.announce) "Only admins" else "All members")
                Spacer(Modifier.height(8.dp))
                InfoCard(Icons.Filled.Edit, "Edit group info", if (info.locked) "Only admins" else "All members")
                Spacer(Modifier.height(8.dp))
                InfoCard(Icons.Filled.PersonAdd, "Add members", if (info.adminAddOnly) "Only admins" else "All members")
                Spacer(Modifier.height(8.dp))
                InfoCard(Icons.Filled.Timer, "Disappearing messages", timerLabel(info.ephemeralSeconds))
                if (info.isCommunity) {
                    Spacer(Modifier.height(8.dp))
                    InfoCard(Icons.Filled.Groups, "Community", "This group is a community parent")
                }
                Spacer(Modifier.height(12.dp))
            }

            if (info.inviteLink.isNotBlank()) item {
                InfoCard(Icons.Filled.Link, "Invite link", info.inviteLink) {
                    val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                            as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("Invite link", info.inviteLink))
                    android.widget.Toast.makeText(context, "Link copied", android.widget.Toast.LENGTH_SHORT).show()
                }
                Spacer(Modifier.height(12.dp))
            }

            item {
                Text(
                    "${info.memberCount} members", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    color = cs.primary,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)
                )
            }
            items(info.members) { MemberRow(it) }
        }
    }
}

private const val DESC_CHAR_LIMIT = 140
private const val DESC_COLLAPSED_LINES = 3

@Composable
private fun ExpandableInfoCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    text: String,
    footer: String = ""
) {
    val cs = MaterialTheme.colorScheme
    var expanded by remember(text) { mutableStateOf(false) }
    val isLong = text.length > DESC_CHAR_LIMIT || text.count { it == '\n' } >= DESC_COLLAPSED_LINES

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .then(if (isLong) Modifier.clickable { expanded = !expanded } else Modifier),
        shape = RoundedCornerShape(24.dp),
        color = cs.surfaceContainer,
        tonalElevation = 1.dp
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(cs.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = cs.primary, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f).animateContentSize()) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = cs.onSurface)
                Text(
                    text = text,
                    fontSize = 13.sp,
                    color = cs.onSurfaceVariant,
                    maxLines = if (isLong && !expanded) DESC_COLLAPSED_LINES else Int.MAX_VALUE,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
                if (isLong) {
                    Text(
                        text = if (expanded) "Show less" else "Read more",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = cs.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (footer.isNotBlank()) {
                    Text(
                        text = footer,
                        fontSize = 12.sp,
                        color = cs.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

