package com.brotimer.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import com.brotimer.MainActivity
import com.brotimer.data.Store
import com.brotimer.model.IntervalAlarm
import com.brotimer.model.TimerItem
import com.brotimer.model.nextFireAt

/**
 * Every `AlarmManager` call in BroTimer goes through here.
 *
 * **Why `setAlarmClock` and not `setExactAndAllowWhileIdle`:** `setAlarmClock` is the only alarm
 * API that is fully exempt from Doze *and* from battery optimisation, and it puts the alarm icon
 * in the status bar. On an aggressive OEM skin (this phone runs HyperOS) that exemption is the
 * difference between an alarm that fires and one that does not. It is used for everything —
 * interval slots, timers, snoozes, and the end of sleep mode.
 */
object Scheduler {

    const val TAG = "BroTimer"

    const val ACTION_FIRE = "com.brotimer.action.FIRE"

    const val EXTRA_TYPE = "com.brotimer.extra.TYPE"
    const val EXTRA_ID = "com.brotimer.extra.ID"
    /** On a [TYPE_SNOOZE]: 0 = a snooze Omar pressed, n = the nth automatic come-back. */
    const val EXTRA_ATTEMPT = "com.brotimer.extra.ATTEMPT"

    /** A repeating interval alarm reached a grid slot. */
    const val TYPE_INTERVAL = "interval"
    /** A countdown timer reached zero. */
    const val TYPE_TIMER = "timer"
    /** A snoozed ring came back. [EXTRA_ID] still points at the original alarm or timer. */
    const val TYPE_SNOOZE = "snooze"
    /** Sleep mode is over. */
    const val TYPE_SLEEP_END = "sleep_end"

    private const val KIND_INTERVAL = 0
    private const val KIND_TIMER = 1
    private const val KIND_SNOOZE = 2

    /**
     * Request codes must be `Int` and must not collide. Ids come from one counter shared by
     * alarms, stopwatches and timers, so an id identifies exactly one entity and `id * 8 + kind`
     * is unique. The 1000 offset keeps the space clear of [CODE_SLEEP_END].
     */
    private const val CODE_SLEEP_END = 1

    private fun code(kind: Int, id: Int) = 1000 + id * 8 + kind

    // ---------------------------------------------------------------------------------------
    // PendingIntent construction
    // ---------------------------------------------------------------------------------------

    private fun firePendingIntent(
        context: Context,
        kind: Int,
        id: Int,
        type: String,
        attempt: Int = 0,
    ): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_FIRE
            // Extras are ignored by Intent.filterEquals, so the data Uri is what actually keeps
            // these PendingIntents distinct from one another. It also means FLAG_UPDATE_CURRENT
            // below refreshes EXTRA_ATTEMPT on an already-armed snooze.
            data = Uri.parse("brotimer://fire/$kind/$id")
            putExtra(EXTRA_TYPE, type)
            putExtra(EXTRA_ID, id)
            putExtra(EXTRA_ATTEMPT, attempt)
        }
        return PendingIntent.getBroadcast(
            context,
            code(kind, id),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun sleepEndPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_FIRE
            data = Uri.parse("brotimer://fire/sleep")
            putExtra(EXTRA_TYPE, TYPE_SLEEP_END)
        }
        return PendingIntent.getBroadcast(
            context,
            CODE_SLEEP_END,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    // ---------------------------------------------------------------------------------------
    // The one place an alarm is actually armed
    // ---------------------------------------------------------------------------------------

    private fun armExact(context: Context, triggerAt: Long, operation: PendingIntent) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val show = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
            if (canExact) {
                am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, show), operation)
            } else {
                // USE_EXACT_ALARM should make this branch unreachable, but never crash over it.
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "exact alarm denied, falling back to inexact", e)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
        }
    }

    private fun disarm(context: Context, operation: PendingIntent) {
        context.getSystemService(AlarmManager::class.java)?.cancel(operation)
        operation.cancel()
    }

    // ---------------------------------------------------------------------------------------
    // Per-entity scheduling
    // ---------------------------------------------------------------------------------------

    fun scheduleInterval(context: Context, alarm: IntervalAlarm, now: Long) {
        val at = alarm.nextFireAt(now)
        if (at <= 0L) {
            cancelInterval(context, alarm.id)
            return
        }
        armExact(context, at, firePendingIntent(context, KIND_INTERVAL, alarm.id, TYPE_INTERVAL))
        Log.i(TAG, "interval ${alarm.id} '${alarm.label}' next at $at (in ${at - now} ms)")
    }

    fun cancelInterval(context: Context, id: Int) =
        disarm(context, firePendingIntent(context, KIND_INTERVAL, id, TYPE_INTERVAL))

    fun scheduleTimer(context: Context, timer: TimerItem) {
        armExact(context, timer.endsAt, firePendingIntent(context, KIND_TIMER, timer.id, TYPE_TIMER))
        Log.i(TAG, "timer ${timer.id} '${timer.label}' due at ${timer.endsAt}")
    }

    fun cancelTimer(context: Context, id: Int) =
        disarm(context, firePendingIntent(context, KIND_TIMER, id, TYPE_TIMER))

    /**
     * A snooze is a one-off extra ring. It deliberately does **not** move the interval grid.
     *
     * Automatic come-backs (an alarm nobody answered) reuse this same slot with [attempt] > 0, so
     * a manual snooze and a come-back for the same alarm can never both be pending — the later one
     * replaces the earlier.
     */
    fun scheduleSnooze(context: Context, id: Int, triggerAt: Long, attempt: Int = 0) {
        armExact(context, triggerAt, firePendingIntent(context, KIND_SNOOZE, id, TYPE_SNOOZE, attempt))
        Log.i(TAG, "snooze for $id at $triggerAt (attempt $attempt)")
    }

    fun cancelSnooze(context: Context, id: Int) =
        disarm(context, firePendingIntent(context, KIND_SNOOZE, id, TYPE_SNOOZE))

    fun scheduleSleepEnd(context: Context, at: Long) {
        armExact(context, at, sleepEndPendingIntent(context))
        Log.i(TAG, "sleep ends at $at")
    }

    fun cancelSleepEnd(context: Context) = disarm(context, sleepEndPendingIntent(context))

    // ---------------------------------------------------------------------------------------
    // The single entry point every mutation calls
    // ---------------------------------------------------------------------------------------

    /**
     * Brings the system's alarm table back in line with the store. Safe to call as often as you
     * like — it re-arms what should be armed and cancels what should not, and re-arming an
     * existing `PendingIntent` simply replaces it.
     */
    fun rescheduleAll(context: Context) {
        val now = System.currentTimeMillis()
        val settings = Store.settings.value
        val sleeping = settings.isSleeping(now)

        Store.alarms.value.forEach { alarm ->
            if (alarm.enabled && !sleeping && alarm.intervalMs > 0L) {
                scheduleInterval(context, alarm, now)
            } else {
                cancelInterval(context, alarm.id)
                // A pending snooze or come-back belongs to the alarm: switching the alarm off, or
                // going to sleep, silences that too. (Timer snoozes are left alone on purpose —
                // a finished timer is "not running" exactly while its come-back is pending.)
                cancelSnooze(context, alarm.id)
            }
        }

        Store.timers.value.forEach { timer ->
            if (timer.running && timer.endsAt > now) {
                scheduleTimer(context, timer)
            } else {
                cancelTimer(context, timer.id)
            }
        }

        if (sleeping) scheduleSleepEnd(context, settings.sleepUntil) else cancelSleepEnd(context)

        Log.i(TAG, "rescheduleAll: sleeping=$sleeping alarms=${Store.alarms.value.size} timers=${Store.timers.value.size}")
    }
}
