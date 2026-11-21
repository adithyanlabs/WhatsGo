package com.whatsgo.app.ui.chat

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
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

fun copyDocumentToCache(context: Context, uri: Uri): Pair<String, String>? {
    return try {
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val index = cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst()) cursor.getString(index) else null
            }
            ?.takeIf { it.isNotBlank() }
            ?.substringAfterLast('/')
            ?: "document"
        val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
        val safeName = name.replace(Regex("[\\\\/]"), "_")
        val finalName = ensureExtension(safeName, mimeType)
        val outFile = File(context.cacheDir, "document_${System.nanoTime()}_$finalName")
        context.contentResolver.openInputStream(uri)?.use { input ->
            outFile.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        outFile.absolutePath to finalName
    } catch (_: Exception) {
        null
    }
}

private fun ensureExtension(fileName: String, mimeType: String): String {
    if (fileName.contains(".")) return fileName
    val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
    return if (ext?.isNotBlank() == true) "$fileName.$ext" else fileName
}
