# MiWear DND Bridge

**Technical Design, Reverse-Engineering Findings, and Implementation Report**

**Project:** POCO HyperOS -> Xiaomi Watch 5 Do Not Disturb + Alarm Synchronization  
**Package:** `com.adam.miweardndbridge`  
**Version:** 1.0.0  
**Phone platform:** POCO running HyperOS 3 / Android 16  
**Watch platform:** Xiaomi Watch 5 running Wear OS  
**Release status:** Functionally validated as of August 20, 2026.

Version 1.0.0 passed manual and scheduled DND synchronization, deep-sleep notification suppression, DND icon persistence, phone-alarm detection, Watch alarm audio/vibration/UI, Watch-side dismissal, DND-through-alarm playback, Developer-Options-OFF testing, and a full normal overnight 2:30 AM-10:15 AM cycle.

## 1. Project objective

The project began with a simple requirement: when the POCO enters Do Not Disturb, the Xiaomi Watch 5 should enter its real native DND state as well. Notifications should still arrive for later review, but should not make sound or vibrate. When the POCO leaves DND, the Watch should return to normal behavior automatically.

Xiaomi's built-in "Sync DND with phone" path was unreliable on the tested POCO/Xiaomi Watch 5 combination. The phone could enter DND correctly and Xiaomi's companion stack appeared to exchange synchronization traffic, yet the Watch did not always behave as if DND had been enabled directly from the Watch.

The project later expanded to a second related problem: POCO/HyperOS Clock alarms synchronized inconsistently to the Watch. Xiaomi could show alarm UI and vibration, but reliable sustained Watch alarm audio was missing. Version 1.0 solves both problems.

## 2. Final high-level architecture

The finished solution uses two cooperating Android applications with the same package identity:

```text
com.adam.miweardndbridge
```

One module runs on the POCO and one on the Xiaomi Watch 5. They communicate through the standard Google Wear OS Data Layer using `play-services-wearable:20.0.1`.

The phone sends three message paths:

```text
/dnd-sync   -> DND Boolean state
/alarm-ring -> real HyperOS Clock alarm began ringing
/alarm-stop -> ringing alarm was stopped/dismissed
```

The Watch handles DND inside `WatchDndListenerService`. Alarm audio is delegated to `AlarmPlaybackService`, a `mediaPlayback` foreground service so playback can survive beyond the short lifetime of the Wear Data Layer listener.

The design does not root the Watch, unlock the bootloader, alter the system partition, replace Mi Fitness, or require a privileged Xiaomi system installation.

## 3. Phone-side DND detection

HyperOS uses Android's Zen/DND framework. The phone service observes `Settings.Global` key `zen_mode` with a `ContentObserver` and also responds to `NotificationListenerService.onInterruptionFilterChanged()`.

The bridge does not implement the user's DND schedule itself. HyperOS remains authoritative. A scheduled transition and a manual toggle therefore use the same synchronization path.

For example, with the validated schedule:

```text
2:30 AM  HyperOS turns DND ON
10:15 AM HyperOS turns DND OFF
```

`PhoneDndListenerService` sees those real state transitions and sends the new Boolean state to the Watch.

## 4. HyperOS background requirements

The stable POCO configuration used during testing was:

- Notification access enabled for MiWear DND Bridge.
- Background Autostart enabled.
- Battery policy set to No restrictions.
- MiWear DND Bridge locked/protected in HyperOS Recents.

These settings allow the phone notification listener to remain available even when the bridge has no foreground user interface open.

Developer Options, USB debugging, and ADB are not required during normal operation after installation and one-time grants are complete.

## 5. Wear OS Data Layer transport

The phone obtains connected Wear nodes from `Wearable.getNodeClient()` and sends messages with `Wearable.getMessageClient()`.

The DND payload is deliberately tiny:

```text
0        = DND OFF
non-zero = DND ON
```

Testing repeatedly showed one connected Xiaomi Watch 5 node and successful message transmission. This standard Data Layer path allowed the project to bypass the unreliable portion of Xiaomi's proprietary phone-to-Watch DND synchronization without removing Mi Fitness.

## 6. The first DND implementation was incomplete

The original Watch bridge changed only Android's interruption filter:

```java
nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY);
```

for DND ON, and:

```java
nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL);
```

for DND OFF.

This could produce `zen_mode=1`, and the DND icon could appear, but the Watch was not always in Xiaomi's complete native DND state. After sleep or UI navigation the icon could become inconsistent, and low-power notification handling could still produce haptic alerts.

## 7. Xiaomi Watch 5 has an additional MCU-side DND state

Reverse engineering and clean state comparisons identified two relevant Xiaomi secure settings:

```text
mcu.persist.disturb_manual_state
mcu.persist.disturb_switch_state
```

The critical value was `mcu.persist.disturb_manual_state`.

When DND was enabled from Xiaomi's native Watch Quick Settings tile, the observed stable state was:

```text
zen=1
manual=1
```

When native DND was disabled:

```text
zen=0
manual=0
```

By contrast, Android-only Priority DND could produce:

```text
zen=1
manual=0
```

That split state explained the misleading early behavior.

## 8. Why the MCU state matters

The Watch includes Xiaomi-specific low-power/MCU/CPC infrastructure in addition to normal Android/Wear OS notification handling. Development logs showed notification-related MCU activity, including `McuHomeCmdModule#NOTIFICATION` and synchronization through Xiaomi's settings/CPC stack.

The practical result was experimentally clear: Android believing DND was active was not sufficient. Xiaomi's low-power side also needed to agree.

The target state therefore became:

```text
DND ON : zen=1 / manual=1
DND OFF: zen=0 / manual=0
```

With those states aligned, deep-sleep sound/vibration suppression and DND icon stability became reliable.

## 9. Native Xiaomi DND sequence

A clean log captured while manually enabling DND from the Watch showed Xiaomi/Wear components reacting to:

```text
content://settings/secure/mcu.persist.disturb_manual_state
```

`TimerDndModule` observed the manual state and Xiaomi's `SyncSettingsModule` synchronized the change toward its CPC/MCU side. System UI inspection also showed that Xiaomi's native DND control combines an Android interruption-filter change with the secure-setting update.

That was the pivotal discovery: the native Watch tile performs both operations, so the bridge had to reproduce both.

## 10. Why Alarms-Only DND was abandoned

An experimental build used `INTERRUPTION_FILTER_ALARMS`, producing Android `zen_mode=3`. While attractive in theory, it did not reproduce Xiaomi's native Watch DND state. The MCU manual value remained incomplete and deep-sleep haptic suppression was still unreliable.

Xiaomi's own native DND behavior used Priority mode plus the MCU manual flag, so Version 1.0 follows that native pattern.

## 11. Why global vibration disabling was rejected

Notification vibration intensity was temporarily reduced to zero during investigation. Some deep-sleep haptic events still occurred without corresponding normal Android vibrator history, reinforcing the conclusion that Xiaomi's offloaded path could be involved.

The final bridge does not globally disable Watch vibration. That is important because alarm vibration must remain available.

## 12. CPC/system-service investigation

The project investigated Xiaomi's internal CPC transport and several related system packages. An experimental receiver could be resolved by Android Package Manager, but Xiaomi's actual callback map continued to use trusted platform/privileged receivers. Xiaomi's outbound transmitter was protected by signature/privileged permissions.

Pursuing that route would have required root, a system installation, or Xiaomi signing privileges. It was deliberately rejected.

Proprietary Xiaomi/Google APKs examined during this investigation are not included in the public repository.

## 13. WRITE_SECURE_SETTINGS breakthrough

The Watch manifest declares:

```xml
<uses-permission android:name="android.permission.WRITE_SECURE_SETTINGS" />
```

On the tested Watch this permission could be granted to the development application through ADB:

```bash
adb shell pm grant com.adam.miweardndbridge android.permission.WRITE_SECURE_SETTINGS
```

The application then performs the secure-setting write directly from its own process:

```java
Settings.Secure.putInt(
    getContentResolver(),
    "mcu.persist.disturb_manual_state",
    dndOn ? 1 : 0
);
```

Direct application-process writes produced the required stable native state, whereas earlier shell experiments did not reliably do so.

The exact internal reason Xiaomi treats those paths differently is not claimed as proven; the experimentally proven result is that the in-app API write works on the tested configuration.

## 14. Final Watch DND logic

For a `/dnd-sync` message, Version 1.0:

1. Reads the Boolean payload.
2. Verifies Notification Policy access.
3. Uses `INTERRUPTION_FILTER_PRIORITY` for ON or `INTERRUPTION_FILTER_ALL` for OFF.
4. Writes `mcu.persist.disturb_manual_state` to `1` or `0`.

This reproduces the two pieces of the native Xiaomi DND state closely enough for the Watch's Android and MCU-side behavior to agree.

## 15. Deep-sleep notification validation

With the final DND implementation active and the Watch allowed to sleep fully, multiple notifications were delivered without sound or vibration. When the Watch was later awakened, notifications were available for review and DND remained active.

This demonstrated the intended behavior: DND suppresses interruptions rather than discarding notification content.

## 16. DND OFF recovery

When DND was turned OFF on the POCO, the Watch followed automatically and returned to:

```text
zen=0
manual=0
```

This confirmed that Version 1.0 does not leave Xiaomi's MCU manual-DND flag stuck after the phone exits DND.

## 17. Scheduled DND validation

Automatic schedules were tested independently of manual toggles. Both ON and OFF transitions synchronized correctly. The normal overnight schedule of 2:30 AM ON and 10:15 AM OFF was then validated in untethered daily use.

## 18. DND icon stability and UI stress testing

Earlier split-state builds could show apparently random DND icon behavior. Once the bridge consistently produced `zen=1 / manual=1`, the icon remained stable through aggressive interaction including:

- sleep/wake cycles;
- Watch-face changes;
- widget navigation;
- Settings navigation;
- flashlight use;
- timers;
- weather and elevation screens;
- calendar and contacts;
- airplane-mode toggling;
- compass and other widgets;
- repeated Quick Settings navigation.

Developer Options and Wireless Debugging were also disabled and the behavior remained correct.

## 19. The separate POCO alarm problem

Xiaomi's native remote-alarm synchronization was inconsistent. A synchronized POCO alarm could produce Watch alarm UI and sustained vibration, or at other times merely a notification-like alert. Reliable continuous Watch alarm audio was not guaranteed.

Local Watch alarms were different: they used the native local alarm path and already sounded/vibrated through DND correctly.

The project therefore added only the missing reliable audio portion while preserving Xiaomi's existing synchronized alarm UI, vibration, snooze, and dismiss behavior.

## 20. How the phone identifies a real ringing alarm

The HyperOS Clock package on the tested POCO is:

```text
com.android.deskclock
```

A normal "alarm set" notification is not the same thing as a ringing alarm. Version 1.0 deliberately ignores that earlier notification.

The phone treats the Clock as actively ringing only when all of the tested conditions match:

```text
package = com.android.deskclock
channel = channel_id_deskclock_alarm
Notification.FLAG_FOREGROUND_SERVICE is set
```

The exact active `StatusBarNotification` key is retained. When that notification is removed, the phone sends `/alarm-stop`.

On listener reconnect, active notifications are inspected. If a real ringing alarm already exists, `/alarm-ring` is resent; otherwise a safety `/alarm-stop` is sent.

## 21. Alarm Data Layer extension

The final message paths are:

```text
/dnd-sync   -> DND Boolean payload
/alarm-ring -> synchronized phone alarm started
/alarm-stop -> synchronized phone alarm stopped/dismissed
```

This alarm signaling is event-driven. The bridge does not create a second alarm schedule or attempt to predict when an alarm will occur.

## 22. First Watch alarm-audio attempt

The first alarm extension played a looping `Ringtone` directly from `WatchDndListenerService` with `AudioAttributes.USAGE_ALARM`.

With DND OFF this could loop correctly. Under DND, however, Android was observed reclaiming the ordinary bridge process only a few seconds after message delivery. Diagnostic markers showed that playback had successfully entered a playing state before the process was killed.

This explained why a Watch alarm could make sound for only several seconds and then fall silent while Xiaomi's vibration continued.

## 23. Foreground alarm playback solution

Version 1.0 moves synchronized phone-alarm audio into `AlarmPlaybackService`, declared as:

```xml
android:foregroundServiceType="mediaPlayback"
```

The Watch manifest also includes:

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
```

When `/alarm-ring` arrives, `WatchDndListenerService` calls `startForegroundService()`. The service immediately enters foreground mode, obtains the Watch's default alarm ringtone, applies `AudioAttributes.USAGE_ALARM`, enables looping, and plays until `/alarm-stop` is received.

`START_NOT_STICKY` is used so a stale alarm does not spontaneously restart after service/process loss.

When `/alarm-stop` arrives, the ringtone is stopped, the foreground notification is removed, and the service stops itself.

## 24. Alarm behavior with DND OFF

Controlled testing with DND OFF passed completely:

- POCO alarm sound and vibration started normally.
- Watch alarm UI appeared.
- Watch vibration ran continuously.
- Watch alarm sound looped continuously.
- Dismissing the alarm from the Watch stopped the phone alarm.

The final selected Watch alarm stream was 10/10.

## 25. Alarm behavior with DND ON

The same controlled test was repeated while DND was active on both devices. With foreground playback in place:

- normal DND remained active;
- the POCO alarm rang/vibrated;
- Watch alarm UI appeared;
- Watch vibration continued;
- Watch alarm audio looped continuously through DND;
- Watch-side dismissal stopped the phone alarm.

This was the critical final alarm test.

## 26. Audio settings used during final validation

The final test device used:

```text
Watch alarm stream        10/10
Watch notification stream  4/10
```

These are user/device preferences and are not hard-coded by MiWear DND Bridge.

## 27. Final overnight validation - August 20, 2026

The final untethered overnight test was performed with Developer Options and debugging disabled for normal use.

HyperOS automatically enabled DND at 2:30 AM. Later in the morning the Watch was visually confirmed to still show DND active.

At 10:00 AM the scheduled alarm woke the user from sleep. Because the phone and Watch were physically close together, that first event was not acoustically isolated by device. A second alarm was therefore set for approximately 10:10 AM while DND was still active. That second alarm was explicitly verified to ring on the phone and to produce continuously looping alarm audio plus vibration on the Watch.

At 10:15 AM HyperOS automatically left the DND window and the Watch followed back to DND OFF. A later snoozed alarm also worked normally with DND off.

Taken together with the controlled DND-on tests performed the previous day, this confirmed the intended behavior in ordinary overnight use.

## 28. Required Watch permissions

The Watch requires two important one-time grants.

Notification policy access:

```bash
adb shell cmd notification allow_dnd com.adam.miweardndbridge
```

Secure Settings access:

```bash
adb shell pm grant com.adam.miweardndbridge android.permission.WRITE_SECURE_SETTINGS
```

No root access, bootloader unlock, firmware modification, or system-app conversion is required.

During testing, `adb install -r` upgrades preserved the Secure Settings grant. A full uninstall/fresh reinstall should be assumed to require the grants again.

## 29. Signing requirement

The phone and Watch modules use the same application ID and must be signed with the same certificate for the tested Wear OS Data Layer pairing behavior.

Do not independently re-sign only one side. If distribution builds are created, both modules should be built and signed from the same persistent signing environment.

The included GitHub Actions workflow builds the paired debug APKs in one run. GitHub-hosted debug signing keys are ephemeral between runs, so those workflow artifacts are useful for testing but should not be treated as a stable long-term update channel unless a persistent release keystore is configured.

## 30. Normal-use state after setup

After successful installation, grants, and testing:

- Watch Developer Options may be OFF.
- Watch Wireless Debugging may be OFF.
- Phone Developer Options may be OFF.
- Phone USB debugging may be OFF.
- No laptop or ADB connection is needed.

The validated daily-use configuration was fully untethered.

## 31. Security and design philosophy

The project intentionally stops at the lowest privilege level found to reproduce correct behavior.

Rejected approaches included routes that would have required Xiaomi signature permissions, a privileged/system installation, root, or system-partition changes.

The final bridge is reversible and event-driven. It does not continuously poll the Watch and does not maintain an ADB session.

## 32. Important limitations

Version 1.0 is experimentally validated for the tested POCO HyperOS 3 / Android 16 + Xiaomi Watch 5 Wear OS configuration.

Other Xiaomi/POCO firmware versions, different Clock packages/channels, or different Wear OS watches may behave differently. In particular, the Xiaomi-specific secure setting used by this project is not a general Wear OS API and may not exist or behave the same way elsewhere.

The project makes no claim that Xiaomi documents or supports this internal MCU setting for third-party use.

## 33. Proprietary reverse-engineering material

During development, system APKs from the Watch were inspected to understand behavior. Those proprietary Xiaomi/Google binaries are intentionally excluded from the public repository.

The public repository contains only the bridge source, build configuration, documentation, and automation needed to reproduce the project.

## 34. Version 1.0 release baseline

Version 1.0.0 is the frozen functional baseline. No further functional changes were made after the successful controlled DND-on/DND-off alarm tests and final overnight validation.

The public release baseline therefore includes:

- phone DND observer and Data Layer sender;
- real HyperOS Clock ringing-state detector;
- Watch Data Layer listener;
- Android Priority + Xiaomi MCU manual-DND synchronization;
- alarm foreground playback service;
- complete one-time permission instructions;
- HyperOS background requirements;
- validation record;
- GitHub build automation.

## 35. Final conclusion

The central technical discovery is that reliable Xiaomi Watch 5 DND on the tested firmware is not represented completely by Android `zen_mode` alone. The stable native state requires agreement between Android/Wear OS and Xiaomi's MCU-side manual-DND state.

The second key discovery is that reliable synchronized phone-alarm audio must outlive the short-lived Wear Data Layer listener. Moving alarm playback to a `mediaPlayback` foreground service solved that process-lifetime problem while retaining Xiaomi's native alarm UI and haptics.

For the tested configuration, Version 1.0.0 is considered functionally validated and ready for publication.
