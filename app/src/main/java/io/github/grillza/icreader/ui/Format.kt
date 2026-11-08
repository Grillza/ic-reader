package io.github.grillza.icreader.ui

import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

private val yenFormat: NumberFormat = NumberFormat.getIntegerInstance(Locale.JAPAN)

fun formatYen(amount: Int): String = "¥" + yenFormat.format(amount)

/** "+¥500" / "−¥210" (true minus sign). */
fun formatSigned(amount: Int): String = when {
    amount > 0 -> "+" + formatYen(amount)
    amount < 0 -> "−" + formatYen(-amount)
    else -> formatYen(0)
}

fun formatTime(secondOfDay: Int): String = "%d:%02d".format(secondOfDay / 3600, secondOfDay / 60 % 60)

val DAY_HEADER: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH)
val LONG_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)

fun relativeTime(epochMs: Long, now: Long = System.currentTimeMillis()): String {
    val mins = (now - epochMs) / 60_000
    return when {
        mins < 1 -> "just now"
        mins < 60 -> "${mins}m ago"
        mins < 60 * 24 -> "${mins / 60}h ago"
        mins < 60 * 24 * 30 -> "${mins / (60 * 24)}d ago"
        else -> "${mins / (60 * 24 * 30)}mo ago"
    }
}
