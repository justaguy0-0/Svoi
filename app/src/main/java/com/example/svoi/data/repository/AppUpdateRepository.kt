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
                    filter {
                        eq("is_active", true)
                    }
                    order("version_code", Order.DESCENDING)
                    limit(1)
                }
                .decodeSingleOrNull<AppVersion>()

            val remoteCode = latest?.versionCode
            val remoteName = latest?.versionName
            val hasDownloadUrl = latest?.resolvedDownloadUrl?.isNotBlank() == true
            val updateAvailable = remoteCode != null &&
                remoteCode > BuildConfig.VERSION_CODE &&
                latest.isActive &&
                hasDownloadUrl

            Log.d(
                "AppUpdate",
                "current=${BuildConfig.VERSION_CODE}/${BuildConfig.VERSION_NAME} " +
                    "remote=${remoteCode ?: "null"}/${remoteName ?: "null"} " +
                    "updateAvailable=$updateAvailable"
            )

            if (latest != null && updateAvailable) {
                val downloadUrl = latest.resolvedDownloadUrl
                if (!downloadUrl.endsWith(".apk", ignoreCase = true)) {
                    Log.d("AppUpdate", "APK URL does not end with .apk: $downloadUrl")
                }
                latest
            } else {
                Log.d(
                    "AppUpdate",
                    "no update (currentCode=${BuildConfig.VERSION_CODE}, " +
                        "remoteCode=${remoteCode ?: "null"}, " +
                        "currentName=${BuildConfig.VERSION_NAME}, " +
                        "remoteName=${remoteName ?: "null"})"
                )
                null
            }
        } catch (e: Exception) {
            Log.w("AppUpdate", "Update check failed: ${e.message}")
            null
        }
    }
}
