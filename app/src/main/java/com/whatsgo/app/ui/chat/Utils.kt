package com.whatsgo.app.ui.chat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import java.io.ByteArrayOutputStream

fun generateImageThumbnail(filePath: String, maxDimension: Int = 200): ByteArray? {
    return try {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(filePath, options)

        var sampleSize = 1
        while (options.outWidth / sampleSize > maxDimension || options.outHeight / sampleSize > maxDimension) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = BitmapFactory.decodeFile(filePath, decodeOptions) ?: return null

        val scaled = scaleBitmap(bitmap, maxDimension)
        bitmapToJpegBytes(scaled)
    } catch (e: Exception) {
        null
    }
}

fun generateVideoThumbnail(filePath: String, maxDimension: Int = 200): ByteArray? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(filePath)
        val frame = retriever.getFrameAtTime(0) ?: return null
        val scaled = scaleBitmap(frame, maxDimension)
        bitmapToJpegBytes(scaled)
    } catch (e: Exception) {
        null
    } finally {
        retriever.release()
    }
}

private fun scaleBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
    val ratio = minOf(maxDimension.toFloat() / bitmap.width, maxDimension.toFloat() / bitmap.height)
    if (ratio >= 1f) return bitmap
    val width = (bitmap.width * ratio).toInt()
    val height = (bitmap.height * ratio).toInt()
    return Bitmap.createScaledBitmap(bitmap, width, height, true)
}

private fun bitmapToJpegBytes(bitmap: Bitmap): ByteArray {
    val stream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
    return stream.toByteArray()
}