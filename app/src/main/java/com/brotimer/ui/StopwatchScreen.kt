package com.brotimer.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
            EmptyHint(
                "No stopwatches yet.\nTap New stopwatch and give it a label.",
                AppIcons.Stopwatch,
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(top = 4.dp, bottom = 104.dp)) {
                items(watches, key = { it.id }) { watch ->
                    StopwatchCard(watch = watch, now = now)
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { dialogOpen = true },
            icon = { Icon(AppIcons.Plus, contentDescription = null) },
            text = { Text("New stopwatch") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .semantics { contentDescription = "New stopwatch" },
        )
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
private fun StopwatchCard(watch: StopwatchItem, now: Long) {
    var confirmDelete by remember { mutableStateOf(false) }

    ItemCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (watch.running) RunningDot() else Spacer(Modifier.width(0.dp))
            Text(
                text = watch.label.ifBlank { "(no label)" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { confirmDelete = true }) {
                Icon(AppIcons.Trash, contentDescription = "Delete stopwatch", modifier = Modifier.size(20.dp))
            }
        }
        Text(
            text = formatStopwatch(watch.elapsedMs(now)),
            style = MaterialTheme.typography.displayMedium.merge(TabularDigits),
            fontSize = 50.sp,
            fontWeight = FontWeight.Light,
            color = if (watch.running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilledIconButton(
                onClick = {
                    val real = System.currentTimeMillis()
                    Store.putStopwatch(
                        if (watch.running) {
                            watch.copy(running = false, accumulatedMs = watch.elapsedMs(real), startedAt = 0L)
                        } else {
                            watch.copy(running = true, startedAt = real)
                        }
                    )
                },
            ) {
                Icon(
                    if (watch.running) AppIcons.Pause else AppIcons.Play,
                    contentDescription = if (watch.running) "Pause" else "Start",
                )
            }
            OutlinedIconButton(
                onClick = { Store.putStopwatch(watch.copy(running = false, startedAt = 0L, accumulatedMs = 0L)) },
            ) {
                Icon(AppIcons.Reset, contentDescription = "Reset", modifier = Modifier.size(20.dp))
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this stopwatch?") },
            text = { Text("\"${watch.label.ifBlank { "(no label)" }}\" will be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    Store.removeStopwatch(watch.id)
                    confirmDelete = false
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

/** A softly pulsing dot: this one is counting. */
@Composable
private fun RunningDot() {
    val pulse = rememberInfiniteTransition(label = "running")
    val alpha by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "alpha",
    )
    Box(
        Modifier
            .padding(end = 10.dp)
            .size(10.dp)
            .alpha(alpha)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
    )
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
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(label.trim()) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
