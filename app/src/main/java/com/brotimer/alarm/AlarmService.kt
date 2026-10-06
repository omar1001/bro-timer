package com.brotimer.alarm

import android.app.KeyguardManager
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
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings as AndroidSettings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.brotimer.R
import com.brotimer.data.SoundLibrary
import com.brotimer.data.Store
import com.brotimer.model.KEEP_RINGING
import com.brotimer.model.nextFireAt

/**
 * Owns the ringing: sound, vibration, the full-screen notification, the give-up timer, and the
 * come-back when nobody answers.
 *
 * **Why a service and not just [AlarmActivity]:** on Android 14+ a full-screen intent can be
 * downgraded to a heads-up notification (when `canUseFullScreenIntent()` is false, or whenever
 * the phone is unlocked and in use). In that case the activity never launches. If the activity
 * owned the sound, that alarm would be silent. The service always runs, so the alarm is always
 * audible.
 *
 * **How a ring ends** — exactly one of:
 * - **Stop** — done until the next scheduled ring. Also cancels any pending come-back.
 * - **Snooze** — rings again after the snooze length.
 * - **Unanswered** — the sound played its set number of times, or (when set to keep ringing)
 *   the give-up time passed. It comes back after `comebackMinutes`, up to `comebackTimes` times.
 * - **Replaced** — another alarm started ringing. Treated as unanswered.
 *
 * Only one alarm rings at a time.
 */
class AlarmService : Service() {

    companion object {
        const val ACTION_RING = "com.brotimer.action.RING"
        const val ACTION_STOP = "com.brotimer.action.STOP"
        const val ACTION_SNOOZE = "com.brotimer.action.SNOOZE"

        const val EXTRA_ID = "com.brotimer.extra.RING_ID"
        const val EXTRA_LABEL = "com.brotimer.extra.RING_LABEL"
        const val EXTRA_SOUND = "com.brotimer.extra.RING_SOUND"
        const val EXTRA_REPEAT = "com.brotimer.extra.RING_REPEAT"
        const val EXTRA_ATTEMPT = "com.brotimer.extra.RING_ATTEMPT"

        private const val CHANNEL_ID = "brotimer_alarms"
        private const val NOTIF_ID = 41

        /**
         * Safety net for counted rings only — a 10-minute song set to play 99 times still ends.
         * Deliberately not a setting: nobody should ever reach it.
         */
        private const val MAX_COUNTED_RING_SECONDS = 30 * 60

        /**
         * The live instance, so [AlarmActivity] can hide the floating card while the full alarm
         * screen is up (both have their buttons at the bottom) and bring it back when you leave.
         * Same process, main thread only; cleared in onDestroy. A direct call instead of a
         * startService intent, which Android refuses from the background.
         */
        @Volatile
        internal var running: AlarmService? = null
            private set

        fun ring(
            context: Context,
            id: Int,
            label: String,
            soundUri: String?,
            repeatCount: Int,
            attempt: Int,
        ) {
            val intent = Intent(context, AlarmService::class.java).apply {
                action = ACTION_RING
                putExtra(EXTRA_ID, id)
                putExtra(EXTRA_LABEL, label)
                putExtra(EXTRA_SOUND, soundUri)
                putExtra(EXTRA_REPEAT, repeatCount)
                putExtra(EXTRA_ATTEMPT, attempt)
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
                // The service plays the sound and drives the vibrator itself, so that it can count
                // plays and cut them off. Letting the channel do it too would double up.
                setSound(null, null)
                enableVibration(false)
                setBypassDnd(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private enum class End { STOPPED, SNOOZED, UNANSWERED, REPLACED }

    private var player: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private var giveUp: Runnable? = null
    private var wrapWatch: Runnable? = null
    private var lastStartId = 0

    private var ring: RingState.Ring? = null
    private var plays = 0
    private var lastPosition = 0
    private var clipMs = 0

    private val overlay by lazy {
        RingOverlay(
            service = this,
            onSnooze = { end(End.SNOOZED) },
            onStop = { end(End.STOPPED) },
            onOpen = { openAlarmScreen() },
        )
    }

    override fun onCreate() {
        super.onCreate()
        running = this
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        Store.init(this)
        ensureChannel(this)

        when (intent?.action) {
            ACTION_RING -> startRinging(intent)
            ACTION_SNOOZE -> end(End.SNOOZED)
            ACTION_STOP -> end(End.STOPPED)
            // Restarted by the system with no intent, or an unknown action: nothing to ring.
            else -> end(End.STOPPED)
        }
        return START_NOT_STICKY
    }

    private fun startRinging(intent: Intent) {
        val id = intent.getIntExtra(EXTRA_ID, -1)
        val label = intent.getStringExtra(EXTRA_LABEL).orEmpty().ifBlank { "Reminder" }
        val sound = intent.getStringExtra(EXTRA_SOUND)
        val repeat = intent.getIntExtra(EXTRA_REPEAT, KEEP_RINGING).coerceAtLeast(0)
        val attempt = intent.getIntExtra(EXTRA_ATTEMPT, 0)

        // Still ringing. A *different* alarm went unanswered, so it gets its come-back before it is
        // replaced. The *same* alarm ringing again (its come-back colliding with its next scheduled
        // ring) is simply superseded — the new ring is the reminder.
        ring?.let { old -> end(if (old.id == id) End.STOPPED else End.REPLACED, keepService = true) }

        val r = RingState.Ring(id, label, repeat, playsDone = 0, attempt = attempt)
        ring = r
        RingState.set(r)

        // startForeground must happen fast, before anything that can block or throw.
        val notification = buildNotification(r)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this, NOTIF_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }

        val playing = startSound(sound, repeat)
        if (!playing && repeat > 0) {
            // Nothing to count, so do not show "Playing 1 of 10" forever: it is a vibrate-only
            // ring that ends at the keep-ringing limit.
            val vibrateOnly = r.copy(repeatCount = KEEP_RINGING)
            ring = vibrateOnly
            RingState.set(vibrateOnly)
            refreshNotification(vibrateOnly)
        }
        // A counted ring ends when the count is reached; the cap is only a safety net. A ring that
        // loops — or that has no playable sound at all, so nothing can be counted — gives up at
        // the "keep ringing" limit from Settings.
        val capSeconds =
            if (playing && repeat > 0) MAX_COUNTED_RING_SECONDS
            else Store.settings.value.ringSeconds.coerceIn(10, 3600)

        acquireWakeLock(capSeconds)
        // Vibration can be switched off in Setup — unless there is no sound, in which case it is
        // the only thing left that can wake anyone up.
        if (Store.settings.value.vibrate || !playing) startVibrating()

        val task = Runnable {
            Log.i(Scheduler.TAG, "ring $id gave up after ${capSeconds}s")
            end(End.UNANSWERED)
        }
        giveUp = task
        handler.postDelayed(task, capSeconds * 1000L)

        showFloatingCardIfInUse()

        Log.i(
            Scheduler.TAG,
            "ringing $id '$label' repeat=${if (repeat > 0) repeat else "keep"} attempt=$attempt " +
                "clip=${clipMs}ms cap=${capSeconds}s",
        )
    }

    // -- sound -------------------------------------------------------------------------------

    /** Returns true if a sound is actually playing (false = vibrate only). */
    private fun startSound(soundUri: String?, repeat: Int): Boolean {
        plays = 0
        lastPosition = 0
        val mp = openPlayer(soundUri) ?: return false
        player = mp
        clipMs = runCatching { mp.duration }.getOrDefault(0).coerceAtLeast(0)

        if (repeat <= 0) {
            mp.isLooping = true
            mp.start()
            return true
        }
        mp.isLooping = false
        mp.setOnCompletionListener { onPlayFinished(restart = true) }
        mp.start()
        watchForSelfLooping()
        return true
    }

    private fun openPlayer(soundUri: String?): MediaPlayer? {
        val candidates = listOfNotNull(
            soundUri?.let { SoundLibrary.resolve(this, Uri.parse(it)) },
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            AndroidSettings.System.DEFAULT_ALARM_ALERT_URI,
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
        )
        for (uri in candidates) {
            val mp = MediaPlayer()
            try {
                mp.setAudioAttributes(
                    AudioAttributes.Builder()
                        // USAGE_ALARM puts this on the alarm stream, so it is audible even when
                        // the phone is on silent or vibrate.
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                mp.setDataSource(this, uri)
                mp.prepare()
                return mp
            } catch (e: Exception) {
                Log.w(Scheduler.TAG, "ringtone $uri failed, trying next", e)
                runCatching { mp.release() }
            }
        }
        Log.e(Scheduler.TAG, "no usable ringtone; alarm will vibrate only")
        return null
    }

    /**
     * One play of the sound has finished. [restart] is true when MediaPlayer reported completion
     * and must be started again; false when a self-looping file wrapped around and is already
     * playing its next round (seeking back there would replay its first fraction of a second).
     */
    private fun onPlayFinished(restart: Boolean) {
        val r = ring ?: return
        plays++
        val updated = r.copy(playsDone = plays)
        ring = updated
        RingState.set(updated)

        if (plays >= r.repeatCount) {
            Log.i(Scheduler.TAG, "ring ${r.id} played $plays/${r.repeatCount}, unanswered")
            end(End.UNANSWERED)
            return
        }
        refreshNotification(updated)
        if (restart) {
            lastPosition = 0
            player?.let { mp -> runCatching { mp.seekTo(0); mp.start() } }
        }
    }

    /**
     * Some system tones are OGG files tagged `ANDROID_LOOP`: MediaPlayer loops them by itself and
     * never reports them as complete, so the completion listener alone would never count a single
     * play. For those, notice the play position jumping back to the start instead.
     *
     * Ordinary files (anything from Zedge, WhatsApp, Downloads) complete normally and are counted
     * by the listener; this watcher then never sees a backwards jump, because a restart resets
     * [lastPosition] to 0 first.
     */
    private fun watchForSelfLooping() {
        val poll = if (clipMs > 0) (clipMs / 4).coerceIn(20, 200).toLong() else 200L
        val slack = (clipMs / 2).coerceAtLeast(150)
        val task = object : Runnable {
            override fun run() {
                if (wrapWatch !== this) return
                val mp = player ?: return
                val pos = runCatching { if (mp.isPlaying) mp.currentPosition else -1 }.getOrDefault(-1)
                if (pos >= 0) {
                    if (pos + slack < lastPosition) {
                        Log.i(Scheduler.TAG, "self-looping sound wrapped ($lastPosition -> $pos ms): counting a play")
                        lastPosition = pos
                        onPlayFinished(restart = false)
                    } else {
                        lastPosition = pos
                    }
                }
                if (wrapWatch === this) handler.postDelayed(this, poll)
            }
        }
        wrapWatch = task
        handler.postDelayed(task, poll)
    }

    private fun stopSound() {
        runCatching {
            player?.let {
                it.setOnCompletionListener(null)
                if (it.isPlaying) it.stop()
                it.release()
            }
        }
        player = null
    }

    // -- notification ------------------------------------------------------------------------

    private fun buildNotification(r: RingState.Ring): Notification {
        val full = Intent(this, AlarmActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_ID, r.id)
            putExtra(EXTRA_LABEL, r.label)
        }
        val fullPi = PendingIntent.getActivity(
            this, 100 + r.id, full,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val snoozePi = PendingIntent.getService(
            this, 200 + r.id, Intent(this, AlarmService::class.java).setAction(ACTION_SNOOZE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopPi = PendingIntent.getService(
            this, 300 + r.id, Intent(this, AlarmService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val snoozeMinutes = Store.settings.value.snoozeMinutes

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(r.label)
            .setContentText(statusText(r))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            // Progress updates ("Playing 3 of 10") must not re-alert or re-launch the screen.
            .setOnlyAlertOnce(true)
            .setContentIntent(fullPi)
            .setFullScreenIntent(fullPi, true)
            .addAction(0, "Snooze ${snoozeMinutes}m", snoozePi)
            .addAction(0, "Stop", stopPi)
            .build()
    }

    private fun statusText(r: RingState.Ring): String {
        val playing =
            if (r.repeatCount > 0) "Playing ${minOf(r.playsDone + 1, r.repeatCount)} of ${r.repeatCount}"
            else "Ringing"
        val s = Store.settings.value
        val after = when {
            s.comebackMinutes <= 0 -> ""
            r.attempt >= s.comebackTimes -> " · last reminder"
            else -> " · back in ${s.comebackMinutes} min if not stopped"
        }
        return playing + after
    }

    private fun refreshNotification(r: RingState.Ring) {
        runCatching {
            getSystemService(NotificationManager::class.java)?.notify(NOTIF_ID, buildNotification(r))
        }
        if (overlay.isShowing) overlay.update(r.label, statusText(r), Store.settings.value.snoozeMinutes)
    }

    // -- stay-on-screen card -----------------------------------------------------------------

    /**
     * Only while the phone is awake, unlocked and in use: that is exactly when Android shows the
     * alarm as a banner that slides away. Asleep or locked, the full-screen alarm shows instead.
     */
    private fun shouldFloat(): Boolean {
        if (!Store.settings.value.stayOnScreen || !overlay.canShow()) return false
        val awake = getSystemService(PowerManager::class.java)?.isInteractive == true
        val locked = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
        return awake && !locked
    }

    private fun showFloatingCardIfInUse() {
        val r = ring ?: return
        if (shouldFloat()) overlay.show(r.label, statusText(r), Store.settings.value.snoozeMinutes)
    }

    /** Called by [AlarmActivity] while it is on screen: its own buttons are where the card sits. */
    internal fun hideFloatingCard() = overlay.hide()

    /** Called by [AlarmActivity] when you leave it while the alarm is still ringing. */
    internal fun restoreFloatingCard() = showFloatingCardIfInUse()

    private fun openAlarmScreen() {
        // Start the activity *before* removing the card: a visible window of ours is what lets
        // Android open an activity from a background service.
        runCatching {
            startActivity(
                Intent(this, AlarmActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        }.onFailure { Log.w(Scheduler.TAG, "could not open the alarm screen from the card", it) }
        overlay.hide()
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
            // Repeat from index 0 = vibrate until cancelled.
            val effect = VibrationEffect.createWaveform(longArrayOf(0, 700, 800), 0)
            // Declaring alarm usage is what lets the vibration through Do Not Disturb.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                vib.vibrate(
                    effect,
                    VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM),
                )
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(
                    effect,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
            }
        }
    }

    private fun stopVibrating() {
        runCatching { vibrator()?.cancel() }
    }

    // -- wake lock ---------------------------------------------------------------------------

    private fun acquireWakeLock(seconds: Int) {
        releaseWakeLock()
        val pm = getSystemService(PowerManager::class.java) ?: return
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BroTimer:ring").apply {
            setReferenceCounted(false)
            // Timeout is a safety net: if anything below leaks, the lock still expires.
            acquire(seconds * 1000L + 10_000L)
        }
    }

    private fun releaseWakeLock() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
    }

    // -- ending ------------------------------------------------------------------------------

    private fun end(reason: End, keepService: Boolean = false) {
        val r = ring
        giveUp?.let(handler::removeCallbacks)
        giveUp = null
        wrapWatch?.let(handler::removeCallbacks)
        wrapWatch = null
        stopSound()
        stopVibrating()
        overlay.hide()
        ring = null

        if (r != null) {
            when (reason) {
                End.SNOOZED -> {
                    val minutes = Store.settings.value.snoozeMinutes.coerceIn(1, 180)
                    Scheduler.scheduleSnooze(
                        this, r.id, System.currentTimeMillis() + minutes * 60_000L, attempt = 0,
                    )
                }
                // Stop means "done with this one until its next scheduled ring": a come-back left
                // over from an earlier, replaced ring of the same alarm must not ring after it.
                End.STOPPED -> Scheduler.cancelSnooze(this, r.id)
                End.UNANSWERED, End.REPLACED -> comeBack(r)
            }
            Log.i(Scheduler.TAG, "ring ${r.id} ended: $reason after $plays play(s)")
        }

        if (keepService) return
        RingState.set(null)
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf(lastStartId)
    }

    /** Nobody answered: ring again after a while, a limited number of times. */
    private fun comeBack(r: RingState.Ring) {
        val s = Store.settings.value
        if (s.comebackMinutes <= 0 || r.attempt >= s.comebackTimes) {
            Log.i(Scheduler.TAG, "ring ${r.id}: no come-back (attempt ${r.attempt} of ${s.comebackTimes})")
            return
        }
        val now = System.currentTimeMillis()
        val at = now + s.comebackMinutes * 60_000L
        val alarm = Store.alarm(r.id)
        when {
            alarm != null -> {
                if (!alarm.enabled || s.isSleeping(now)) return
                // The alarm's own next ring comes first anyway — it will do the reminding.
                if (alarm.nextFireAt(now) in 1..at) return
            }
            Store.timer(r.id) == null -> return // deleted while ringing
        }
        Scheduler.scheduleSnooze(this, r.id, at, attempt = r.attempt + 1)
        Log.i(Scheduler.TAG, "ring ${r.id}: comes back at $at (come-back ${r.attempt + 1} of ${s.comebackTimes})")
    }

    override fun onDestroy() {
        if (running === this) running = null
        giveUp?.let(handler::removeCallbacks)
        wrapWatch?.let(handler::removeCallbacks)
        stopSound()
        stopVibrating()
        overlay.hide()
        releaseWakeLock()
        if (ring != null) {
            ring = null
            RingState.set(null)
        }
        super.onDestroy()
    }
}
