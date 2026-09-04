package com.brotimer.data

import android.content.Context
import android.content.SharedPreferences
import com.brotimer.model.Codec
import com.brotimer.model.IntervalAlarm
import com.brotimer.model.Settings
import com.brotimer.model.StopwatchItem
import com.brotimer.model.TimerItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The single source of truth, shared by the UI and by the alarm entry points.
 *
 * It is a process-wide singleton because [com.brotimer.alarm.AlarmReceiver] and
 * [com.brotimer.alarm.BootReceiver] can run when no Activity exists — every entry point calls
 * [init] first, and [init] is idempotent.
 *
 * Writes use `commit()`, not `apply()`: these are tiny writes, and a receiver that finishes
 * quickly must not race the process being killed before the data reaches disk.
 */
object Store {

    private const val PREFS = "brotimer"
    private const val KEY_ALARMS = "alarms"
    private const val KEY_STOPWATCHES = "stopwatches"
    private const val KEY_TIMERS = "timers"
    private const val KEY_SETTINGS = "settings"
    private const val KEY_NEXT_ID = "nextId"

    private var prefs: SharedPreferences? = null

    private val _alarms = MutableStateFlow<List<IntervalAlarm>>(emptyList())
    val alarms: StateFlow<List<IntervalAlarm>> = _alarms.asStateFlow()

    private val _stopwatches = MutableStateFlow<List<StopwatchItem>>(emptyList())
    val stopwatches: StateFlow<List<StopwatchItem>> = _stopwatches.asStateFlow()

    private val _timers = MutableStateFlow<List<TimerItem>>(emptyList())
    val timers: StateFlow<List<TimerItem>> = _timers.asStateFlow()

    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        _alarms.value = Codec.decodeAlarms(p.getString(KEY_ALARMS, null))
        _stopwatches.value = Codec.decodeStopwatches(p.getString(KEY_STOPWATCHES, null))
        _timers.value = Codec.decodeTimers(p.getString(KEY_TIMERS, null))
        _settings.value = Codec.decodeSettings(p.getString(KEY_SETTINGS, null))
    }

    /**
     * Ids are small sequential ints, not timestamps, because they are turned into `PendingIntent`
     * request codes (which are `Int`) by [com.brotimer.alarm.Scheduler].
     */
    @Synchronized
    fun nextId(): Int {
        val p = prefs ?: return 1
        val id = p.getInt(KEY_NEXT_ID, 1)
        p.edit().putInt(KEY_NEXT_ID, id + 1).commit()
        return id
    }

    // -- alarms --------------------------------------------------------------------------------

    fun alarm(id: Int): IntervalAlarm? = _alarms.value.firstOrNull { it.id == id }

    fun putAlarm(item: IntervalAlarm) {
        val list = _alarms.value.toMutableList()
        val at = list.indexOfFirst { it.id == item.id }
        if (at >= 0) list[at] = item else list.add(item)
        writeAlarms(list)
    }

    fun removeAlarm(id: Int) = writeAlarms(_alarms.value.filterNot { it.id == id })

    fun writeAlarms(list: List<IntervalAlarm>) {
        _alarms.value = list
        prefs?.edit()?.putString(KEY_ALARMS, Codec.encodeAlarms(list))?.commit()
    }

    // -- stopwatches ---------------------------------------------------------------------------

    fun stopwatch(id: Int): StopwatchItem? = _stopwatches.value.firstOrNull { it.id == id }

    fun putStopwatch(item: StopwatchItem) {
        val list = _stopwatches.value.toMutableList()
        val at = list.indexOfFirst { it.id == item.id }
        if (at >= 0) list[at] = item else list.add(item)
        writeStopwatches(list)
    }

    fun removeStopwatch(id: Int) = writeStopwatches(_stopwatches.value.filterNot { it.id == id })

    private fun writeStopwatches(list: List<StopwatchItem>) {
        _stopwatches.value = list
        prefs?.edit()?.putString(KEY_STOPWATCHES, Codec.encodeStopwatches(list))?.commit()
    }

    // -- timers --------------------------------------------------------------------------------

    fun timer(id: Int): TimerItem? = _timers.value.firstOrNull { it.id == id }

    fun putTimer(item: TimerItem) {
        val list = _timers.value.toMutableList()
        val at = list.indexOfFirst { it.id == item.id }
        if (at >= 0) list[at] = item else list.add(item)
        writeTimers(list)
    }

    fun removeTimer(id: Int) = writeTimers(_timers.value.filterNot { it.id == id })

    private fun writeTimers(list: List<TimerItem>) {
        _timers.value = list
        prefs?.edit()?.putString(KEY_TIMERS, Codec.encodeTimers(list))?.commit()
    }

    // -- settings ------------------------------------------------------------------------------

    fun putSettings(s: Settings) {
        _settings.value = s
        prefs?.edit()?.putString(KEY_SETTINGS, Codec.encodeSettings(s))?.commit()
    }
}
