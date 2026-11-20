package com.whatsgo.app.ui.chat

import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import java.io.File

private var stickerLoaderInstance: ImageLoader? = null
private fun stickerLoader(context: Context): ImageLoader = stickerLoaderInstance ?: synchronized(ImageLoader::class.java) {
    stickerLoaderInstance ?: ImageLoader.Builder(context.applicationContext).components {
        if (Build.VERSION.SDK_INT >= 28) add(AnimatedImageDecoder.Factory()) else add(GifDecoder.Factory())
    }.build().also { stickerLoaderInstance = it }
}

@Composable
fun StickerImage(path: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val file = remember(path) { path?.let(::File)?.takeIf { it.exists() } }
    if (file == null) {
        Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }
    } else {
        coil3.compose.AsyncImage(file, contentDescription = null, imageLoader = stickerLoader(context),
            modifier = modifier, contentScale = ContentScale.Fit)
    }
}
