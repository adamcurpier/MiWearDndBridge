# Release Checklist

Version 1.0 source and documentation are frozen after final validation.

Before attaching public installable APKs to a GitHub Release:

1. Use the **exact validated phone and Watch APKs** preserved on the development laptop when possible.
2. Confirm the two APKs share the same signing certificate.
3. Do not re-sign only one side.
4. If rebuilding for distribution, configure a persistent signing key and sign both modules with that same key.
5. Do not publish proprietary Xiaomi/Google APKs used during reverse engineering.

Validated local filenames recorded during the project:

```text
MiWearDndBridge-Phone-FINAL-WORKING.apk
MiWearDndBridge-Watch-FINAL-WORKING.apk
```
