package com.brotimer.ui

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.brotimer.data.Store
import kotlinx.coroutines.delay

/**
 * Settings, plus the checklist that decides whether this app actually works.
 *
 * The checklist is not decoration. On stock Android two grants are genuinely needed
 * (notifications, and full-screen intent on Android 14+), and on this phone's HyperOS skin there
 * are two more that **cannot be requested programmatically at all** — they have no public intent,
 * so the only honest thing to do is name them and say exactly where they live.
 */
@Composable
fun SetupScreen() {
    val context = LocalContext.current
    val settings by Store.settings.collectAsState()

    // Re-read the permission states periodically, so returning from a system settings screen
    // updates the ticks without needing to leave and re-enter the app.
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            refresh++
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        // -- settings ------------------------------------------------------------------------
        Text("Settings", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))

        Text("Snooze length", style = MaterialTheme.typography.labelLarge)
        NumberField(
            value = settings.snoozeMinutes,
            onValueChange = { Store.putSettings(Store.settings.value.copy(snoozeMinutes = it.coerceIn(1, 180))) },
            label = "Minutes",
            range = 1..180,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(14.dp))

        Text("How long an ignored alarm rings", style = MaterialTheme.typography.labelLarge)
        NumberField(
            value = settings.ringSeconds / 60,
            onValueChange = {
                Store.putSettings(
                    Store.settings.value.copy(ringSeconds = (it.coerceIn(1, 30)) * 60)
                )
            },
            label = "Minutes",
            range = 1..30,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(14.dp))

        Text("\"I will sleep now\" length", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField(
                value = settings.sleepMinutes / 60,
                onValueChange = {
                    val current = Store.settings.value
                    Store.putSettings(current.copy(sleepMinutes = it.coerceIn(0, 23) * 60 + current.sleepMinutes % 60))
                },
                label = "Hours",
                range = 0..23,
                modifier = Modifier.weight(1f),
            )
            NumberField(
                value = settings.sleepMinutes % 60,
                onValueChange = {
                    val current = Store.settings.value
                    Store.putSettings(current.copy(sleepMinutes = (current.sleepMinutes / 60) * 60 + it.coerceIn(0, 59)))
                },
                label = "Minutes",
                range = 0..59,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            "Currently ${formatInterval(settings.sleepMinutes / 60, settings.sleepMinutes % 60)}.",
            style = MaterialTheme.typography.bodySmall,
        )

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        // -- permissions ---------------------------------------------------------------------
        Text("Permissions BroTimer needs", style = MaterialTheme.typography.titleLarge)
        Text(
            "If an alarm does not appear on your lock screen, the reason is almost always one of these.",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(12.dp))

        // Keyed on `refresh` so the ticks re-evaluate after a trip to a system settings screen.
        val notificationsOk = remember(refresh) {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
        val fullScreenOk = remember(refresh) { canUseFullScreenIntent(context) }
        val exactOk = remember(refresh) { canScheduleExact(context) }
        val batteryOk = remember(refresh) { ignoresBatteryOptimisation(context) }

        CheckRow(
            ok = notificationsOk,
            title = "Notifications allowed",
            why = "Without this there is no alarm notification, so no full-screen alarm.",
            buttonText = "Open notification settings",
        ) {
            context.launch(
                Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
            )
        }

        CheckRow(
            ok = fullScreenOk,
            title = "Full-screen alarms allowed",
            why = "Android 14+ treats this as a special permission. Without it the alarm only " +
                "appears as a banner and does not take over the screen.",
            buttonText = "Allow full-screen alarms",
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                context.launch(
                    Intent(AndroidSettings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                        .setData(Uri.parse("package:${context.packageName}"))
                )
            } else {
                context.openAppDetails()
            }
        }

        CheckRow(
            ok = exactOk,
            title = "Exact alarms allowed",
            why = "Alarms must fire at the exact minute, not whenever the system feels like it.",
            buttonText = "Allow exact alarms",
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.launch(Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
            } else {
                context.openAppDetails()
            }
        }

        CheckRow(
            ok = batteryOk,
            title = "Battery optimisation turned off",
            why = "Otherwise Android can freeze BroTimer in the background between alarms.",
            buttonText = "Turn off battery optimisation",
        ) {
            @Suppress("BatteryLife")
            context.launch(
                Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:${context.packageName}"))
            )
        }

        Spacer(Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        // -- the OEM part nobody can automate --------------------------------------------------
        Text("Xiaomi / HyperOS — do these by hand", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            "These two have no public setting an app can open, so they must be switched on " +
                "manually. On this phone they are the most common reason an alarm never shows.",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "1.  Autostart → ON\n" +
                "     Settings → Apps → Manage apps → BroTimer → Autostart\n\n" +
                "2.  Display pop-up windows while running in background → ON\n" +
                "     Settings → Apps → Manage apps → BroTimer → Other permissions\n\n" +
                "3.  Battery saver → No restrictions\n" +
                "     Settings → Apps → Manage apps → BroTimer → Battery saver\n\n" +
                "4.  Lock BroTimer in Recents\n" +
                "     Open Recents, drag the BroTimer card down, tap the padlock",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = { context.openAppDetails() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Open BroTimer's app info page")
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun CheckRow(
    ok: Boolean,
    title: String,
    why: String,
    buttonText: String,
    onFix: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (ok) "OK" else "!",
                style = MaterialTheme.typography.titleMedium,
                color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(end = 10.dp),
            )
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        Text(why, style = MaterialTheme.typography.bodySmall)
        if (!ok) {
            Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick = onFix) { Text(buttonText) }
        }
    }
}

// -- checks, each safe on every API level BroTimer supports -----------------------------------

private fun canUseFullScreenIntent(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        context.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() ?: false
    } else {
        true // install-granted before Android 14
    }

private fun canScheduleExact(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() ?: false
    } else {
        true
    }

private fun ignoresBatteryOptimisation(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java)
        ?.isIgnoringBatteryOptimizations(context.packageName) ?: false

/** Some OEMs simply do not have a given settings screen — fall back rather than crash. */
private fun Context.launch(intent: Intent) {
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        openAppDetails()
    }
}

private fun Context.openAppDetails() {
    runCatching {
        startActivity(
            Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
