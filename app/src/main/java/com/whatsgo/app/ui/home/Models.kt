package com.whatsgo.app.ui.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

import androidx.compose.runtime.Immutable

@Immutable
data class ChatItem(
    val id: String,
    val name: String,
    val lastMessage: String,
    val time: String,
    val unread: Int = 0,
    val isOnline: Boolean = false,
    val isPinned: Boolean = false,
    val isGroup: Boolean = false,
    val avatarPath: String? = null
)

enum class TopTab(val label: String) {
    ALL("Chats"),
    GROUPS("Groups"),
    CHANNELS("Channels"),
    UNREAD("Unread")
}

enum class BottomDest(val label: String, val icon: ImageVector, val filledIcon: ImageVector) {
    CHATS("Chats", Icons.Outlined.ChatBubbleOutline, Icons.Filled.ChatBubble),
    UPDATES("Updates", Icons.Outlined.DynamicFeed, Icons.Filled.DynamicFeed),
    CALLS("Calls", Icons.Outlined.Call, Icons.Filled.Call),
    SETTINGS("Settings", Icons.Outlined.Settings, Icons.Filled.Settings)
}