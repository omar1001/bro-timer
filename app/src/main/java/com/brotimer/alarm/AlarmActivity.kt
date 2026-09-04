package com.brotimer.alarm

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.addCallback
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.brotimer.data.Store
import com.brotimer.ui.formatClock
import kotlinx.coroutines.delay

/**
 * The screen you actually see when an alarm goes off — over the lock screen, with the screen
 * turned on for you.
 *
 * It does not play the sound; [AlarmService] does. This activity is the visible half, and it is
 * allowed to never appear (see the note in [AlarmService]) without the alarm going silent.
 */
class AlarmActivity : ComponentActivity() {

    private var label by mutableStateOf("Reminder")

    /** The ring can end from the notification buttons or the give-up timer, not just from here. */
    private val ringEnded = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        showOverLockScreen()

        label = intent?.getStringExtra(AlarmService.EXTRA_LABEL).orEmpty().ifBlank { "Reminder" }

        // An alarm screen must not be dismissible with Back — that would leave it ringing with
        // nothing on screen to stop it.
        onBackPressedDispatcher.addCallback(this) { /* deliberately ignored */ }

        ContextCompat.registerReceiver(
            this,
            ringEnded,
            IntentFilter(AlarmService.ACTION_RING_ENDED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        setContent {
            RingScreen(
                label = label,
                snoozeMinutes = Store.settings.value.snoozeMinutes,
                onSnooze = { send(AlarmService.ACTION_SNOOZE) },
                onStop = { send(AlarmService.ACTION_STOP) },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        label = intent.getStringExtra(AlarmService.EXTRA_LABEL).orEmpty().ifBlank { "Reminder" }
    }

    private fun send(action: String) {
        startService(Intent(this, AlarmService::class.java).setAction(action))
        finish()
    }

    private fun showOverLockScreen() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            getSystemService(KeyguardManager::class.java)?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(ringEnded) }
        super.onDestroy()
    }
}

@Composable
private fun RingScreen(
    label: String,
    snoozeMinutes: Int,
    onSnooze: () -> Unit,
    onStop: () -> Unit,
) {
    // Fixed dark palette rather than the app theme: this screen is read at 3am, half awake,
    // and should look identical whatever the system theme is doing.
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101418)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = formatClock(now),
                color = Color(0xFF9FB3C8),
                fontSize = 22.sp,
            )
            Spacer(Modifier.height(28.dp))
            Text(
                text = label,
                color = Color.White,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 46.sp,
            )
            Spacer(Modifier.height(56.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Button(
                    onClick = onSnooze,
                    modifier = Modifier
                        .weight(1f)
                        .height(84.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF33475F),
                        contentColor = Color.White,
                    ),
                ) {
                    Text("Snooze\n$snoozeMinutes min", textAlign = TextAlign.Center, fontSize = 18.sp)
                }
                Button(
                    onClick = onStop,
                    modifier = Modifier
                        .weight(1f)
                        .height(84.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFB4402F),
                        contentColor = Color.White,
                    ),
                ) {
                    Text("Stop", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
