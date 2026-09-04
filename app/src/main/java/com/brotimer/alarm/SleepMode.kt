package com.brotimer.alarm

import android.content.Context
import com.brotimer.data.Store

/**
 * "I will sleep now" — silences the repeating interval alarms for a while.
 *
 * Timers and stopwatches are deliberately untouched: sleep mode is about the repeating reminders
 * only.
 */
object SleepMode {

    fun start(context: Context) {
        val settings = Store.settings.value
        val until = System.currentTimeMillis() + settings.sleepMinutes * 60_000L
        Store.putSettings(settings.copy(sleepUntil = until))
        Scheduler.rescheduleAll(context)
    }

    /**
     * Ends sleep, whether it ran out on its own or Omar pressed "Wake up now".
     *
     * Re-anchoring every enabled alarm at [now] is what makes waking up quiet: each interval
     * starts a *fresh full countdown*, so a 2-hour alarm rings 2 hours after waking rather than
     * the instant sleep mode lifts.
     */
    fun end(context: Context) {
        val now = System.currentTimeMillis()
        Store.writeAlarms(
            Store.alarms.value.map { if (it.enabled) it.copy(anchorAt = now) else it }
        )
        Store.putSettings(Store.settings.value.copy(sleepUntil = 0L))
        Scheduler.rescheduleAll(context)
    }
}
