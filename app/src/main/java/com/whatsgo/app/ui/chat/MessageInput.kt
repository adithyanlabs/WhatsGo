package com.whatsgo.app.ui.chat

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whatsgo.app.ui.chat.models.ChatMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageInput(
    replyingTo: ChatMessage?,
    onDismissReply: () -> Unit,
    onAttachClick: () -> Unit,
    onSend: (String) -> Unit,
    onVoiceClick: () -> Unit = {}
) {
    var text by remember { mutableStateOf("") }
    val isSendActive = text.isNotBlank()
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
   val pillElevation by animateDpAsState(
        targetValue = if (isFocused) 3.dp else 1.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "pillElevation"
    )

    val cs = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        AnimatedVisibility(
            visible = replyingTo != null,
            enter = expandVertically(animationSpec = spring()) + fadeIn(spring()),
            exit = shrinkVertically(animationSpec = spring()) + fadeOut(spring())
        ) {
            replyingTo?.let { msg ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = cs.secondaryContainer.copy(alpha = 0.6f),
                    tonalElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(32.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(cs.primary)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Replying to ${if (msg.isMine) "You" else msg.senderName.ifBlank { "them" }}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = cs.primary,
                                letterSpacing = 0.5.sp
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (msg.media != null) {
                                    Icon(Icons.Filled.Image, null, modifier = Modifier.size(14.dp), tint = cs.onSecondaryContainer.copy(alpha = 0.7f))
                                    Spacer(Modifier.width(4.dp))
                                }
                                Text(
                                    msg.text.ifEmpty { "Media attachment" },
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = cs.onSecondaryContainer
                                )
                            }
                        }
                        IconButton(onClick = onDismissReply, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Close, "Close", modifier = Modifier.size(16.dp), tint = cs.onSecondaryContainer)
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FloatingActionButton(
                onClick = onAttachClick,
                modifier = Modifier.size(48.dp),
                containerColor = cs.secondaryContainer,
                contentColor = cs.onSecondaryContainer,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp, pressedElevation = 4.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add Media", modifier = Modifier.size(24.dp))
            }

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp, max = 160.dp),
                shape = RoundedCornerShape(32.dp),
                color = cs.surfaceContainerHigh,
                tonalElevation = pillElevation,
                shadowElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { /* TODO: Emoji picker */ },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Filled.EmojiEmotions, "Emoji", tint = cs.onSurfaceVariant, modifier = Modifier.size(22.dp))
                    }

                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        textStyle = LocalTextStyle.current.copy(
                            color = cs.onSurface,
                            fontSize = 16.sp,
                            lineHeight = 22.sp
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                        maxLines = 4,
                        cursorBrush = SolidColor(cs.primary),
                        interactionSource = interactionSource,
                        decorationBox = { innerTextField ->
                            if (text.isEmpty()) {
                                Text(
                                    "Message",
                                    color = cs.onSurfaceVariant.copy(alpha = 0.6f),
                                    fontSize = 16.sp
                                )
                            }
                            innerTextField()
                        }
                    )
                }
            }

            val actionContainerColor = if (isSendActive) cs.primary else cs.surfaceContainer
            val actionContentColor = if (isSendActive) cs.onPrimary else cs.onSurfaceVariant

            FloatingActionButton(
                onClick = {
                    if (isSendActive) {
                        onSend(text.trim())
                        text = ""
                    } else {
                        onVoiceClick()
                    }
                },
                modifier = Modifier.size(56.dp),
                containerColor = actionContainerColor,
                contentColor = actionContentColor,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = if (isSendActive) 4.dp else 2.dp,
                    pressedElevation = 6.dp
                )
            ) {
                AnimatedContent(
                    targetState = isSendActive,
                    transitionSpec = {
                        if (targetState) {
                            scaleIn(initialScale = 0.5f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) +
                                    fadeIn(spring()) togetherWith
                                    scaleOut(targetScale = 1.5f, animationSpec = spring()) +
                                    fadeOut(spring())
                        } else {
                            scaleIn(initialScale = 0.5f, animationSpec = spring()) +
                                    fadeIn(spring()) togetherWith
                                    scaleOut(targetScale = 1.5f, animationSpec = spring()) +
                                    fadeOut(spring())
                        }
                    },
                    label = "actionIconMorph"
                ) { isActive ->
                    Icon(
                        imageVector = if (isActive) Icons.AutoMirrored.Filled.Send else Icons.Filled.Mic,
                        contentDescription = if (isActive) "Send message" else "Record voice",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}