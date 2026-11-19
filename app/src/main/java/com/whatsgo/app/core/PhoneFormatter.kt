package com.whatsgo.app.core

import android.content.Context
import io.michaelrocks.libphonenumber.android.NumberParseException
import io.michaelrocks.libphonenumber.android.PhoneNumberUtil
import io.michaelrocks.libphonenumber.android.PhoneNumberUtil.PhoneNumberFormat
import java.util.concurrent.ConcurrentHashMap

object PhoneFormatter {
    @Volatile private var util: PhoneNumberUtil? = null
    private val cache = ConcurrentHashMap<String, String>()
    private val looksLikePhone = Regex("^\\+?\\d{7,15}$")

    private fun util(ctx: Context): PhoneNumberUtil =
        util ?: synchronized(this) {
            util ?: PhoneNumberUtil.createInstance(ctx.applicationContext).also { util = it }
        }

    fun isPhoneLike(text: String) = looksLikePhone.matches(text.trim())

    fun format(ctx: Context, jidOrDigits: String): String {
        val server = jidOrDigits.substringAfter("@", "s.whatsapp.net")
        val digits = jidOrDigits.substringBefore("@").substringBefore(":").removePrefix("+")
        if (server != "s.whatsapp.net" || digits.isEmpty() || !digits.all { it.isDigit() }) {
            return jidOrDigits.substringBefore("@")
        }
        return cache.getOrPut(digits) {
            try {
                val u = util(ctx)
                val n = u.parse("+$digits", null)
                if (!u.isValidNumber(n)) return@getOrPut "+$digits"
                u.format(n, PhoneNumberFormat.INTERNATIONAL)
            } catch (_: NumberParseException) {
                "+$digits"
            }
        }
    }
    fun warmUp(ctx: Context) {
        runCatching { format(ctx, "919876543210@s.whatsapp.net") }
    }
}