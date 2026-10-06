package com.brotimer.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.brotimer.data.Store

/**
 * Where every armed alarm lands. This may run with no Activity alive and with the app process
 * freshly created, so it initialises [Store] before touching anything.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)

        val type = intent.getStringExtra(Scheduler.EXTRA_TYPE) ?: return
        val id = intent.getIntExtra(Scheduler.EXTRA_ID, -1)
        val attempt = intent.getIntExtra(Scheduler.EXTRA_ATTEMPT, 0)
        val now = System.currentTimeMillis()
        Log.i(Scheduler.TAG, "fired type=$type id=$id attempt=$attempt")

        when (type) {
            Scheduler.TYPE_SLEEP_END -> SleepMode.end(context)

            Scheduler.TYPE_INTERVAL -> {
                val alarm = Store.alarm(id) ?: return
                // Re-check both conditions: the armed alarm may be a leftover from before the
                // switch was turned off or sleep mode was started.
                if (!alarm.enabled || Store.settings.value.isSleeping(now)) {
                    Scheduler.cancelInterval(context, id)
                    return
                }
                // A pending snooze or come-back for this same alarm would ring twice. The live,
                // scheduled ring wins, and starts a fresh come-back count.
                Scheduler.cancelSnooze(context, id)
                AlarmService.ring(context, id, alarm.label, alarm.soundUri, alarm.repeatCount, attempt = 0)
                // Immediately arm the next grid slot, so a slow dismiss cannot lose the chain.
                Scheduler.scheduleInterval(context, alarm, now)
            }

            Scheduler.TYPE_TIMER -> {
                val timer = Store.timer(id) ?: return
                Scheduler.cancelSnooze(context, id)
                // A finished timer stops and resets to its full duration, ready to run again.
                Store.putTimer(timer.copy(running = false, endsAt = 0L, remainingMs = timer.durationMs))
                AlarmService.ring(context, id, timer.label, timer.soundUri, timer.repeatCount, attempt = 0)
            }

            Scheduler.TYPE_SNOOZE -> {
                // Ids are unique across alarms and timers, so at most one of these matches.
                val alarm = Store.alarm(id)
                val timer = Store.timer(id)
                when {
                    alarm != null -> {
                        // Switched off or asleep since the snooze was set: stay quiet.
                        if (!alarm.enabled || Store.settings.value.isSleeping(now)) return
                        AlarmService.ring(context, id, alarm.label, alarm.soundUri, alarm.repeatCount, attempt)
                    }
                    timer != null ->
                        AlarmService.ring(context, id, timer.label, timer.soundUri, timer.repeatCount, attempt)
                    else -> Log.w(Scheduler.TAG, "snooze for deleted entity $id, ignoring")
                }
            }
        }
    }
}
