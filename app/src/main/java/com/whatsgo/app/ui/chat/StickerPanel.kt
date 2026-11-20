package com.whatsgo.app.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whatsgo.app.core.db.StickerEntity
import java.io.File

private val EMOJIS = "😀 😃 😄 😁 😆 😅 😂 🤣 😊 😇 🙂 🙃 😉 😍 🥰 😘 😋 😛 😝 😜 🤪 😎 🥳 😏 😒 😞 😔 😢 😭 😤 😠 🤯 😳 😱 😨 🤗 🤔 😐 🙄 😮 😴 👍 👎 👌 ✌️ 🤞 👋 🙏 💪 ❤️ 💔 🔥 ✨ ⭐ 🎉 👏".split(" ")

@Composable
fun EmojiStickerPanel(favorites: List<StickerEntity>, recent: List<StickerEntity>, received: List<StickerEntity>,
    onEmoji: (String) -> Unit, onSticker: (StickerEntity) -> Unit, onFavorite: (StickerEntity) -> Unit,
    onDownload: (StickerEntity) -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxWidth().height(300.dp)) {
        Row(Modifier.padding(horizontal = 4.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(tab == 0, { tab = 0 }, label = { Text("Emoji") })
            FilterChip(tab == 1, { tab = 1 }, label = { Text("Stickers") })
        }
        if (tab == 0) LazyVerticalGrid(GridCells.Adaptive(44.dp), Modifier.fillMaxSize()) {
            items(EMOJIS) { e -> Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).clickable { onEmoji(e) }, contentAlignment = Alignment.Center) { Text(e, fontSize = 26.sp) } }
        } else LazyVerticalGrid(GridCells.Fixed(4), Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (favorites.isEmpty() && recent.isEmpty() && received.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Text("Received stickers and phone favorites appear here.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            stickerSection("Favorites", "fav", favorites, onSticker, onFavorite, onDownload)
            stickerSection("Recent", "recent", recent, onSticker, onFavorite, onDownload)
            stickerSection("Received", "received", received, onSticker, onFavorite, onDownload)
        }
    }
}

private fun LazyGridScope.stickerSection(title: String, prefix: String, list: List<StickerEntity>, onSticker: (StickerEntity) -> Unit,
    onFavorite: (StickerEntity) -> Unit, onDownload: (StickerEntity) -> Unit) {
    if (list.isEmpty()) return
    item(key = "header-$prefix", span = { GridItemSpan(maxLineSpan) }) { Text(title, Modifier.padding(start = 4.dp, top = 8.dp), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
    items(list, key = { "$prefix-${it.id}" }) { s -> StickerCell(s, onSticker, onFavorite, onDownload) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StickerCell(s: StickerEntity, onSticker: (StickerEntity) -> Unit, onFavorite: (StickerEntity) -> Unit, onDownload: (StickerEntity) -> Unit) {
    val file = remember(s.localPath) { s.localPath?.takeIf { File(it).exists() } }
    LaunchedEffect(s.id, file) { if (file == null) onDownload(s) }
    Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(12.dp)).combinedClickable(onClick = { onSticker(s) }, onLongClick = { onFavorite(s) }).padding(4.dp), contentAlignment = Alignment.Center) {
        StickerImage(file, Modifier.fillMaxSize())
        if (s.isFavorite) Icon(Icons.Filled.Star, null, Modifier.align(Alignment.TopEnd).size(14.dp), tint = MaterialTheme.colorScheme.primary)
    }
}
