# Hearing Companion and offline voice update

## What is added

- Adds a Hear destination in bottom navigation.
- Provides a high-contrast live-caption screen with adjustable 20–38 sp text, recent phrase history, start/pause/stop controls, and phrase correction.
- Uses the existing bundled Sherpa-ONNX streaming recognizer and the same on-device model assets as JARVIS command listening.
- Adds user-authored phrase-to-phrase correction memory in app-private SharedPreferences. It saves text corrections only, not raw audio.
- Separates the live-caption event flow from the command event flow. Captions are not emitted as executable commands.
- Adds a process-wide microphone lease so the HUD, hearing screen, hands-free listener, and ambient sampler can coordinate one app-owned microphone capture.
- Removes the HUD's redundant ambient AudioRecord start. Ambient sound levels continue to update from the voice engine's existing audio stream, while light/motion sensors can remain enabled separately.
- Fixes a nested mutex acquisition in forceWake() and makes engine shutdown callbacks safe when UI or service coroutine scopes are cancelled.
- Adds a Sovereign Offline Mode setting, enabled by default, that prevents LocalCompanionEngine's assistant reasoning path from using the configured cloud API or LAN brain. Turning it off explicitly re-enables those routes.
- Adds a GitHub Actions workflow for JVM unit tests and debug APK assembly.

## Hearing support notes

This feature is a visual speech-access aid, not a medical device, clinical treatment, or replacement for prescribed hearing aids. Transcription can be wrong, especially in noise, with overlapping speakers, low-volume speech, accents, or specialized vocabulary. Check important names, numbers, instructions, safety announcements, and academic details with the speaker or another trusted source.

Raw audio is processed in memory by the local model and is not written to disk by this screen. Completed captions remain in the current screen's memory until the screen is cleared/disposed. Phrase correction mappings remain in app-private storage until removed or app data is cleared.

Live caption mode is started explicitly by the user. It requires the microphone permission and uses the on-device Sherpa model files already configured by the app. This does not guarantee recognition below 200 ms; measure partial/final caption latency and word error rate on the target Galaxy S25 in quiet and noisy situations.

## Offline mode boundary

Sovereign Offline Mode gates assistant requests in LocalCompanionEngine; it does not remove the app's Android INTERNET permission or prove the entire application never makes network requests. Other app areas, such as explicitly configured connectivity tools or settings diagnostics, may still use networking. For a strict zero-network build, audit every network entry point, remove unneeded INTERNET permission/dependencies, and verify with Android's network security tooling before claiming the whole package is air-gapped.

A local language model is still required for open-ended language reasoning. Streaming ASR model files are not an LLM and do not by themselves provide frontier-level reasoning. If no compatible GGUF model is present, the existing on-device rules/knowledge fallback remains limited.

## Manual acceptance checklist

1. Build and run on API 36 and Android 14/15 if supported by the project's target-device matrix.
2. Open Hear, grant microphone permission, and verify captions appear without saying Hey Jarvis.
3. Say ordinary sentences while Hear is active and verify none invoke phone actions.
4. Correct a caption, stop/restart Hear, and check the correction is applied to future matching phrases.
5. Start Hear while hands-free listening is enabled, then exit Hear. Confirm there is never a second AudioRecord conflict and the hands-free service restores only if its preference remains enabled.
6. From the HUD, press push-to-talk repeatedly and confirm the command engine responds rather than hanging.
7. Set Sovereign Offline Mode ON, configure a cloud key/LAN brain, and verify assistant prompts stay on-device; set it OFF and verify only the explicitly configured route becomes available.
8. Inspect battery and thermal use during a 15-minute caption session. Quantify partial-caption latency, final-caption latency, word error rate, missed-speech rate, and false phrase correction rate.
9. Test with hearing-support workflows in quiet and noisy settings. Do not assume transcripts are authoritative for emergencies or other high-stakes instructions.