package com.example.svoi.ui.chat

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.content.res.Configuration
import android.graphics.Color
import androidx.core.graphics.Insets
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.canhub.cropper.CropImage
import com.example.svoi.R
import com.example.svoi.SvoiApp
import com.example.svoi.data.local.ThemeMode
import com.canhub.cropper.CropImageActivity
import com.canhub.cropper.CropImageView
import com.canhub.cropper.parcelable
import kotlin.math.max

class PhotoCropActivity : CropImageActivity() {

    private var cropBackgroundColor: Int = Color.TRANSPARENT
    private var isDarkTheme: Boolean = false

    private val safeMarginPx by lazy {
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            24f,
            resources.displayMetrics
        ).toInt()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        isDarkTheme = resolveIsDarkTheme()
        cropBackgroundColor = ContextCompat.getColor(
            this,
            if (isDarkTheme) R.color.background_dark else R.color.background_light
        )
        Log.d("PhotoCrop", "onCreate savedInstanceState=${savedInstanceState != null}")
        Log.d("PhotoCrop", "effectiveTheme dark=$isDarkTheme")
        Log.d("PhotoCrop", "inputUri present=${inputUriFromIntent() != null}")
        WindowCompat.setDecorFitsSystemWindows(window, true)
        super.onCreate(savedInstanceState)
        updateSystemBars()
        Log.d("PhotoCrop", "image load requested")
        Log.d("PhotoCrop", "backgroundColor=${String.format("#%08X", cropBackgroundColor)}")
    }

    override fun setCropImageView(cropImageView: CropImageView) {
        super.setCropImageView(cropImageView)
        val root = findViewById<FrameLayout>(android.R.id.content)
        val cropContainer = FrameLayout(this).apply {
            clipToPadding = true
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(cropBackgroundColor)
        }

        (cropImageView.parent as? ViewGroup)?.removeView(cropImageView)
        cropImageView.setPadding(0, 0, 0, 0)
        cropImageView.clipToPadding = false
        cropImageView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )

        root.addView(cropContainer)
        root.setBackgroundColor(cropBackgroundColor)
        cropContainer.addView(cropImageView)
        logViewSize("root", root)
        logViewSize("cropContainer", cropContainer)
        logViewSize("cropImageView", cropImageView)

        ViewCompat.setOnApplyWindowInsetsListener(cropContainer) { view, insets ->
            val safeInsets = maxInsets(
                insets.getInsets(WindowInsetsCompat.Type.systemBars()),
                insets.getInsets(WindowInsetsCompat.Type.displayCutout()),
                insets.getInsets(WindowInsetsCompat.Type.systemGestures())
            )
            Log.d(
                "PhotoCrop",
                "safeInsets left=${safeInsets.left} top=${safeInsets.top} right=${safeInsets.right} bottom=${safeInsets.bottom}"
            )
            view.setPadding(
                safeInsets.left + safeMarginPx,
                safeInsets.top + safeMarginPx,
                safeInsets.right + safeMarginPx,
                safeInsets.bottom + safeMarginPx
            )
            insets
        }
        ViewCompat.requestApplyInsets(cropContainer)
    }

    private fun updateSystemBars() {
        window.statusBarColor = cropBackgroundColor
        window.navigationBarColor = cropBackgroundColor
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = !isDarkTheme
        controller.isAppearanceLightNavigationBars = !isDarkTheme
        Log.d("PhotoCrop", "systemBars updated")
    }

    private fun resolveIsDarkTheme(): Boolean =
        when ((application as? SvoiApp)?.themeManager?.getThemeMode()) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM, null -> {
                val nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                nightMode == Configuration.UI_MODE_NIGHT_YES
            }
        }

    private fun inputUriFromIntent(): Uri? {
        val bundle = intent.getBundleExtra(CropImage.CROP_IMAGE_EXTRA_BUNDLE)
        return bundle?.parcelable(CropImage.CROP_IMAGE_EXTRA_SOURCE)
    }

    override fun onSetImageUriComplete(view: CropImageView, uri: Uri, error: Exception?) {
        super.onSetImageUriComplete(view, uri, error)
        if (error == null) {
            val imageRect = view.wholeImageRect
            Log.d("PhotoCrop", "opened imageSize=${imageRect?.width()}x${imageRect?.height()}")
        } else {
            Log.w("PhotoCrop", "failed error=${error::class.simpleName}: ${error.message}")
        }
    }

    override fun onCropImageComplete(view: CropImageView, result: CropImageView.CropResult) {
        if (result.error == null) {
            Log.d("PhotoCrop", "crop confirmed rect=${result.cropRect}")
            result.uriContent?.let { uri ->
                readImageSize(uri)?.let { (width, height) ->
                    Log.d("PhotoCrop", "output created size=${width}x$height")
                }
            }
        } else {
            val error = result.error
            Log.w("PhotoCrop", "failed error=${error?.let { it::class.simpleName }}: ${error?.message}")
        }
        super.onCropImageComplete(view, result)
    }

    override fun setResultCancel() {
        Log.d("PhotoCrop", "crop cancelled")
        super.setResultCancel()
    }

    private fun readImageSize(uri: Uri): Pair<Int, Int>? = try {
        val input = contentResolver.openInputStream(uri) ?: return null
        input.use {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(it, null, options)
            val width = options.outWidth
            val height = options.outHeight
            if (width > 0 && height > 0) {
                width to height
            } else {
                null
            }
        }
    } catch (_: Exception) {
        null
    }

    private fun logViewSize(label: String, view: View) {
        view.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
            override fun onLayoutChange(
                changedView: View,
                left: Int,
                top: Int,
                right: Int,
                bottom: Int,
                oldLeft: Int,
                oldTop: Int,
                oldRight: Int,
                oldBottom: Int
            ) {
                val width = right - left
                val height = bottom - top
                if (width > 0 && height > 0) {
                    Log.d("PhotoCrop", "$label size=${width}x$height")
                    changedView.removeOnLayoutChangeListener(this)
                }
            }
        })
    }

    private fun maxInsets(vararg insets: Insets): Insets {
        var left = 0
        var top = 0
        var right = 0
        var bottom = 0
        insets.forEach {
            left = max(left, it.left)
            top = max(top, it.top)
            right = max(right, it.right)
            bottom = max(bottom, it.bottom)
        }
        return Insets.of(left, top, right, bottom)
    }
}
