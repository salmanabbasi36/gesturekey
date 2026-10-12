# GestureKey (Android)

**Tap the widget → show a gesture → your chat opens with the message ready → tap Send.**

- One-tap home-screen widget (or "Try it now" in the app).
- Front camera plus Google's on-device MediaPipe gesture model with its 7 built-in
  gestures: 🤟 ILoveYou, 👍 Thumb_Up, ✌️ Victory, ☝️ Pointing_Up, 👎 Thumb_Down,
  ✊ Closed_Fist and 🖐️ Open_Palm. No training needed.
- Each gesture has its own message, and optionally its own recipient.
- Send through WhatsApp (straight into a contact's chat if you enter their number),
  Instagram, Telegram, SMS, or any app via the share sheet.
- A gesture must be held for about 8 frames (around half a second). Then there's a
  1.2-second countdown you can cancel before the app opens.
- Frames are processed in memory, on the phone. Nothing is saved or uploaded.
  The camera only runs while the gesture screen is open.

## Install the APK

1. Download `app-debug.apk` from the latest successful run under the repo's **Actions** tab
   (or use the copy Claude sent you).
2. On the phone, open it and allow "Install unknown apps" for your browser or file manager when asked.
3. Open GestureKey, set **Send to** (e.g. WhatsApp plus your partner's number with country
   code), then tap **Add widget**.

Every build is signed with the same committed debug key, so new builds install over old ones.

## Build it yourself

Open this folder in Android Studio and press Run. Or run `./gradlew testDebugUnitTest assembleDebug`.
The first build downloads `gesture_recognizer.task` into `app/src/main/assets/`.
Every push to GitHub builds the APK and runs the unit tests (`.github/workflows/build.yml`).

## Code map

| File | What it does |
| --- | --- |
| `Model.kt` | Gestures, targets, settings, the hold-to-trigger `GestureStabilizer`, link building (pure Kotlin, unit-tested) |
| `CaptureActivity.kt` | CameraX front camera, then MediaPipe in VIDEO mode, then stabilizer, then countdown, then launch |
| `MessageLauncher.kt` | WhatsApp deep link, share intents, SMS, chooser fallback |
| `MainActivity.kt` | Compose settings screen and the pin-widget button |
| `GestureWidget.kt` | The one-tap widget |
| `SettingsStore.kt` | SharedPreferences |

## Known limits (honest list)

- **You always tap Send.** Android doesn't let an app send through WhatsApp or Instagram
  silently. Only our own in-app channel could, and that would need both people to install
  GestureKey.
- **Instagram** has no "message this person" link, so it opens Instagram's share screen.
  The text is also copied to the clipboard in case Instagram drops it.
- The debug key is for testing only. A Play Store release needs a private release key.
