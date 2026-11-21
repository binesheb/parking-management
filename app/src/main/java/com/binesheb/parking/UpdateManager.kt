package com.binesheb.parking

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.concurrent.thread

internal fun isValidApkSha256(value: String): Boolean =
    value.matches(Regex("^[a-fA-F0-9]{64}$")) && value != "0".repeat(64)

internal fun isValidInitialApkUrl(url: URL): Boolean =
    url.protocol.equals("https", ignoreCase = true) &&
        url.host.equals("github.com", ignoreCase = true) &&
        url.path.startsWith("/binesheb/parking-management/releases/") &&
        url.userInfo == null &&
        (url.port == -1 || url.port == 443)

internal fun isAllowedApkRedirectUrl(url: URL): Boolean =
    url.protocol.equals("https", ignoreCase = true) &&
        url.host.lowercase() in setOf(
            "release-assets.githubusercontent.com",
            "objects.githubusercontent.com",
        ) &&
        url.userInfo == null &&
        (url.port == -1 || url.port == 443)

private fun openApkConnection(initialUrl: URL): HttpURLConnection {
    require(isValidInitialApkUrl(initialUrl)) { "APK URL is not an approved repository release URL" }
    var current = initialUrl
    var redirected = false
    for (redirectCount in 0..5) {
        val connection = (current.openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 30000
            instanceFollowRedirects = false
        }
        val status = connection.responseCode
        if (status in setOf(301, 302, 303, 307, 308)) {
            val location = connection.getHeaderField("Location")
            connection.disconnect()
            if (redirectCount == 5) {
                throw IllegalStateException("APK download exceeded the redirect limit")
            }
            if (location.isNullOrBlank()) {
                throw IllegalStateException("APK redirect has no Location")
            }
            val next = URL(current, location)
            if (!isAllowedApkRedirectUrl(next)) {
                throw IllegalStateException("APK redirect target is not trusted")
            }
            current = next
            redirected = true
        } else {
            if (status !in 200..299) {
                connection.disconnect()
                throw IllegalStateException("APK download returned HTTP $status")
            }
            if (redirected && !isAllowedApkRedirectUrl(connection.url)) {
                connection.disconnect()
                throw IllegalStateException("Final APK host is not trusted")
            }
            return connection
        }
    }
    throw IllegalStateException("APK download exceeded the redirect limit")
}

object UpdateManager {
    private const val manifestUrl = "https://raw.githubusercontent.com/binesheb/parking-management/main/releases/android.json"
    fun check(context: Context, currentVersionCode: Int, onResult: (String) -> Unit) = thread {
        runCatching {
            val json = URL(manifestUrl).readText(); val obj = JSONObject(json); val remote = obj.getInt("versionCode")
            if (remote <= currentVersionCode) return@thread onResult("up_to_date")
            val apkUrl = URL(obj.getString("apkUrl"))
            val sha = obj.getString("sha256")
            if (!isValidInitialApkUrl(apkUrl) || !isValidApkSha256(sha)) {
                return@thread onResult("invalid_manifest")
            }
            val apk = File(context.cacheDir, "parking-update.apk")
            openApkConnection(apkUrl).let { connection ->
                try {
                    connection.inputStream.use { input -> apk.outputStream().use { input.copyTo(it) } }
                } finally {
                    connection.disconnect()
                }
            }
            val digest = MessageDigest.getInstance("SHA-256").digest(apk.readBytes()).joinToString("") { "%02x".format(it) }
            if (!digest.equals(sha, true)) { apk.delete(); return@thread onResult("integrity_failed") }
            val uri = FileProvider.getUriForFile(context, context.packageName + ".files", apk)
            context.startActivity(Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, "application/vnd.android.package-archive"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK) })
            onResult("update_ready")
        }.onFailure { onResult("check_failed:${it.javaClass.simpleName}") }
    }
}
