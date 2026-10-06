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

private val dateFormat = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault())

/** `Monday, 6 October` */
fun formatDate(epochMs: Long): String = dateFormat.format(Date(epochMs))

/** `0:03` / `1:20` / `1:02:03` — the length of one play of a sound. Empty when unknown. */
fun formatClip(ms: Long): String {
    if (ms <= 0L) return ""
    val total = (ms + 500L) / 1000L
    val h = total / 3600
    val m = (total / 60) % 60
    val s = total % 60
    return if (h > 0) {
        String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.US, "%d:%02d", m, s)
    }
}

/**
 * `2.3 s` / `21 s` / `1:20` — one play of a sound, precise enough that "10 × 2.3 s ≈ 23 s" adds
 * up. (`formatClip` rounds 2.3 s to `0:02`, and "10 × 0:02 — about 23 s" reads as bad maths.)
 */
fun formatClipShort(ms: Long): String {
    if (ms <= 0L) return ""
    return when {
        ms < 10_000L -> String.format(Locale.US, "%.1f s", ms / 1000.0)
        ms < 60_000L -> "${(ms + 500L) / 1000L} s"
        else -> formatClip(ms)
    }
}

/** `about 30 s` / `about 3 min` / `about 1 h 5 min` — a rough total, e.g. 10 plays × 3 s. */
fun formatAbout(ms: Long): String {
    if (ms <= 0L) return ""
    val s = (ms + 500L) / 1000L
    return when {
        s < 60 -> "about $s s"
        s < 3600 -> "about ${(s + 30) / 60} min"
        else -> {
            val m = (s + 30) / 60
            if (m % 60 == 0L) "about ${m / 60} h" else "about ${m / 60} h ${m % 60} min"
        }
    }
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
