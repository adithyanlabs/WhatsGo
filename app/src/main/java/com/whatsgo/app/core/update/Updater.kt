package com.whatsgo.app.core.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.io.IOException
import java.math.BigInteger

data class UpdateInfo(
    val version: String,
    val notes: String,
    val apkUrl: String,
    val sizeBytes: Long
)

object Updater {
    private const val RELEASES_URL =
        "https://api.github.com/repos/adithyanlabs/WhatsGo/releases?per_page=10"

    private val http = OkHttpClient()

    fun currentVersion(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"

    /** Returns the newest release if it is newer than the installed version, else null. Throws on network errors. */
    suspend fun fetchLatest(context: Context, includeNightly: Boolean = false): UpdateInfo? = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(RELEASES_URL)
            .header("Accept", "application/vnd.github+json")
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("GitHub returned ${resp.code}")
            val releases = JSONArray(resp.body?.string() ?: "[]")
            for (i in 0 until releases.length()) {
                val r = releases.getJSONObject(i)
                if (r.optBoolean("draft")) continue
                val tag = r.optString("tag_name")
                val isNightlyTag = tag.equals("nightly", ignoreCase = true)

                // Nightly is an opt-in channel; alpha and beta releases stay available.
                if (isNightlyTag && !includeNightly) continue

                val version = if (isNightlyTag) {
                    nightlyVersion(r.optString("body")) ?: continue
                } else {
                    tag
                }

                val assets = r.optJSONArray("assets") ?: continue
                var apk: org.json.JSONObject? = null
                for (j in 0 until assets.length()) {
                    val a = assets.getJSONObject(j)
                    if (a.optString("name").endsWith(".apk", ignoreCase = true)) { apk = a; break }
                }
                if (apk == null) continue
                val info = UpdateInfo(
                    version = version,
                    notes = cleanNotes(r.optString("body")),
                    apkUrl = apk.getString("browser_download_url"),
                    sizeBytes = apk.optLong("size", 0L)
                )
                if (compareVersions(version, currentVersion(context)) > 0) return@withContext info
            }
            null
        }
    }

    suspend fun download(context: Context, info: UpdateInfo, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val out = File(dir, "WhatsGo-${info.version}.apk")

            dir.listFiles()?.forEach { it.delete() }
            val tmp = File(dir, out.name + ".part")
            http.newCall(Request.Builder().url(info.apkUrl).build()).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                val body = resp.body ?: throw IOException("Empty response")
                val total = if (info.sizeBytes > 0) info.sizeBytes else body.contentLength()
                body.byteStream().use { input ->
                    tmp.outputStream().use { output ->
                        val buf = ByteArray(32 * 1024)
                        var read = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            ensureActive()
                            output.write(buf, 0, n)
                            read += n
                            if (total > 0) onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }
            if (!tmp.renameTo(out)) throw IOException("Couldn't save the update file")
            out
        }

    /** Opens the system installer. Returns false if the user must first allow installs from this app. */
    fun install(context: Context, apk: File): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return true
    }

    private data class ParsedVersion(
        val numbers: List<BigInteger>,
        val prerelease: List<String>?
    )

    private fun nightlyVersion(notes: String): String? =
        Regex("(?m)^Nightly version:\\s*(\\S+)\\s*$")
            .find(notes)?.groupValues?.get(1)

    private fun compareVersions(left: String, right: String): Int {
        val a = parseVersion(left) ?: return 0
        val b = parseVersion(right) ?: return 0

        for (i in 0 until maxOf(a.numbers.size, b.numbers.size)) {
            val order = a.numbers.getOrElse(i) { BigInteger.ZERO }
                .compareTo(b.numbers.getOrElse(i) { BigInteger.ZERO })
            if (order != 0) return order
        }

        if (a.prerelease == null) return if (b.prerelease == null) 0 else 1
        if (b.prerelease == null) return -1
        for (i in 0 until maxOf(a.prerelease.size, b.prerelease.size)) {
            val x = a.prerelease.getOrNull(i) ?: return -1
            val y = b.prerelease.getOrNull(i) ?: return 1
            val xNumber = x.toBigIntegerOrNull()
            val yNumber = y.toBigIntegerOrNull()
            val order = when {
                xNumber != null && yNumber != null -> xNumber.compareTo(yNumber)
                xNumber != null -> -1
                yNumber != null -> 1
                else -> x.compareTo(y, ignoreCase = true)
            }
            if (order != 0) return order
        }
        return 0
    }

    private fun parseVersion(version: String): ParsedVersion? {
        val match = Regex("^v?(\\d+(?:\\.\\d+)*)(?:-([0-9A-Za-z.-]+))?(?:\\+[0-9A-Za-z.-]+)?$")
            .matchEntire(version.trim()) ?: return null
        val numbers = match.groupValues[1].split('.').map { it.toBigIntegerOrNull() ?: return null }
        val prerelease = match.groupValues[2].takeIf { it.isNotEmpty() }?.split('.')
        return ParsedVersion(numbers, prerelease)
    }

    private fun cleanNotes(md: String): String =
        md.lines().joinToString("\n") { l ->
            l.trim()
                .replace(Regex("^#{1,6}\\s*"), "")
                .replace(Regex("^[-*]\\s+"), "• ")
                .replace("**", "")
                .replace("`", "")
        }.trim().ifBlank { "No release notes provided." }
}
