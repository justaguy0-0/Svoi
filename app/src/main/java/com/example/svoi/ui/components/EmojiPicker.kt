package com.example.svoi.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val EMOJI_CATEGORIES = listOf(
    "😊" to listOf(
        "😊","😂","🤣","😍","🥰","😎","😅","😁","🥳","🤩",
        "😴","🥺","😭","😤","😡","😱","🤔","😇","😘","🥲",
        "😏","😒","🙄","🤗","🤫","😬","🤯","😵","🫡","🫶"
    ),
    "🐶" to listOf(
        "🐶","🐱","🐭","🐰","🦊","🐻","🐼","🐨","🐯","🦁",
        "🐮","🐷","🐸","🐙","🦋","🦄","🐳","🦈","🦅","🐬",
        "🐝","🦜","🐲","🦎","🐺","🐔","🦆","🐧","🦚","🦋"
    ),
    "🍕" to listOf(
        "🍕","🍔","🌮","🍜","🍣","🍎","🍊","🍋","🍇","🍓",
        "🥑","🥕","🍩","🎂","🍫","🍿","☕","🍺","🍸","🧃",
        "🍰","🍦","🥗","🍱","🥐","🍞","🧀","🍳","🥞","🧁"
    ),
    "⚽" to listOf(
        "⚽","🏀","🎮","🎯","🎲","🎸","🎵","🎬","📸","🚗",
        "✈️","🚀","🎡","🏋️","🤸","🏊","🚴","🎿","🎭","🎨",
        "🎪","🏆","🎖️","🥊","🏄","🧗","🤺","🎳","🎻","🎹"
    ),
    "🌸" to listOf(
        "🌸","🌿","🍀","🌈","☀️","🌙","⭐","🌊","🔥","❄️",
        "🌺","🌻","🍂","🌲","🌵","🌴","☁️","⚡","💧","🌏",
        "🌋","🏔️","🌅","🌄","🌃","🏝️","🌾","🍁","🌷","🌼"
    ),
    "❤️" to listOf(
        "❤️","🧡","💛","💚","💙","💜","🖤","🤍","💔","💯",
        "✨","🎉","🏆","💪","👍","👎","👏","🙏","🤝","💋",
        "👋","🫂","💎","🎁","🔑","💌","🎊","🥂","🌟","🍀"
    )
)

@Composable
fun EmojiPicker(
    onEmojiSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember { mutableIntStateOf(0) }

    Column(modifier = modifier.fillMaxWidth()) {
        // Category tabs
        ScrollableTabRow(
            selectedTabIndex = selectedCategory,
            edgePadding = 8.dp,
            containerColor = MaterialTheme.colorScheme.surface,
            divider = {}
        ) {
            EMOJI_CATEGORIES.forEachIndexed { index, (icon, _) ->
                Tab(
                    selected = selectedCategory == index,
                    onClick = { selectedCategory = index },
                    modifier = Modifier.size(44.dp)
                ) {
                    Text(text = icon, fontSize = 20.sp)
                }
            }
        }
        HorizontalDivider()
        // Emoji grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(8),
            modifier = Modifier.height(200.dp),
            contentPadding = PaddingValues(4.dp)
        ) {
            items(EMOJI_CATEGORIES[selectedCategory].second) { emoji ->
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clickable { onEmojiSelected(emoji) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = emoji, fontSize = 22.sp, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
