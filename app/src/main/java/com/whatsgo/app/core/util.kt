package com.whatsgo.app.ui.chat

import android.content.Context
import android.net.Uri
import java.io.File

fun copyUriToCacheFile(context: Context, uri: Uri, suffix: String): String? {
    return try {
        val outFile = File(context.cacheDir, "media_${System.nanoTime()}.$suffix")
        context.contentResolver.openInputStream(uri)?.use { input ->
            outFile.outputStream().use { output -> input.copyTo(output) }
        }
        outFile.absolutePath
    } catch (e: Exception) {
        null
    }
}