package com.whatsgo.app.ui.home

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(FlowPreview::class)
@Composable
fun VisibleAvatarLoader(
    listState: LazyListState,
    chats: List<ChatItem>,
    onRequest: (String) -> Unit,
    prefetch: Int = 4
) {
    val latestChats by rememberUpdatedState(chats)
    val latestRequest by rememberUpdatedState(onRequest)

    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo.visibleItemsInfo
            Triple(info.firstOrNull()?.index ?: -1, info.lastOrNull()?.index ?: -1, latestChats.size)
        }
            .distinctUntilChanged()
            .debounce(250) // a fast fling doesn't queue every row it passes
            .collect { (first, last, size) ->
                if (first < 0 || size == 0) return@collect
                val from = (first - prefetch).coerceAtLeast(0)
                val to = (last + prefetch).coerceAtMost(size - 1)
                for (i in from..to) {
                    val chat = latestChats.getOrNull(i) ?: continue
                    if (chat.avatarPath == null) latestRequest(chat.id)
                }
            }
    }
}