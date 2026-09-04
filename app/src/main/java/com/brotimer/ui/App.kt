package com.brotimer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.brotimer.alarm.Scheduler
import com.brotimer.alarm.SleepMode
import com.brotimer.data.Store
import kotlinx.coroutines.delay

/**
 * The whole app shell: three tabs, a settings screen behind the gear, and the sleep banner that
 * stays visible on every tab.
 *
 * One `now` value is ticked here and passed down to every screen, so the stopwatches, the timers
 * and the "next alarm in ..." lines all move together and there is exactly one timer loop.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var showSetup by remember { mutableStateOf(false) }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (showSetup) "Setup & settings" else "BroTimer") },
                actions = {
                    IconButton(onClick = { showSetup = !showSetup }) {
                        Icon(
                            imageVector = if (showSetup) Icons.Filled.DateRange else Icons.Filled.Settings,
                            contentDescription = if (showSetup) "Back to lists" else "Setup and settings",
                        )
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
                        icon = { Icon(Icons.Filled.Notifications, contentDescription = null) },
                        label = { Text("Alarms") },
                    )
                    NavigationBarItem(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        icon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                        label = { Text("Stopwatch") },
                    )
                    NavigationBarItem(
                        selected = tab == 2,
                        onClick = { tab = 2 },
                        icon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
                        label = { Text("Timers") },
                    )
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (showSetup) {
                SetupScreen()
            } else {
                SleepBanner(now = now)
                when (tab) {
                    0 -> AlarmsScreen(now = now)
                    1 -> StopwatchScreen(now = now)
                    else -> TimersScreen(now = now)
                }
            }
        }
    }
}

/**
 * "I will sleep now" and, while it is running, what is happening and how to cancel it.
 * Deliberately at the top of every tab: it is the button most likely to be needed in a hurry.
 */
@Composable
private fun SleepBanner(now: Long) {
    val context = LocalContext.current
    val settings by Store.settings.collectAsState()
    val sleeping = settings.isSleeping(now)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (sleeping) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            if (sleeping) {
                Text(
                    "Sleeping — interval alarms are paused",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "Wakes at ${formatClock(settings.sleepUntil)} · ${formatUntil(settings.sleepUntil - now)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    OutlinedButton(onClick = { SleepMode.end(context) }) { Text("Wake up now") }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text("Going to bed?", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Pauses interval alarms for " +
                                formatInterval(settings.sleepMinutes / 60, settings.sleepMinutes % 60),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Button(
                        onClick = { SleepMode.start(context) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary,
                            contentColor = MaterialTheme.colorScheme.onTertiary,
                        ),
                    ) { Text("I will sleep now") }
                }
            }
        }
    }
}

/** Called by every screen after it changes something the alarm table depends on. */
internal fun resync(context: android.content.Context) = Scheduler.rescheduleAll(context)
