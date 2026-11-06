package com.whatsgo.app.ui.home

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTopBar(
    title: String,
    searchActive: Boolean,
    onSearchActiveChange: (Boolean) -> Unit,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    isDark: Boolean,
    onToggleTheme: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )

                Box {
                    IconButton(onClick = { onMenuExpandedChange(true) }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { onMenuExpandedChange(false) },
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (isDark) "Switch to Light theme"
                                    else "Switch to Dark theme"
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    if (isDark) Icons.Filled.LightMode
                                    else Icons.Filled.DarkMode,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                onToggleTheme()
                               // onMenuExpandedChange(false)
                            }
                        )
                    }
                }
            }

            var query by remember { mutableStateOf("") }
            val searchBarShape by animateDpAsState(
                targetValue = if (searchActive) 12.dp else 28.dp,
                animationSpec = tween(250),
                label = "search-shape"
            )

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search chats, contacts, messages") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    AnimatedVisibility(
                        visible = query.isNotEmpty(),
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(searchBarShape),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor =
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    unfocusedContainerColor =
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Transparent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .onFocusChanged(onSearchActiveChange)
            )
        }
    }
}

private fun Modifier.onFocusChanged(callback: (Boolean) -> Unit): Modifier =
    this.then(Modifier.onFocusEvent { state -> callback(state.isFocused) })