package com.whatsgo.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.whatsgo.app.ui.chat.ConversationScreen
import com.whatsgo.app.ui.user.ProfileSettingsScreen

@Composable
internal fun ProfileSettingsOverlay(
    visible: Boolean,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(
            initialOffsetX = { it },
            animationSpec = tween(380, easing = EmphasizedDecelerate)
        ) + fadeIn(tween(380)),
        exit = slideOutHorizontally(
            targetOffsetX = { it },
            animationSpec = tween(350, easing = EmphasizedAccelerate)
        ) + fadeOut(tween(350)),
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            SettingsCategoryTopBar(title = "Profile", onBack = onDismiss)
            ProfileSettingsScreen(onBack = onDismiss)
        }
    }
}

@Composable
internal fun ConversationOverlay(
    visible: Boolean,
    chat: ChatItem?,
    onBack: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(
            initialOffsetX = { it },
            animationSpec = tween(220, easing = EmphasizedDecelerate)
        ) + fadeIn(tween(180)),
        exit = slideOutHorizontally(
            targetOffsetX = { it },
            animationSpec = tween(180, easing = EmphasizedAccelerate)
        ) + fadeOut(tween(120)),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (chat != null) {
                ConversationScreen(
                    chatJid = chat.id,
                    contactName = chat.name,
                    contactStatus = when {
                        chat.isChannel -> "Channel"
                        chat.isOnline -> "online"
                        else -> ""
                    },
                    contactAvatarPath = chat.avatarPath,
                    readOnly = chat.isChannel,
                    onBack = onBack
                )
            }
        }
    }
}