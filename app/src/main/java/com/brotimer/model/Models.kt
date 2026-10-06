package com.brotimer.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * All four stored types plus their JSON codecs.
 *
 * Persistence is `SharedPreferences` + `org.json` on purpose: both are in the Android framework,
 * so BroTimer needs no Room / DataStore / kotlinx-serialization dependency and builds entirely
 * from the Gradle cache that is already on this machine.
 *
 * Every timestamp here is **wall clock** (`System.currentTimeMillis()`), never `elapsedRealtime`,
 * so a running stopwatch or timer survives a reboot.
 */

// ---------------------------------------------------------------------------------------------
// Interval alarms
// ---------------------------------------------------------------------------------------------

data class IntervalAlarm(
    val id: Int,
    val label: String,
    val hours: Int,
    val minutes: Int,
    val enabled: Boolean,
    /** Wall-clock ms the repeating grid is measured from. Reset whenever the alarm is switched on. */
    val anchorAt: Long,
    /** null = the phone's default alarm ringtone. See [com.brotimer.data.SoundLibrary]. */
    val soundUri: String? = null,
    /** How many times the sound plays per ring. [KEEP_RINGING] = loop until stopped or given up. */
    val repeatCount: Int = KEEP_RINGING,
) {
    val intervalMs: Long get() = (hours * 60L + minutes) * 60_000L
}

/**
 * `repeatCount` value meaning "loop the sound until Stop, or until [Settings.ringSeconds]".
 * It is the default so that alarms created before repeat counts existed keep ringing exactly as
 * they always did.
 */
const val KEEP_RINGING = 0

/**
 * The fixed grid: slots are `anchorAt + n * interval`, so dismissing an alarm late never shifts
 * the schedule. Slots missed while ringing, asleep or powered off are simply skipped, which is
 * what makes reboot recovery need no catch-up code.
 *
 * Returns 0 when the alarm has no usable interval.
 */
fun IntervalAlarm.nextFireAt(now: Long): Long {
    val step = intervalMs
    if (step <= 0L) return 0L
    if (now < anchorAt) return anchorAt
    val slotsElapsed = (now - anchorAt) / step
    return anchorAt + (slotsElapsed + 1) * step
}

private fun IntervalAlarm.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("label", label)
    .put("hours", hours)
    .put("minutes", minutes)
    .put("enabled", enabled)
    .put("anchorAt", anchorAt)
    .put("soundUri", soundUri ?: JSONObject.NULL)
    .put("repeatCount", repeatCount)

private fun intervalAlarmFrom(o: JSONObject) = IntervalAlarm(
    id = o.getInt("id"),
    label = o.optString("label", ""),
    hours = o.optInt("hours", 0),
    minutes = o.optInt("minutes", 0),
    enabled = o.optBoolean("enabled", false),
    anchorAt = o.optLong("anchorAt", 0L),
    soundUri = o.optStringOrNull("soundUri"),
    repeatCount = o.optInt("repeatCount", KEEP_RINGING),
)

// ---------------------------------------------------------------------------------------------
// Stopwatches
// ---------------------------------------------------------------------------------------------

data class StopwatchItem(
    val id: Int,
    val label: String,
    val running: Boolean,
    /** Wall-clock ms of the most recent start/resume. Meaningless while paused. */
    val startedAt: Long,
    /** Time banked by previous runs. */
    val accumulatedMs: Long,
)

/** No service is needed to keep a stopwatch honest — the elapsed time is derived, not ticked. */
fun StopwatchItem.elapsedMs(now: Long): Long =
    if (running) accumulatedMs + (now - startedAt).coerceAtLeast(0L) else accumulatedMs

private fun StopwatchItem.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("label", label)
    .put("running", running)
    .put("startedAt", startedAt)
    .put("accumulatedMs", accumulatedMs)

private fun stopwatchFrom(o: JSONObject) = StopwatchItem(
    id = o.getInt("id"),
    label = o.optString("label", ""),
    running = o.optBoolean("running", false),
    startedAt = o.optLong("startedAt", 0L),
    accumulatedMs = o.optLong("accumulatedMs", 0L),
)

// ---------------------------------------------------------------------------------------------
// Countdown timers
// ---------------------------------------------------------------------------------------------

data class TimerItem(
    val id: Int,
    val label: String,
    val durationMs: Long,
    val running: Boolean,
    /** Wall-clock ms this timer is due. Meaningless while paused. */
    val endsAt: Long,
    /** What is left on the clock while paused. */
    val remainingMs: Long,
    val soundUri: String? = null,
    /** Same meaning as [IntervalAlarm.repeatCount]. */
    val repeatCount: Int = KEEP_RINGING,
)

fun TimerItem.remainingAt(now: Long): Long =
    if (running) (endsAt - now).coerceAtLeast(0L) else remainingMs

private fun TimerItem.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("label", label)
    .put("durationMs", durationMs)
    .put("running", running)
    .put("endsAt", endsAt)
    .put("remainingMs", remainingMs)
    .put("soundUri", soundUri ?: JSONObject.NULL)
    .put("repeatCount", repeatCount)

private fun timerFrom(o: JSONObject) = TimerItem(
    id = o.getInt("id"),
    label = o.optString("label", ""),
    durationMs = o.optLong("durationMs", 0L),
    running = o.optBoolean("running", false),
    endsAt = o.optLong("endsAt", 0L),
    remainingMs = o.optLong("remainingMs", 0L),
    soundUri = o.optStringOrNull("soundUri"),
    repeatCount = o.optInt("repeatCount", KEEP_RINGING),
)

// ---------------------------------------------------------------------------------------------
// Settings
// ---------------------------------------------------------------------------------------------

data class Settings(
    val snoozeMinutes: Int = 10,
    /**
     * How long a [KEEP_RINGING] alarm rings before it gives up. Alarms set to play a fixed number
     * of times end when the count is reached instead.
     */
    val ringSeconds: Int = 300,
    /** Sleep length, stored in whole minutes so the 8.5 h default is exact. 8.5 h = 510. */
    val sleepMinutes: Int = 510,
    /** Wall-clock ms sleep mode ends. 0 = not sleeping. */
    val sleepUntil: Long = 0L,
    /**
     * An alarm that ends without Stop or Snooze being pressed comes back after this many minutes.
     * 0 = never come back. Omar asked for 5 (2026-10-06) — deliberately shorter than the snooze.
     */
    val comebackMinutes: Int = 5,
    /** ...at most this many times in a row before it gives up until its next scheduled ring. */
    val comebackTimes: Int = 3,
    /** 0 = follow the phone, 1 = always light, 2 = always dark. */
    val themeMode: Int = THEME_SYSTEM,
    /** Android 12+: take the app's colours from the wallpaper instead of the BroTimer palette. */
    val wallpaperColors: Boolean = false,
    /**
     * Vibrate while ringing. Omar asked for an off switch (2026-10-06, "it is annoying").
     * Ignored — the phone vibrates anyway — when the sound fails to play, so an alarm is never
     * completely silent.
     */
    val vibrate: Boolean = true,
) {
    fun isSleeping(now: Long): Boolean = sleepUntil > now
}

const val THEME_SYSTEM = 0
const val THEME_LIGHT = 1
const val THEME_DARK = 2

private fun Settings.toJson(): JSONObject = JSONObject()
    .put("snoozeMinutes", snoozeMinutes)
    .put("ringSeconds", ringSeconds)
    .put("sleepMinutes", sleepMinutes)
    .put("sleepUntil", sleepUntil)
    .put("comebackMinutes", comebackMinutes)
    .put("comebackTimes", comebackTimes)
    .put("themeMode", themeMode)
    .put("wallpaperColors", wallpaperColors)
    .put("vibrate", vibrate)

private fun settingsFrom(o: JSONObject) = Settings(
    snoozeMinutes = o.optInt("snoozeMinutes", 10),
    ringSeconds = o.optInt("ringSeconds", 300),
    sleepMinutes = o.optInt("sleepMinutes", 510),
    sleepUntil = o.optLong("sleepUntil", 0L),
    comebackMinutes = o.optInt("comebackMinutes", 5),
    comebackTimes = o.optInt("comebackTimes", 3),
    themeMode = o.optInt("themeMode", THEME_SYSTEM),
    wallpaperColors = o.optBoolean("wallpaperColors", false),
    vibrate = o.optBoolean("vibrate", true),
)

// ---------------------------------------------------------------------------------------------
// Codec entry points used by the store
// ---------------------------------------------------------------------------------------------

private fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key, "").ifEmpty { null }

private inline fun <T> encodeList(items: List<T>, toJson: (T) -> JSONObject): String {
    val arr = JSONArray()
    items.forEach { arr.put(toJson(it)) }
    return arr.toString()
}

/** Decoding never throws: a corrupt or half-written entry is dropped, the rest still loads. */
private inline fun <T> decodeList(raw: String?, from: (JSONObject) -> T): List<T> {
    if (raw.isNullOrBlank()) return emptyList()
    return try {
        val arr = JSONArray(raw)
        (0 until arr.length()).mapNotNull { i ->
            try {
                from(arr.getJSONObject(i))
            } catch (_: Exception) {
                null
            }
        }
    } catch (_: Exception) {
        emptyList()
    }
}

object Codec {
    fun encodeAlarms(items: List<IntervalAlarm>): String = encodeList(items) { it.toJson() }
    fun decodeAlarms(raw: String?): List<IntervalAlarm> = decodeList(raw, ::intervalAlarmFrom)

    fun encodeStopwatches(items: List<StopwatchItem>): String = encodeList(items) { it.toJson() }
    fun decodeStopwatches(raw: String?): List<StopwatchItem> = decodeList(raw, ::stopwatchFrom)

    fun encodeTimers(items: List<TimerItem>): String = encodeList(items) { it.toJson() }
    fun decodeTimers(raw: String?): List<TimerItem> = decodeList(raw, ::timerFrom)

    fun encodeSettings(s: Settings): String = s.toJson().toString()
    fun decodeSettings(raw: String?): Settings {
        if (raw.isNullOrBlank()) return Settings()
        return try {
            settingsFrom(JSONObject(raw))
        } catch (_: Exception) {
            Settings()
        }
    }
}
