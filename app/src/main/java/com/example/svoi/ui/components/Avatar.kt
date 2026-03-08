package com.example.svoi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Avatar for personal chats — shows an emoji on a colored circle.
 * Avatar for group chats — shows the first letter of the group name on a colored circle.
 *
 * [isGroup] — if true, shows [letter]; if false, shows [emoji]
 * [bgColor] — hex color string like "#5C6BC0"
 */
@Composable
fun Avatar(
    emoji: String,
    bgColor: String,
    isGroup: Boolean = false,
    letter: String = "",
    size: Dp = 50.dp,
    fontSize: TextUnit = 22.sp,
    modifier: Modifier = Modifier
) {
    val color = runCatching {
        Color(android.graphics.Color.parseColor(bgColor))
    }.getOrDefault(Color(0xFF5C6BC0))

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        if (isGroup) {
            Text(
                text = letter.take(1).uppercase(),
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        } else {
            Text(
                text = emoji,
                fontSize = fontSize
            )
        }
    }
}
