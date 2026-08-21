# Version 1.0 Validation Matrix

| Test | Result |
|---|---|
| Manual POCO DND ON -> Watch | PASS |
| Manual POCO DND OFF -> Watch | PASS |
| Scheduled HyperOS DND ON | PASS |
| Scheduled HyperOS DND OFF | PASS |
| Deep-sleep notification sound suppression | PASS |
| Deep-sleep notification vibration suppression | PASS |
| Notifications remain available for later viewing | PASS |
| Watch DND icon persistence through sleep/wake | PASS |
| Watch UI stress test with widgets/settings/watch faces | PASS |
| Developer Options OFF | PASS |
| Wireless Debugging OFF | PASS |
| Phone alarm detection ignores "alarm set" notification | PASS |
| Phone alarm -> Watch alarm UI | PASS |
| Phone alarm -> Watch vibration | PASS |
| Phone alarm -> looping Watch audio, DND OFF | PASS |
| Phone alarm -> looping Watch audio, DND ON | PASS |
| Watch dismissal stops phone alarm | PASS |
| Full overnight 2:30 AM-10:15 AM schedule | PASS |
| Post-DND snoozed alarm | PASS |

## Final overnight sequence

- DND automatically enabled at 2:30 AM.
- DND was visibly still active later in the morning.
- 10:00 AM alarm woke the user.
- A second alarm around 10:10 AM, still inside the DND window, was explicitly verified to ring and loop on both phone and Watch.
- DND automatically disabled at 10:15 AM.
- A later snoozed alarm worked with DND off.
