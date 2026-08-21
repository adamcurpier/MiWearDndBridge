# MiWear DND Bridge

**Version 1.0.0**  
Package: `com.adam.miweardndbridge`

MiWear DND Bridge is a two-part Android/Wear OS bridge built and validated for a **POCO phone running HyperOS 3 / Android 16** and a **Xiaomi Watch 5 running Wear OS**.

It fixes two Xiaomi ecosystem behaviors that were unreliable in the tested configuration:

1. **Do Not Disturb synchronization** - the Watch enters the same native Xiaomi DND state as if DND had been enabled from the Watch itself.
2. **Phone alarm audio on the Watch** - a real POCO/HyperOS Clock alarm produces Watch alarm UI, vibration, and continuously looping alarm audio, including while DND is active.

## Status

Version 1.0.0 is the frozen, functionally validated baseline. The final overnight test passed with the normal schedule:

- 2:30 AM - HyperOS DND automatically enabled and the Watch followed.
- 10:00 AM - scheduled alarm occurred inside the DND window.
- ~10:10 AM - a second alarm was explicitly verified to ring on both phone and Watch while DND remained active.
- 10:15 AM - HyperOS DND automatically disabled and the Watch followed.
- A later snoozed alarm also worked with DND off.

Developer Options, Wireless Debugging, and USB debugging are **not required during normal use**.

## How it works

The phone and Watch modules use the same package ID and communicate through the Google Wear OS Data Layer (`play-services-wearable:20.0.1`).

| Path | Purpose |
|---|---|
| `/dnd-sync` | Boolean DND state from the phone |
| `/alarm-ring` | HyperOS Clock has entered the real ringing state |
| `/alarm-stop` | The ringing alarm was stopped/dismissed |

### DND

HyperOS owns the schedule. `PhoneDndListenerService` observes Android `zen_mode` and sends the current state to the Watch. The Watch then applies both pieces of Xiaomi's native DND state:

- Android/Wear OS interruption filter: Priority for ON, All for OFF.
- Xiaomi secure setting: `mcu.persist.disturb_manual_state` = `1` for ON, `0` for OFF.

The experimentally validated target states are:

```text
DND ON : zen=1 / manual=1
DND OFF: zen=0 / manual=0
```

The second value is critical on the Xiaomi Watch 5. Android-only DND (`zen=1 / manual=0`) could show the DND icon yet still allow MCU/offloaded haptics after deep sleep.

### Phone alarms

The phone does **not** react to the ordinary "alarm set" notification. It identifies the real ringing state of `com.android.deskclock` by requiring the Clock alarm channel plus `Notification.FLAG_FOREGROUND_SERVICE`.

When that state begins, the phone sends `/alarm-ring`. The Watch starts `AlarmPlaybackService` as a `mediaPlayback` foreground service, plays the Watch's default alarm ringtone with `AudioAttributes.USAGE_ALARM`, and loops it until `/alarm-stop` arrives.

The foreground service is necessary because the normal Wear listener process was observed being reclaimed only a few seconds after alarm start while DND was active.

## Installation

See **[INSTALLATION.md](INSTALLATION.md)**. The Watch requires two one-time ADB grants after installation:

```bash
adb shell cmd notification allow_dnd com.adam.miweardndbridge
adb shell pm grant com.adam.miweardndbridge android.permission.WRITE_SECURE_SETTINGS
```

A full uninstall/reinstall should be assumed to require those grants again. `adb install -r` upgrades preserved the grant during testing.

## HyperOS phone settings

For reliable background behavior on the tested POCO configuration:

- Notification access: ON for MiWear DND Bridge.
- Background Autostart: ON.
- Battery: No restrictions.
- Lock/protect the app in HyperOS Recents.

Mi Fitness remains installed and continues to provide Xiaomi's normal watch integration.

## Signing requirement

The phone and Watch APKs must use the **same package ID and signing certificate** for Wear OS Data Layer communication. Build both modules from the same checkout/signing environment. Do not independently re-sign only one APK.

The included GitHub Actions workflow builds both debug APKs together and uploads them as one paired workflow artifact. GitHub-hosted debug signing keys are ephemeral between runs, so for a stable update channel configure a persistent signing key and sign both modules with it.

## Tested user audio settings

The final test device used:

- Watch alarm stream: 10/10.
- Watch notification stream: 4/10.

These are user/device preferences and are **not hard-coded** by the app.

## Security / scope

MiWear DND Bridge does not root the Watch, unlock the bootloader, modify the system partition, or install as a privileged Xiaomi system app. It uses standard Android/Wear OS APIs plus the one-time development-grantable `WRITE_SECURE_SETTINGS` permission on the Watch.

Proprietary Xiaomi/Google APKs examined during reverse engineering are intentionally **not included** in this repository.

## Documentation

- [Installation guide](INSTALLATION.md)
- [Troubleshooting guide](TROUBLESHOOTING.md)
- [Full technical report](TECHNICAL_REPORT.md)
- [Validation matrix](docs/VALIDATION.md)
- [Version 1.0 release notes](CHANGELOG.md)
- [Release checklist](docs/RELEASE_CHECKLIST.md)
- [Word version of the final technical report](docs/MiWear-DND-Bridge-v1.0.0-Final-Report.docx)

## Compatibility

This release is experimentally validated for the specific combination above. It may work on other Xiaomi/POCO phones or Wear OS watches, but those combinations have not been validated and may not expose the Xiaomi MCU secure setting used here.
