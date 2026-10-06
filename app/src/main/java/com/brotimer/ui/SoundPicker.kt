package com.brotimer.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import com.brotimer.data.SoundLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Choose what an alarm or timer plays.
 *
 * Built around the Zedge case Omar described: download a tone in Zedge (or receive a voice clip
 * in WhatsApp, or save one to Downloads), open this screen, and it is the **first row** of "On this
 * phone", because that list is sorted newest first. One tap copies it into "Your sounds" and picks
 * it. ▶ previews any row without choosing it.
 *
 * Tapping a row chooses it and closes the screen; nothing else needs confirming.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundPicker(
    current: String?,
    onDismiss: () -> Unit,
    onPick: (String?) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preview = remember { Previewer(context) }
    DisposableEffect(Unit) { onDispose { preview.stop() } }

    var libraryVersion by remember { mutableIntStateOf(0) }
    var library by remember { mutableStateOf<List<File>>(emptyList()) }
    LaunchedEffect(libraryVersion) {
        library = withContext(Dispatchers.IO) { SoundLibrary.list(context) }
    }

    var hasPermission by remember { mutableStateOf(SoundLibrary.hasAudioPermission(context)) }
    var refused by remember { mutableStateOf(false) }
    var phoneAudio by remember { mutableStateOf<List<SoundLibrary.PhoneAudio>?>(null) }
    LaunchedEffect(hasPermission) {
        phoneAudio = if (hasPermission) withContext(Dispatchers.IO) { SoundLibrary.queryPhoneAudio(context) } else null
    }

    var query by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<File?>(null) }

    fun importAndPick(uri: Uri) {
        preview.stop()
        busy = true
        error = null
        scope.launch {
            try {
                val file = SoundLibrary.import(context, uri)
                libraryVersion++
                onPick(SoundLibrary.uriOf(file))
            } catch (e: SoundLibrary.ImportFailed) {
                error = e.message
            } catch (e: Exception) {
                error = "Could not copy that sound: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                busy = false
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        refused = !granted
    }
    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) importAndPick(uri) }
    val ringtoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val picked = result.data?.let {
                IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            }
            // System ringtones are managed by the phone and do not disappear, so they are kept as
            // a reference rather than copied.
            if (picked != null) onPick(picked.toString())
        }
    }

    FullScreenDialog(onDismissRequest = onDismiss) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text("Choose a sound") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            @Suppress("DEPRECATION")
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    windowInsets = WindowInsets(0, 0, 0, 0),
                )
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())

                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    error?.let { msg ->
                        item {
                            Text(
                                msg,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(8.dp),
                            )
                        }
                    }

                    item {
                        SoundRow(
                            title = rememberSoundName(null),
                            subtitle = "What your phone's own alarm clock plays",
                            selected = current == null,
                            playing = preview.playingKey == "default",
                            // The settings URI, not the media URI it points at: the system serves it
                            // from its own cached copy, readable without any media permission.
                            onPreview = {
                                preview.toggle("default", RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
                            },
                            onClick = { onPick(null) },
                        )
                    }

                    item { ListHeader("Your sounds") }
                    if (library.isEmpty()) {
                        item {
                            Text(
                                "Sounds you pick from your phone are copied here, so they keep working " +
                                    "even if the original file is deleted.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                    items(library, key = { "lib:" + it.name }) { file ->
                        val uri = SoundLibrary.uriOf(file)
                        SoundRow(
                            title = file.name.substringBeforeLast('.', file.name),
                            subtitle = formatClip(rememberSoundDurationMs(uri)),
                            selected = current == uri,
                            playing = preview.playingKey == uri,
                            onPreview = { preview.toggle(uri, Uri.fromFile(file)) },
                            onClick = { onPick(uri) },
                            onDelete = { confirmDelete = file },
                        )
                    }

                    item {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                        ) {
                            FilledTonalButton(
                                onClick = { fileLauncher.launch(arrayOf("audio/*")) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(AppIcons.FolderPlus, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Browse files")
                            }
                            OutlinedButton(
                                onClick = { ringtoneLauncher.launch(ringtonePickerIntent(context, current)) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(AppIcons.Bell, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Ringtones")
                            }
                        }
                    }

                    item { ListHeader("On this phone · newest first") }
                    if (!hasPermission) {
                        item {
                            PermissionCard(
                                refused = refused,
                                onAllow = { permissionLauncher.launch(SoundLibrary.audioPermission) },
                                onOpenSettings = { openAppSettings(context) },
                            )
                        }
                    } else {
                        item {
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                placeholder = { Text("Search your audio") },
                                leadingIcon = { Icon(Icons.Filled.Search, null) },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp),
                            )
                        }
                        val all = phoneAudio
                        if (all == null) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }
                        } else {
                            val shown = if (query.isBlank()) all else all.filter {
                                it.name.contains(query, ignoreCase = true) || it.folder.contains(query, ignoreCase = true)
                            }
                            if (shown.isEmpty()) {
                                item {
                                    Text(
                                        if (all.isEmpty()) "No audio files found on this phone yet."
                                        else "Nothing matches \"$query\".",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(8.dp),
                                    )
                                }
                            }
                            items(shown, key = { "phone:" + it.uri }) { audio ->
                                val key = audio.uri.toString()
                                SoundRow(
                                    title = audio.name,
                                    subtitle = listOf(formatClip(audio.durationMs), audio.folder)
                                        .filter { it.isNotBlank() }
                                        .joinToString(" · "),
                                    selected = false,
                                    playing = preview.playingKey == key,
                                    onPreview = { preview.toggle(key, audio.uri) },
                                    onClick = { importAndPick(audio.uri) },
                                )
                            }
                        }
                    }
                }
            }
    }

    confirmDelete?.let { file ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete this sound?") },
            text = {
                Text(
                    "\"${file.name.substringBeforeLast('.', file.name)}\" will be removed from BroTimer. " +
                        "Any alarm or timer using it goes back to the phone's default alarm sound.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    preview.stop()
                    SoundLibrary.delete(context, file)
                    if (current == SoundLibrary.uriOf(file)) onPick(null)
                    confirmDelete = null
                    libraryVersion++
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ListHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, top = 22.dp, bottom = 8.dp),
    )
}

@Composable
private fun SoundRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    playing: Boolean,
    onPreview: () -> Unit,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor =
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        ) {
            IconButton(onClick = onPreview) {
                Surface(
                    shape = CircleShape,
                    color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    modifier = Modifier.size(38.dp),
                ) {
                    Icon(
                        if (playing) AppIcons.StopSquare else AppIcons.Play,
                        contentDescription = if (playing) "Stop preview" else "Preview",
                        tint = if (playing) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(9.dp),
                    )
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (selected) {
                Icon(
                    AppIcons.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(AppIcons.Trash, contentDescription = "Delete sound", modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(refused: Boolean, onAllow: () -> Unit, onOpenSettings: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.Music, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Spacer(Modifier.width(10.dp))
                Text(
                    "See every sound on your phone",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Zedge downloads, WhatsApp voice notes, music, recordings — newest first, so a tone " +
                    "you just downloaded is right at the top. BroTimer only reads audio files.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.height(12.dp))
            if (refused) {
                Text(
                    "Access was refused. You can still use Browse files, or allow it in Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onAllow) { Text("Ask again") }
                    OutlinedButton(onClick = onOpenSettings) { Text("Open Settings") }
                }
            } else {
                // A filled button: a tonal one is the same colour as this card and disappears.
                Button(onClick = onAllow) { Text("Show my audio files") }
            }
        }
    }
}

/**
 * The phone has two ringtone pickers (stock and Xiaomi's theme manager), which makes Android ask
 * "open with?" every time. Prefer the stock one so it opens straight away.
 */
private fun ringtonePickerIntent(context: Context, current: String?): Intent {
    val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
        putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Alarm sound")
        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false)
        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
        if (current != null && current.startsWith("content:")) {
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(current))
        }
    }
    val stock = Intent(intent).setPackage("com.android.soundpicker")
    return if (stock.resolveActivity(context.packageManager) != null) stock else intent
}

private fun openAppSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

/** Plays one sound at a time for ▶ preview, at alarm volume so it sounds like the real thing. */
private class Previewer(private val context: Context) {
    private var player: MediaPlayer? = null
    var playingKey by mutableStateOf<String?>(null)
        private set

    fun toggle(key: String, uri: Uri) {
        if (playingKey == key) {
            stop()
            return
        }
        stop()
        val mp = MediaPlayer()
        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            mp.setDataSource(context, SoundLibrary.resolve(context, uri))
            mp.setOnPreparedListener { it.start() }
            mp.setOnCompletionListener { if (player === it) stop() }
            mp.setOnErrorListener { p, _, _ -> if (player === p) stop(); true }
            mp.prepareAsync()
            player = mp
            playingKey = key
        } catch (_: Exception) {
            runCatching { mp.release() }
        }
    }

    fun stop() {
        runCatching { player?.release() }
        player = null
        playingKey = null
    }
}
