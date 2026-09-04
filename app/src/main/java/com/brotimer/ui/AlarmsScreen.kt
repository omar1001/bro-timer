package com.brotimer.ui

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import com.brotimer.alarm.Scheduler
import com.brotimer.data.Store
import com.brotimer.model.IntervalAlarm
import com.brotimer.model.nextFireAt

@Composable
fun AlarmsScreen(now: Long) {
    val context = LocalContext.current
    val alarms by Store.alarms.collectAsState()
    val settings by Store.settings.collectAsState()
    val sleeping = settings.isSleeping(now)

    var editing by remember { mutableStateOf<IntervalAlarm?>(null) }
    var dialogOpen by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (alarms.isEmpty()) {
            EmptyHint("No interval alarms yet.\nTap + to add one — it will ring every X hours and minutes with the text you write.")
        } else {
            LazyColumn(contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp)) {
                items(alarms, key = { it.id }) { alarm ->
                    AlarmRow(
                        alarm = alarm,
                        now = now,
                        sleeping = sleeping,
                        onEdit = { editing = alarm; dialogOpen = true },
                        onToggle = { on ->
                            // Switching on re-anchors the grid to this moment, which is what
                            // "every 2 hours from when I turned it on" means.
                            Store.putAlarm(
                                alarm.copy(
                                    enabled = on,
                                    anchorAt = if (on) System.currentTimeMillis() else alarm.anchorAt,
                                )
                            )
                            resync(context)
                        },
                        onDelete = {
                            Scheduler.cancelInterval(context, alarm.id)
                            Scheduler.cancelSnooze(context, alarm.id)
                            Store.removeAlarm(alarm.id)
                            resync(context)
                        },
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = { editing = null; dialogOpen = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add interval alarm")
        }
    }

    if (dialogOpen) {
        AlarmDialog(
            initial = editing,
            onDismiss = { dialogOpen = false },
            onSave = { label, hours, minutes, sound ->
                val existing = editing
                val intervalChanged =
                    existing == null || existing.hours != hours || existing.minutes != minutes
                Store.putAlarm(
                    IntervalAlarm(
                        id = existing?.id ?: Store.nextId(),
                        label = label,
                        hours = hours,
                        minutes = minutes,
                        enabled = existing?.enabled ?: true,
                        // Changing the interval necessarily changes the grid, so re-anchor.
                        // Editing only the label or the sound leaves the schedule alone.
                        anchorAt = if (intervalChanged) {
                            System.currentTimeMillis()
                        } else {
                            existing?.anchorAt ?: System.currentTimeMillis()
                        },
                        soundUri = sound,
                    )
                )
                resync(context)
                dialogOpen = false
            },
        )
    }
}

@Composable
private fun AlarmRow(
    alarm: IntervalAlarm,
    now: Long,
    sleeping: Boolean,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    ItemCard(modifier = Modifier.clickable(onClick = onEdit)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = alarm.label.ifBlank { "(no text)" },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Every ${formatInterval(alarm.hours, alarm.minutes)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = statusLine(alarm, now, sleeping),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Switch(checked = alarm.enabled, onCheckedChange = onToggle)
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete alarm")
            }
        }
    }
}

private fun statusLine(alarm: IntervalAlarm, now: Long, sleeping: Boolean): String = when {
    !alarm.enabled -> "Off"
    alarm.intervalMs <= 0L -> "No interval set — it will not ring"
    sleeping -> "Paused — sleeping"
    else -> {
        val at = alarm.nextFireAt(now)
        "Next at ${formatClock(at)} · ${formatUntil(at - now)}"
    }
}

@Composable
private fun AlarmDialog(
    initial: IntervalAlarm?,
    onDismiss: () -> Unit,
    onSave: (label: String, hours: Int, minutes: Int, sound: String?) -> Unit,
) {
    val context = LocalContext.current
    var label by remember { mutableStateOf(initial?.label ?: "") }
    var hours by remember { mutableIntStateOf(initial?.hours ?: 1) }
    var minutes by remember { mutableIntStateOf(initial?.minutes ?: 0) }
    var sound by remember { mutableStateOf(initial?.soundUri) }

    // The system ringtone chooser. Using it means BroTimer needs no sound-picker UI of its own
    // and no bundled audio files.
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val picked = data?.let {
                IntentCompat.getParcelableExtra(
                    it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java
                )
            }
            sound = picked?.toString()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New interval alarm" else "Edit interval alarm") },
        text = {
            Column {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Text to show when it rings") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text("Ring every", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                        label = "Minutes",
                        range = 0..59,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(14.dp))
                OutlinedButton(
                    onClick = {
                        picker.launch(
                            Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Alarm sound")
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                putExtra(
                                    RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                    sound?.let(Uri::parse),
                                )
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Sound: ${ringtoneTitle(context, sound)}")
                }
                if (sound != null) {
                    TextButton(onClick = { sound = null }) { Text("Use the phone's default alarm sound") }
                }
                if (hours == 0 && minutes == 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Set at least 1 minute, otherwise this alarm can never ring.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = hours > 0 || minutes > 0,
                onClick = { onSave(label.trim(), hours, minutes, sound) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Human name for a ringtone Uri, falling back gracefully if the sound has gone missing. */
@Composable
private fun ringtoneTitle(context: android.content.Context, uri: String?): String =
    remember(uri) {
        if (uri == null) return@remember "phone default"
        runCatching {
            RingtoneManager.getRingtone(context, Uri.parse(uri))?.getTitle(context)
        }.getOrNull() ?: "custom"
    }
