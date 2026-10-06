package com.brotimer.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.brotimer.alarm.Scheduler
import com.brotimer.data.Store
import com.brotimer.model.IntervalAlarm
import com.brotimer.model.KEEP_RINGING
import com.brotimer.model.nextFireAt

@Composable
fun AlarmsScreen(now: Long) {
    val context = LocalContext.current
    val alarms by Store.alarms.collectAsState()
    val settings by Store.settings.collectAsState()
    val sleeping = settings.isSleeping(now)

    var editing by remember { mutableStateOf<IntervalAlarm?>(null) }
    var editorOpen by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (alarms.isEmpty()) {
            EmptyHint(
                "No interval alarms yet.\nTap New alarm — it rings every few hours or minutes, " +
                    "showing the text you write.",
                AppIcons.AlarmClock,
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(top = 4.dp, bottom = 104.dp)) {
                items(alarms, key = { it.id }) { alarm ->
                    AlarmCard(
                        alarm = alarm,
                        now = now,
                        sleeping = sleeping,
                        onEdit = {
                            editing = alarm
                            editorOpen = true
                        },
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
            text = { Text("New alarm") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                // ExtendedFloatingActionButton does not surface its label to accessibility
                // services (TalkBack, uiautomator) on this Compose version — name it explicitly.
                .semantics { contentDescription = "New alarm" },
        )
    }

    if (editorOpen) {
        val existing = editing
        AlarmEditor(
            initial = existing,
            onDismiss = { editorOpen = false },
            onSave = { label, hours, minutes, sound, repeat ->
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
                        // Editing only the text, sound or repeat leaves the schedule alone.
                        anchorAt = if (intervalChanged) {
                            System.currentTimeMillis()
                        } else {
                            existing?.anchorAt ?: System.currentTimeMillis()
                        },
                        soundUri = sound,
                        repeatCount = repeat,
                    )
                )
                resync(context)
                editorOpen = false
            },
            onDelete = existing?.let { alarm ->
                {
                    Scheduler.cancelInterval(context, alarm.id)
                    Scheduler.cancelSnooze(context, alarm.id)
                    Store.removeAlarm(alarm.id)
                    resync(context)
                    editorOpen = false
                }
            },
        )
    }
}

@Composable
private fun AlarmCard(
    alarm: IntervalAlarm,
    now: Long,
    sleeping: Boolean,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    val onColor =
        if (alarm.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant

    ItemCard(dimmed = !alarm.enabled, onClick = onEdit) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = alarm.label.ifBlank { "(no text)" },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = onColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    InfoChip(AppIcons.Repeat, "Every ${formatInterval(alarm.hours, alarm.minutes)}")
                    InfoChip(
                        AppIcons.Music,
                        soundChipText(alarm.soundUri, alarm.repeatCount),
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Switch(checked = alarm.enabled, onCheckedChange = onToggle)
        }

        Spacer(Modifier.height(14.dp))
        when {
            !alarm.enabled -> Text(
                "Off",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            alarm.intervalMs <= 0L -> Text(
                "No interval set — it will not ring",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            sleeping -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.Moon, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Paused while you sleep",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            else -> {
                val next = alarm.nextFireAt(now)
                val progress = (1f - (next - now).toFloat() / alarm.intervalMs).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
                    trackColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "Next at ${formatClock(next)}",
                        style = MaterialTheme.typography.bodyMedium.merge(TabularDigits),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        formatUntil(next - now),
                        style = MaterialTheme.typography.bodyMedium.merge(TabularDigits),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/** "Default sound" / "Astaghfirullah ×10" */
@Composable
fun soundChipText(soundUri: String?, repeatCount: Int): String {
    val name = if (soundUri == null) "Default sound" else rememberSoundName(soundUri)
    return if (repeatCount > 0) "$name ×$repeatCount" else name
}

@Composable
private fun AlarmEditor(
    initial: IntervalAlarm?,
    onDismiss: () -> Unit,
    onSave: (label: String, hours: Int, minutes: Int, sound: String?, repeat: Int) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var label by remember { mutableStateOf(initial?.label ?: "") }
    var hours by remember { mutableIntStateOf(initial?.hours ?: 1) }
    var minutes by remember { mutableIntStateOf(initial?.minutes ?: 0) }
    var sound by remember { mutableStateOf(initial?.soundUri) }
    var repeat by remember { mutableIntStateOf(initial?.repeatCount ?: KEEP_RINGING) }
    // NumberField keeps its own text while typing; bumping this rebuilds the fields after a
    // quick-pick chip changes the numbers underneath them.
    var fieldsVersion by remember { mutableIntStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }
    val clipMs = rememberSoundDurationMs(sound)
    val valid = hours > 0 || minutes > 0

    FullScreenEditor(
        title = if (initial == null) "New interval alarm" else "Edit alarm",
        saveEnabled = valid,
        onDismiss = onDismiss,
        onSave = { onSave(label.trim(), hours, minutes, sound, repeat) },
    ) {
        FieldLabel("Text to show when it rings")
        OutlinedTextField(
            value = label,
            onValueChange = { label = it },
            placeholder = { Text("e.g. Drink water") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        )

        FieldLabel("Ring every")
        key(fieldsVersion) {
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
        }
        QuickPicks(
            options = listOf(
                "15 min" to 15, "30 min" to 30, "45 min" to 45, "1 h" to 60,
                "1½ h" to 90, "2 h" to 120, "3 h" to 180, "4 h" to 240,
            ),
            selected = hours * 60 + minutes,
            onPick = { total ->
                hours = total / 60
                minutes = total % 60
                fieldsVersion++
            },
        )
        if (!valid) {
            Text(
                "Set at least 1 minute, otherwise this alarm can never ring.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        FieldLabel("Sound")
        SoundField(soundUri = sound, onChange = { sound = it })

        FieldLabel("How it rings")
        RepeatChooser(repeatCount = repeat, onChange = { repeat = it }, clipMs = clipMs)

        if (onDelete != null) {
            Spacer(Modifier.height(28.dp))
            DeleteButton("Delete this alarm") { confirmDelete = true }
        }
        Spacer(Modifier.height(32.dp))
    }

    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this alarm?") },
            text = { Text("\"${initial?.label?.ifBlank { "(no text)" }}\" will stop ringing and be removed.") },
            confirmButton = { TextButton(onClick = onDelete) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

/** Red outlined delete button used at the bottom of every editor. */
@Composable
fun DeleteButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(AppIcons.Trash, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}
