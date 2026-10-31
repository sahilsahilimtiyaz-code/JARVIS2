# Progress

## Phase 0 — Skeleton and CI

- [x] Create the native Kotlin / Jetpack Compose app module (`minSdk 31`).
- [x] Add a dark Compose launch screen that says **Jarvis online**.
- [x] Add the GitHub Actions debug-APK build and artifact upload workflow.
- [x] Add the manually triggered keystore-generation workflow.
- [x] Add phone-only signing setup and test instructions to `README.md`.
- [x] Configure CI signing from four repository secrets, with a secure fallback for the bootstrap build.
- [x] Initialize a fresh Git repository in `/sdcard/JARVIS2` and commit Phase 0.
- [ ] Push to GitHub and get the first green **Build Jarvis APK** run. **Deferred by the owner until the full app is complete.**
- [ ] Owner adds the four signing secrets and downloads the signed APK. Deferred with the GitHub setup.
- [ ] Install the signed APK on the Galaxy A54 and verify **Jarvis online**. Deferred until the first cloud build.
- [ ] Install a second signed build over the first and confirm data is preserved. Deferred until the first cloud build.

**Note:** local compile checks were run with JDK 17, Gradle 9.6.0 and the Android SDK platform 37 (`/tmp/android-build/sdk`). `:app:compileDebugKotlin`, `:app:testDebugUnitTest` and `assembleDebug` are green, and the debug APK signs with the Android debug key. The owner has chosen to defer creating the GitHub repository and running the cloud build until the full app is complete, so no APK artifact exists yet.

## Phase 1 — Brain, chat, persona

- [x] Add the shared LLM model types, `LlmProvider.stream`, event types and provider exceptions.
- [x] Add the OpenAI-compatible streaming adapter with tool-call accumulation and full-response parsing.
- [x] Add the Gemini streaming adapter with function calling and provider-native schema conversion.
- [x] Add the Anthropic Messages streaming adapter with top-level system, tool use and streaming text.
- [x] Add the provider registry, fallback flow and provider connection tester.
- [x] Add provider presets plus Settings UI for add, edit, save, test, default, reorder and remove.
- [x] Add AES-GCM Android Keystore storage for provider API keys.
- [x] Add persona prompt builder, owner onboarding and local greeting engine.
- [x] Add Compose chat screen with streamed replies, tool activity, provider status, errors and retry.
- [x] Green local build, unit tests and debug APK sign check.
- [ ] Physical phone test with real provider keys, launch greeting and streamed chat. Deferred until the first signed APK and cloud build.

## Phase 2 — Voice

- [x] Add `SttEngine` interface and Android speech recognizer implementation.
- [x] Add `TtsEngine` interface and Fish Audio PCM streaming implementation.
- [x] Add ElevenLabs streaming PCM implementation with Flash model.
- [x] Add Android system TTS fallback.
- [x] Add audio playback, audio focus, barge-in detection and sentence queueing.
- [x] Add microphone permission flow and voice settings screen.
- [x] Speak launch greeting and interrupt speech on Stop or detected speech.
- [x] Fix the main-thread ANR that froze the app (and blocked the mic) on Send, Stop and mic tap, and keep the speech pipeline alive across interruptions.
- [x] Green local build and unit tests.

## Phase 3 — Tier 1 phone tools

- [x] Add `ConfirmationGate` and `PermissionGate` to the tool context so a tool can ask the owner before it acts.
- [x] Add 17 Tier 1 tools: open app, YouTube search, web search, media control, volume, ringer, flashlight, timer, alarm, call contact, send SMS, navigate, calendar event, share text, open settings screen, device status, current time.
- [x] Add fuzzy installed-app matching with spoken-style names and suggestions when nothing matches.
- [x] Add contact lookup (name or spoken number) for calls and messages.
- [x] Add a local JSON action log with a real Action Log screen, newest first, clearable after confirmation.
- [x] Add runtime permission requests driven from the tool that needs them.
- [x] Run every tool off the main thread and record each run to the action log.
- [x] Tell the persona prompt to act with tools and to call several in order for multi-step requests.
- [x] Rebuild the UI as a HUD: boot sequence, arc reactor home, tools screen, action log, restyled settings, swipe-in quick panel, confirmation sheet.
- [x] Green local `assembleDebug` and unit tests with no compiler errors or warnings.
- [ ] Physical phone test of the six Phase 3 commands by voice. Deferred until the next signed APK is installed.

## Phase 4 — Memory

- [ ] Not started.

## Phase 5 — Always-on / default assistant

- [ ] Not started.

## Phase 6 — Notifications and screen control

- [ ] Not started.

## Phase 7 — Skills and proactive mode

- [ ] Not started.

## Phase 8 — Polish

- [ ] Not started.

## Phase 9 — Optional Google services

- [ ] Not started.
