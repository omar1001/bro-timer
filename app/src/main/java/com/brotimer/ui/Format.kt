package com.brotimer.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * All display formatting, in one place.
 *
 * Clock times are 24-hour on purpose: this is a tool, and "13:05" cannot be misread the way
 * "1:05" can when you are half asleep.
 */

private val clockFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
private val clockWithSecondsFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

fun formatClock(epochMs: Long): String = clockFormat.format(Date(epochMs))

fun formatClockWithSeconds(epochMs: Long): String = clockWithSecondsFormat.format(Date(epochMs))

/** `1:02:03.45` / `02:03.45` — centiseconds, because a stopwatch that does not move looks broken. */
fun formatStopwatch(ms: Long): String {
    val safe = ms.coerceAtLeast(0L)
    val hours = safe / 3_600_000L
    val minutes = (safe / 60_000L) % 60
    val seconds = (safe / 1_000L) % 60
    val centis = (safe % 1_000L) / 10
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d.%02d", hours, minutes, seconds, centis)
    } else {
        String.format(Locale.US, "%02d:%02d.%02d", minutes, seconds, centis)
    }
}

/** `1:02:03` / `02:03` — whole seconds, rounded up so a countdown never shows 0 while running. */
fun formatCountdown(ms: Long): String {
    val safe = ms.coerceAtLeast(0L)
    val totalSeconds = (safe + 999L) / 1000L
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds / 60) % 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

/** `2 h 30 min` / `45 min` / `3 h` */
fun formatInterval(hours: Int, minutes: Int): String = when {
    hours > 0 && minutes > 0 -> "$hours h $minutes min"
    hours > 0 -> "$hours h"
    minutes > 0 -> "$minutes min"
    else -> "not set"
}

/** `in 1 h 12 min` / `in 45 min` / `in 20 s` — how long until something happens. */
fun formatUntil(deltaMs: Long): String {
    if (deltaMs <= 0L) return "now"
    val totalMinutes = deltaMs / 60_000L
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "in $hours h $minutes min"
        hours > 0 -> "in $hours h"
        totalMinutes > 0 -> "in $totalMinutes min"
        else -> "in ${deltaMs / 1000L} s"
    }
}
