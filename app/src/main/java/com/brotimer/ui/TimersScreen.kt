package com.brotimer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.brotimer.alarm.Scheduler
import com.brotimer.data.Store
import com.brotimer.model.KEEP_RINGING
import com.brotimer.model.TimerItem
import com.brotimer.model.remainingAt

/**
 * Countdown timers. A running timer is just an `endsAt` timestamp plus one `setAlarmClock`, so it
 * keeps counting with the app closed and rings through [com.brotimer.alarm.AlarmService] exactly
 * like an interval alarm does — including its own sound and repeat count.
 *
 * Timers are **not** affected by sleep mode — that only silences the repeating reminders.
 */
@Composable
fun TimersScreen(now: Long) {
    val context = LocalContext.current
    val timers by Store.timers.collectAsState()
    var editing by remember { mutableStateOf<TimerItem?>(null) }
    var editorOpen by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (timers.isEmpty()) {
            EmptyHint(
                "No timers yet.\nTap New timer — when it reaches zero it rings like an alarm.",
                AppIcons.Hourglass,
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(top = 4.dp, bottom = 104.dp)) {
                items(timers, key = { it.id }) { timer ->
                    TimerCard(
                        timer = timer,
                        now = now,
                        onEdit = {
                            editing = timer
                            editorOpen = true
                        },
                    )
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = {
                editing = null
                editorOpen = true
            },
            icon = { Icon(AppIcons.Plus, contentDescription = null) },
            text = { Text("New timer") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .semantics { contentDescription = "New timer" },
        )
    }

    if (editorOpen) {
        val existing = editing
        TimerEditor(
            initial = existing,
            onDismiss = { editorOpen = false },
            onSave = { label, durationMs, sound, repeat ->
                if (existing == null) {
                    Store.putTimer(
                        TimerItem(
                            id = Store.nextId(),
                            label = label,
                            durationMs = durationMs,
                            running = false,
                            endsAt = 0L,
                            remainingMs = durationMs,
                            soundUri = sound,
                            repeatCount = repeat,
                        )
                    )
                } else if (existing.durationMs != durationMs) {
                    // A new length means a new countdown: stop and reset to it.
                    Scheduler.cancelTimer(context, existing.id)
                    Store.putTimer(
                        existing.copy(
                            label = label,
                            durationMs = durationMs,
                            running = false,
                            endsAt = 0L,
                            remainingMs = durationMs,
                            soundUri = sound,
                            repeatCount = repeat,
                        )
                    )
                } else {
                    // Only the text or the sound changed: a running timer keeps running.
                    Store.putTimer(existing.copy(label = label, soundUri = sound, repeatCount = repeat))
                }
                resync(context)
                editorOpen = false
            },
            onDelete = existing?.let { timer ->
                {
                    Scheduler.cancelTimer(context, timer.id)
                    Scheduler.cancelSnooze(context, timer.id)
                    Store.removeTimer(timer.id)
                    resync(context)
                    editorOpen = false
                }
            },
        )
    }
}

@Composable
private fun TimerCard(timer: TimerItem, now: Long, onEdit: () -> Unit) {
    val context = LocalContext.current
    val remaining = timer.remainingAt(now)
    val fraction = if (timer.durationMs > 0) remaining.toFloat() / timer.durationMs else 0f
    val partlyRun = !timer.running && remaining in 1 until timer.durationMs

    ItemCard(onClick = onEdit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(116.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { fraction },
                    strokeWidth = 8.dp,
                    strokeCap = StrokeCap.Round,
                    color = if (timer.running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    modifier = Modifier.fillMaxSize(),
                )
                Text(
                    formatCountdown(remaining),
                    style = MaterialTheme.typography.titleLarge.merge(TabularDigits),
                    fontWeight = FontWeight.SemiBold,
                    color = if (timer.running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    timer.label.ifBlank { "(no label)" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    when {
                        timer.running -> "Rings at ${formatClock(timer.endsAt)}"
                        partlyRun -> "Paused"
                        else -> "${formatCountdown(timer.durationMs)} timer"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                InfoChip(AppIcons.Music, soundChipText(timer.soundUri, timer.repeatCount))
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledIconButton(
                        onClick = {
                            val real = System.currentTimeMillis()
                            if (timer.running) {
                                Store.putTimer(
                                    timer.copy(running = false, remainingMs = timer.remainingAt(real), endsAt = 0L)
                                )
                            } else {
                                // Starting again answers any come-back left from the last time it rang.
                                Scheduler.cancelSnooze(context, timer.id)
                                val left = if (timer.remainingMs > 0L) timer.remainingMs else timer.durationMs
                                Store.putTimer(timer.copy(running = true, endsAt = real + left, remainingMs = left))
                            }
                            resync(context)
                        },
                        enabled = timer.durationMs > 0L,
                    ) {
                        Icon(
                            if (timer.running) AppIcons.Pause else AppIcons.Play,
                            contentDescription = if (timer.running) "Pause" else "Start",
                        )
                    }
                    OutlinedIconButton(
                        onClick = {
                            Scheduler.cancelSnooze(context, timer.id)
                            Store.putTimer(timer.copy(running = false, endsAt = 0L, remainingMs = timer.durationMs))
                            resync(context)
                        },
                    ) {
                        Icon(AppIcons.Reset, contentDescription = "Reset", modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun TimerEditor(
    initial: TimerItem?,
    onDismiss: () -> Unit,
    onSave: (label: String, durationMs: Long, sound: String?, repeat: Int) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val initialSeconds = ((initial?.durationMs ?: 300_000L) / 1000L).toInt()
    var label by remember { mutableStateOf(initial?.label ?: "") }
    var hours by remember { mutableIntStateOf(initialSeconds / 3600) }
    var minutes by remember { mutableIntStateOf((initialSeconds / 60) % 60) }
    var seconds by remember { mutableIntStateOf(initialSeconds % 60) }
    var sound by remember { mutableStateOf(initial?.soundUri) }
    var repeat by remember { mutableIntStateOf(initial?.repeatCount ?: KEEP_RINGING) }
    var fieldsVersion by remember { mutableIntStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }
    val clipMs = rememberSoundDurationMs(sound)
    val durationMs = (hours * 3600L + minutes * 60L + seconds) * 1000L

    FullScreenEditor(
        title = if (initial == null) "New timer" else "Edit timer",
        saveEnabled = durationMs > 0L,
        onDismiss = onDismiss,
        onSave = { onSave(label.trim(), durationMs, sound, repeat) },
    ) {
        FieldLabel("Label")
        OutlinedTextField(
            value = label,
            onValueChange = { label = it },
            placeholder = { Text("e.g. Tea") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        )

        FieldLabel("Counts down from")
        key(fieldsVersion) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(hours, { hours = it }, "Hours", 0..99, Modifier.weight(1f))
                NumberField(minutes, { minutes = it }, "Min", 0..59, Modifier.weight(1f))
                NumberField(seconds, { seconds = it }, "Sec", 0..59, Modifier.weight(1f))
            }
        }
        QuickPicks(
            options = listOf(
                "1 min" to 60, "3 min" to 180, "5 min" to 300, "10 min" to 600,
                "15 min" to 900, "30 min" to 1800, "45 min" to 2700, "1 h" to 3600,
            ),
            selected = hours * 3600 + minutes * 60 + seconds,
            onPick = { total ->
                hours = total / 3600
                minutes = (total / 60) % 60
                seconds = total % 60
                fieldsVersion++
            },
        )
        if (initial != null && initial.running && durationMs != initial.durationMs) {
            Text(
                "Saving a new length stops this timer and resets it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        FieldLabel("Sound")
        SoundField(soundUri = sound, onChange = { sound = it })

        FieldLabel("How it rings")
        RepeatChooser(repeatCount = repeat, onChange = { repeat = it }, clipMs = clipMs)

        if (onDelete != null) {
            Spacer(Modifier.height(28.dp))
            DeleteButton("Delete this timer") { confirmDelete = true }
        }
        Spacer(Modifier.height(32.dp))
    }

    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this timer?") },
            text = { Text("\"${initial?.label?.ifBlank { "(no label)" }}\" will be removed.") },
            confirmButton = { TextButton(onClick = onDelete) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
