package com.whatsgo.app.ui.updates

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.whatsgo.app.core.db.StatusEntity
import java.util.concurrent.TimeUnit

@Composable
fun UpdatesScreen(
    myStatuses: List<StatusEntity>,
    contactGroups: List<ContactStatusGroup>,
    onOpenMyStatus: () -> Unit,
    onOpenContactGroup: (ContactStatusGroup) -> Unit,
    onCompose: () -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp = 110.dp
) {
    val hasContent = myStatuses.isNotEmpty() || contactGroups.isNotEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomPadding, top = 4.dp)
    ) {
        item {
            MyStatusRow(
                myStatuses = myStatuses,
                onClick = {
                    if (myStatuses.isEmpty()) onCompose() else onOpenMyStatus()
                },
                onAddClick = onCompose
            )
        }

        if (contactGroups.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Recent updates",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                )
            }
            items(contactGroups, key = { it.senderJid }) { group ->
                ContactStatusRow(group = group, onClick = { onOpenContactGroup(group) })
            }
        }

        if (!hasContent) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "No updates yet",
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Status updates disappear after 24 hours",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

data class ContactStatusGroup(
    val senderJid: String,
    val senderName: String,
    val statuses: List<StatusEntity>
) {
    val latest: StatusEntity get() = statuses.last()
    val allViewed: Boolean get() = statuses.all { it.viewed }
    val count: Int get() = statuses.size
}

@Composable
private fun MyStatusRow(
    myStatuses: List<StatusEntity>,
    onClick: () -> Unit,
    onAddClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            AvatarBubble(
                imageUrl = myStatuses.lastOrNull()?.let { null },
                fallbackText = "You",
                size = 52.dp,
                ringColor = if (myStatuses.isNotEmpty()) MaterialTheme.colorScheme.primary else null
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .border(2.dp, MaterialTheme.colorScheme.background, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "Add status",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "My status",
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (myStatuses.isEmpty()) "Tap to add status update"
                else "${myStatuses.size} update${if (myStatuses.size == 1) "" else "s"}",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ContactStatusRow(group: ContactStatusGroup, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            AvatarBubble(
                imageUrl = null,
                fallbackText = group.senderName.ifBlank { "?" },
                size = 52.dp,
                ringColor = if (group.allViewed) Color.Transparent
                            else MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = group.senderName.ifBlank { group.senderJid },
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = relativeTime(group.latest.timestamp) +
                    if (group.count > 1) " · ${group.count} updates" else "",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AvatarBubble(
    imageUrl: String?,
    fallbackText: String,
    size: androidx.compose.ui.unit.Dp,
    ringColor: Color?
) {
    val ringBorder = if (ringColor != null) Modifier
        .border(3.dp, ringColor, CircleShape)
        .padding(3.dp) else Modifier
    Box(
        modifier = Modifier
            .size(size)
            .then(ringBorder)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = fallbackText.firstOrNull()?.uppercase() ?: "?",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 18.sp
            )
        }
    }
}

internal fun relativeTime(seconds: Long): String {
    val now = System.currentTimeMillis()
    val t = seconds * 1000L
    val diff = now - t
    return when {
        diff < TimeUnit.MINUTES.toMillis(1) -> "Just now"
        diff < TimeUnit.HOURS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toMinutes(diff)}m ago"
        diff < TimeUnit.DAYS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toHours(diff)}h ago"
        else -> "Yesterday"
    }
}
