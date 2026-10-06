package com.brotimer.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.brotimer.data.Store
import com.brotimer.model.KEEP_RINGING

/** The "Sound" row in an editor: what plays, how long one play lasts, tap to change. */
@Composable
fun SoundField(soundUri: String?, onChange: (String?) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    val name = rememberSoundName(soundUri)
    val ms = rememberSoundDurationMs(soundUri)

    Card(
        onClick = { picking = true },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(14.dp)) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(42.dp)) {
                Icon(
                    AppIcons.Music,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(10.dp),
                )
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp),
            ) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (ms > 0) "${formatClipShort(ms)} per play · tap to change" else "Tap to change",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(AppIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (picking) {
        SoundPicker(
            current = soundUri,
            onDismiss = { picking = false },
            onPick = {
                onChange(it)
                picking = false
            },
        )
    }
}

/**
 * "Keep ringing" vs "Play it N times". The count remembers itself while you flip between the two,
 * so switching to "keep ringing" and back does not lose the 10 you typed.
 */
@Composable
fun RepeatChooser(repeatCount: Int, onChange: (Int) -> Unit, clipMs: Long) {
    val settings by Store.settings.collectAsState()
    var lastCount by remember { mutableIntStateOf(if (repeatCount > 0) repeatCount else 10) }
    val counted = repeatCount > 0

    Column(Modifier.fillMaxWidth()) {
        ChoiceRow(
            selected = !counted,
            title = "Keep ringing until I stop it",
            subtitle = "Gives up after ${formatInterval(0, settings.ringSeconds / 60)} — change that in Setup",
            onClick = { onChange(KEEP_RINGING) },
        )
        ChoiceRow(
            selected = counted,
            title = "Play it a set number of times",
            subtitle =
                if (counted && clipMs > 0) "$repeatCount × ${formatClipShort(clipMs)} — ${formatAbout(repeatCount * clipMs)}"
                else "Best for a short voice clip that says a word",
            onClick = { onChange(lastCount) },
        )
        if (counted) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 48.dp, top = 4.dp),
            ) {
                Stepper(
                    value = repeatCount,
                    onValueChange = {
                        lastCount = it
                        onChange(it)
                    },
                    range = 1..99,
                )
                Spacer(Modifier.width(8.dp))
                Text("times", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun ChoiceRow(selected: Boolean, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 6.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A sideways-scrolling row of one-tap presets. */
@Composable
fun QuickPicks(
    options: List<Pair<String, Int>>,
    selected: Int?,
    onPick: (Int) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(top = 10.dp),
    ) {
        options.forEach { (label, value) ->
            FilterChip(
                selected = selected == value,
                onClick = { onPick(value) },
                label = { Text(label) },
            )
        }
    }
    Spacer(Modifier.height(2.dp))
}
