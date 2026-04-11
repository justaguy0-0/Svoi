package com.example.svoi.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.GetContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.svoi.R
import com.example.svoi.SvoiApp
import com.example.svoi.data.local.ChatWallpaper
import kotlinx.coroutines.launch
import java.io.File

private val PRESET_LABELS = listOf("Фон 1", "Фон 2", "Фон 3", "Фон 4", "Фон 5")

private fun presetResId(id: Int): Int = when (id) {
    1 -> R.drawable.wallpaper_preset_1
    2 -> R.drawable.wallpaper_preset_2
    3 -> R.drawable.wallpaper_preset_3
    4 -> R.drawable.wallpaper_preset_4
    5 -> R.drawable.wallpaper_preset_5
    else -> R.drawable.wallpaper_preset_1
}

// ── Shared composable: renders the wallpaper as a full-size background ────────

@Composable
internal fun ChatWallpaperBackground(
    wallpaper: ChatWallpaper,
    dim: Float = 0f,
    modifier: Modifier = Modifier.fillMaxSize()
) {
    when (wallpaper) {
        is ChatWallpaper.None -> Unit
        is ChatWallpaper.Preset -> Box(modifier = modifier) {
            AsyncImage(
                model = presetResId(wallpaper.id),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            if (dim > 0f) Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
        }
        is ChatWallpaper.Custom -> Box(modifier = modifier) {
            AsyncImage(
                model = File(wallpaper.path),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            if (dim > 0f) Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
        }
    }
}

// ── Picker screen ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperPickerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val wallpaperManager = (context.applicationContext as SvoiApp).wallpaperManager
    val current by wallpaperManager.wallpaper.collectAsState()
    val dim by wallpaperManager.dim.collectAsState()
    val scope = rememberCoroutineScope()

    val galleryLauncher = rememberLauncherForActivityResult(GetContent()) { uri ->
        uri?.let { scope.launch { wallpaperManager.setCustom(it) } }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Фон чата", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // ── Chat preview (fills remaining space) ──────────────────────────
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (current is ChatWallpaper.None) {
                    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                } else {
                    ChatWallpaperBackground(current, dim = dim)
                }
                MockChatPreview(hasWallpaper = current !is ChatWallpaper.None)
            }

            // ── Wallpaper options panel ────────────────────────────────────────
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 0.dp
            ) {
                Column(modifier = Modifier.padding(top = 16.dp, bottom = 20.dp)) {
                    Text(
                        text = "Выберите фон",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp)
                    )

                    if (current !is ChatWallpaper.None) {
                        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Затемнение фона",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${(dim * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Slider(
                                value = dim,
                                onValueChange = { wallpaperManager.setDim(it) },
                                valueRange = 0f..0.75f,
                                modifier = Modifier.fillMaxWidth(),
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // ── No wallpaper ──────────────────────────────────────
                        item(key = "none") {
                            WallpaperOption(
                                label = "Нет",
                                isSelected = current is ChatWallpaper.None,
                                onClick = { wallpaperManager.setNone() }
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize()
                                        .background(MaterialTheme.colorScheme.surface),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "—",
                                        fontSize = 24.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    )
                                }
                            }
                        }

                        // ── 5 presets ─────────────────────────────────────────
                        items((1..5).toList(), key = { "preset_$it" }) { id ->
                            WallpaperOption(
                                label = PRESET_LABELS.getOrElse(id - 1) { "Фон $id" },
                                isSelected = current is ChatWallpaper.Preset &&
                                    (current as ChatWallpaper.Preset).id == id,
                                onClick = { wallpaperManager.setPreset(id) }
                            ) {
                                AsyncImage(
                                    model = presetResId(id),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }

                        // ── Custom (gallery) ──────────────────────────────────
                        item(key = "custom") {
                            val isCustom = current is ChatWallpaper.Custom
                            WallpaperOption(
                                label = if (isCustom) "Моё фото" else "Галерея",
                                isSelected = isCustom,
                                onClick = { galleryLauncher.launch("image/*") }
                            ) {
                                if (isCustom) {
                                    AsyncImage(
                                        model = File((current as ChatWallpaper.Custom).path),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxSize()
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AddPhotoAlternate,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Thumbnail item ────────────────────────────────────────────────────────────

@Composable
private fun WallpaperOption(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .width(60.dp)
                .aspectRatio(9f / 16f)
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onClick)
                .then(
                    if (isSelected)
                        Modifier.border(
                            BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary),
                            RoundedCornerShape(10.dp)
                        )
                    else Modifier
                )
        ) {
            content()

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .size(18.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(5.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── Mock chat preview ─────────────────────────────────────────────────────────

@Composable
private fun MockChatPreview(hasWallpaper: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.Bottom
    ) {
        MockBubble("Привет! Как дела?", isOutgoing = false, hasWallpaper = hasWallpaper)
        Spacer(Modifier.height(4.dp))
        MockBubble("Всё отлично, спасибо 😊", isOutgoing = true, hasWallpaper = hasWallpaper)
        Spacer(Modifier.height(4.dp))
        MockBubble("Классно! Ты сегодня свободен?", isOutgoing = false, hasWallpaper = hasWallpaper)
        Spacer(Modifier.height(4.dp))
        MockBubble("Да, давай встретимся!", isOutgoing = true, hasWallpaper = hasWallpaper)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun MockBubble(text: String, isOutgoing: Boolean, hasWallpaper: Boolean) {
    val bubbleColor = if (isOutgoing)
        MaterialTheme.colorScheme.primaryContainer
    else if (hasWallpaper)
        MaterialTheme.colorScheme.surface
    else
        MaterialTheme.colorScheme.surfaceVariant

    val shape = if (isOutgoing)
        RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
    else
        RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOutgoing) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = shape,
            color = bubbleColor,
            shadowElevation = if (hasWallpaper) 1.dp else 0.dp,
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = if (isOutgoing) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}
