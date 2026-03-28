package com.example.svoi.ui.theme

import androidx.compose.ui.graphics.Color
import com.example.svoi.data.local.SvoiAccent

// ── Accent palettes ───────────────────────────────────────────────────────────
// Each accent has: primary, dark (for dark-mode primary), container (light tint), onContainer

data class AccentPalette(
    val primary: Color,
    val primaryDark: Color,    // slightly lighter for dark mode readability
    val container: Color,      // very light tint
    val onContainer: Color     // dark text on container
)

val AccentPalettes: Map<SvoiAccent, AccentPalette> = mapOf(
    SvoiAccent.BLUE to AccentPalette(
        primary       = Color(0xFF1565C0),   // Material Blue 800 — тёмно-синий
        primaryDark   = Color(0xFF5C9CE5),
        container     = Color(0xFFBBDEFB),
        onContainer   = Color(0xFF0D47A1)
    ),
    SvoiAccent.ORANGE to AccentPalette(
        primary       = Color(0xFFBF5517),   // тёмный жжёный оранжевый
        primaryDark   = Color(0xFFFF9A5C),
        container     = Color(0xFFFFE0CC),
        onContainer   = Color(0xFF7A2E00)
    ),
    SvoiAccent.RED to AccentPalette(
        primary       = Color(0xFFA52828),   // тёмный кирпичный красный
        primaryDark   = Color(0xFFE57373),
        container     = Color(0xFFFFCDD2),
        onContainer   = Color(0xFF6A0F0F)
    ),
    SvoiAccent.GREEN to AccentPalette(
        primary       = Color(0xFF276B3A),   // тёмный лесной зелёный
        primaryDark   = Color(0xFF66BB6A),
        container     = Color(0xFFC8E6C9),
        onContainer   = Color(0xFF14381D)
    ),
    SvoiAccent.PINK to AccentPalette(
        primary       = Color(0xFF9C3060),   // тёмный малиново-розовый
        primaryDark   = Color(0xFFE879A0),
        container     = Color(0xFFFCE4EC),
        onContainer   = Color(0xFF65103C)
    ),
    SvoiAccent.PURPLE to AccentPalette(
        primary       = Color(0xFF5E35B1),   // Material Deep Purple 600
        primaryDark   = Color(0xFF9B7FD4),
        container     = Color(0xFFEDE7F6),
        onContainer   = Color(0xFF311B92)
    )
)

fun accentPalette(accent: SvoiAccent) = AccentPalettes[accent] ?: AccentPalettes[SvoiAccent.BLUE]!!

// ── Legacy named constants (kept for non-themed uses) ─────────────────────────

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

// Avatar palette — for user picks (Material 300–600, bright)
val AvatarColors = listOf(
    "#EF5350", "#EC407A", "#AB47BC", "#7E57C2", "#5C6BC0", "#1E88E5",
    "#26C6DA", "#26A69A", "#66BB6A", "#FFA726", "#FF7043", "#8D6E63",
    "#4CAF50", "#CDDC39", "#FFC107", "#FF5722", "#795548", "#607D8B"
)

// Group avatar palette — darker Material 700–900 shades, visually distinct from user avatars
val GroupAvatarColors = listOf(
    "#1565C0", // Blue 800
    "#283593", // Indigo 800
    "#6A1B9A", // Purple 800
    "#AD1457", // Pink 800
    "#C62828", // Red 800
    "#D84315", // Deep Orange 800
    "#2E7D32", // Green 800
    "#00695C", // Teal 800
    "#00838F", // Cyan 800
    "#4E342E", // Brown 800
    "#37474F", // Blue Grey 800
    "#4527A0", // Deep Purple 800
)

/** Deterministic group avatar color derived from chatId hash. */
fun groupAvatarColor(chatId: String): String =
    GroupAvatarColors[Math.abs(chatId.hashCode()) % GroupAvatarColors.size]
