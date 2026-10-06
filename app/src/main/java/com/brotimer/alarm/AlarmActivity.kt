package com.brotimer.alarm

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brotimer.data.Store
import com.brotimer.ui.AppIcons
import com.brotimer.ui.TabularDigits
import com.brotimer.ui.formatClock
import com.brotimer.ui.formatCountdown
import com.brotimer.ui.formatDate
import com.brotimer.ui.formatInterval
import kotlinx.coroutines.delay

private val Amber = Color(0xFFF4BF48)
private val Navy = Color(0xFF1B2A4A)
private val Mist = Color(0xFF9FB3C8)

/**
 * The screen you actually see when an alarm goes off — over the lock screen, with the screen
 * turned on for you.
 *
 * It does not play the sound; [AlarmService] does. This activity is the visible half, and it is
 * allowed to never appear (see the note in [AlarmService]) without the alarm going silent. It
 * shows whatever [RingState] says is ringing, and closes itself the moment nothing is.
 *
 * **It must not ask to unlock the phone.** Until 2026-10-06 it called
 * `KeyguardManager.requestDismissKeyguard`, which on a PIN-locked phone (Omar's is) raises the PIN
 * pad over the alarm — covering Stop and Snooze. Stop and Snooze work on the lock screen without
 * unlocking, like any alarm clock.
 */
class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        Store.init(this)
        showOverLockScreen()

        // An alarm screen must not be dismissible with Back — that would leave it ringing with
        // nothing on screen to stop it.
        onBackPressedDispatcher.addCallback(this) { /* deliberately ignored */ }

        setContent {
            val ring by RingState.current.collectAsState()
            // Ended from the notification, by itself, or never started: nothing to show.
            LaunchedEffect(ring == null) { if (ring == null) finish() }
            ring?.let {
                RingScreen(
                    ring = it,
                    onSnooze = { send(AlarmService.ACTION_SNOOZE) },
                    onStop = { send(AlarmService.ACTION_STOP) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The floating Snooze/Stop card sits exactly where this screen's own buttons are.
        AlarmService.running?.hideFloatingCard()
    }

    override fun onStop() {
        super.onStop()
        // Left the alarm screen (Home, another app) while it is still ringing: bring the card back,
        // so Stop stays one tap away.
        if (!isFinishing && !isChangingConfigurations && RingState.current.value != null) {
            AlarmService.running?.restoreFloatingCard()
        }
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
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
    }
}

@Composable
private fun RingScreen(
    ring: RingState.Ring,
    onSnooze: () -> Unit,
    onStop: () -> Unit,
) {
    val settings by Store.settings.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val subtitle = remember(ring.id) {
        Store.alarm(ring.id)?.let { "Every ${formatInterval(it.hours, it.minutes)}" }
            ?: Store.timer(ring.id)?.let { "${formatCountdown(it.durationMs)} timer" }
            ?: ""
    }

    // Fixed night palette rather than the app theme: this screen is read at 3am, half awake,
    // and should look identical whatever the theme setting is.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF13234A), Color(0xFF070B14)))),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 28.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(0.5f))
            Text(
                formatClock(now),
                style = TabularDigits,
                fontSize = 68.sp,
                fontWeight = FontWeight.Light,
                color = Color.White,
            )
            Text(formatDate(now), fontSize = 16.sp, color = Mist)

            Spacer(Modifier.weight(0.45f))
            Pulse()
            Spacer(Modifier.weight(0.45f))

            Text(
                ring.label,
                fontSize = 34.sp,
                lineHeight = 40.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            val back = if (ring.attempt > 0) "Back again · ${ring.attempt} of ${settings.comebackTimes}" else ""
            val line = listOf(subtitle, back).filter { it.isNotEmpty() }.joinToString("  ·  ")
            if (line.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(line, fontSize = 15.sp, color = Mist, textAlign = TextAlign.Center)
            }

            Spacer(Modifier.height(22.dp))
            if (ring.repeatCount > 0) {
                Text(
                    "Playing ${minOf(ring.playsDone + 1, ring.repeatCount)} of ${ring.repeatCount}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { ring.playsDone.toFloat() / ring.repeatCount },
                    color = Amber,
                    trackColor = Color.White.copy(alpha = 0.15f),
                    modifier = Modifier
                        .width(220.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                )
            } else {
                Text("Ringing until you stop it", fontSize = 15.sp, color = Color.White)
            }
            val comeback = when {
                settings.comebackMinutes <= 0 -> ""
                ring.attempt >= settings.comebackTimes -> "Last reminder — it won't come back after this"
                else -> {
                    val left = settings.comebackTimes - ring.attempt
                    "Not stopped? It rings again in ${settings.comebackMinutes} min " +
                        "($left more time${if (left == 1) "" else "s"})"
                }
            }
            if (comeback.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(comeback, fontSize = 13.sp, color = Mist, textAlign = TextAlign.Center)
            }

            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Button(
                    onClick = onSnooze,
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.12f),
                        contentColor = Color.White,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(76.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Snooze", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Text("${settings.snoozeMinutes} min", fontSize = 13.sp, color = Mist)
                    }
                }
                Button(
                    onClick = onStop,
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color(0xFF2A1D00)),
                    modifier = Modifier
                        .weight(1f)
                        .height(76.dp),
                ) {
                    Icon(AppIcons.StopSquare, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Stop", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** Amber rings rippling out from the alarm clock, so the screen reads as "ringing" even muted. */
@Composable
private fun Pulse() {
    val transition = rememberInfiniteTransition(label = "pulse")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
        label = "phase",
    )
    Box(Modifier.size(210.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val maxRadius = size.minDimension / 2f
            for (i in 0 until 3) {
                val p = (phase + i / 3f) % 1f
                drawCircle(
                    color = Amber.copy(alpha = (1f - p) * 0.32f),
                    radius = maxRadius * (0.38f + 0.62f * p),
                )
            }
        }
        Surface(shape = CircleShape, color = Amber, modifier = Modifier.size(92.dp)) {
            Icon(
                AppIcons.AlarmClock,
                contentDescription = null,
                tint = Navy,
                modifier = Modifier.padding(22.dp),
            )
        }
    }
}
