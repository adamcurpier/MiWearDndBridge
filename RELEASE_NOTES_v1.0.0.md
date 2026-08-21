# MiWear DND Bridge v1.0.0

Version 1.0.0 is the first frozen, functionally validated baseline for the tested POCO HyperOS 3 / Android 16 + Xiaomi Watch 5 Wear OS configuration.

## Highlights

- Manual and scheduled POCO DND synchronize to the Watch's complete native DND state.
- Final DND ON state is Android Priority DND plus Xiaomi `mcu.persist.disturb_manual_state=1`.
- Deep-sleep notification sound and vibration are suppressed while notifications remain available for later viewing.
- DND icon remains stable through extensive Watch UI stress testing.
- Real HyperOS Clock alarms are distinguished from ordinary "alarm set" notifications.
- Watch receives Xiaomi alarm UI and vibration plus reliable continuously looping alarm audio.
- Alarm playback works with DND ON and DND OFF.
- Watch-side alarm dismissal stops the phone alarm.
- Developer Options and debugging can be disabled after setup.

## Final overnight validation

The normal untethered schedule passed:

```text
2:30 AM  DND automatically ON
10:00 AM scheduled alarm occurred during DND
~10:10 AM second alarm explicitly verified on phone + Watch during DND
10:15 AM DND automatically OFF
later snoozed alarm worked with DND OFF
```

## Required one-time Watch grants

```bash
adb shell cmd notification allow_dnd com.adam.miweardndbridge
adb shell pm grant com.adam.miweardndbridge android.permission.WRITE_SECURE_SETTINGS
```

See `INSTALLATION.md` for the complete setup procedure.

## CI APK note

The GitHub release workflow builds the phone and Watch debug APKs together so they form a matched signing pair. GitHub-hosted debug signing identities are ephemeral; do not mix one CI APK with a differently signed local build. For stable future updates, configure a persistent signing key for both modules.

The exact locally device-validated APK binaries were separately preserved on the development laptop before publication.
