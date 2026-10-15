# JARVIS V2 — clean core

This branch starts a separate application module, `:jarvis-v2`, with application ID `com.mindfortress.jarvisv2`. The original `:app` module's source is retained in the repository as a reference but is not included in this branch's Gradle settings, build or APK.

## First milestone

- One Activity and one Compose chat screen.
- Explicit import for a locally exported `.litertlm` model.
- No network permission, cloud/PC fallback, microphone request, speech enrollment, AccessibilityService, notification listener, boot receiver or auto-started service.
- Inference starts only after the user sends a message and the model has been imported.
- Model initialization and generation run off the UI thread.
- A 60-second generation timeout provides an explicit error instead of leaving the UI endlessly waiting.
- Import validates the extension, checks available storage when provider metadata is available, copies to a temporary file, then replaces the installed model.
- Local-only error reporting; no fake answer when no model is installed.

## Build

The branch's GitHub Actions workflow builds `:jarvis-v2:assembleDebug` and runs `:jarvis-v2:testDebugUnitTest`. Artifact name: `JARVIS-V2-debug-apk`.

## Model

Import a full Android-ready LiteRT-LM `.litertlm` export, not a training checkpoint archive and not the PEFT `adapter_model.safetensors` file. The fine-tuned Gemma adapter is not yet integrated into this module: it must be merged/exported with compatible tooling and tested before the app can claim it is using the custom-trained weights.

## Roadmap after the clean core passes real-device testing

1. Validate repeated text turns, first-load latency, timeouts and memory on the Galaxy S25.
2. Add a separate diagnostics screen with a crash-safe log export.
3. Add voice as an opt-in capability with explicit enrollment state; voice assets must be packaged and tested before exposing enrollment.
4. Add user-granted notification reading and visible-screen reading.
5. Add deterministic Android action adapters; keep consequential actions behind confirmation.

Do not claim V2 is device-tested merely because CI builds it. A physical-device trial is still required.
