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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brotimer.data.Store
import com.brotimer.model.StopwatchItem
import com.brotimer.model.elapsedMs

/**
 * Stopwatches need no service and no notification: the elapsed time is derived from a stored
 * wall-clock start time, so it stays correct while the app is closed, swiped away, or rebooted.
 */
@Composable
fun StopwatchScreen(now: Long) {
    val watches by Store.stopwatches.collectAsState()
    var dialogOpen by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (watches.isEmpty()) {
            EmptyHint("No stopwatches yet.\nTap + to add one and give it a label.")
        } else {
            LazyColumn(contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp)) {
                items(watches, key = { it.id }) { watch ->
                    StopwatchRow(watch = watch, now = now)
                }
            }
        }
        FloatingActionButton(
            onClick = { dialogOpen = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add stopwatch")
        }
    }

    if (dialogOpen) {
        LabelDialog(
            title = "New stopwatch",
            onDismiss = { dialogOpen = false },
            onSave = { label ->
                Store.putStopwatch(
                    StopwatchItem(
                        id = Store.nextId(),
                        label = label,
                        running = false,
                        startedAt = 0L,
                        accumulatedMs = 0L,
                    )
                )
                dialogOpen = false
            },
        )
    }
}

@Composable
private fun StopwatchRow(watch: StopwatchItem, now: Long) {
    ItemCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = watch.label.ifBlank { "(no label)" },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { Store.removeStopwatch(watch.id) }) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete stopwatch")
            }
        }
        Text(
            text = formatStopwatch(watch.elapsedMs(now)),
            fontSize = 38.sp,
            // Monospaced digits, so the number does not jitter sideways as it counts.
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = {
                    val real = System.currentTimeMillis()
                    Store.putStopwatch(
                        if (watch.running) {
                            watch.copy(
                                running = false,
                                accumulatedMs = watch.elapsedMs(real),
                                startedAt = 0L,
                            )
                        } else {
                            watch.copy(running = true, startedAt = real)
                        }
                    )
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(if (watch.running) "Pause" else "Start")
            }
            OutlinedButton(
                onClick = {
                    Store.putStopwatch(
                        watch.copy(running = false, startedAt = 0L, accumulatedMs = 0L)
                    )
                },
                modifier = Modifier.weight(1f),
            ) {
                Text("Reset")
            }
        }
    }
}

/** Shared "give it a name" dialog. */
@Composable
fun LabelDialog(
    title: String,
    initial: String = "",
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var label by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(label.trim()) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
