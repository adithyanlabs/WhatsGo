package com.whatsgo.app.ui.home

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.whatsgo.app.ui.settings.SettingsCategoryContent
import com.whatsgo.app.ui.settings.SettingsRegistry
import com.whatsgo.app.ui.settings.SettingsScreen
import com.whatsgo.app.ui.theme.ChatAppTheme
import com.whatsgo.app.ui.theme.DarkModeOption
import com.whatsgo.app.ui.theme.ThemeViewModel
import com.whatsgo.app.ui.updates.ContactStatusGroup
import com.whatsgo.app.ui.updates.StatusComposerSheet
import com.whatsgo.app.ui.updates.StatusViewerScreen
import com.whatsgo.app.ui.updates.UpdatesScreen
import com.whatsgo.app.ui.updates.UpdatesViewModel
import kotlinx.coroutines.launch
import com.whatsgo.app.ui.settings.components.LogoutDialog

@SuppressLint("RestrictedApi")
@Composable
fun ChatAppRoot() {
    val coroutineScope = rememberCoroutineScope()
    val themeViewModel: ThemeViewModel = viewModel()
    val chatListViewModel: ChatListViewModel = viewModel()
    val updatesViewModel: UpdatesViewModel = viewModel()
    val chats by chatListViewModel.chats.collectAsState()
    val allStatuses by updatesViewModel.allStatuses.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val session = remember { com.whatsgo.app.core.SessionHolder.get(context) }

    ChatAppTheme(themeViewModel = themeViewModel) {
        val themeSettings by themeViewModel.settings.collectAsState()
        val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
        val isDark = when (themeSettings.darkModeOption) {
            DarkModeOption.DARK -> true
            DarkModeOption.LIGHT -> false
            DarkModeOption.SYSTEM -> systemDark
        }

        var showLogoutDialog by remember { mutableStateOf(false) }
        var isLoggingOut by remember { mutableStateOf(false) }
        var searchActive by remember { mutableStateOf(false) }
        var menuExpanded by remember { mutableStateOf(false) }
        val bottomPagerState = rememberPagerState(initialPage = 0) { BottomDest.entries.size }
        val selectedBottom = BottomDest.entries[bottomPagerState.currentPage]

        var selectedChat by remember { mutableStateOf<ChatItem?>(null) }
        var settingsCategory by remember { mutableStateOf<String?>(null) }
        var showProfileSettings by remember { mutableStateOf(false) }
        var showStatusComposer by remember { mutableStateOf(false) }
        var statusViewerGroupIds by remember { mutableStateOf<List<String>?>(null) }

        var lastOpenedChat by remember { mutableStateOf<ChatItem?>(null) }
        LaunchedEffect(selectedChat) {
            if (selectedChat != null) lastOpenedChat = selectedChat
        }

        val allChatsListState = rememberLazyListState()
        val groupsListState = rememberLazyListState()
        val tabs = TopTab.entries.toTypedArray()
        val pagerState = rememberPagerState(initialPage = 0) { tabs.size }
        val currentTab by remember { derivedStateOf { tabs[pagerState.currentPage] } }

        val focusManager = LocalFocusManager.current

        val directChats = remember(chats) { chats.filter { !it.isGroup } }
        val groupChats = remember(chats) { chats.filter { it.isGroup } }

        LaunchedEffect(selectedBottom) {
            if (selectedBottom != BottomDest.SETTINGS) settingsCategory = null
        }
        LaunchedEffect(pagerState.currentPage) {
            focusManager.clearFocus()
            searchActive = false
        }

        LaunchedEffect(bottomPagerState.currentPage) {
            if (selectedBottom != BottomDest.CHATS) {
                focusManager.clearFocus()
                searchActive = false
                menuExpanded = false
            }
        }

        BackHandler(enabled = showLogoutDialog && !isLoggingOut) { showLogoutDialog = false }
        BackHandler(enabled = selectedChat != null) { selectedChat = null }
        BackHandler(enabled = showStatusComposer) { showStatusComposer = false }
        BackHandler(enabled = statusViewerGroupIds != null) { statusViewerGroupIds = null }
        BackHandler(enabled = showProfileSettings) { showProfileSettings = false }
        BackHandler(enabled = settingsCategory != null) { settingsCategory = null }
        BackHandler(enabled = searchActive) {
            focusManager.clearFocus()
            searchActive = false
        }
        BackHandler(
            enabled = selectedBottom != BottomDest.CHATS
                    && settingsCategory == null
                    && !showProfileSettings
        ) { coroutineScope.launch { bottomPagerState.scrollToPage(0) } }

        LaunchedEffect(Unit) {
            SettingsRegistry.actionHandlers = mapOf(
                "edit_profile"     to { showProfileSettings = true },
                "blocked_contacts" to { },
                "delete_account"   to { }
            )
        }

        val myStatuses = remember(allStatuses) {
            allStatuses.filter { it.fromMe }.sortedBy { it.timestamp }
        }
        val contactGroups = remember(allStatuses) {
            allStatuses.filter { !it.fromMe }
                .groupBy { it.senderJid }
                .map { (jid, list) ->
                    ContactStatusGroup(
                        senderJid = jid,
                        senderName = list.firstOrNull()?.senderName ?: jid,
                        statuses = list.sortedBy { it.timestamp }
                    )
                }
                .sortedByDescending { it.latest.timestamp }
        }


        val statusViewerStatuses = remember(statusViewerGroupIds, allStatuses) {
            statusViewerGroupIds?.let { ids ->
                ids.mapNotNull { id -> allStatuses.find { it.id == id } }
            }
        }

        val swipeNavEnabled = selectedBottom != BottomDest.CHATS
                && settingsCategory == null
                && !showProfileSettings
                && !showStatusComposer
                && statusViewerGroupIds == null
                && selectedChat == null

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = {}
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = padding.calculateTopPadding())
                        .then(
                            if (searchActive) {
                                Modifier.pointerInput(Unit) {
                                    detectTapGestures(onPress = {
                                        focusManager.clearFocus()
                                        searchActive = false
                                    })
                                }
                            } else Modifier
                        )

                ) {
                    HorizontalPager(
                        state = bottomPagerState,
                        userScrollEnabled = swipeNavEnabled,
                        beyondViewportPageCount = 0,
                        flingBehavior = PagerDefaults.flingBehavior(
                            state = bottomPagerState,
                            snapAnimationSpec = spring(dampingRatio = 0.9f, stiffness = 350f)
                        ),
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        val dest = BottomDest.entries[page]
                        Column(Modifier.fillMaxSize()) {
                        when {
                                dest == BottomDest.SETTINGS && settingsCategory != null ->
                                    SettingsCategoryTopBar(
                                        title = SettingsRegistry
                                            .categoryById(settingsCategory!!)?.title
                                            ?: "Settings",
                                        onBack = { settingsCategory = null }
                                    )

                                dest == BottomDest.CHATS -> {
                                    ChatTopBar(
                                        title = currentTab.label,
                                        searchActive = searchActive,
                                        onSearchActiveChange = { searchActive = it },
                                        menuExpanded = menuExpanded,
                                        onMenuExpandedChange = { menuExpanded = it },
                                        isDark = isDark,
                                        onToggleTheme = {
                                            coroutineScope.launch {
                                                themeViewModel.setDarkModeOption(
                                                    if (isDark) DarkModeOption.LIGHT
                                                    else DarkModeOption.DARK
                                                )
                                            }
                                        }
                                    )
                                    ChatTopTabs(
                                        selected = currentTab,
                                        onSelect = { tab ->
                                            coroutineScope.launch {
                                                pagerState.scrollToPage(tabs.indexOf(tab))
                                            }
                                        }
                                    )
                                }

                                dest == BottomDest.UPDATES -> UpdatesTopBar(
                                    onComposeClick = { showStatusComposer = true },
                                    onMenuClick = { }
                                )

                                else -> SimpleDestinationTopBar(title = dest.label)
                            }

                            Box(Modifier.weight(1f).fillMaxWidth()) {
                                when (dest) {
                                    BottomDest.CHATS -> HorizontalPager(
                                        state = pagerState,
                                        beyondViewportPageCount = 0,
                                        flingBehavior = PagerDefaults.flingBehavior(
                                            state = pagerState,
                                            snapAnimationSpec = spring(
                                                dampingRatio = 0.9f,
                                                stiffness = 350f
                                            )
                                        ),
                                        modifier = Modifier.fillMaxSize()
                                    ) { innerPage ->
                                        when (tabs[innerPage]) {
                                            TopTab.ALL -> ChatList(
                                                chats = directChats,
                                                listState = allChatsListState,
                                                bottomPadding = 110.dp,
                                                onChatClick = {
                                                    chatListViewModel.markRead(it.id)
                                                    selectedChat = it
                                                }
                                            )
                                            TopTab.GROUPS -> ChatList(
                                                chats = groupChats,
                                                listState = groupsListState,
                                                bottomPadding = 110.dp,
                                                onChatClick = {
                                                    chatListViewModel.markRead(it.id)
                                                    selectedChat = it
                                                }
                                            )
                                            else -> EmptyTabPlaceholder(tabs[innerPage])
                                        }
                                    }

                                    BottomDest.UPDATES -> UpdatesScreen(
                                        myStatuses = myStatuses,
                                        contactGroups = contactGroups,
                                        onOpenMyStatus = { statusViewerGroupIds = myStatuses.map { it.id } },
                                        onOpenContactGroup = { g -> statusViewerGroupIds = g.statuses.map { it.id } },
                                        onCompose = { showStatusComposer = true }
                                    )

                                    BottomDest.SETTINGS -> if (settingsCategory != null) {
                                        SettingsCategoryContent(categoryId = settingsCategory!!)
                                    } else {
                                        SettingsScreen(
                                            onNavigateToCategory = { settingsCategory = it },
                                            onLogout = { showLogoutDialog = true }
                                        )
                                    }

                                    else -> DestinationPlaceholder(dest)
                                }
                            }
                        }
                    }

                    FloatingBottomNav(
                        selected = BottomDest.entries[bottomPagerState.currentPage],
                        onSelect = { newDest ->
                            if (newDest.ordinal == bottomPagerState.currentPage) return@FloatingBottomNav
                            focusManager.clearFocus()
                            searchActive = false
                            coroutineScope.launch { bottomPagerState.scrollToPage(newDest.ordinal) }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 20.dp)
                    )

                    ProfileSettingsOverlay(
                        visible = showProfileSettings,
                        onDismiss = { showProfileSettings = false }
                    )

                    if (showLogoutDialog) {
                        LogoutDialog(
                            onConfirm = {
                                showLogoutDialog = false
                                isLoggingOut = true
                                session.logout { serverOk ->
                                    if (!serverOk) {
                                        isLoggingOut = false
                                    }
                                }
                            },
                            onDismiss = { showLogoutDialog = false }
                        )
                    }

                    if (isLoggingOut) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.material3.CircularProgressIndicator()
                        }
                    }
                }
            }

            ConversationOverlay(
                visible = selectedChat != null,
                chat = selectedChat ?: lastOpenedChat,
                onBack = { selectedChat = null }
            )

            statusViewerStatuses?.takeIf { it.isNotEmpty() }?.let { group ->
                StatusViewerScreen(
                    statuses = group,
                    onViewed = { status ->
                        if (!status.fromMe && !status.viewed) {
                            updatesViewModel.markViewed(status.senderJid, listOf(status.id))
                        }
                    },
                    onDownload = { status -> updatesViewModel.downloadStatus(status) },
                    onDelete = { status ->
                        updatesViewModel.deleteStatus(status)
                        val newIds = statusViewerGroupIds?.filter { it != status.id }
                        statusViewerGroupIds = newIds?.takeIf { it.isNotEmpty() }
                    },
                    onClose = { statusViewerGroupIds = null }
                )
            }

            if (showStatusComposer) {
                StatusComposerSheet(
                    onDismiss = { showStatusComposer = false },
                    onPostText = { text, color -> updatesViewModel.postTextStatus(text, color) },
                    onPostImage = { path, caption -> updatesViewModel.postImageStatus(path, caption) },
                    onPostVideo = { path, caption -> updatesViewModel.postVideoStatus(path, caption) }
                )
            }
        }
    }
}
