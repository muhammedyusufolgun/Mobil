# Damar

Android app that opens the camera and offers a real-time superficial vein appearance enhancement filter.

## Honest capability note

This software processes ordinary visible-light camera frames. It can increase contrast in vein patterns that are already visible or faintly visible in the image. It cannot see through skin and is not a clinical vein finder. Near-infrared imaging needs compatible camera hardware and NIR illumination.

## Build

Open this folder in Android Studio (JDK 17, Android SDK 35), or run `gradle assembleDebug` with Android Gradle Plugin 8.7.3 available. Every push to `main` runs the unit tests and builds a debug APK in GitHub Actions. Download `app-debug.apk` from the successful workflow run's `Damar-apk` artifact.

The app requests camera permission only. It has no Internet permission and does not upload or persist camera frames.

## Validation

The workflow runs the JVM unit tests and assembles the APK. Camera preview and filter performance still need a real-device check because ordinary visible-light cameras cannot see through skin.

## Intended use

Visual demonstration only. Do not use this app to select an injection site, perform venipuncture, diagnose a condition, or guide treatment.
