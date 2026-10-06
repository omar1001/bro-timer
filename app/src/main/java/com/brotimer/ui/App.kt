package com.brotimer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brotimer.alarm.Scheduler
import com.brotimer.alarm.SleepMode
import com.brotimer.data.SoundLibrary
import com.brotimer.data.Store
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A sound that just arrived through Android's Share menu (WhatsApp, Telegram, Files…), already
 * copied into the library and waiting for Omar to say which alarm or timer it is for.
 */
object IncomingSound {
    /** The imported copy's soundUri. */
    val pending = MutableStateFlow<String?>(null)
    val error = MutableStateFlow<String?>(null)
}

/**
 * The whole app shell: three tabs, a settings screen behind the gear, and the sleep card that
 * stays visible on every tab.
 *
 * One `now` value is ticked here and passed down to every screen, so the stopwatches, the timers
 * and the "next alarm in ..." lines all move together and there is exactly one timer loop.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showSetup by rememberSaveable { mutableStateOf(false) }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            // 100 ms so the stopwatch centiseconds actually move.
            delay(100)
        }
    }

    val settings by Store.settings.collectAsState()

    // When sleep runs out while the app is open in front of you, nothing else would notice —
    // the AlarmManager wake-up also fires, but this keeps the screen honest immediately.
    LaunchedEffect(settings.sleepUntil, now / 1000) {
        if (settings.sleepUntil != 0L && !settings.isSleeping(System.currentTimeMillis())) {
            SleepMode.end(context)
        }
    }

    BackHandler(enabled = showSetup) { showSetup = false }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (showSetup) "Setup" else "BroTimer",
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    if (showSetup) {
                        IconButton(onClick = { showSetup = false }) {
                            @Suppress("DEPRECATION")
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (!showSetup) {
                        IconButton(onClick = { showSetup = true }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Setup and settings")
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (!showSetup) {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        icon = { Icon(AppIcons.AlarmClock, contentDescription = null) },
                        label = { Text("Alarms") },
                    )
                    NavigationBarItem(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        icon = { Icon(AppIcons.Stopwatch, contentDescription = null) },
                        label = { Text("Stopwatch") },
                    )
                    NavigationBarItem(
                        selected = tab == 2,
                        onClick = { tab = 2 },
                        icon = { Icon(AppIcons.Hourglass, contentDescription = null) },
                        label = { Text("Timers") },
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (showSetup) {
                SetupScreen()
            } else {
                SleepCard(now = now)
                when (tab) {
                    0 -> AlarmsScreen(now = now)
                    1 -> StopwatchScreen(now = now)
                    else -> TimersScreen(now = now)
                }
            }
        }
    }

    IncomingSoundDialog()
}

/**
 * "I will sleep now" and, while it is running, what is happening and how to cancel it.
 * Deliberately at the top of every tab: it is the button most likely to be needed in a hurry.
 */
@Composable
private fun SleepCard(now: Long) {
    val context = LocalContext.current
    val settings by Store.settings.collectAsState()
    val sleeping = settings.isSleeping(now)

    if (sleeping) {
        // Night sky — fixed colours so it reads as "night" in light and dark theme alike.
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.linearGradient(listOf(Color(0xFF1B2A4A), Color(0xFF3A3F7A))))
                    .padding(20.dp),
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(AppIcons.Moon, null, tint = Color(0xFFF4BF48), modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Sleeping",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        formatCountdown(settings.sleepUntil - now),
                        style = MaterialTheme.typography.displaySmall.merge(TabularDigits),
                        fontWeight = FontWeight.Light,
                        color = Color.White,
                    )
                    Text(
                        "Interval alarms are quiet until ${formatClock(settings.sleepUntil)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFC9D3E8),
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = { SleepMode.end(context) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFF4BF48),
                            contentColor = Color(0xFF2A1D00),
                        ),
                    ) {
                        Icon(AppIcons.Sun, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Wake up now", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    } else {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        AppIcons.Moon,
                        null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(10.dp),
                    )
                }
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                ) {
                    Text("Going to bed?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Quiet for ${formatInterval(settings.sleepMinutes / 60, settings.sleepMinutes % 60)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = { SleepMode.start(context) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary,
                    ),
                ) { Text("I will sleep now", fontSize = 13.sp) }
            }
        }
    }
}

/** After Share → BroTimer: "which alarm is this sound for?" */
@Composable
private fun IncomingSoundDialog() {
    val pending by IncomingSound.pending.collectAsState()
    val error by IncomingSound.error.collectAsState()

    error?.let { msg ->
        AlertDialog(
            onDismissRequest = { IncomingSound.error.value = null },
            title = { Text("Could not add that sound") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { IncomingSound.error.value = null }) { Text("OK") } },
        )
    }

    val uri = pending ?: return
    val alarms by Store.alarms.collectAsState()
    val timers by Store.timers.collectAsState()
    var target by remember(uri) { mutableIntStateOf(-1) }
    val name = rememberSoundName(uri)

    AlertDialog(
        onDismissRequest = { IncomingSound.pending.value = null },
        title = { Text("Sound added") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("“$name” is now in your sounds. Use it for:")
                Spacer(Modifier.height(8.dp))
                alarms.forEach { a -> TargetRow(a.label.ifBlank { "(no text)" }, "Alarm", target == a.id) { target = a.id } }
                timers.forEach { t -> TargetRow(t.label.ifBlank { "(no label)" }, "Timer", target == t.id) { target = t.id } }
                TargetRow("Nothing yet — keep it for later", null, target == -1) { target = -1 }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                Store.alarm(target)?.let { Store.putAlarm(it.copy(soundUri = uri)) }
                Store.timer(target)?.let { Store.putTimer(it.copy(soundUri = uri)) }
                IncomingSound.pending.value = null
            }) { Text("Done") }
        },
    )
}

@Composable
private fun TargetRow(title: String, kind: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 4.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(10.dp))
        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (kind != null) {
            Text(kind, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Called by every screen after it changes something the alarm table depends on. */
internal fun resync(context: android.content.Context) = Scheduler.rescheduleAll(context)

/** Imports a shared sound into the library, then asks where it goes. */
internal suspend fun receiveSharedSound(context: android.content.Context, uri: android.net.Uri) {
    try {
        val file = SoundLibrary.import(context, uri)
        IncomingSound.pending.value = SoundLibrary.uriOf(file)
    } catch (e: SoundLibrary.ImportFailed) {
        IncomingSound.error.value = e.message
    } catch (e: Exception) {
        IncomingSound.error.value = "Could not copy that sound: ${e.message ?: e.javaClass.simpleName}"
    }
}
