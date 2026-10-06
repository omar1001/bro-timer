# BroTimer — on-device test plan

What "working" means. Everything here needs the real phone; none of it can be proven by building.

**Status key:** ✅ verified on the phone · ⬜ still to do

Phone: Xiaomi `23117RA68G`, Android 16 / API 36, HyperOS `V816`. Verified 2026-09-04 and
2026-10-06 (evidence for each ✅ is in [`CHANGELOG.md`](CHANGELOG.md)).

---

## 0. Install and launch

| | Check |
|---|---|
| ✅ | `.\build.ps1` produces the APK; a clean build has **0 warnings** |
| ✅ | `.\install.ps1` succeeds (falls back to `pm install` past HyperOS's block when needed) |
| ✅ | App launches with no crash; saved data from the previous version loads unchanged |
| ✅ | **v1.1 from the GitHub Release** (the publicly downloaded file, SHA-256 checked) installs over 1.0: data byte-identical, all 4 scheduled alarms kept to the millisecond, no crash |
| ✅ | Setup shows "BroTimer 1.1 · github.com/omar1001/bro-timer" (checked on screen after Omar unlocked) |

## 1. Permissions

| | Check |
|---|---|
| ✅ | Setup shows all checks green: notifications, full-screen alarms, exact alarms, battery optimisation off, audio files |
| ⬜ | HyperOS by hand: Autostart, Show on lock screen, pop-up windows in background, battery saver no restrictions — *the lock-screen alarm already works without anyone confirming these this session* |

## 2. The core loop

| | Check |
|---|---|
| ✅ | Creating an alarm schedules a real alarm-clock entry: `window=0`, `device_idle=--` |
| ✅ | A real interval alarm fired on its exact grid slot (03:10:48.917) on the new build |
| ✅ | **PIN-locked phone, screen off:** the alarm turns the screen on and shows over the lock screen; **no PIN pad**; Stop works without unlocking |
| ✅ | Snooze → rings again after the snooze minutes (scheduled exactly +10 min) |
| ✅ | Stop → ends, and cancels any pending come-back |
| ⬜ | Grid proof: dismiss one ring ~30 s late; the next must still land on the original grid |
| ⬜ | Switching an alarm off silences its pending snooze/come-back too |

## 2b. Always on display (v1.2)

| | Check |
|---|---|
| ✅ | "Display over other apps" granted; Setup shows the "Always on display" switch and checklist row |
| ✅ | Main screen top bar shows the **● Always on** chip with a **red dot** while on |
| ✅ | **Omar's real alarm** rang while the phone was unlocked and in use → `always-on card shown`; the card was **still on screen a minute later**, long after the heads-up banner had gone; it was stopped from there |
| ✅ | Stop on the card ends the ring and removes the card |
| ✅ | Snooze on the card → `snooze … (attempt 0)`, `ended: SNOOZED` |
| ✅ | Tapping the card's text opens the full alarm screen, and the card is gone while it is up |
| ✅ | Ring while **locked** → full-screen alarm only (the card is skipped and logs why) |
| ⬜ | The **opaque** card (v1.2 release build) seen on screen — and a clean screenshot of it over BroTimer for the README |

## 3. Play it N times + come back if missed

| | Check |
|---|---|
| ✅ | A clip set to 3 plays exactly 3 times, then stops (`played 3/3`, 7.7 s for a 2.4 s clip) |
| ✅ | Notification and ring screen show "Playing N of M" live |
| ✅ | Unanswered → comes back after the set minutes, as "Back again · n of 3" |
| ✅ | Come-backs stop after the set count (`no come-back (attempt 3 of 3)`) |
| ✅ | A **self-looping** OGG (`ANDROID_LOOP=true`) is still counted correctly (wrap detection) |
| ⬜ | "Keep ringing" alarm left alone: gives up at the max ring time, then comes back |
| ⬜ | Two different alarms ringing back to back: the first gets its come-back |

## 4. Sounds

| | Check |
|---|---|
| ✅ | Default sound shows its real name ("Default (Alarm Sunny Instrument)") and length |
| ✅ | **On this phone · newest first** lists a just-downloaded file as the first row |
| ✅ | Picking a phone file copies it into the app, byte-identical, and selects it |
| ✅ | ▶ preview plays; delete a sound → confirm → gone; alarms using it fall back to the default |
| ✅ | **Share → BroTimer** imports the file and offers to assign it to an alarm or timer |
| ⬜ | **Browse files** (system file picker) import |
| ✅ | **Ringtones** (system picker): Omar picked "Breeze" for a timer himself; stored as a canonical URI (`…/media/7?title=Breeze&canonical=1`) |
| ⬜ | …and that ringtone actually plays when the timer rings |
| ⬜ | **A real Zedge download** — Omar's actual use case |

## 5. Vibration, sleep, look

| | Check |
|---|---|
| ✅ | Vibration off: a full ring adds **no** entry to the phone's vibration history |
| ⬜ | Vibration forced on when the sound cannot be played |
| ⬜ | **I will sleep now** → alarms silent for the period; on waking nothing fires immediately |
| ✅ | Sleep card, light/dark theme and wallpaper-colour switch render correctly |

## 6. Timers and stopwatches

| | Check |
|---|---|
| ✅ | Timer fires on time (10.0 s after Start) with its own sound and repeat count |
| ✅ | Timers can be edited; pause / reset behave |
| ⬜ | Stopwatch keeps counting after the app is swiped away (verified indirectly: Omar's `soy` stopwatch kept its start time across reinstalls and a data restore) |

## 7. Survival

| | Check |
|---|---|
| ✅ | Reinstall over the top keeps alarms, stopwatches, timers |
| ⬜ | `adb reboot` → alarms re-armed without opening the app |

---

## Diagnostics

```powershell
adb shell dumpsys alarm | Select-String brotimer     # what is actually scheduled
adb logcat -s BroTimer:V                             # what the app decided
```

The phone's log buffer is small and busy: for a long test, stream it to a file on the PC
(`adb logcat -v time -s BroTimer:V > log.txt`) — otherwise early lines are gone by the time you look.

**If an alarm did not fire, check the HyperOS settings in section 1 before reading any code.**
