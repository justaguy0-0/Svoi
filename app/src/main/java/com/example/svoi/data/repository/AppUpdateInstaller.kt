package com.example.svoi.data.repository

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.example.svoi.BuildConfig
import com.example.svoi.data.model.AppVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

class AppUpdateInstaller(private val context: Context) {

    private val client = OkHttpClient()
    @Volatile private var activeCall: Call? = null

    suspend fun downloadApk(
        update: AppVersion,
        onProgress: suspend (Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val versionCode = update.versionCode ?: error("Unknown update version code")
        val url = update.resolvedDownloadUrl
        require(url.isNotBlank()) { "Empty APK URL" }

        val target = apkFile(versionCode)
        if (target.length() > 0L) {
            Log.d("AppUpdate", "Using cached APK: ${target.absolutePath}")
            withContext(Dispatchers.Main) { onProgress(100) }
            return@withContext target
        }

        if (!url.endsWith(".apk", ignoreCase = true)) {
            Log.d("AppUpdate", "Downloading APK from non-.apk URL: $url")
        }

        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "${target.name}.part")
        if (temp.exists()) temp.delete()

        val request = Request.Builder().url(url).get().build()
        val call = client.newCall(request)
        activeCall = call

        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    error("HTTP ${response.code}")
                }

                val body = response.body ?: error("Empty response body")
                val total = body.contentLength()
                var downloaded = 0L
                var lastPercent = -1

                body.byteStream().use { input ->
                    temp.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read

                            if (total > 0L) {
                                val percent = ((downloaded * 100) / total).toInt().coerceIn(0, 100)
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    withContext(Dispatchers.Main) { onProgress(percent) }
                                }
                            }
                        }
                    }
                }

                if (temp.length() <= 0L) {
                    temp.delete()
                    error("Downloaded APK is empty")
                }

                if (target.exists()) target.delete()
                if (!temp.renameTo(target)) {
                    temp.copyTo(target, overwrite = true)
                    temp.delete()
                }
                if (target.length() <= 0L) {
                    target.delete()
                    error("Saved APK is empty")
                }

                withContext(Dispatchers.Main) { onProgress(100) }
                target
            }
        } finally {
            activeCall = null
        }
    }

    fun cancelDownload() {
        activeCall?.cancel()
    }

    fun canInstallPackages(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

    fun openUnknownSourcesSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val packageUri = Uri.parse("package:${context.packageName}")
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, packageUri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun installApk(apk: File) {
        require(apk.length() > 0L) { "APK file is empty" }
        val uri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            apk
        )
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME_TYPE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun cachedApk(update: AppVersion): File? {
        val versionCode = update.versionCode ?: return null
        return apkFile(versionCode).takeIf { it.length() > 0L }
    }

    private fun apkFile(versionCode: Int): File =
        File(File(context.cacheDir, "updates"), "svoi-update-$versionCode.apk")

    private companion object {
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        const val DEFAULT_BUFFER_SIZE = 8 * 1024
    }
}
