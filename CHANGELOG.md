# Changelog

## 1.0.0 - 2026-08-20

Initial validated release.

### DND

- Mirrors manual and scheduled HyperOS DND changes to Xiaomi Watch 5.
- Applies Android/Wear OS Priority DND plus Xiaomi `mcu.persist.disturb_manual_state`.
- Fixes deep-sleep notification vibration caused by the incomplete `zen=1 / manual=0` state.
- Stabilizes the Watch DND icon across sleep/wake, Watch-face changes, widgets, Settings, flashlight, weather, contacts, airplane mode, and other UI stress tests.
- Correctly clears both Android and Xiaomi DND state when phone DND turns off.

### Alarms

- Detects the real ringing state of HyperOS Clock (`com.android.deskclock`) instead of the earlier alarm-set notification.
- Adds `/alarm-ring` and `/alarm-stop` Data Layer messages.
- Adds `AlarmPlaybackService` using foreground service type `mediaPlayback`.
- Plays the Watch default alarm ringtone with `USAGE_ALARM` and continuous looping.
- Works with DND ON and DND OFF.
- Preserves Xiaomi's alarm UI and vibration.
- Watch-side dismissal stops the phone alarm.

### Validation

- Full overnight schedule passed: 2:30 AM DND ON, alarm during DND, 10:15 AM DND OFF.
- Developer Options and debugging disabled after setup with normal operation intact.
