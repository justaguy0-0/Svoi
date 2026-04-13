package com.example.svoi.data.repository

import android.util.Log
import com.example.svoi.data.model.AppVersion
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order

class AppUpdateRepository(private val supabase: SupabaseClient) {

    companion object {
        // Текущая версия приложения — обновлять вручную при каждом релизе
        const val CURRENT_VERSION_CODE = 7
    }

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

            if (latest != null && latest.versionCode > CURRENT_VERSION_CODE) {
                Log.d("AppUpdate", "Update available: ${latest.versionName} (code ${latest.versionCode})")
                latest
            } else {
                Log.d("AppUpdate", "App is up to date (current=$CURRENT_VERSION_CODE)")
                null
            }
        } catch (e: Exception) {
            Log.w("AppUpdate", "Update check failed: ${e.message}")
            null
        }
    }
}
