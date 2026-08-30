# Recall — automatic call recorder (Android)

An Android app that **automatically records your phone calls** and lets you play them
back from a list. No button to press per call: once permissions are granted it stays
armed in the background (a persistent foreground service) and records every call from
the moment it connects until it ends.

- Detects call start/end via the system phone state.
- Records to `m4a` (AAC) in the app's private storage.
- List screen with caller number, direction (in/out), date and duration.
- Tap a row to play/stop, long-press to delete, **share icon to send a recording out**
  (to WhatsApp, email, Drive, etc. via the Android share sheet).
- **Auto speakerphone** toggle: when on, the app switches the call to speaker while
  recording so the microphone also picks up the other person, then restores it after.
- Re-arms itself after a reboot.

---

## ⚠️ Read this first — two honest limitations

**1. The law.** Recording calls is regulated. Many places require that **all parties
consent** (two-party consent); others need only one. Recording your own calls where you
are a party is legal in some jurisdictions and illegal in others. **Only use this on your
own calls where it is legal for you to do so, and tell the other person if the law where
you are requires it.** You are responsible for how you use it.

**2. The technology.** Google deliberately locked down third-party access to *call audio*
starting in Android 10, and tightened it further since. On a normal, non-rooted modern
phone the "record both sides of the call" audio sources (`VOICE_CALL`) are usually
**blocked**. This app tries them first and then falls back to the microphone. What you
actually get on a stock recent phone is:

- **Your side:** clear.
- **The other person's side:** faint or missing — unless the call is on **speakerphone**.

To reliably capture both sides you generally need one of: an older Android device, a
rooted device, a manufacturer that allows it (some Xiaomi/Samsung regions), or an
accessibility/telephony privilege that Google no longer grants to normal apps. That is a
platform restriction, not something the app code can bypass.

The **Auto speakerphone** toggle in the app is the practical workaround: with the call on
speaker, the far side plays out the loudspeaker and the mic records it too. It is off by
default (turning speaker on mid-call is intrusive if you're holding the phone to your ear),
and on modern telephony calls the OS may still override routing — so treat it as
best-effort, not guaranteed.

**iPhone:** not possible at all — iOS gives apps zero access to call audio. This is
Android-only for that reason.

---

## Build the APK

You need JDK 17 and the Android SDK (you already have both).

### Easiest: Android Studio
1. **File → Open** and select this `Recall` folder.
2. Let it sync (it downloads Gradle 8.7 and generates the wrapper automatically).
3. **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
4. The APK lands in `app/build/outputs/apk/debug/app-debug.apk`.

### Command line
From this folder, generate the wrapper once (if you have a `gradle` on PATH), then build:

```bash
gradle wrapper --gradle-version 8.7
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`.

> If files here look empty or the build fails to read them, copy the whole `Recall`
> folder out of OneDrive first (OneDrive can turn files into cloud placeholders).
> This project already lives at `C:\Users\canel\Recall`, outside OneDrive, for that reason.

## Install & use
1. Copy `app-debug.apk` to the phone and install it (allow "install from unknown sources").
2. Open **Recall**, tap **Grant permissions**, and allow microphone, phone, and (13+)
   notifications. On some phones you must also allow the ongoing notification and disable
   battery optimization so the service is not killed.
3. That's it — make or answer a call. When it ends, it appears in the list. Tap to listen.

## How it works
- `CallRecorderService` — one always-on foreground service; listens to call state and owns
  the `MediaRecorder`. Kept alive so it never has to cold-start during a call (which
  Android 12+ would block).
- `PhoneStateReceiver` — feeds the caller number and direction to the service, keeps it alive.
- `BootReceiver` — re-arms after reboot.
- `RecordingStore` / `Recording` — tiny JSON index + audio files in app-private storage.
- `MainActivity` — the list + playback UI.

## Permissions used
`RECORD_AUDIO`, `READ_PHONE_STATE`, `READ_CALL_LOG`, `PROCESS_OUTGOING_CALLS`,
`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE`, `POST_NOTIFICATIONS`,
`RECEIVE_BOOT_COMPLETED`, `MODIFY_AUDIO_SETTINGS` (for the speakerphone toggle).

Sharing uses a `FileProvider` (authority `com.larprober.recall.fileprovider`) so recordings
in the app's private folder can be handed to other apps safely.

## Notes
- Recordings are stored **only on the device**, in the app's private folder. Nothing is
  uploaded anywhere.
- `minSdk 26` (Android 8), `targetSdk 34` (Android 14).
- Not a Play Store build: Google Play bans call-recording apps that use these APIs, so this
  is for sideloading / personal use.
