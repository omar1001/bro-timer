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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brotimer.alarm.Scheduler
import com.brotimer.data.Store
import com.brotimer.model.TimerItem
import com.brotimer.model.remainingAt

/**
 * Countdown timers. A running timer is just an `endsAt` timestamp plus one `setAlarmClock`, so it
 * keeps counting with the app closed and rings through [com.brotimer.alarm.AlarmService] exactly
 * like an interval alarm does.
 *
 * Timers are **not** affected by sleep mode — that only silences the repeating reminders.
 */
@Composable
fun TimersScreen(now: Long) {
    val context = LocalContext.current
    val timers by Store.timers.collectAsState()
    var dialogOpen by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (timers.isEmpty()) {
            EmptyHint("No timers yet.\nTap + to add one — when it reaches zero it rings like an alarm.")
        } else {
            LazyColumn(contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp)) {
                items(timers, key = { it.id }) { timer ->
                    TimerRow(timer = timer, now = now)
                }
            }
        }
        FloatingActionButton(
            onClick = { dialogOpen = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add timer")
        }
    }

    if (dialogOpen) {
        TimerDialog(
            onDismiss = { dialogOpen = false },
            onSave = { label, durationMs ->
                Store.putTimer(
                    TimerItem(
                        id = Store.nextId(),
                        label = label,
                        durationMs = durationMs,
                        running = false,
                        endsAt = 0L,
                        remainingMs = durationMs,
                    )
                )
                resync(context)
                dialogOpen = false
            },
        )
    }
}

@Composable
private fun TimerRow(timer: TimerItem, now: Long) {
    val context = LocalContext.current
    val remaining = timer.remainingAt(now)

    ItemCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = timer.label.ifBlank { "(no label)" },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Set to ${formatCountdown(timer.durationMs)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(onClick = {
                Scheduler.cancelTimer(context, timer.id)
                Scheduler.cancelSnooze(context, timer.id)
                Store.removeTimer(timer.id)
            }) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete timer")
            }
        }
        Text(
            text = formatCountdown(remaining),
            fontSize = 38.sp,
            fontFamily = FontFamily.Monospace,
            color = if (timer.running) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        if (timer.running) {
            Text(
                text = "Rings at ${formatClock(timer.endsAt)}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = {
                    val real = System.currentTimeMillis()
                    if (timer.running) {
                        Store.putTimer(
                            timer.copy(
                                running = false,
                                remainingMs = timer.remainingAt(real),
                                endsAt = 0L,
                            )
                        )
                    } else {
                        // Starting a finished or freshly reset timer runs the full duration again.
                        val left = if (timer.remainingMs > 0L) timer.remainingMs else timer.durationMs
                        Store.putTimer(
                            timer.copy(running = true, endsAt = real + left, remainingMs = left)
                        )
                    }
                    resync(context)
                },
                enabled = timer.durationMs > 0L,
                modifier = Modifier.weight(1f),
            ) {
                Text(if (timer.running) "Pause" else "Start")
            }
            OutlinedButton(
                onClick = {
                    Store.putTimer(
                        timer.copy(running = false, endsAt = 0L, remainingMs = timer.durationMs)
                    )
                    resync(context)
                },
                modifier = Modifier.weight(1f),
            ) {
                Text("Reset")
            }
        }
    }
}

@Composable
private fun TimerDialog(
    onDismiss: () -> Unit,
    onSave: (label: String, durationMs: Long) -> Unit,
) {
    var label by remember { mutableStateOf("") }
    var hours by remember { mutableIntStateOf(0) }
    var minutes by remember { mutableIntStateOf(5) }
    var seconds by remember { mutableIntStateOf(0) }
    val durationMs = (hours * 3600L + minutes * 60L + seconds) * 1000L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New timer") },
        text = {
            Column {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text("Counts down from", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(
                        value = hours,
                        onValueChange = { hours = it },
                        label = "Hours",
                        range = 0..99,
                        modifier = Modifier.weight(1f),
                    )
                    NumberField(
                        value = minutes,
                        onValueChange = { minutes = it },
                        label = "Min",
                        range = 0..59,
                        modifier = Modifier.weight(1f),
                    )
                    NumberField(
                        value = seconds,
                        onValueChange = { seconds = it },
                        label = "Sec",
                        range = 0..59,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = durationMs > 0L,
                onClick = { onSave(label.trim(), durationMs) },
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
