# YXSHU Security for Android

An Android Studio starter app for a consent-first, on-device security review.

## Open and build

1. Install Android Studio and its Android SDK (API 35).
2. Open this `SentinelAndroid` folder in Android Studio and let Gradle sync.
3. Connect an Android phone with USB debugging enabled, or create an emulator.
4. Press **Run** in Android Studio. To make an APK, use **Build → Build APK(s)**.

This workspace did not have Android Studio, Gradle, or an Android SDK available, so an APK has not been built here.

## What the scan does

- Waits for the user to accept a plain-language consent dialog before reading anything.
- Queries launcher apps visible under Android package-visibility rules. It does not ask for broad `QUERY_ALL_PACKAGES` access.
- Reports Android release/security patch metadata and a best-effort legacy unknown-source install setting.
- Runs a deliberately weak local name/package heuristic. A match is a review prompt, not a malware verdict.
- Keeps results on device. It has no Internet permission and does not upload data, inspect other apps' private files, modify device settings, or uninstall software.
- Offers an explicitly opt-in WorkManager check every 12 hours. Android may defer these checks for battery and system scheduling; they are not continuous protection. Notifications are only sent for weak heuristic matches and require notification permission on Android 13+.

Android limits app visibility and isolates application data. This app is not equivalent to a commercial antivirus engine, cannot scan all files or other apps' private data, and cannot guarantee detection of spyware or root-level compromise. Use Google Play Protect and trusted specialist support for suspected targeted spyware.
