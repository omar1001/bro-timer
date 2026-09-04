# BroTimer — on-device test plan

What "working" means. Everything here needs the real phone; none of it can be proven by building.

**Status key:** ✅ done · ⬜ still to do

Verified so far on Xiaomi `23117RA68G`, Android 16 / API 36, HyperOS `V816`, 2026-09-04.

---

## 0. Install and launch

| | Check |
|---|---|
| ✅ | `.\build.ps1` produces `app\build\outputs\apk\debug\app-debug.apk` |
| ✅ | `.\install.ps1` reports success (falls back to `pm install` past HyperOS's block) |
| ✅ | App launches with no crash; three tabs, sleep banner and + button all render |
| ✅ | Install-time permissions granted (`dumpsys package com.brotimer`) |

## 1. Permissions

| | Check |
|---|---|
| ⬜ | First launch asks for notifications; allow it |
| ⬜ | Setup tab (gear icon) shows **OK** on all four rows |
| ⬜ | HyperOS by hand: **Autostart ON**, **Display pop-up windows while running in background ON**, **Battery saver → No restrictions**, app locked in Recents |

⚠️ Until those four HyperOS items are done, everything below can fail for reasons that are not
bugs. Do them first.

## 2. The core loop — the part that must not break

Use a **0 h 1 min** alarm labelled `test water` for these, so each step takes a minute rather than
an hour.

| | Check |
|---|---|
| ✅ | Creating an alarm schedules it: `adb shell dumpsys alarm \| Select-String brotimer` shows an `Alarm clock:` entry with `window=0` and `device_idle=--` |
| ⬜ | **Lock the phone, screen off.** Within ~60 s it turns the screen on, shows the full-screen alarm over the lock screen, plays the alarm ringtone, vibrates, and shows the text `test water` |
| ⬜ | **Snooze** → sound stops → it comes back after the snooze minutes |
| ⬜ | **Stop** → sound stops |
| ⬜ | **Grid proof.** Note three consecutive fire times. Dismiss one deliberately late — wait ~30 s before pressing Stop. The next fire time must still land on the original grid, *not* 1 min after the dismiss. **This is the whole point of the design; if it fails, the app is wrong.** |
| ⬜ | Switch the alarm **off** → no more rings. Switch **on** → the grid re-anchors to that moment |
| ⬜ | Delete an alarm while it is enabled → nothing rings afterwards |
| ⬜ | Two alarms enabled at once both ring on their own grids |

## 3. Sound

| | Check |
|---|---|
| ⬜ | Default: the phone's normal alarm ringtone plays |
| ⬜ | Edit an alarm → **Sound** → pick a different ringtone → that one plays next time |
| ⬜ | "Use the phone's default alarm sound" resets it |
| ⬜ | Put the phone on **silent**. The alarm is still audible (it plays on the alarm stream) |

## 4. Sleep mode

Set the sleep length to **0 h 3 min** in Setup first, so this takes minutes.

| | Check |
|---|---|
| ⬜ | **I will sleep now** → banner turns to "Sleeping", shows the wake time and a countdown |
| ⬜ | The 1-minute alarm stays completely silent for the whole 3 minutes |
| ⬜ | When sleep ends, **nothing fires immediately**; the next ring is a *full* 1 minute later |
| ⬜ | **Wake up now** mid-sleep → banner clears, and again nothing fires immediately |
| ⬜ | A **timer** started before sleep still rings during sleep (sleep is alarms-only) |

Then put the sleep length back to 8 h 30 min.

## 5. Timers

| | Check |
|---|---|
| ⬜ | Timer `tea`, 30 s → rings with the same full-screen alarm as an interval alarm |
| ⬜ | Pause mid-countdown → the number stops; Start → it resumes from there |
| ⬜ | Reset → back to the full duration |
| ⬜ | After it rings, the timer shows its full duration again, ready to re-run |
| ⬜ | Leave the app while a timer runs, come back → the remaining time is correct |

## 6. Stopwatches

| | Check |
|---|---|
| ⬜ | Start, **swipe the app out of Recents**, wait 60 s, reopen → it advanced by ~60 s |
| ⬜ | Pause / resume / reset all behave |
| ⬜ | Two stopwatches run independently |

## 7. Survival

| | Check |
|---|---|
| ⬜ | `adb reboot`. **Without opening the app**, `adb shell dumpsys alarm \| Select-String brotimer` lists the alarms again |
| ⬜ | After reboot the 1-minute alarm still rings |
| ⬜ | A running stopwatch still shows the right elapsed time after reboot |
| ⬜ | Reinstall over the top (`.\install.ps1`) → alarms and stopwatches survive |
| ⬜ | **Give-up timer:** let an alarm ring untouched. It stops itself at ~5 minutes, and the grid carries on |

## 8. Many at once

| | Check |
|---|---|
| ⬜ | 3 alarms, 3 stopwatches, 3 timers all exist and all show correctly in their tabs |

---

## Diagnostics

```powershell
adb shell dumpsys alarm | Select-String brotimer     # what is actually scheduled
adb logcat -s BroTimer:V                             # what the app decided
adb logcat -s BroTimer:V AndroidRuntime:E            # ...plus crashes
```

In a `dumpsys alarm` entry, the healthy signs are `window=0` (exact),
`whenElapsed == maxWhenElapsed` (no slack), and `device_idle=--  battery_saver=--` (nothing is
deferring it).

**If an alarm did not fire, check the four HyperOS settings in section 1 before reading any code.**
