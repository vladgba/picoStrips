# picoStrips

Lightweight barcode / QR / Aztec / Data Matrix scanner and generator for Android.
No internet permission, no Google Play Services, no AppCompat/Compose — just the Android framework,
[zxing-cpp](https://github.com/zxing-cpp/zxing-cpp) for scanning and [ZXing](https://github.com/zxing/zxing) core for generating.
Everything is processed on the device.

## Screens

Header with three tabs:

- **Scan** — continuous live scanning (Camera2), torch, scan from an image file. Every new code slides up a
  bottom popup (Open / Copy / Share, tap for details); the next new code replaces it automatically. A code that
  stays in view doesn't re-trigger.
- **Last** — scanned and saved codes, showing the code type; tap one to see it drawn full-screen
  (brightness boosted so another scanner can read it) with Copy / Share / Open / Share image / Delete.
  Long-press for quick actions.
- **Generate** — pick a format, type content, live preview; share as PNG or save to Last.

## Honeywell scanners

On Honeywell mobile computers (CT40, CT60, EDA5x, …) the built-in scan engine works too, through Honeywell's
`DataCollection.jar` (`app/libs`). Press the hardware trigger on any tab: the app switches to **Scan** and shows the
code in the same popup, and it is saved to **Last**. The scanner is claimed only while the app is in the foreground, so
other apps get it back when you leave. On other devices this does nothing: the app first checks that Honeywell's
scanner service is installed.

## Libraries

- **Scanning — zxing-cpp** (native, ~1.2–1.8 MB per ABI): QR, Micro QR, rMQR, Aztec, Data Matrix, PDF417,
  MicroPDF417, MaxiCode, EAN-13/8, UPC-A/E (+add-ons), Code 128/39/93, Codabar, ITF, GS1 DataBar (all variants),
  DX film edge. Tolerant of blur, rotation, inverted codes.
- **Generating — ZXing core** (Java; the zxing-cpp Android wrapper has no writer): QR (with error-correction level),
  Aztec, Data Matrix, PDF417, Code 128/39/93, Codabar, ITF, EAN-13/8, UPC-A/E. R8 strips its reader classes.

Release APK: ~2.0 MB (arm64-v8a), ~1.5 MB (armeabi-v7a), ~5 MB universal.

## Build

```
gradlew assembleRelease          # per-ABI + universal APKs in app/build/outputs/apk/release/
gradlew testDebugUnitTest        # generate → decode round-trip for every writable format
```

Release builds are signed with the debug key so they install directly; set your own `signingConfig` before publishing.
Requires JDK 17+, Android SDK 36. minSdk 24.

## Privacy

The merged manifest contains two permissions: `CAMERA`, and `com.honeywell.decode.permission.DECODE`, which
only means anything on Honeywell devices. `INTERNET` / network-state permissions are
explicitly removed with `tools:node="remove"`, so no dependency can add them. History lives in a local SQLite
database and is excluded from cloud backup.
