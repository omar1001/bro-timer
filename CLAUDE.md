# BroTimer

**Criticality tier:** normal

Android app: repeating interval reminders that ring as real full-screen alarms (sound, lock
screen, Snooze/Stop, custom text), plus labelled stopwatches and countdown timers, plus an
"I will sleep now" button that pauses the interval alarms for a set time. Each alarm/timer can
play a chosen sound **N times**, and an unanswered ring **comes back** after a few minutes.
Public at `github.com/omar1001/bro-timer` (not pinned to the profile, at Omar's request).

Kotlin + Jetpack Compose, single module, no database. Target device: Xiaomi `23117RA68G`,
Android 16 / API 36, HyperOS `V816`. Sideloaded over USB with `adb`; never published to Play.

## Hard invariants — do not re-litigate

1. **Interval alarms use a fixed grid**, `anchorAt + n × interval`. Dismissing late must NOT push
   the next alarm later. Omar chose this explicitly over "restart the clock after you dismiss".
2. **Sleep mode re-anchors on wake.** When sleep ends, every enabled alarm's `anchorAt` is set to
   that moment, so nothing fires immediately. Sleep silences **interval alarms only** — never
   timers, never stopwatches.
3. **`AlarmManager.setAlarmClock()` for everything.** It is the only alarm API exempt from both
   Doze and battery optimisation. Do not "simplify" it to `setExactAndAllowWhileIdle`.
4. **[`AlarmService`](app/src/main/java/com/brotimer/alarm/AlarmService.kt) owns the sound, not
   [`AlarmActivity`](app/src/main/java/com/brotimer/alarm/AlarmActivity.kt).** On Android 14+ a
   full-screen intent can be downgraded to a banner and the activity never launches. If the
   activity owned the sound, that alarm would be silent.
5. **No dependency outside the Gradle cache already on this PC.** No Room, no DataStore, no
   kotlinx-serialization, no navigation-compose, no `material-icons-extended`. Persistence is
   `SharedPreferences` + `org.json`; navigation is Compose state. Adding a dependency means a
   download, and possibly a build that only works online.
6. **Version pins are borrowed from BroMic and are known-good on this machine:** AGP 8.5.0,
   Kotlin 2.0.0, `compileSdk`/`targetSdk` 35 (only `android-35` is installed), minSdk 26,
   Compose BOM 2024.06.00, JDK 17, Gradle 8.7. Do not bump one without checking the SDK is there.
7. **`tools.ps1` redirects `TMP` into `.gradle-tmp/`.** Not decoration: without it the Gradle
   daemon dies on this machine with "Unable to establish loopback connection". Inherited from
   `bro mic\build_android.ps1`.
8. **Sounds from outside the phone's ringtones are copied into `files/sounds/`, never referenced**
   ([`SoundLibrary`](app/src/main/java/com/brotimer/data/SoundLibrary.kt)). A Zedge/WhatsApp/
   Downloads file can vanish; an alarm must not silently lose its sound.
9. **How a ring ends** (`AlarmService.End`): Stop = done *and cancels any pending come-back*;
   Snooze = rings again, come-back count reset; count reached or give-up = unanswered → comes back
   (`comebackMinutes` × at most `comebackTimes`, skipped if the alarm's own next ring is sooner).
   Come-backs reuse the snooze `PendingIntent` slot, so the two can never both be pending.
10. **`AlarmActivity` must never call `requestDismissKeyguard`.** On Omar's PIN-locked phone it
    puts the PIN pad over the alarm, covering Stop. Verified fixed on the locked phone 2026-10-06.
11. **Vibration off is overridden when the sound cannot play** — a ring with neither would wake
    nobody.

## Folder map

| Path | What is in it |
|---|---|
| `app/src/main/java/com/brotimer/` | All the code |
| ` ├─ model/Models.kt` | The 4 stored types, the grid maths (`nextFireAt`), JSON codecs |
| ` ├─ data/` | `Store` (singleton over `SharedPreferences`, `StateFlow`s), `SoundLibrary` (import/copy, names, durations, phone-audio query) |
| ` ├─ alarm/` | `Scheduler` (every `AlarmManager` call), `AlarmReceiver`, `AlarmService` (sound, counting, come-backs), `RingState` (what is ringing, as a flow), `AlarmActivity` (ring screen), `BootReceiver`, `SleepMode` |
| ` └─ ui/` | `App` (tabs, sleep card, share dialog), the three list screens + their full-screen editors, `SoundPicker`, `EditorParts`, `SetupScreen`, `AppIcons` (own vector icons), `Common`, `Format`, `Theme` |
| `app/src/main/res/` | Vector-only icons, two themes (day/night), strings |
| `*.ps1` | `tools.ps1` (toolchain resolution), `build.ps1`, `install.ps1` |
| `docs/` | The full record — see the routing table |

## Build & deploy

- `.\install.ps1` — build + `adb install -r`. `-Fresh` uninstalls first (**wipes saved alarms**),
  `-SkipBuild`, `-Launch`.
- `.\build.ps1` — build only → `app\build\outputs\apk\debug\app-debug.apk`. `-Clean` wipes first.
- **The toolchain is borrowed, not vendored.** `tools.ps1` looks for `$env:BROTIMER_TOOLS`, then
  `.\.tools`, then `..\..\bro mic\.tools`, then `$HOME\Desktop\bro mic\.tools`.
  ⚠️ **Moving or deleting `Desktop\bro mic\.tools\` breaks this build.** Fix by setting
  `$env:BROTIMER_TOOLS`, or copy that folder in here (`.gitignore` already excludes it).
- **Releases** (GitHub, `v1.1` onward): bump `versionCode` **and** `versionName` in
  `app/build.gradle.kts`, `.\build.ps1 -Clean`, copy the APK to `BroTimer-<version>.apk`, then
  `gh release create v<version> BroTimer-<version>.apk --notes-file …`. The asset is the
  **debug-signed** build on purpose (see the 2026-10-06 v1.1 entry). ⚠️ It is signed with
  `~\.android\debug.keystore` — **lose that file and no future APK can update existing installs**.
- Diagnostics: `adb shell dumpsys alarm | Select-String brotimer` (what is scheduled),
  `adb logcat -s BroTimer:V` (what the app did — stream it to a file for long tests; the phone's
  buffer drops early lines).
- **Testing on Omar's phone touches his real data.** Back up `shared_prefs/brotimer.xml` with
  `run-as` first and restore it after. Device-testing traps (binary `adb shell` pipes truncate,
  `run-as … sh -c` loses permission, `uiautomator` cannot see notification buttons) are in the
  2026-10-06 changelog entry under "Tooling lessons".

## ⚠️ HyperOS is the real risk, not the code

Some settings have **no public intent** and must be switched on by hand: **Autostart**, **Show on
lock screen**, **Display pop-up windows while running in background** (Settings → Apps → Manage
apps → BroTimer). `SetupScreen` names them with the exact menu paths. Before debugging a "the alarm
did not fire" report, check those first. (On 2026-10-06 the lock-screen alarm worked on Omar's
phone — whether he had set them is unknown.)

## Read on demand — routing table

| File | Answers | Cost |
|---|---|---|
| [`docs/CHANGELOG.md`](docs/CHANGELOG.md) | What changed, when, and **why**. The authoritative record of intent. Open the dated entry, not the file. | ~7k |
| [`docs/TEST-PLAN.md`](docs/TEST-PLAN.md) | The on-device acceptance list — ✅ proven on the phone vs ⬜ still to do. | ~2.5k |
| [`README.md`](README.md) | Public-facing: what the app does, screenshots, Zedge how-to, build, permissions. | ~2.5k |
| `docs/screenshots/` | README images: status bar cropped off (it shows Omar's notification icons). `hero.png` is a composed banner. | images |

## Change log

Full dated entries live in [`docs/CHANGELOG.md`](docs/CHANGELOG.md) — the rows below are the index.

| Date | Headline | Read before touching |
|---|---|---|
| 2026-10-06 | v1.1 released on GitHub with the debug-signed APK; version shown in Setup; README download badge | Releasing, signing, `versionCode` |
| 2026-10-06 | Play a sound N times, come back if unanswered, own sounds (Zedge/phone audio/share, copied in), vibration switch, visual redesign; lock-screen alarm verified with PIN | `alarm/AlarmService`, `alarm/AlarmActivity`, `data/SoundLibrary`, any sound or ring-ending logic, `FullScreenDialog` |
| 2026-09-04 | Initial build: interval alarms on a fixed grid, sleep mode, stopwatches, timers, borrowed toolchain | Everything — this is the whole app |
