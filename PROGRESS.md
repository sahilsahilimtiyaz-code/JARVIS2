# Progress

## Phase 0 — Skeleton and CI

- [x] Create the native Kotlin / Jetpack Compose app module (`minSdk 31`).
- [x] Add a dark Compose launch screen that says **Jarvis online**.
- [x] Add the GitHub Actions debug-APK build and artifact upload workflow.
- [x] Add the manually triggered keystore-generation workflow.
- [x] Add phone-only signing setup and test instructions to `README.md`.
- [x] Configure CI signing from four repository secrets, with a secure fallback for the bootstrap build.
- [ ] Owner: add the four signing secrets, download the signed APK and install it.

**Note:** local `assembleDebug` and unit tests are green throughout (JDK 17, Gradle 9.6.0,
Android SDK platform 37.0, build-tools 36.0.0). Device testing is still pending because no signed
APK has been installed yet.

## Phase 1 — Brain, chat, persona

- [x] Shared LLM model types, `LlmProvider.stream`, event types and provider exceptions.
- [x] OpenAI-compatible streaming adapter with tool-call accumulation.
- [x] Gemini streaming adapter with function calling.
- [x] Anthropic Messages streaming adapter with tool use.
- [x] Provider registry, fallback order and connection tester.
- [x] Provider presets and Settings UI for add, edit, save, test, default, reorder, remove.
- [x] AES-GCM Android Keystore storage for provider keys.
- [x] Persona prompt builder, owner onboarding and local greeting engine.
- [x] Compose chat with streamed replies, tool activity, provider status and retry.

## Phase 2 — Voice

- [x] `SttEngine` and Android speech recognition, with on-device recognition preferred on Android 13+.
- [x] `TtsEngine` as a streaming PCM flow; Fish Audio and ElevenLabs implemented against their current APIs.
- [x] Android system TTS fallback, with a working stop/cancel path.
- [x] AudioPlayer: one AudioTrack per reply, no gap between sentences, bounded drain, safe stop.
- [x] Barge-in and audio focus, including a proper audio-focus release.
- [x] Voice settings screen, test voice and error surfacing.

### Voice repair pass (2026-10-31)

- [x] Fish Audio rewritten: `s2.1-pro-free` default model header, raw 24 kHz PCM, chunked streaming,
      language hint, retry on 429/5xx, and mapped errors (401/402/403/404/413/429/5xx) fit to show and speak.
- [x] ElevenLabs rewritten with the same streaming and error contract.
- [x] Fixed speech reordering: synthesis is now one sequential coroutine, so sentence 2 can never
      play before sentence 1.
- [x] Fixed system TTS hanging forever after a barge-in (`onStop` never resumed the caller before).
- [x] Fixed the audio-focus leak (abandon used a brand-new request instead of the active one).
- [x] Fixed per-chunk fading that dipped the middle of an utterance; only the last chunk of a sentence fades.
- [x] Automatic cloud→system fallback now reports the cloud error in the UI and in the voice status.
- [x] Raw PCM is written to a shared track so playback starts on the first chunk.
- [x] Greeting no longer silently arms the microphone; hands-free follow-ups only continue
      a conversation the owner started by voice.

## Phase 3 — Tier 1 phone tools

- [x] `ConfirmationGate` and `PermissionGate` on the tool context.
- [x] 17 Tier 1 tools plus fuzzy app matching, contact lookup and the action log.
- [x] Runtime permission requests driven by the tool that needs them.
- [x] Every tool runs off the main thread and is recorded in the action log.
- [x] HUD UI: boot sequence, arc reactor home, tools, action log, quick panel, confirmation sheet.

## Phase 4 — Memory (complete)

- [x] Room database (`jarvis.db`) with `profile_facts`, `memories`, `memories_fts`, `conversations`,
      `messages`, `summaries`, `skills` and `action_log`.
- [x] FTS4 (unicode61) mirror kept in sync by Room triggers, with a LIKE fallback.
- [x] `MemoryRepository`: remember with fingerprint de-duplication, retrieve, edit, delete, count,
      profile-fact sync, conversation persistence and summaries.
- [x] Retrieval on every turn: always-on profile facts plus the memories that match the request,
      injected into the system prompt.
- [x] `remember_about_owner`, `search_memory` and `forget_memory` tools, with a secret guard and a
      confirmation before deleting.
- [x] Conversations and messages persist across a full restart; the transcript is reloaded on launch.
- [x] Background extraction with a WorkManager job that runs a few minutes after the last turn,
      distils at most six durable facts, skips secrets and writes a short summary.
- [x] Memory screen: search, add, edit, delete, profile facts, "extract now", JSON export and import,
      and "forget everything".
- [x] Action log moved into Room, with a one-time import of the old JSON log file.
- [ ] Phone test: tell Jarvis a fact, force-stop, reopen, ask him about it.

## Phase 5 — Always-on (this pass: background listening)

- [x] App-scoped `JarvisCore` shared by the UI, the listening service and the workers.
- [x] `JarvisListeningService`: foreground service with `microphone` type, ongoing notification with
      Stop, partial wake lock, and `START_NOT_STICKY` so Android never rejects a background restart.
- [x] Continuous wake-word loop: recognition text is matched against the wake word, so no second
      Picovoice key is required; Jarvis ignores room chatter unless the owner turns the wake word off.
- [x] Summons with a short "Yes, sir?" and a fresh time-aware greeting after a gap.
- [x] Quick Settings tile to start and stop listening.
- [x] Battery guidance and the "start it from the app" rule in the permission wizard.
- [ ] Default assistant (`VoiceInteractionService`) and side-key/assist gesture integration.
- [ ] Porcupine offline wake word (needs the owner's Picovoice AccessKey; the text wake word works today).

## Phase 6 — Notifications and screen control (partial)

- [x] Accessibility service and single-step screen primitives: read, tap by label, type, scroll,
      back/home/recents/notifications/quick settings.
- [x] Tools for all of the above, with a blocklist (banking/payments/password managers) and a
      spoken/visual confirmation for risky labels such as Send, Pay, Delete and Confirm.
- [x] Password fields are never reported, and a blocked app is refused before any read.
- [x] Permission wizard explains the restricted-settings step and Advanced Protection Mode.
- [ ] Notification listener (reading, summarising and dismissing notifications).
- [ ] Multi-step automation loop with its own persistent "Jarvis is controlling the screen" notification.

## Phase 7 — Self-improvement and proactive mode

- [ ] Not started. The `skills` table and proposal groundwork exist, but no UI or replay yet.

## Phase 8 — Polish

- [x] Quick Settings tile.
- [x] Crash-safe runtime start (the application logs rather than kills the process).
- [ ] Home-screen widget, JSON settings export, richer notification controls.

## Phase 9 — Optional Google services

- [ ] Not started.
