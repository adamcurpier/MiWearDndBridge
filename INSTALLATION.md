# MiWear DND Bridge - Installation

These steps describe the tested Version 1.0.0 configuration.

## Requirements

- POCO phone running HyperOS 3 / Android 16 (tested platform).
- Xiaomi Watch 5 running Wear OS (tested platform).
- Mi Fitness installed and the Watch paired normally.
- ADB/platform-tools on a computer for the one-time Watch grants.
- Phone and Watch APKs built/signed with the **same certificate**.

## 1. Install the phone APK

Install the phone module on the POCO.

```bash
adb install -r MiWearDndBridge-Phone-v1.0.0.apk
```

Then enable **Notification access** for MiWear DND Bridge. The phone module is a `NotificationListenerService`; it needs notification access both to observe Android interruption state and to detect the real HyperOS Clock ringing notification.

## 2. Configure HyperOS background behavior

On the POCO:

- Background Autostart: **ON**
- Battery policy: **No restrictions**
- Lock/protect MiWear DND Bridge in HyperOS Recents

These settings were used throughout the successful scheduled and overnight tests.

## 3. Install the Watch APK

Enable Developer Options and Wireless Debugging temporarily on the Watch, pair/connect ADB, then install:

```bash
adb -s <watch-ip:port> install -r MiWearDndBridge-Watch-v1.0.0.apk
```

## 4. Grant Watch DND access

Run once:

```bash
adb -s <watch-ip:port> shell cmd notification allow_dnd com.adam.miweardndbridge
```

## 5. Grant Watch WRITE_SECURE_SETTINGS

Run once:

```bash
adb -s <watch-ip:port> shell pm grant com.adam.miweardndbridge android.permission.WRITE_SECURE_SETTINGS
```

Optional verification:

```bash
adb -s <watch-ip:port> shell dumpsys package com.adam.miweardndbridge
```

Look for:

```text
android.permission.WRITE_SECURE_SETTINGS: granted=true
```

## 6. Test DND

Turn DND ON on the POCO. The Watch should enter native DND and show the DND icon. Turn DND OFF on the POCO and confirm the Watch follows.

The target internal states observed during development are:

```text
ON : zen=1 / mcu.persist.disturb_manual_state=1
OFF: zen=0 / mcu.persist.disturb_manual_state=0
```

## 7. Test a phone alarm

With DND OFF first, set a near-future alarm in HyperOS Clock. When it rings:

- Phone should ring/vibrate normally.
- Watch should show Xiaomi's synchronized alarm UI.
- Watch should vibrate.
- Watch should play continuously looping alarm audio.
- Dismissing from the Watch should stop the phone alarm.

Repeat once with DND ON. The Watch alarm audio should continue looping through DND.

## 8. Disable debugging

After validation, Developer Options/Wireless Debugging on the Watch and Developer Options/USB debugging on the phone may be disabled. Normal bridge operation does not require ADB.

## Upgrades and reinstalls

`adb install -r` preserved `WRITE_SECURE_SETTINGS` during development. A full uninstall/fresh reinstall should be assumed to require the one-time Watch grants again.

## Troubleshooting

### DND icon appears but Watch still vibrates after deep sleep

Check that the Watch has the `WRITE_SECURE_SETTINGS` grant. Android-only Priority DND can produce `zen=1 / manual=0`, which is not the complete Xiaomi native DND state.

### Phone DND changes but Watch does not follow

Confirm:

- Phone Notification access is enabled.
- HyperOS Autostart is enabled.
- Battery policy is No restrictions.
- Phone and Watch APKs have the same package ID and signing certificate.
- Watch is connected to the phone through the normal Wear OS/Mi Fitness stack.

### "Alarm set" notification appears but no alarm should be ringing

That is expected Xiaomi notification behavior. Version 1.0 ignores the alarm-set notification and only reacts when HyperOS Clock enters its foreground ringing state.

### Watch alarm sound starts then stops after a few seconds

Make sure the Version 1.0 Watch build includes `AlarmPlaybackService` and the `mediaPlayback` foreground-service declarations. Earlier test builds played directly from `WearableListenerService`, whose process could be reclaimed while DND was active.
