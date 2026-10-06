package com.brotimer.ui

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.brotimer.data.SoundLibrary
import com.brotimer.data.Store
import com.brotimer.model.Settings
import com.brotimer.model.THEME_DARK
import com.brotimer.model.THEME_LIGHT
import com.brotimer.model.THEME_SYSTEM
import kotlinx.coroutines.delay

/**
 * Settings, plus the checklist that decides whether this app actually works.
 *
 * The checklist is not decoration. On stock Android two grants are genuinely needed
 * (notifications, and full-screen intent on Android 14+), and on this phone's HyperOS skin there
 * are more that **cannot be requested programmatically at all** — they have no public intent,
 * so the only honest thing to do is name them and say exactly where they live.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen() {
    val context = LocalContext.current
    val settings by Store.settings.collectAsState()
    fun update(change: (Settings) -> Settings) = Store.putSettings(change(Store.settings.value))

    // Re-read the permission states periodically, so returning from a system settings screen
    // updates the ticks without needing to leave and re-enter the app.
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            refresh++
        }
    }
    val audioPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh++ }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        // -- ringing --------------------------------------------------------------------------
        SectionCard("When an alarm rings", AppIcons.AlarmClock) {
            SettingRow("Vibrate", "Off = sound only. It still vibrates if the sound can't play.") {
                Switch(
                    checked = settings.vibrate,
                    onCheckedChange = { on -> update { it.copy(vibrate = on) } },
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            SettingRow("Snooze for", "When you press Snooze") {
                Stepper(settings.snoozeMinutes, { v -> update { it.copy(snoozeMinutes = v) } }, 1..60, suffix = " min")
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Text(
                "If you don't press Stop",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                if (settings.comebackMinutes == 0) {
                    "It stays quiet until its next scheduled ring."
                } else {
                    "It rings again ${settings.comebackMinutes} min later, up to ${settings.comebackTimes} " +
                        "time${if (settings.comebackTimes == 1) "" else "s"}. Set minutes to 0 to switch this off."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            SettingRow("Come back after", null) {
                Stepper(settings.comebackMinutes, { v -> update { it.copy(comebackMinutes = v) } }, 0..60, suffix = " min")
            }
            if (settings.comebackMinutes > 0) {
                SettingRow("At most", null) {
                    Stepper(settings.comebackTimes, { v -> update { it.copy(comebackTimes = v) } }, 1..20, suffix = "×")
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            SettingRow("Max ring time", "For alarms set to keep ringing until stopped") {
                Stepper(
                    settings.ringSeconds / 60,
                    { v -> update { it.copy(ringSeconds = v * 60) } },
                    1..30,
                    suffix = " min",
                )
            }
        }

        // -- sleep ----------------------------------------------------------------------------
        SectionCard("“I will sleep now”", AppIcons.Moon) {
            Text(
                "Pauses every interval alarm for " +
                    "${formatInterval(settings.sleepMinutes / 60, settings.sleepMinutes % 60)}. " +
                    "Timers and stopwatches keep going.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            SettingRow("Hours", null) {
                Stepper(
                    settings.sleepMinutes / 60,
                    { v -> update { it.copy(sleepMinutes = v * 60 + it.sleepMinutes % 60) } },
                    0..23,
                )
            }
            SettingRow("Minutes", null) {
                Stepper(
                    settings.sleepMinutes % 60,
                    { v -> update { it.copy(sleepMinutes = (it.sleepMinutes / 60) * 60 + v) } },
                    0..55,
                    step = 5,
                )
            }
        }

        // -- look -----------------------------------------------------------------------------
        SectionCard("Look", AppIcons.Sun) {
            val modes = listOf(THEME_SYSTEM to "Phone", THEME_LIGHT to "Light", THEME_DARK to "Dark")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                modes.forEachIndexed { i, (mode, label) ->
                    SegmentedButton(
                        selected = settings.themeMode == mode,
                        onClick = { update { it.copy(themeMode = mode) } },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = modes.size),
                    ) { Text(label) }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Wallpaper colours", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Colour the app from your wallpaper instead of BroTimer's navy and amber",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = settings.wallpaperColors,
                        onCheckedChange = { on -> update { it.copy(wallpaperColors = on) } },
                    )
                }
            }
        }

        // -- permissions ----------------------------------------------------------------------
        // Keyed on `refresh` so the ticks re-evaluate after a trip to a system settings screen.
        val notificationsOk = remember(refresh) { NotificationManagerCompat.from(context).areNotificationsEnabled() }
        val fullScreenOk = remember(refresh) { canUseFullScreenIntent(context) }
        val exactOk = remember(refresh) { canScheduleExact(context) }
        val batteryOk = remember(refresh) { ignoresBatteryOptimisation(context) }
        val audioOk = remember(refresh) { SoundLibrary.hasAudioPermission(context) }

        SectionCard("Permissions", AppIcons.Check) {
            Text(
                "If an alarm does not appear on your lock screen, the reason is almost always one of these.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CheckRow(notificationsOk, "Notifications", "Without this there is no alarm notification, so no full-screen alarm.", "Open notification settings") {
                context.launch(
                    Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
                )
            }
            CheckRow(fullScreenOk, "Full-screen alarms", "Android 14+ treats this as a special permission. Without it the alarm is only a banner.", "Allow full-screen alarms") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    context.launch(
                        Intent(AndroidSettings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                            .setData(Uri.parse("package:${context.packageName}"))
                    )
                } else {
                    context.openAppDetails()
                }
            }
            CheckRow(exactOk, "Exact alarms", "Alarms must fire at the exact minute, not whenever the system feels like it.", "Allow exact alarms") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.launch(Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                } else {
                    context.openAppDetails()
                }
            }
            CheckRow(batteryOk, "Battery optimisation off", "Otherwise Android can freeze BroTimer in the background between alarms.", "Turn off battery optimisation") {
                @Suppress("BatteryLife")
                context.launch(
                    Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        .setData(Uri.parse("package:${context.packageName}"))
                )
            }
            CheckRow(audioOk, "Audio files (optional)", "Lets the sound picker list Zedge downloads and other audio on your phone.", "Allow audio files") {
                audioPermission.launch(SoundLibrary.audioPermission)
            }
        }

        // -- the OEM part nobody can automate ---------------------------------------------------
        SectionCard("Xiaomi / HyperOS — by hand", AppIcons.Alert) {
            Text(
                "These have no setting an app is allowed to open or change, so they must be switched on " +
                    "manually. On this phone they are the most common reason an alarm never shows.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            ManualStep("1", "Autostart → ON", "Apps → Manage apps → BroTimer → Autostart")
            ManualStep("2", "Show on Lock screen → Allow", "Apps → Manage apps → BroTimer → Other permissions")
            ManualStep("3", "Display pop-up windows while running in background → Allow", "Same Other permissions page")
            ManualStep("4", "Battery saver → No restrictions", "Apps → Manage apps → BroTimer → Battery saver")
            ManualStep("5", "Lock BroTimer in Recents", "Open Recents, hold the BroTimer card, tap the padlock")
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { context.openAppDetails() }, modifier = Modifier.fillMaxWidth()) {
                Text("Open BroTimer's app info page")
            }
        }

        // Which build this is — so a copy installed from GitHub Releases can be compared with
        // the latest one there.
        val version = remember {
            runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
                .getOrNull()
                .orEmpty()
        }
        Text(
            "BroTimer $version · github.com/omar1001/bro-timer",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp),
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String?, control: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        control()
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
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = if (ok) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                if (ok) AppIcons.Check else AppIcons.Alert,
                contentDescription = if (ok) "OK" else "Needs attention",
                tint = if (ok) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(7.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!ok) {
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = onFix) { Text(buttonText) }
            }
        }
    }
}

@Composable
private fun ManualStep(number: String, what: String, where: String) {
    Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.size(26.dp)) {
            Text(
                number,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.padding(top = 3.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        Column {
            Text(what, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(where, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
