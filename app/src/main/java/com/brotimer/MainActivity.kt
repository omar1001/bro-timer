package com.brotimer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import com.brotimer.alarm.AlarmService
import com.brotimer.alarm.Scheduler
import com.brotimer.data.Store
import com.brotimer.ui.App
import com.brotimer.ui.BroTimerTheme
import com.brotimer.ui.receiveSharedSound
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* Setup tab reports the result */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Store.init(this)
        AlarmService.ensureChannel(this)
        askForNotificationsOnce()
        // Only on a fresh start: a rotation recreates the activity with the same share intent,
        // which must not import the sound a second time.
        if (savedInstanceState == null) handleShare(intent)

        setContent {
            BroTimerTheme {
                App()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    override fun onStart() {
        super.onStart()
        // Cheap self-heal: if anything ever drifted — a killed process, a restored backup, a
        // system that dropped an alarm — opening the app puts the alarm table back in order.
        Scheduler.rescheduleAll(this)
    }

    /** Share → BroTimer from WhatsApp, Telegram, Files, a voice recorder… */
    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val type = intent.type.orEmpty()
        if (!type.startsWith("audio/") && type != "application/ogg") return
        val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java) ?: return
        // Copy it straight away: the sender's read permission only lasts while this activity does.
        lifecycleScope.launch { receiveSharedSound(this@MainActivity, uri) }
    }

    private fun askForNotificationsOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
