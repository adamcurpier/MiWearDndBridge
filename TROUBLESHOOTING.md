# MiWear DND Bridge - Troubleshooting

This file records the failure modes that mattered during Version 1.0 development.

## Watch shows DND, but notifications still vibrate after sleep

The most important check is whether the Watch has the one-time `WRITE_SECURE_SETTINGS` grant. Android Priority DND alone can produce:

```text
zen=1
manual=0
```

That is not the complete Xiaomi native state. The validated state is:

```text
zen=1
manual=1
```

Re-grant if necessary:

```bash
adb -s <watch-ip:port> shell pm grant com.adam.miweardndbridge android.permission.WRITE_SECURE_SETTINGS
```

Also confirm DND access:

```bash
adb -s <watch-ip:port> shell cmd notification allow_dnd com.adam.miweardndbridge
```

## DND works manually but not on schedule

The bridge does not own the schedule. HyperOS must actually transition its DND state. Check the phone DND schedule first, then verify:

- Notification access for MiWear DND Bridge is ON.
- Background Autostart is ON.
- Battery is set to No restrictions.
- The bridge is locked/protected in HyperOS Recents.

## Phone and Watch apps are installed but Data Layer messages do not arrive

The two APKs must use the same package ID and signing certificate. Build/sign the phone and Watch modules as a pair. Do not re-sign just one APK.

## Watch receives an "alarm set" alert before the alarm time

That is Xiaomi's normal synchronization notification and is not treated as a ringing alarm by MiWear DND Bridge. Version 1.0 only reacts to the real HyperOS Clock foreground ringing state.

## Watch alarm vibrates but audio stops after several seconds

Use the Version 1.0 Watch build containing `AlarmPlaybackService`. Earlier experiments played a ringtone directly from `WearableListenerService`; Android could reclaim that process a few seconds after alarm start, especially while DND was active.

The final build moves looping alarm playback to a `mediaPlayback` foreground service.

## Alarm audio does not play through DND

Confirm the final Watch manifest contains:

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
```

and:

```xml
<service
    android:name=".AlarmPlaybackService"
    android:exported="false"
    android:foregroundServiceType="mediaPlayback" />
```

## ADB connection drops during setup

Wireless Debugging ports on Wear OS can change. Re-open Wireless Debugging on the Watch and use the currently displayed IP/port. `adb mdns services` can also help discover the active endpoint.

ADB is only needed during installation/diagnostics. Once the one-time grants are complete, Developer Options and debugging can be disabled.

## Full uninstall/reinstall

A complete uninstall should be assumed to remove the one-time Watch grants. Reinstall the Watch APK and repeat the DND access and `WRITE_SECURE_SETTINGS` grant commands.

An `adb install -r` upgrade preserved the grant during Version 1.0 testing, but verify after any major package/signing change.
