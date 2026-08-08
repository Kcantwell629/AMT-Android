# AMT-Android

Native Android wrapper for the [Flat Rate Quote Builder](https://github.com/Kcantwell629/flat-rate-quote-builder) web app.

## What this is

A single-Activity Kotlin app that loads `app/src/main/assets/index.html` —
a byte-for-byte copy of the web app — into a full-screen `WebView`. All
pricing logic, the Inspection Checklist, and the `.docx` quote generator run
exactly as they do in a browser; nothing was reimplemented natively.

`MainActivity.kt` adds three small native bridges for the things a bare
`WebView` can't do on its own:

| Web app feature | How it's bridged |
|---|---|
| "Text Update to Customer Phone" (`sms:` link) | Intercepted and handed off to the device's SMS app via an `Intent`. `tel:` and `mailto:` links are handled the same way. |
| "Download Quote (.docx)" (blob download) | The page's anchor-click download is caught in JS, the blob is read as base64 and passed to Kotlin, which saves it into the device's **Downloads** folder via `MediaStore`. |
| "Print / Save Quote (PDF)" (`window.print()`) | Routed to Android's native `PrintManager`, which opens the system print dialog (Save as PDF or print to a real printer). |

## Updating the app

Since the web app is the source of truth, pull any future changes by copying
the latest file over the asset:

```bash
cp ../flat-rate-quote-builder/index.html app/src/main/assets/index.html
```

No other changes are needed unless the web app starts relying on a new
browser API the bridges don't cover yet.

## Building

Requires Android Studio (or the SDK + JDK 17 on `PATH`). From the project root:

```bash
./gradlew assembleDebug
```

The debug APK lands in `app/build/outputs/apk/debug/app-debug.apk`.

- `minSdk` 26 (Android 8.0+)
- `compileSdk` / `targetSdk` 36
- No special permissions required — everything is local assets, plus
  `Intent`s handed off to other apps for SMS/telephone/email/print.
