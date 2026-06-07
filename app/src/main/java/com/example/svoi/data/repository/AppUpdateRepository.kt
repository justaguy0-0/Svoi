package com.example.svoi.data.repository

import android.util.Log
import com.example.svoi.BuildConfig
import com.example.svoi.data.model.AppVersion
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order

class AppUpdateRepository(private val supabase: SupabaseClient) {

    /**
     * Проверяет наличие обновления.
     * Возвращает [AppVersion] если в БД есть версия с version_code > текущего, иначе null.
     */
    suspend fun checkForUpdate(): AppVersion? {
        return try {
            val latest = supabase.from("app_versions")
                .select {
                    order("version_code", Order.DESCENDING)
                    limit(1)
                }
                .decodeSingleOrNull<AppVersion>()

            if (latest == null) {
                Log.d("AppUpdate", "no remote version found")
                return null
            }

            val remoteCode = latest?.versionCode
            val remoteName = latest?.versionName
            val hasDownloadUrl = latest.downloadUrl?.isNotBlank() == true
            val updateAvailable = latest.versionCode > BuildConfig.VERSION_CODE

            Log.d(
                "AppUpdate",
                "remote version loaded code=${latest.versionCode}, " +
                    "name=${latest.versionName}, " +
                    "hasUrl=$hasDownloadUrl, " +
                    "hasChangelog=${latest.changelog?.isNotBlank() == true}"
            )

            Log.d(
                "AppUpdate",
                "current=${BuildConfig.VERSION_CODE}/${BuildConfig.VERSION_NAME} " +
                    "remote=$remoteCode/$remoteName " +
                    "updateAvailable=$updateAvailable"
            )

            if (updateAvailable) {
                val downloadUrl = latest.downloadUrl
                if (downloadUrl.isNullOrBlank()) {
                    Log.d("AppUpdate", "update found but download_url is empty")
                } else if (!downloadUrl.endsWith(".apk", ignoreCase = true)) {
                    Log.d("AppUpdate", "APK URL does not end with .apk: $downloadUrl")
                }
                latest
            } else {
                Log.d(
                    "AppUpdate",
                    "no update (currentCode=${BuildConfig.VERSION_CODE}, " +
                        "remoteCode=$remoteCode, " +
                        "currentName=${BuildConfig.VERSION_NAME}, " +
                        "remoteName=$remoteName)"
                )
                null
            }
        } catch (e: Exception) {
            Log.w("AppUpdate", "failed to check update: ${e.message}")
            null
        }
    }
}
