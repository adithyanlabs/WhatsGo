package com.whatsgo.app.core

import android.graphics.BitmapFactory
import java.io.File

object StickerFiles {
    data class Info(val animated: Boolean, val width: Int, val height: Int)

    fun info(path: String): Info {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, options)
        val header = ByteArray(32)
        val count = runCatching { File(path).inputStream().use { it.read(header) } }.getOrDefault(0)
        val animated = count > 20 && header[12] == 'V'.code.toByte() && header[13] == 'P'.code.toByte() &&
            header[14] == '8'.code.toByte() && header[15] == 'X'.code.toByte() && (header[20].toInt() and 2) != 0
        return Info(animated, options.outWidth.takeIf { it > 0 } ?: 512, options.outHeight.takeIf { it > 0 } ?: 512)
    }
}
