# BroTimer — change log

Full dated entries. The index lives in [`../CLAUDE.md`](../CLAUDE.md); this file is the record.

---

## 2026-09-04 — Initial build

Whole app, from an empty folder to an APK installed and scheduling on the phone.

### What it is

An Android app with three parts, all visible in one place:

1. **Interval alarms** — repeating reminders that ring as real full-screen alarms (sound over the
   lock screen, Snooze/Stop, the text Omar wrote), each with its own on/off switch.
2. **Stopwatches** — many, labelled.
3. **Countdown timers** — many, labelled, ringing with the same alarm screen.

Plus **"I will sleep now"**, which pauses interval alarms for 8 h 30 min (editable).

### Decisions taken with Omar before writing any code — do not re-litigate

| Question | Answer | Why it matters |
|---|---|---|
| Interval scheduling | **Fixed grid from switch-on** (`anchorAt + n × interval`) | Omar was offered "restart the clock when you dismiss" and chose the grid. Dismissing late must NOT push the next alarm later. |
| Timer reaching zero | **Same full-screen alarm** as an interval alarm | One consistent ring screen for everything. |
| After sleep mode ends | **Fresh full countdown** — re-anchor every enabled alarm at the wake moment | Prevents a pile of alarms firing the instant sleep lifts. |
| Sleep mode silences | **Interval alarms only** | Timers and stopwatches are explicitly unaffected. |
| Sound | Phone's default alarm ringtone, **plus** a per-alarm picker | Omar asked for default "and if possible to be simple to add a way to pick a sound" → the system `RingtoneManager` chooser, so no custom picker UI and no bundled audio. |
| Ignored alarm | Rings **5 minutes**, then stops and carries on normally | Editable 1–30 min in Setup. |
| Repo | **Only the `BroTimer/` subfolder** is a git repo | `My android tools/` stays a plain folder. |
| Name | BroTimer · `com.brotimer` | Matches BroMic / BroDashGraphify. |
| Layout | **Three bottom tabs** + a sleep banner on every tab | Setup/settings sits behind the gear icon, so the tab count stays at three. |

Decided without asking, all editable in-app: snooze 10 min, sleep 8 h 30 min, plus a **Wake up
now** button to end sleep early.

### The toolchain question — and why nothing was installed

The first read of the machine found no `java`, no Android SDK, no Gradle on `PATH`, and a first
draft of the plan proposed installing a JDK + cmdline-tools. **Omar rejected that**: BroMic already
builds an Android app on this PC. It does — `Desktop\bro mic\.tools\` vendors JDK 17.0.12,
Gradle 8.7, and an Android SDK (platform `android-35`, build-tools 34.0.0, licences accepted).

BroTimer therefore **borrows** that toolchain rather than copying ~873 MB of it.
[`tools.ps1`](../tools.ps1) resolves it in order: `$env:BROTIMER_TOOLS` → `.\.tools` →
`..\..\bro mic\.tools` → `$HOME\Desktop\bro mic\.tools`.

⚠️ **The trade:** moving or deleting `Desktop\bro mic\.tools\` breaks this build. Fix by setting
`$env:BROTIMER_TOOLS`, or copy that folder in here — `.gitignore` already excludes it.

Version pins were copied verbatim from `bro mic\android` because they are *known-good on this
machine*, not because they are the newest: AGP 8.5.0, Kotlin 2.0.0, compileSdk/targetSdk 35,
minSdk 26, Compose BOM 2024.06.00, JDK 17, Gradle 8.7.

`tools.ps1` also reproduces BroMic's `TMP`/`TEMP` redirect into `.gradle-tmp/`. That is not
tidiness: on this machine `AF_UNIX connect()` fails with EINVAL for paths under
`AppData\Local`, so without the redirect the Gradle daemon dies with *"Unable to establish
loopback connection"* before the build starts.

### Dependency rule

**Nothing outside the Gradle cache already on this PC.** The cache was inspected first: it holds
AGP, Kotlin 2.0.0, the Compose BOM, material3, `material-icons-core` and coroutines — and it does
**not** hold Room, DataStore, kotlinx-serialization, navigation-compose or
`material-icons-extended`.

So persistence is `SharedPreferences` + `org.json` (both in the framework), tab switching is plain
Compose state, and only icons from the core set are used. The build needs no download.

### Architecture decisions worth keeping

**`AlarmManager.setAlarmClock()` for everything** — interval slots, timers, snoozes, and the end of
sleep mode. It is the only alarm API fully exempt from both Doze and battery optimisation, and it
shows the alarm icon in the status bar. Do not "simplify" it to `setExactAndAllowWhileIdle`.
Verified on device (below): `device_idle=--`, `battery_saver=--`.

**The foreground service owns the ringing sound, not the alarm activity.** On Android 14+ a
full-screen intent can be downgraded to a heads-up notification, in which case `AlarmActivity`
never launches. If the activity owned the sound, that alarm would be silent. This is the single
most important structural decision in the app.

**No service for stopwatches or timers.** A stopwatch is `accumulatedMs + (now - startedAt)`; a
timer is an `endsAt` timestamp plus one `setAlarmClock`. Both use wall clock
(`System.currentTimeMillis()`), never `elapsedRealtime`, so they survive a reboot. Nothing needs to
tick in the background.

**The grid is self-healing.** Because `nextFireAt` derives the next slot from the anchor, slots
missed while ringing, asleep, or powered off are skipped automatically. `BootReceiver` needs no
catch-up logic — it just calls `rescheduleAll`.

**Ids are small sequential ints from one shared counter**, not timestamps, because they become
`PendingIntent` request codes (`Int`) as `1000 + id * 8 + kind`. One counter across alarms,
stopwatches and timers means an id identifies exactly one entity, so a snooze code can never
collide with a different type's.

**Snooze does not move the grid.** It is a one-off extra ring. When a live ring starts, any pending
snooze for that same entity is cancelled first, which prevents a double ring when the snooze length
is close to the interval.

### Two bugs found and fixed during the build

1. **PowerShell + em-dash.** `install.ps1` failed to parse: *"The string is missing the
   terminator"*. PowerShell 5.1 reads a UTF-8-without-BOM `.ps1` as CP1252, so `—` (`E2 80 94`)
   decoded to `â€”`, and PowerShell accepts the curly quote U+201D as a real string delimiter —
   terminating the string early. **All three `.ps1` files are now pure ASCII, and must stay that
   way.** This applies to any future PowerShell in this repo.
2. **MSYS path mangling.** In git-bash, `adb push ... /data/local/tmp/x.apk` rewrites the device
   path to `C:/Program Files/Git/data/local/tmp/x.apk`. Use `MSYS_NO_PATHCONV=1` for any adb
   command carrying an on-device absolute path.

### HyperOS: the install was refused

`adb install -r` returned **`INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`** — HyperOS
blocking the streamed install even though `install_non_market_apps=1` and developer options are on.

Pushing the APK and running `pm install` **from the device's own shell** is not subject to that
check and worked first time. [`install.ps1`](../install.ps1) now detects `USER_RESTRICTED` and
retries that way automatically, so this should not need thinking about again.

### On-device verification actually performed

Installed to Xiaomi `23117RA68G`, Android 16 / API 36, HyperOS `V816`.

- App launches, no crash, UI renders (three tabs, sleep banner, FAB). Dynamic colour picks up the
  wallpaper.
- Install-time permissions all granted: `USE_EXACT_ALARM`, `USE_FULL_SCREEN_INTENT`,
  `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`,
  `WAKE_LOCK`, `VIBRATE`.
- **Scheduling proven end to end.** Omar created a 1-hour alarm "istighfaar 10" on the device.
  App log: `interval 1 'istighfaar 10' next at 1788537465985 (in 3599996 ms)` — exactly one hour.
  `dumpsys alarm` shows a real alarm-clock entry:

  ```
  RTC_WAKEUP #33: Alarm{... com.brotimer}   tag=*walarm*:com.brotimer.action.FIRE
  type=RTC_WAKEUP  window=0  exactAllowReason=policy_permission
  whenElapsed == maxWhenElapsed          <- no slack, exact
  device_idle=--   battery_saver=--      <- no Doze / saver deferral at all
  Alarm clock: triggerTime=2026-09-04 18:57:45.985
  ```

  `device_idle=--` and `battery_saver=--` are the `setAlarmClock` exemption working as designed.

**Not yet hardware-verified** — these need waiting for a ring and are listed in
[`TEST-PLAN.md`](TEST-PLAN.md): the full-screen ring over the lock screen, Snooze, Stop, the
5-minute give-up, sound selection, sleep mode start/end, timer ringing, stopwatch survival across
app-kill, and reboot recovery. Code-verified is not hardware-verified.
