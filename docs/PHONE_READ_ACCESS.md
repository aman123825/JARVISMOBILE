# JARVIS read-only phone access milestone

## What this milestone does

- Notification listener: after the user enables JARVIS under Android Settings > Notification access, JARVIS keeps up to 40 recent text-bearing notification previews in process memory. It does not persist notification text to disk or upload it.
- Screen reader: a separate read-only AccessibilityService captures accessible text from the last non-JARVIS app screen. Password nodes and keyboard/system windows are excluded. The snapshot is capped at 12,000 characters and is considered usable for two minutes.
- Voice/chat commands supported by deterministic routing:
  - `Read my latest WhatsApp message`
  - `Read my latest notification`
  - `Read all my notifications` (reads the newest eight previews)
  - `Read everything on this screen`
- Long read responses are spoken in multiple text-to-speech chunks, instead of one long utterance.
- The HUD provides buttons to open Android's Notification access and Accessibility settings and a button to clear the local read cache.

## Enable on Android

1. Open JARVIS.
2. In **PHONE ACCESS**, tap **NOTIFICATION ACCESS** and explicitly enable JARVIS under Android Notification access.
3. Tap **SCREEN READING ACCESS** and explicitly enable **JARVIS Screen Reader**. Do not enable the existing distraction blocker just to get read-only screen access.
4. Return to an app with visible text, wait for the screen to settle, then use the hands-free JARVIS command path and say one of the supported read commands.
5. Use **CLEAR READ CACHE** to clear the process-memory cache.

## Privacy and limits

- No notification bodies or screen snapshots are written to disk by this access layer or sent to a network endpoint.
- This cache is process-memory-only and bounded. It is not an archive.
- Notification access exposes notification content only. It does not grant access to WhatsApp's private chat database or guarantee full text when notification previews are hidden/grouped.
- Accessibility reading is limited to content the app exposes through its accessibility node tree. Text baked into images, canvas-only UI, protected/secure windows and other apps' private databases may not be available.
- Password fields are intentionally excluded.
- This milestone does not yet implement universal tapping, typing, call control, or reading every third-party app's private storage. Those require separate, explicit capabilities and tests.
- Sensitive actions such as sending, deleting, purchasing, or changing persistent state must remain behind the action approval flow.

## Manual phone test checklist

- [ ] With notification access disabled, ask JARVIS to read the latest WhatsApp message; verify it says the permission is needed.
- [ ] Enable notification access; receive a WhatsApp notification with a visible preview; ask again and verify the text and source label match the preview.
- [ ] Ask for recent notifications; verify only locally cached previews are read.
- [ ] Enable **JARVIS Screen Reader** without enabling the app blocker; verify ordinary app navigation is not intercepted.
- [ ] Open a text-heavy webpage/notes app and ask JARVIS to read the screen through the hands-free/overlay path.
- [ ] Confirm password fields are omitted and canvas/image text is not falsely claimed to be available.
- [ ] Tap **CLEAR READ CACHE**, then repeat a screen read after opening a different app.
- [ ] Disable each access capability in Android Settings and confirm JARVIS reports the missing access accurately.
- [ ] Test repeated turns, TTS chunk ordering, battery and memory on the actual phone.

## Build status

A fresh Android CI run is required for the latest branch commit before distributing an APK. The earlier CI success applied to the older action-broker commit, not this phone-access code.
