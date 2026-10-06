# BroTimer — change log

Full dated entries. The index lives in [`../CLAUDE.md`](../CLAUDE.md); this file is the record.
Newest first.

---

## 2026-10-06 — "Always on display": Snooze/Stop stay on screen while ringing (v1.2)

### What Omar asked for

> "when I hear it and then go to pause or stop I find it disappear and then I have to scroll in
> the notifications to find it and expand it to see the buttons — I want a static always-on to
> directly click on."

While the phone is **unlocked and in use**, Android shows a ringing alarm as a heads-up banner
(only a locked or sleeping phone gets the full-screen alarm), and HyperOS slides that banner away
after a few seconds. Then: "also add [the] always-on option when I am opening the program, [with] a
tiny red mark, so when you see it you know it is on". Asked which red mark he meant (a dot floating
over all apps, or one inside the app), he chose **inside the app**.

### What was built

- **`alarm/RingOverlay.kt`** (new) — a card with the alarm text, "Playing 3 of 10 · back in 5 min",
  and big **Snooze** / **Stop** buttons, floating at the bottom of the screen over any app
  (`TYPE_APPLICATION_OVERLAY`, needs **Display over other apps** / `SYSTEM_ALERT_WINDOW`). It stays
  until a button is pressed or the ring ends; tapping the text opens the full alarm screen. Plain
  Views, not Compose (a Compose view in a service-owned window would need its own lifecycle owners).
  Not focusable and not touch-modal, so Back, the keyboard and the rest of the screen keep working.
- **`AlarmService`** — shows the card when a ring starts **only if** the setting is on, the
  permission is granted, the screen is on **and** the phone is unlocked (`floatBlocker()`, which also logs *why* the card was not shown); asleep or
  locked, the full-screen alarm shows instead (overlays sit below the lock screen anyway). The card
  is updated with each play, removed by every kind of ending. `openAlarmScreen()` starts the
  activity *before* removing the card: our visible window is what allows an activity start from the
  background.
- **`AlarmActivity`** hides the card while it is on screen (its own buttons are exactly where the
  card sits) and brings it back if you leave it while still ringing — via `AlarmService.running`, a
  direct same-process call, because `startService` is refused from the background.
- **`Settings.stayOnScreen`** (default **on**), shown in Setup as **"Always on display"**, plus a
  "Display over other apps" row in the permission checklist.
- **Main screen: an "Always on" chip** in the top bar — **red dot = on and working**, amber dot = on
  but the permission is missing, grey ring = off. Tapping toggles it and explains in a snackbar;
  turning it on without the permission opens the permission screen.
- Version **1.2** (`versionCode 3`). Not released on GitHub yet — v1.1 is the latest release.

### Verified / not verified

- ✅ Builds clean; installed on Omar's phone over 1.1 with his data byte-identical.
- ✅ Omar granted "Display over other apps" (`SYSTEM_ALERT_WINDOW: allow`).
- ✅ **Verified on the phone** (after a USB reconnect): Omar's **real** `istighfaar 10` alarm rang at
  09:10:48 while the phone was unlocked → `always-on card shown for 1`; a screenshot a minute later
  shows the card still at the bottom of the home screen, long after the heads-up banner had gone;
  it was then stopped. With a 10-second test timer: Stop on the card, Snooze on the card
  (`snooze … (attempt 0)`), and tapping the card's text (opens the full alarm screen; the card is
  gone while it is up) all behaved. The test timer was deleted afterwards; Omar's alarms,
  stopwatch, timers and settings were identical to before. 0 crashes.
- The card now logs `always-on card shown` / `not shown: <reason>` (switched off, no permission,
  screen off, locked), so a "the card didn't appear" report can be answered from the log.

---

## 2026-10-06 — v1.1 published as a GitHub Release with the APK

Omar asked for a release with the APK so visitors can install it without building it.

- **Version bumped** to `versionCode 2`, `versionName "1.1"` (`app/build.gradle.kts`) — it had never
  moved from 1.0. Tag `v1.1`, asset `BroTimer-1.1.apk`.
- **Setup shows the version** at the bottom ("BroTimer 1.1 · github.com/omar1001/bro-timer"), read
  from `PackageManager` so no `BuildConfig` generation is needed.
- **README**: a "download APK" badge (shields.io, tracks the latest release) and an **Install**
  section ahead of "Build it yourself".

### Decision: publish the debug-signed APK, not a minified release build

The APK on the release is `assembleDebug` output — the same build type that was tested on the phone
all day. A `release` build would add R8 shrinking (untested here) and need a signing key of its own;
a new key would also make the release **unable to update the copy on Omar's phone** without an
uninstall (Android refuses a different signature), which wipes his alarms. The debug APK is
`debuggable`, which is harmless for an offline alarm app. Revisit only with a reason.

### ⚠️ The signing key lives on this PC only

Every APK built here — the release asset and everything `install.ps1` puts on the phone — is signed
with `C:\Users\VENOM TECH\.android\debug.keystore` (certificate `CN=Android Debug`, SHA-256
`0c47eb12c7b002246d82759c91de9a4f2ff416bf4f63fc2a334a66052886d3be`). **If that file is lost, no
future APK can update an existing install**; everyone, Omar included, would have to uninstall first
and lose their alarms. It is not in the repo and must not be. Back it up somewhere private.

### Installed on Omar's phone afterwards

When the phone was reconnected, the **publicly downloaded release file** (SHA-256 re-checked) was
installed over 1.0 with `adb install -r`. Before/after evidence: his data file was
**byte-identical**, and his four scheduled alarms (one interval alarm, three running timers) were
still scheduled at the **same millisecond** — before the app was even opened, confirming that
`AlarmManager` keeps a package's alarms across an update. No crash. The phone was locked, so the
"BroTimer 1.1" line in Setup was not seen on screen.

Seen in his data: he had already used the **Ringtones** button himself (a timer set to "Breeze",
stored as `content://media/internal/audio/media/7?title=Breeze&canonical=1`) — the canonical-URI
form that `SoundLibrary.titleOf` reads the title from.

---

## 2026-10-06 — Play a sound N times, come back if missed, your own sounds, vibration switch, visual redesign

### What Omar asked for

1. Pick a specific sound — e.g. a clip that **says a word** — and have the alarm **play it a set
   number of times** (his example: 10), then stop.
2. If he does not press Stop, it **comes back after 5 minutes** ("not ten"), adjustable.
3. Sounds from **outside the phone's built-in ringtones** — he plans to use **Zedge** — "the easiest
   possible", downloading a file and picking it manually being acceptable.
4. Improve the visuals a little, retake screenshots in as many situations as possible, put 2–3 in a
   README that makes the project look good to people visiting his GitHub, push. "Do all
   automatically — don't tell me to do things manually unless there is no way."
5. *(Mid-session)* An **off switch for vibration** — "it is annoying".

### Decisions — do not re-litigate

| Question | Decision | Why |
|---|---|---|
| Where does the repeat count live? | **Per alarm and per timer** (`repeatCount`), `0` = keep ringing | Different reminders want different counts. `0` is the default so alarms made before this keep ringing exactly as they did. |
| How does a counted ring end? | When the count is reached → treated as **unanswered** | That is what "if I didn't click Stop" means. |
| Come-back: global or per alarm? | **Global** (`comebackMinutes` = 5, `comebackTimes` = 3) | Omar described one rule. Samsung-style "interval + how many times"; 3 is a guess, adjustable, and 0 minutes turns it off. |
| Come-back vs. the snooze slot | **Same `PendingIntent` slot** (`KIND_SNOOZE`), with an `EXTRA_ATTEMPT` | A manual snooze and a come-back for one alarm can never both be pending; the later replaces the earlier. Manual snooze resets the attempt to 0. |
| Stop | **Cancels any pending come-back** for that alarm | Otherwise a come-back racing the next scheduled ring could ring after Stop. |
| Come-back later than the alarm's next scheduled ring | **Skipped** (`AlarmService.comeBack`) | The scheduled ring reminds anyway; avoids a double ring. |
| Same alarm rings while still ringing | **Superseded silently** (treated as STOPPED, no come-back) | Its own come-back colliding with its next scheduled ring. A *different* alarm replacing it = unanswered → come-back. |
| How are outside sounds stored? | **Copied into `files/sounds/`**, never referenced | A Zedge/WhatsApp/Downloads file can be deleted, moved, or lose its read grant; an alarm that silently loses its sound is the worst failure this app can have. System ringtones stay as references (the phone manages them). |
| Easiest Zedge path | Sound picker lists **all phone audio, newest first** (MediaStore, needs `READ_MEDIA_AUDIO`) | A tone downloaded a minute ago is the first row. Plus **Browse files** (SAF, no permission), **Ringtones** (system picker), and **Share → BroTimer** (`ACTION_SEND audio/*`). |
| Vibration | **Global switch**, default on; **forced on if the sound fails to play** | Omar asked for the ability; per-alarm was judged unnecessary. A ring with no sound *and* no vibration would wake nobody. Set **off** on Omar's phone at his request. |
| Look | **Fixed BroTimer palette** (navy `#1B2A4A` from the icon + amber), wallpaper colours now **opt-in** | Dynamic colour made the app (and README screenshots) depend on the wallpaper. Plus a Phone / Light / Dark choice. |

### What changed, by file

- **`model/Models.kt`** — `repeatCount` on `IntervalAlarm` and `TimerItem` (`KEEP_RINGING = 0`);
  `Settings` gains `comebackMinutes`, `comebackTimes`, `themeMode`, `wallpaperColors`, `vibrate`. All
  read with `opt*` defaults, so old saved data loads unchanged.
- **`alarm/AlarmService.kt`** — rewritten ring engine. Counted mode: `isLooping = false`, the
  completion listener restarts the clip until the count is reached. **Self-looping files**
  (OGG tagged `ANDROID_LOOP`, common in AOSP tones) never report completion, so
  `watchForSelfLooping()` polls the position and counts a play when it jumps back to the start —
  without seeking, so there is no stutter. Endings are an explicit `End` enum (STOPPED / SNOOZED /
  UNANSWERED / REPLACED). The notification shows "Playing 3 of 10 · back in 5 min if not stopped"
  (`setOnlyAlertOnce` so updates never re-alert). A vibrate-only ring (no playable sound) shows
  "Ringing" instead of a count that never moves. Hard safety cap for counted rings: 30 min.
- **`alarm/RingState.kt`** (new) — a `StateFlow` of what is ringing. Replaced the old
  `ACTION_RING_ENDED` broadcast: a flow always has a current value, so an alarm screen that opens
  late or is recreated closes itself immediately if the ring already ended.
- **`alarm/AlarmActivity.kt`** — redesigned ring screen (clock, date, pulsing amber rings, label,
  "Playing N of M" with a progress bar, come-back hint, big Snooze / Stop). **Removed
  `KeyguardManager.requestDismissKeyguard`** — on a PIN-locked phone it raises the PIN pad *over*
  the alarm, covering Stop. Found by reasoning about Omar's PIN before testing; then verified on the
  locked phone (below).
- **`alarm/Scheduler.kt`** — `EXTRA_ATTEMPT`; `scheduleSnooze(…, attempt)`; `rescheduleAll` now also
  cancels pending snoozes/come-backs of alarms that are switched off or asleep (timer snoozes are
  deliberately left alone: a finished timer is "not running" exactly while its come-back is pending).
- **`alarm/AlarmReceiver.kt`** — passes repeat count and attempt through; a snooze for an alarm that
  was **switched off** since no longer rings (it used to).
- **`data/SoundLibrary.kt`** (new) — import (copy, size cap 30 MB, playability check, re-import
  dedupe by name+size), delete (resets any alarm/timer using it to the default), display names,
  durations, MediaStore query, permission helpers.
- **`data/Store.kt`** — `replaceSound(old, new)`.
- **`ui/`** — new `AppIcons.kt` (own vector icons: alarm clock, stopwatch, hourglass, pause… — the
  cached `material-icons-core` has none of them), `SoundPicker.kt`, `EditorParts.kt` (sound field,
  repeat chooser, quick-pick chips), full-screen editors for alarms **and timers** (timers can now be
  edited at all), circular timer rings, alarm progress bars, night-sky sleep card, Setup regrouped
  into cards with steppers. `Theme.kt` rewritten.
- **`MainActivity.kt`** — `enableEdgeToEdge()`; handles `ACTION_SEND` (only on a fresh start, so a
  rotation does not import twice).
- **`AndroidManifest.xml`** — `READ_MEDIA_AUDIO` (+ `READ_EXTERNAL_STORAGE` ≤ API 32); share
  intent-filters for `audio/*` and `application/ogg`.

### Bugs found and fixed on the device this session

1. **Stale media ID in the default alarm URI.** Omar's phone stores the default alarm as
   `content://media/internal/audio/media/270?title=Alarm_Sunny_Instrument&canonical=1`, but ID 270
   is now `charging.ogg`; the real file is 272. Without `READ_MEDIA_AUDIO`, `Ringtone.getTitle`
   fails quietly and returns the last path segment, so the editor said **"Default (270)"**. Fix:
   read the title from the canonical URI's own `title` parameter; treat an all-digits title as
   unknown; always resolve via `ContentResolver.uncanonicalize` before playing.
2. **`MediaMetadataRetriever` cannot open `content://settings/system/alarm_alert`** ("could not
   access"). `MediaPlayer` can — it falls back to the system's cached ringtone copy. Durations are
   now measured with `MediaPlayer`.
3. **Full-screen dialogs had a black strip over the status bar**, then (after
   `decorFitsSystemWindows = false`) **white status icons on a white background**. Root cause, found
   from the logged appearance flags plus the window dump: Compose dialogs carry `FLAG_DIM_BEHIND`,
   and while it is set the system forces light icons whatever `isAppearanceLightStatusBars` says.
   `FullScreenDialog` clears it and adds `FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS`. Measured: darkest
   status-bar pixel 250 → 62.
4. The "Show my audio files" button was a tonal button on a same-coloured card — invisible.
5. "10 × 0:02 — about 23 s" read as bad maths (the clip is 2.3 s): short clips now show tenths.
6. `ExtendedFloatingActionButton` does not expose its label to accessibility on this Compose
   version — explicit `contentDescription` added.

### Verified on the phone (Xiaomi 23117RA68G, Android 16, HyperOS V816)

All from a background `logcat` stream captured to a file (the phone's own log buffer is too small
and busy to keep a whole test). **17 rings, 0 crashes, 0 app warnings.**

| Test | Evidence |
|---|---|
| Counted play: clip × 3 then stop | `ringing 5 'Tea' repeat=3 … clip=2429ms` → `played 3/3, unanswered` 7.7 s later |
| Come-back chain, capped | come-backs 1, 2, 3 rang; then `no come-back (attempt 3 of 3)` |
| Stop cancels come-back | `ended: STOPPED after 1 play(s)`, no `comes back` line |
| Snooze | `snooze for 5 … (attempt 0)`, due exactly 10 min later |
| Self-looping OGG (`ANDROID_LOOP=true`, made with ffmpeg) | three `self-looping sound wrapped … counting a play`, then `played 3/3` |
| Vibration off | phone's vibration history for BroTimer: 55 entries before a ring, 55 after |
| **Ring over the PIN-locked screen** | screen `Dozing` + keyguard showing → alarm fired → `Awake`, top activity `AlarmActivity`, keyguard **still showing**, **no PIN pad**; "Playing 7 of 10" on screen; Stop pressed without unlocking → `ended: STOPPED` |
| Zedge-style flow | test clips pushed to `Download/` appeared as the **first rows** of "On this phone"; one tap copied (byte-identical) and selected it |
| Share → BroTimer | `ACTION_SEND audio/mpeg` → "Sound added … Use it for:" listing the real alarm and timer |
| Delete a sound | confirm dialog → file removed → library list updated |
| Real alarm on the new build | system log: `03:10:48.917 … com.brotimer.action.FIRE` — its exact grid slot |

The lock-screen alarm appeared **without** anyone confirming the HyperOS "Show on lock screen" /
"pop-up windows" toggles in this session — whether Omar set them earlier is unknown, so Setup still
lists them.

None of the Xiaomi alarm tones carry `ANDROID_LOOP` (checked all with ffprobe), so on Omar's phone
the completion-listener path is the one that runs; the wrap watcher is for other phones.

### How the testing touched Omar's phone — and how it was undone

- His data was backed up first (`.gradle-tmp/omar-data-backup-2026-10-06.xml`, gitignored), demo
  data loaded for screenshots, then **restored exactly** (same alarm grid, stopwatch still counting
  from its original start, timer stopped at 2:05:00) with `vibrate: false` added at his request.
  His alarm was re-armed and verified: next ring 05:10:48, `window=0`.
- One test tap **started his real "check soy" timer** by mistake (the new card sat below it); it was
  reset immediately and verified identical to the backup.
- The audio permission prompt appeared on screen and was answered **Allow** on the phone — not by
  the test script, which only took a screenshot.
- Test clips pushed to `Download/BroTimer test sounds/` and the copies in the app's library were all
  deleted afterwards, MediaStore rows included.

### Tooling lessons (also in memory)

- **Never pipe a binary file from Windows into `adb shell`** — stdin is text mode and stops at the
  first `0x1A`. Sound files arrived truncated (862 bytes instead of 37,713). `adb push` it, then copy
  on the device: `adb shell "cat /data/local/tmp/x | run-as <pkg> tee 'files/...'"`.
- `run-as <pkg> sh -c '…'` **loses the app's SELinux context** on HyperOS; `run-as <pkg> tee <relative
  path>` (no shell) works.
- `uiautomator dump` sees the app's windows but **not SystemUI** — notification action buttons must be
  tapped by coordinates.
- `adb shell am start` cannot open a non-exported activity on Android 16.

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

### The ring path, partly verified while taking screenshots

To produce README screenshots, demo data was injected by force-stopping the app and writing
`shared_prefs/brotimer.xml` through `adb shell run-as com.brotimer tee ...`. Omar's own data was
backed up first and restored afterwards, and `dumpsys alarm` was re-checked to confirm exactly one
pending alarm (his) remained.

⚠️ **`run-as` writes must not spawn a shell.** `run-as com.brotimer sh -c 'cat > ...'` fails with
*Permission denied* on HyperOS — the spawned `sh` loses the app's SELinux context. `run-as
com.brotimer tee <relative path>` works, because no shell is involved and `run-as` sets the app
home as cwd. Relative paths only; absolute `/data/data/...` is denied.

One of the demo timers reached zero during the session, which verified the ring path for free:

```
BroTimer: fired type=timer id=6
BroTimer: ringing 6 'Tea' for up to 300s
```

The notification appeared with the label `Tea` and working **Snooze 10m** and **Stop** actions
(captured in `docs/screenshots/ringing.png`). Sound started — `playSound` logs
`no usable ringtone` if every candidate fails, and it did not.

It appeared as a **heads-up banner rather than taking over the screen**. That is correct: Android
only launches a full-screen intent's activity when the device is locked or idle; while the user is
actively using an unlocked phone it is shown as a banner. The locked-screen case is therefore still
untested.

The Setup screen also reported **notifications, full-screen alarms and exact alarms all OK**, with
**battery optimisation still ON** — the one stock-Android item left for Omar to press.

`adb shell am start` **cannot** launch `AlarmActivity` for a screenshot: it is `exported="false"`
and the shell user is refused on Android 16. The only way to see that screen is a real alarm on a
locked phone.

**Still not hardware-verified** — listed in [`TEST-PLAN.md`](TEST-PLAN.md): the full-screen ring
over the **lock screen**, Snooze actually returning, Stop, the 5-minute give-up, sound selection,
sleep mode start/end, stopwatch survival across app-kill, and reboot recovery. Code-verified is not
hardware-verified.

### Published

`github.com/omar1001/bro-timer` — **public**, at Omar's instruction, and deliberately **not pinned**
to his profile (his six pinned repos were checked and left untouched).
