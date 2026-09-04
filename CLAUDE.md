# BroTimer

**Criticality tier:** normal

Android app: repeating interval reminders that ring as real full-screen alarms (sound, lock
screen, Snooze/Stop, custom text), plus labelled stopwatches and countdown timers, plus an
"I will sleep now" button that pauses the interval alarms for a set time.

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

## Folder map

| Path | What is in it |
|---|---|
| `app/src/main/java/com/brotimer/` | All the code |
| ` ├─ model/Models.kt` | The 4 stored types, the grid maths (`nextFireAt`), JSON codecs |
| ` ├─ data/Store.kt` | Process-wide singleton over `SharedPreferences`, exposes `StateFlow`s |
| ` ├─ alarm/` | `Scheduler` (every `AlarmManager` call), `AlarmReceiver`, `AlarmService` (sound), `AlarmActivity` (ring screen), `BootReceiver`, `SleepMode` |
| ` └─ ui/` | `App` (tabs + sleep banner), the three list screens, `SetupScreen`, `Format`, `Common`, `Theme` |
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
- Diagnostics: `adb shell dumpsys alarm | Select-String brotimer` (what is scheduled),
  `adb logcat -s BroTimer:V` (what the app did).

## ⚠️ HyperOS is the real risk, not the code

Two settings have **no public intent** and must be switched on by hand, or full-screen alarms
never appear: **Autostart**, and **Display pop-up windows while running in background** (both
under Settings → Apps → Manage apps → BroTimer). `SetupScreen` names them with the exact menu
path. Before debugging a "the alarm did not fire" report, check those two first.

## Read on demand — routing table

| File | Answers | Cost |
|---|---|---|
| [`docs/CHANGELOG.md`](docs/CHANGELOG.md) | What changed, when, and **why**. The authoritative record of intent. | ~3k |
| [`docs/TEST-PLAN.md`](docs/TEST-PLAN.md) | The on-device acceptance list. What "working" is defined as. | ~2k |
| [`README.md`](README.md) | User-facing: what the app does, how to build it, the permission checklist. | ~1.5k |

## Change log

Full dated entries live in [`docs/CHANGELOG.md`](docs/CHANGELOG.md) — the rows below are the index.

| Date | Headline | Read before touching |
|---|---|---|
| 2026-09-04 | Initial build: interval alarms on a fixed grid, sleep mode, stopwatches, timers, borrowed toolchain | Everything — this is the whole app |
