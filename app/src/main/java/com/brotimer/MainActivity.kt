package com.brotimer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.brotimer.alarm.AlarmService
import com.brotimer.alarm.Scheduler
import com.brotimer.data.Store
import com.brotimer.ui.App
import com.brotimer.ui.BroTimerTheme

class MainActivity : ComponentActivity() {

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* Setup tab reports the result */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        AlarmService.ensureChannel(this)
        askForNotificationsOnce()

        setContent {
            BroTimerTheme {
                App()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Cheap self-heal: if anything ever drifted — a killed process, a restored backup, a
        // system that dropped an alarm — opening the app puts the alarm table back in order.
        Scheduler.rescheduleAll(this)
    }

    private fun askForNotificationsOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
