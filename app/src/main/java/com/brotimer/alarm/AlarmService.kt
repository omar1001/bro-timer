package com.brotimer.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings as AndroidSettings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.brotimer.R
import com.brotimer.data.Store

/**
 * Owns the ringing: sound, vibration, the full-screen notification, and the give-up timer.
 *
 * **Why a service and not just [AlarmActivity]:** on Android 14+ a full-screen intent can be
 * downgraded to a heads-up notification (when `canUseFullScreenIntent()` is false, or at the
 * system's discretion). In that case the activity never launches. If the activity owned the
 * sound, that alarm would be silent. The service always runs, so the alarm is always audible.
 *
 * Only one alarm rings at a time. A second alarm arriving while one is ringing replaces it.
 */
class AlarmService : Service() {

    companion object {
        const val ACTION_RING = "com.brotimer.action.RING"
        const val ACTION_STOP = "com.brotimer.action.STOP"
        const val ACTION_SNOOZE = "com.brotimer.action.SNOOZE"

        /** Broadcast so a visible [AlarmActivity] can close itself when the ring ends elsewhere. */
        const val ACTION_RING_ENDED = "com.brotimer.action.RING_ENDED"

        const val EXTRA_ID = "com.brotimer.extra.RING_ID"
        const val EXTRA_LABEL = "com.brotimer.extra.RING_LABEL"
        const val EXTRA_SOUND = "com.brotimer.extra.RING_SOUND"

        private const val CHANNEL_ID = "brotimer_alarms"
        private const val NOTIF_ID = 41

        fun ring(context: Context, id: Int, label: String, soundUri: String?) {
            val intent = Intent(context, AlarmService::class.java).apply {
                action = ACTION_RING
                putExtra(EXTRA_ID, id)
                putExtra(EXTRA_LABEL, label)
                putExtra(EXTRA_SOUND, soundUri)
            }
            try {
                context.startForegroundService(intent)
            } catch (e: Exception) {
                // Should not happen: a broadcast delivered by an exact alarm is allowed to start a
                // foreground service. Log loudly rather than fail silently.
                Log.e(Scheduler.TAG, "could not start AlarmService", e)
            }
        }

        fun ensureChannel(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Alarms",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Interval alarms and timers"
                // The service plays the sound and drives the vibrator itself, so that it can loop
                // them and cut them off at the give-up time. Letting the channel do it too would
                // double up.
                setSound(null, null)
                enableVibration(false)
                setBypassDnd(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private var player: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private var giveUp: Runnable? = null

    private var ringingId: Int = -1
    private var ringingLabel: String = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Store.init(this)
        ensureChannel(this)

        when (intent?.action) {
            ACTION_RING -> startRinging(intent)
            ACTION_SNOOZE -> snooze()
            ACTION_STOP -> stopRinging()
            else -> {
                // Restarted by the system with no intent, or an unknown action: nothing to ring.
                stopRinging()
            }
        }
        return START_NOT_STICKY
    }

    private fun startRinging(intent: Intent) {
        val id = intent.getIntExtra(EXTRA_ID, -1)
        val label = intent.getStringExtra(EXTRA_LABEL).orEmpty().ifBlank { "Reminder" }
        val sound = intent.getStringExtra(EXTRA_SOUND)

        ringingId = id
        ringingLabel = label

        // startForeground must happen fast, before anything that can block or throw.
        val notification = buildNotification(id, label)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this, NOTIF_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }

        val ringSeconds = Store.settings.value.ringSeconds.coerceIn(10, 3600)
        acquireWakeLock(ringSeconds)
        stopSound()
        playSound(sound)
        startVibrating()

        giveUp?.let(handler::removeCallbacks)
        val task = Runnable {
            Log.i(Scheduler.TAG, "ring $id gave up after ${ringSeconds}s")
            stopRinging()
        }
        giveUp = task
        handler.postDelayed(task, ringSeconds * 1000L)

        Log.i(Scheduler.TAG, "ringing $id '$label' for up to ${ringSeconds}s")
    }

    private fun buildNotification(id: Int, label: String): Notification {
        val full = Intent(this, AlarmActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_ID, id)
            putExtra(EXTRA_LABEL, label)
        }
        val fullPi = PendingIntent.getActivity(
            this, 100 + id, full,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val snoozePi = PendingIntent.getService(
            this, 200 + id, Intent(this, AlarmService::class.java).setAction(ACTION_SNOOZE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopPi = PendingIntent.getService(
            this, 300 + id, Intent(this, AlarmService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val snoozeMinutes = Store.settings.value.snoozeMinutes

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(label)
            .setContentText("BroTimer")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(fullPi)
            .setFullScreenIntent(fullPi, true)
            .addAction(0, "Snooze ${snoozeMinutes}m", snoozePi)
            .addAction(0, "Stop", stopPi)
            .build()
    }

    // -- sound -------------------------------------------------------------------------------

    private fun playSound(soundUri: String?) {
        val candidates = listOfNotNull(
            soundUri?.let(Uri::parse),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            AndroidSettings.System.DEFAULT_ALARM_ALERT_URI,
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
        )
        for (uri in candidates) {
            try {
                player = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            // USAGE_ALARM puts this on the alarm stream, so it is audible even
                            // when the phone is on silent or vibrate.
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    setDataSource(this@AlarmService, uri)
                    isLooping = true
                    prepare()
                    start()
                }
                return
            } catch (e: Exception) {
                Log.w(Scheduler.TAG, "ringtone $uri failed, trying next", e)
                runCatching { player?.release() }
                player = null
            }
        }
        Log.e(Scheduler.TAG, "no usable ringtone; alarm will vibrate only")
    }

    private fun stopSound() {
        runCatching {
            player?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        }
        player = null
    }

    // -- vibration ---------------------------------------------------------------------------

    private fun vibrator(): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }

    private fun startVibrating() {
        val vib = vibrator() ?: return
        runCatching {
            val pattern = longArrayOf(0, 700, 800)
            vib.vibrate(
                VibrationEffect.createWaveform(pattern, 0),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }
    }

    private fun stopVibrating() {
        runCatching { vibrator()?.cancel() }
    }

    // -- wake lock ---------------------------------------------------------------------------

    private fun acquireWakeLock(ringSeconds: Int) {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(PowerManager::class.java) ?: return
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BroTimer:ring").apply {
            setReferenceCounted(false)
            // Timeout is a safety net: if anything below leaks, the lock still expires.
            acquire(ringSeconds * 1000L + 10_000L)
        }
    }

    private fun releaseWakeLock() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
    }

    // -- ending ------------------------------------------------------------------------------

    private fun snooze() {
        val id = ringingId
        if (id >= 0) {
            val minutes = Store.settings.value.snoozeMinutes.coerceIn(1, 180)
            Scheduler.scheduleSnooze(this, id, System.currentTimeMillis() + minutes * 60_000L)
        }
        stopRinging()
    }

    private fun stopRinging() {
        giveUp?.let(handler::removeCallbacks)
        giveUp = null
        stopSound()
        stopVibrating()
        releaseWakeLock()
        // Tell a visible AlarmActivity to close — the ring may have been stopped from the
        // notification, or by the give-up timer, rather than by the on-screen buttons.
        sendBroadcast(Intent(ACTION_RING_ENDED).setPackage(packageName))
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        ringingId = -1
        stopSelf()
    }

    override fun onDestroy() {
        giveUp?.let(handler::removeCallbacks)
        stopSound()
        stopVibrating()
        releaseWakeLock()
        super.onDestroy()
    }
}
