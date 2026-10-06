package com.brotimer.alarm

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What is ringing right now, published by [AlarmService] and watched by [AlarmActivity].
 *
 * A process-wide flow rather than a broadcast, because a flow always has a current value: an
 * alarm screen that opens late, or is recreated, immediately learns whether the ring is still
 * going (and closes itself if not) instead of waiting for an event it may already have missed.
 */
object RingState {

    data class Ring(
        val id: Int,
        val label: String,
        /** 0 = loop until stopped or given up ([com.brotimer.model.KEEP_RINGING]). */
        val repeatCount: Int,
        /** Completed plays of the sound so far. */
        val playsDone: Int,
        /** 0 = the scheduled ring (or a snooze Omar pressed); n = the nth automatic come-back. */
        val attempt: Int,
    )

    private val _current = MutableStateFlow<Ring?>(null)
    val current: StateFlow<Ring?> = _current.asStateFlow()

    internal fun set(ring: Ring?) {
        _current.value = ring
    }
}
