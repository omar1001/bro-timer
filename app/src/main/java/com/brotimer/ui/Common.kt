package com.brotimer.ui

import android.util.Log
import android.view.ViewParent
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.brotimer.data.SoundLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A whole-number entry box.
 *
 * The typed text, not the parsed number, is the source of truth while editing — otherwise
 * clearing the field to retype it would immediately snap back to "0" under the cursor.
 */
@Composable
fun NumberField(
    value: Int,
    onValueChange: (Int) -> Unit,
    label: String,
    range: IntRange,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            val digits = raw.filter { it.isDigit() }.take(4)
            text = digits
            onValueChange((digits.toIntOrNull() ?: 0).coerceIn(range))
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = TabularDigits,
        modifier = modifier,
    )
}

/** The one card shape used by all three lists, so the tabs look like one app. */
@Composable
fun ItemCard(
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(
        containerColor =
            if (dimmed) MaterialTheme.colorScheme.surfaceContainerLow
            else MaterialTheme.colorScheme.surfaceContainerHigh,
    )
    val shape = RoundedCornerShape(24.dp)
    val m = modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 6.dp)
    if (onClick != null) {
        Card(onClick = onClick, modifier = m, shape = shape, colors = colors) {
            Column(modifier = Modifier.padding(18.dp), content = content)
        }
    } else {
        Card(modifier = m, shape = shape, colors = colors) {
            Column(modifier = Modifier.padding(18.dp), content = content)
        }
    }
}

/** Centred "nothing here yet" panel with a hint at what the + button does. */
@Composable
fun EmptyHint(text: String, icon: ImageVector? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(88.dp),
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(24.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A label on the left, a value on the right. */
@Composable
fun LabelledRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    }
}

/** A small rounded tag: an icon and a few words, e.g. "♪ Astaghfirullah ×10". */
@Composable
fun InfoChip(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(container)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(5.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** [−] value [+] — a number you adjust with your thumb rather than the keyboard. */
@Composable
fun Stepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    modifier: Modifier = Modifier,
    step: Int = 1,
    suffix: String = "",
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        FilledTonalIconButton(
            onClick = { onValueChange((value - step).coerceIn(range)) },
            enabled = value > range.first,
        ) { Icon(AppIcons.Minus, contentDescription = "Less") }
        Text(
            text = "$value$suffix",
            style = MaterialTheme.typography.titleMedium.merge(TabularDigits),
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 64.dp),
        )
        FilledTonalIconButton(
            onClick = { onValueChange((value + step).coerceIn(range)) },
            enabled = value < range.last,
        ) { Icon(AppIcons.Plus, contentDescription = "More") }
    }
}

/** A titled group on the Setup screen. */
@Composable
fun SectionCard(
    title: String,
    icon: ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                }
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

/**
 * A dialog that covers the whole screen, status bar included, and paints it in the app's own
 * surface colour. Without `decorFitsSystemWindows = false` the area behind the status bar stays
 * the dialog's dark dim — a black strip across the top of every editor.
 */
@Composable
fun FullScreenDialog(onDismissRequest: () -> Unit, content: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val view = LocalView.current
        val lightSurface = MaterialTheme.colorScheme.surface.luminance() > 0.5f
        SideEffect {
            // The dialog's own window — found by walking up, since how many views sit between
            // this composition and the DialogWindowProvider is a Compose implementation detail.
            val window = generateSequence(view as ViewParent?) { it.parent }
                .filterIsInstance<DialogWindowProvider>()
                .firstOrNull()
                ?.window
            if (window == null) {
                Log.w("BroTimer", "FullScreenDialog: no dialog window found; status bar icons may be unreadable")
            } else {
                // No dim: the surface below is opaque and covers everything anyway. This is not
                // cosmetic — while FLAG_DIM_BEHIND is set the system treats the status bar as
                // dimmed and forces white icons, whatever appearance is requested. Measured
                // 2026-10-06: appearance=LIGHT was set, icons stayed white until this was cleared.
                window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                window.setDimAmount(0f)
                // A window may only recolour the system bar icons if it draws the bar
                // backgrounds itself. Activities get this flag from their theme; dialog windows
                // do not.
                window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                @Suppress("DEPRECATION")
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                @Suppress("DEPRECATION")
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = lightSurface
                    isAppearanceLightNavigationBars = lightSurface
                }
            }
        }
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Box(
                Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .imePadding(),
            ) { content() }
        }
    }
}

/** A full-screen editor with Cancel (✕) and Save — roomier than an alert dialog. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenEditor(
    title: String,
    saveEnabled: Boolean,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    FullScreenDialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Cancel") }
                },
                actions = {
                    TextButton(onClick = onSave, enabled = saveEnabled) {
                        Text("Save", fontWeight = FontWeight.SemiBold)
                    }
                },
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                content = content,
            )
        }
    }
}

/** A section label inside an editor. */
@Composable
fun FieldLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
    )
}

/** The display name of a sound, looked up off the main thread. */
@Composable
fun rememberSoundName(soundUri: String?): String {
    val context = LocalContext.current
    val name by produceState(
        initialValue = if (soundUri == null) "Phone's default alarm" else "…",
        soundUri,
    ) {
        value = withContext(Dispatchers.IO) { SoundLibrary.displayName(context, soundUri) }
    }
    return name
}

/** How long one play of a sound lasts (0 = unknown), looked up off the main thread. */
@Composable
fun rememberSoundDurationMs(soundUri: String?): Long {
    val context = LocalContext.current
    val ms by produceState(initialValue = 0L, soundUri) {
        value = withContext(Dispatchers.IO) { SoundLibrary.durationMs(context, soundUri) }
    }
    return ms
}
