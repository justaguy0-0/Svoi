package com.example.svoi.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Formats an ISO-8601 timestamp string to "HH:mm" for display in messages */
fun String.toMessageTime(): String = runCatching {
    val instant = Instant.parse(this)
    val formatter = DateTimeFormatter.ofPattern("HH:mm")
        .withZone(ZoneId.systemDefault())
    formatter.format(instant)
}.getOrDefault("")

/** Formats an ISO-8601 timestamp string to "12 мар", "Вчера" or "HH:mm" for chat list */
fun String.toChatListTime(): String = runCatching {
    val instant = Instant.parse(this)
    val messageDate = instant.atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    when {
        messageDate == today -> {
            val formatter = DateTimeFormatter.ofPattern("HH:mm")
                .withZone(ZoneId.systemDefault())
            formatter.format(instant)
        }
        messageDate == today.minusDays(1) -> "Вчера"
        messageDate.year == today.year -> {
            val formatter = DateTimeFormatter.ofPattern("d MMM", Locale("ru"))
                .withZone(ZoneId.systemDefault())
            formatter.format(instant)
        }
        else -> {
            val formatter = DateTimeFormatter.ofPattern("dd.MM.yy")
                .withZone(ZoneId.systemDefault())
            formatter.format(instant)
        }
    }
}.getOrDefault("")

/** Formats an ISO-8601 timestamp string to "12 марта" for date separators */
fun String.toDateSeparator(): String = runCatching {
    val instant = Instant.parse(this)
    val messageDate = instant.atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    when {
        messageDate == today -> "Сегодня"
        messageDate == today.minusDays(1) -> "Вчера"
        messageDate.year == today.year -> {
            val formatter = DateTimeFormatter.ofPattern("d MMMM", Locale("ru"))
                .withZone(ZoneId.systemDefault())
            formatter.format(instant)
        }
        else -> {
            val formatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ru"))
                .withZone(ZoneId.systemDefault())
            formatter.format(instant)
        }
    }
}.getOrDefault("")

/** Formats an ISO-8601 timestamp string to "был(а) в HH:mm", "был(а) вчера в HH:mm", etc. */
fun String.toLastSeen(): String = runCatching {
    val instant = Instant.parse(this)
    val zone = ZoneId.systemDefault()
    val timeOnly = DateTimeFormatter.ofPattern("HH:mm").withZone(zone).format(instant)
    val messageDate = instant.atZone(zone).toLocalDate()
    val today = LocalDate.now()
    when {
        messageDate == today -> "был(а) в $timeOnly"
        messageDate == today.minusDays(1) -> "был(а) вчера в $timeOnly"
        messageDate.year == today.year ->
            "был(а) ${DateTimeFormatter.ofPattern("d MMM", Locale("ru")).withZone(zone).format(instant)} в $timeOnly"
        else ->
            "был(а) ${DateTimeFormatter.ofPattern("d MMM yyyy", Locale("ru")).withZone(zone).format(instant)}"
    }
}.getOrDefault("")

/** Formats file size in bytes to human-readable string */
fun Long.toReadableSize(): String = when {
    this < 1024 -> "$this Б"
    this < 1024 * 1024 -> "${this / 1024} КБ"
    this < 1024 * 1024 * 1024 -> "${this / (1024 * 1024)} МБ"
    else -> "${this / (1024 * 1024 * 1024)} ГБ"
}

/** Formats an ISO-8601 timestamp to "12 марта 2024" for registration date display */
fun String.toRegistrationDate(): String = runCatching {
    val instant = Instant.parse(this)
    val formatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ru"))
        .withZone(ZoneId.systemDefault())
    formatter.format(instant)
}.getOrDefault("")

/** Extracts date part from ISO-8601 string for grouping messages by day */
fun String.toDateKey(): String = runCatching {
    Instant.parse(this).atZone(ZoneId.systemDefault()).toLocalDate().toString()
}.getOrDefault(this)

fun String.isSameDay(other: String): Boolean = toDateKey() == other.toDateKey()
