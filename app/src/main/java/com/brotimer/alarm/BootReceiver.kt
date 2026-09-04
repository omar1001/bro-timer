package com.brotimer.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.brotimer.data.Store

/**
 * The alarm table does not survive a reboot, an app update, or a manual clock change — so this
 * rebuilds it. No catch-up logic is needed: the interval grid is derived from each alarm's anchor,
 * so slots missed while the phone was off are simply skipped.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(Scheduler.TAG, "BootReceiver: ${intent.action}")
        Store.init(context)

        // If the phone was off past the end of sleep mode, wake up properly rather than staying
        // silent forever.
        if (!Store.settings.value.isSleeping(System.currentTimeMillis()) &&
            Store.settings.value.sleepUntil != 0L
        ) {
            SleepMode.end(context)
            return
        }

        Scheduler.rescheduleAll(context)
    }
}
