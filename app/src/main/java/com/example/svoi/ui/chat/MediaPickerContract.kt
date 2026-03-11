package com.example.svoi.ui.chat

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract

/** Пикер для нескольких медиафайлов (фото + видео) через ACTION_GET_CONTENT + EXTRA_MIME_TYPES */
class GetMultipleMedia : ActivityResultContract<Unit, List<Uri>>() {
    override fun createIntent(context: Context, input: Unit) =
        Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }

    override fun parseResult(resultCode: Int, intent: Intent?): List<Uri> {
        if (resultCode != Activity.RESULT_OK || intent == null) return emptyList()
        val clip = intent.clipData
        if (clip != null) return (0 until clip.itemCount).map { clip.getItemAt(it).uri }
        return listOfNotNull(intent.data)
    }
}
