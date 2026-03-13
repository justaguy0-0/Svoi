package com.example.svoi.ui.theme

import androidx.compose.ui.graphics.Color

// Primary brand color — clean messenger blue
val Blue500 = Color(0xFF1E88E5)
val Blue600 = Color(0xFF1976D2)
val Blue700 = Color(0xFF1565C0)
val Blue200 = Color(0xFF90CAF9)
val Blue100 = Color(0xFFBBDEFB)

// ── Light theme ──────────────────────────────────────────────────────────────

val Background = Color(0xFFF5F7FA)
val Surface = Color(0xFFFFFFFF)
val SurfaceVariant = Color(0xFFEEF2F6)

val BubbleOwn = Color(0xFF1E88E5)          // outgoing — blue
val BubbleOther = Color(0xFFFFFFFF)        // incoming — white
val BubbleOwnText = Color(0xFFFFFFFF)
val BubbleOtherText = Color(0xFF1A1A2E)

val Divider = Color(0xFFE8ECF0)
val Unread = Color(0xFF1E88E5)

val TextPrimary = Color(0xFF1A1A2E)
val TextSecondary = Color(0xFF6B7280)
val TextHint = Color(0xFF9CA3AF)

// ── Dark theme ───────────────────────────────────────────────────────────────

val DarkBackground = Color(0xFF111111)
val DarkSurface = Color(0xFF1C1C1C)
val DarkSurfaceVariant = Color(0xFF282828)

val DarkBubbleOther = Color(0xFF262626)     // incoming bubble — dark
val DarkBubbleOtherText = Color(0xFFE1E1E6)

val DarkDivider = Color(0xFF2E2E2E)
val DarkTextPrimary = Color(0xFFE1E1E6)
val DarkTextSecondary = Color(0xFF9B9B9B)

// ── Shared ───────────────────────────────────────────────────────────────────

val Online = Color(0xFF4CAF50)
val Error = Color(0xFFE53935)

// Avatar palette — for user picks
val AvatarColors = listOf(
    "#EF5350", "#EC407A", "#AB47BC", "#7E57C2", "#5C6BC0", "#1E88E5",
    "#26C6DA", "#26A69A", "#66BB6A", "#FFA726", "#FF7043", "#8D6E63",
    "#4CAF50", "#CDDC39", "#FFC107", "#FF5722", "#795548", "#607D8B"
)
