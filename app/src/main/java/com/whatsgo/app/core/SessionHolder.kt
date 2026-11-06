package com.whatsgo.app.core

import android.content.Context
import java.io.File

object SessionHolder {
    @Volatile private var instance: ChatSession? = null

    fun get(context: Context): ChatSession {
        return instance ?: synchronized(this) {
            instance ?: ChatSession(
                File(context.applicationContext.filesDir, "whatsgo.db").absolutePath,
                context.applicationContext
            ).also { instance = it }
        }
    }

    fun reset() {
        synchronized(this) {
            instance = null
        }
    }
}