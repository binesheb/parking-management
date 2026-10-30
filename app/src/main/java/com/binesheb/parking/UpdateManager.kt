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

object UpdateManager {
    private const val manifestUrl = "https://raw.githubusercontent.com/binesheb/parking-management/main/releases/android.json"
    fun check(context: Context, currentVersionCode: Int, onResult: (String) -> Unit) = thread {
        runCatching {
            val json = URL(manifestUrl).readText(); val obj = JSONObject(json); val remote = obj.getInt("versionCode")
            if (remote <= currentVersionCode) return@thread onResult("up_to_date")
            val apkUrl = URL(obj.getString("apkUrl"))
            val sha = obj.getString("sha256")
            if (apkUrl.protocol != "https" || apkUrl.host != "github.com" ||
                !apkUrl.path.startsWith("/binesheb/parking-management/releases/") ||
                !isValidApkSha256(sha)) {
                return@thread onResult("invalid_manifest")
            }
            val apk = File(context.cacheDir, "parking-update.apk")
            (apkUrl.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 30000
                instanceFollowRedirects = true
            }.let { connection ->
                try {
                    if (connection.responseCode !in 200..299) {
                        throw IllegalStateException("APK download returned HTTP ${connection.responseCode}")
                    }
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
