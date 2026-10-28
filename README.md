# Yadea Wallpaper Studio

Android BLE diagnostic prototype for the Yadea G5Ⅱpro-M-悦享版双碟 EC-A instrument cluster.

## Current safety scope
- Scan nearby Bluetooth Low Energy devices and show advertised names / addresses.
- Connect to a selected device and enumerate GATT services and characteristics.
- Read characteristic values only when the user explicitly requests a read and the characteristic advertises read support.
- Does not write to unknown characteristics or claim support for an undocumented dashboard protocol.

## Build APK
Open the **Actions** tab and run **Build Android APK**. After a successful run, open its run details and download the `YadeaWallpaperStudio-debug-apk` artifact. This is a debug APK for testing, not a production-signed release.
