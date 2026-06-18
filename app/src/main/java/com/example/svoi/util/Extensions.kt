package com.example.svoi.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val shortDateFormatter = DateTimeFormatter.ofPattern("d MMM", Locale("ru"))
private val numericDateFormatter = DateTimeFormatter.ofPattern("dd.MM.yy")
private val fullMonthFormatter = DateTimeFormatter.ofPattern("d MMMM", Locale("ru"))
private val fullDateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ru"))
private val shortMonthYearFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale("ru"))

/** Formats an ISO-8601 timestamp string to "HH:mm" for display in messages */
fun String.toMessageTime(): String = runCatching {
    val instant = Instant.parse(this)
    instant.atZone(ZoneId.systemDefault()).format(timeFormatter)
}.getOrDefault("")

/** Formats an ISO-8601 timestamp string to "12 мар", "Вчера" or "HH:mm" for chat list */
fun String.toChatListTime(): String = runCatching {
    val instant = Instant.parse(this)
    val zoned = instant.atZone(ZoneId.systemDefault())
    val messageDate = zoned.toLocalDate()
    val today = LocalDate.now()
    when {
        messageDate == today -> zoned.format(timeFormatter)
        messageDate == today.minusDays(1) -> "Вчера"
        messageDate.year == today.year -> zoned.format(shortDateFormatter)
        else -> zoned.format(numericDateFormatter)
    }
}.getOrDefault("")

/** Formats an ISO-8601 timestamp string to "12 марта" for date separators */
fun String.toDateSeparator(): String = runCatching {
    val instant = Instant.parse(this)
    val zoned = instant.atZone(ZoneId.systemDefault())
    val messageDate = zoned.toLocalDate()
    val today = LocalDate.now()
    when {
        messageDate == today -> "Сегодня"
        messageDate == today.minusDays(1) -> "Вчера"
        messageDate.year == today.year -> zoned.format(fullMonthFormatter)
        else -> zoned.format(fullDateFormatter)
    }
}.getOrDefault("")

/** Formats an ISO-8601 timestamp string to "был(а) в HH:mm", "был(а) вчера в HH:mm", etc. */
fun String.toLastSeen(): String = runCatching {
    val instant = Instant.parse(this)
    val zoned = instant.atZone(ZoneId.systemDefault())
    val timeOnly = zoned.format(timeFormatter)
    val messageDate = zoned.toLocalDate()
    val today = LocalDate.now()
    when {
        messageDate == today -> "был(а) в $timeOnly"
        messageDate == today.minusDays(1) -> "был(а) вчера в $timeOnly"
        messageDate.year == today.year ->
            "был(а) ${zoned.format(shortDateFormatter)} в $timeOnly"
        else ->
            "был(а) ${zoned.format(shortMonthYearFormatter)}"
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
    instant.atZone(ZoneId.systemDefault()).format(fullDateFormatter)
}.getOrDefault("")

/** Extracts date part from ISO-8601 string for grouping messages by day */
fun String.toDateKey(): String = runCatching {
    Instant.parse(this).atZone(ZoneId.systemDefault()).toLocalDate().toString()
}.getOrDefault(this)

fun String.isSameDay(other: String): Boolean = toDateKey() == other.toDateKey()
