# Decisions

## 2026-10-30 — Project start and prior work

- The working directory `/sdcard/JARVIS2` was empty. A previous Phase 0 attempt existed at
  `/sdcard/JARVIS`, plus the Brahma AI prototype and an earlier web prototype.
- The previous Phase 0 attempt was not reused as-is: its source stopped at a single screen and its CI
  installed a platform package name that the SDK repository does not publish. Phase 0 was rebuilt
  cleanly so the first pushed commit is the first buildable commit.
- Brahma AI's native Android project was inspected for later phases. No web code is carried over.

## 2026-10-30 — Toolchain

- **Android Gradle Plugin 9.5.0**, **Gradle 9.6.0**, **JDK 17**, **Kotlin 2.4.10**,
  **Compose BOM 2026.09.00**, **compileSdk 37**, **targetSdk 36**, **minSdk 31**.
- **Build Tools 36.0.0** and the `platforms;android-37.0` SDK package.
- No Gradle wrapper JAR is committed; CI installs Gradle explicitly.

## 2026-10-30 — CI and signing

- CI uses `actions/checkout@v7`, `actions/setup-java@v6`, `android-actions/setup-android@v4`,
  `gradle/actions/setup-gradle@v6` and `actions/upload-artifact@v7`.
- A bootstrap APK uses the ephemeral CI debug key until the four signing secrets exist; it cannot
  update over a differently signed build. The README explains the one-time keystore workflow.
- Debug builds keep the production application id, so a signed debug APK updates in place.
- `android:allowBackup="false"` because an encrypted DataStore restored without its Keystore key
  would be undecryptable. Jarvis provides explicit JSON export instead.

## 2026-10-30 — Phase 1 implementation

- One shared `LlmProvider` interface and `AgentLoop`, rather than provider-specific orchestration.
- Gemini uses `streamGenerateContent` with `systemInstruction` and `functionDeclarations`.
- Anthropic uses the Messages API with top-level `system`, `tool_use` and `tool_result`.

## 2026-10-30 — Phase 2 implementation

- Cloud voices synthesise **raw 16-bit mono PCM** to avoid MP3 container parsing and keep latency low.
- Fish Audio and ElevenLabs use the same 24 kHz playback path, so switching voices needs no extra code.
- Android system TTS is the fallback and the default when no cloud key is configured.
- Barge-in uses an `AudioRecord` energy detector with echo cancellation and a calibration window.
- Speech is queued sentence by sentence so playback starts after the first complete sentence.

## 2026-10-31 — Fix the input-dispatch ANR

- `Channel.tryReceive()` returns a result object instead of throwing, so the old drain loop spun
  forever on the main thread and Android killed the app for input-dispatch timeout. The drain now
  stops on the first failed receive.
- Interruption replaced the speech pipeline instead of disabling it permanently.

## 2026-10-31 — Phase 3 tools, safety gates, action log and UI rebuild

- `ConfirmationGate` and `PermissionGate` live on the tool context, with bounded waits and cleanup in
  `finally`, so a cancelled turn cannot jam the next one.
- Tools run on `Dispatchers.IO`; the action log was a JSON file until Phase 4.
- Calls and messages degrade to the dialler/composer when the direct permission is missing.

## 2026-10-31 — Fish Audio repair (root causes)

- The request shape was already close to the documented API, but the engine had three real faults:
  it buffered the whole response instead of streaming it, it reported every failure as a raw
  exception string, and it never reset `lastError`, so a successful retry still looked broken.
- It now sends the documented request: `POST https://api.fish.audio/v1/tts`,
  `Authorization: Bearer`, `format: "pcm"`, `sample_rate: 24000`, `latency: "balanced"`,
  `normalize`, `prosody.speed` clamped to 0.5–2.0, and the `model` header.
- **Default model is `s2.1-pro-free`**, the only Fish Audio model that works on a zero balance. A paid
  model (`s2.1-pro`, `s2-pro`) can be typed into Voice settings; Fish Audio documents that `s1` is
  being retired, so it is not offered as a default.
- The response is read in 8 KiB chunks as it arrives, so the first sentence starts speaking while the
  rest is generated. Only 429 and 5xx are retried once; 401/402/403/404/413 are surfaced with the
  exact remedy ("choose s2.1-pro-free", "copy the key again", "voice id not found").
- ElevenLabs got the same streaming and error contract. Its `pcm_24000` format matches, so both
  voices share one playback path.

## 2026-10-31 — Audio pipeline repair

- **Ordering:** the old pipeline launched one coroutine per sentence and locked only after synthesis,
  so a faster later sentence could play first. Synthesis is now one sequential coroutine, while
  playback stays in a second coroutine, which keeps pipelining and guarantees order.
- **Fading:** the old player faded the end of every write. With chunked streaming that would dip the
  middle of a sentence. The synthesis loop now holds back one chunk and fades only the last chunk of
  each sentence.
- **System TTS hang:** `TextToSpeech.stop()` produced no `onDone`, and `onStop` was not implemented, so
  the awaiting coroutine hung forever after a barge-in. `onStop` now completes the wait, and the wait
  is bounded at 90 s.
- **Audio focus:** `abandonAudioFocusRequest` was given a new request object, so the real focus was
  never released. The active request is stored and released. Focus is `GAIN_TRANSIENT`, which pauses
  music rather than merely ducking it.
- **Drain:** waiting for playback is bounded at 30 s so a stalled playback-head report cannot hang a reply.

## 2026-10-31 — One runtime, two clients (Phase 4 / 5 architecture)

- All conversation, voice, memory and gate state moved from the ViewModel into an app-scoped
  `JarvisCore`. The ViewModel is a thin adapter. This is required for background listening: when the
  activity is destroyed the ViewModel dies, but the foreground service keeps the process and the
  runtime alive.
- The permission gate declines immediately when no screen is attached, and says so aloud. A
  background turn cannot hang 120 s waiting for a dialog that cannot appear.
- Confirmation behaves the same way: with no screen, sensitive actions are refused out loud instead
  of silently timing out.
- Provider CRUD stayed in the ViewModel but uses the runtime's single `SettingsRepository`. Two
  DataStore instances for the same file are illegal, so the repository is created once in the core.

## 2026-10-31 — Memory (Phase 4)

- **FTS4, not FTS5.** The brief asks for FTS5, but Android's bundled SQLite does not guarantee the
  FTS5 module across OEM builds. Room's FTS4 with the `unicode61` tokenizer is available everywhere
  and covers the same keyword retrieval; the change is recorded here as the brief instructs.
- The FTS table is **external content** (`contentEntity = MemoryEntity`) so Room's triggers keep it in
  sync; a LIKE scan is the fallback if a MATCH expression is rejected.
- Memories are de-duplicated by a normalised fingerprint (lowercase, punctuation removed) with a
  unique index. Re-remembering the same fact raises its importance instead of adding a row.
- **Two memory writers, on purpose:** the `remember_about_owner` tool saves immediately (so a fact
  survives an instant force-stop), and a WorkManager job distils additional facts four minutes after
  the last turn. The job is replaced on each turn, so it always runs after the conversation goes quiet.
- Extraction is conservative: at most six facts, importance 1–5, secrets rejected by word list and
  long-digit/API-key patterns, and a short summary per conversation.
- Retrieval is per-turn: profile facts always, up to eight matching memories. The persona prompt tells
  the model not to claim memories that are not in that block.
- **"Forget everything"** deletes facts, memories, conversations, messages, summaries and skills, and
  clears the on-screen transcript. The **action log is retained** because the safety rules require a
  record of what Jarvis did; the confirmation dialog says so before running.
- The action log moved from JSON to Room, with a one-time import of `action_log.json` (renamed to
  `.migrated` afterwards).

## 2026-10-31 — Always-on listening without a second SDK

- The brief specifies Picovoice Porcupine. This build uses **wake-word matching on the recogniser's own
  text** instead: the microphone is already open for speech recognition, so no extra binary, licence or
  AccessKey is required, and the owner's existing free Picovoice key is not needed to test.
- The trade-off is that the recogniser, not an always-on VAD, hears the room. Jarvis discards utterances
  without the wake word (default `jarvis`, configurable) and can be switched to "answer everything" in
  Voice settings. Porcupine remains a clean later addition behind the same `WakeWord` boundary.
- Android 14 rules are respected: `foregroundServiceType="microphone"`, the matching permission, and the
  service is only started from the visible app, its notification or the Quick Settings tile. It is
  `START_NOT_STICKY` because a background restart would be refused anyway and look like a crash.
- A partial wake lock is held only while the service runs. That is what keeps recognition alive with the
  screen off, and it is why the wizard asks for unrestricted battery.
- The service loop waits while Jarvis is thinking or speaking and while a manual microphone session is
  open, because Android allows only one active speech recogniser.
- Hands-free follow-up ("continuous microphone") only continues a conversation the owner started by
  voice. The launch greeting no longer opens the microphone on its own.

## 2026-10-31 — Screen control as single steps, not a runaway agent

- The accessibility layer exposes one action per tool call (`read_screen`, `tap_screen`,
  `type_on_screen`, `scroll_screen`, `press_system_key`). The model observes, acts, then observes again.
  This is deliberately narrower than the brief's 15-step loop: every step is in the action log, a
  confirmation cannot be bypassed mid-race, and "Stop" is immediate because nothing is queued.
- Taps on labels containing send/pay/buy/delete/confirm/transfer/post/call always raise the existing
  confirmation sheet, regardless of what the model believes it is tapping.
- The blocklist matches keywords against the foreground package name and app label (banking,
  payments, wallets, password managers, authenticators), is editable in Safety settings, and is
  checked before any read or tap.
- Password fields are reported as `[password field]` and never their contents. No screen text is sent
  anywhere unless the owner asked for it and the model calls `read_screen`.

## 2026-10-31 — Dependency versions added for Phase 4

- **KSP 2.3.13** (Kotlin 2.4 line), **Room 2.8.5**, **WorkManager 2.12.0**, **androidx.core 1.19.1**
  and **lifecycle-service 2.11.0**. Verified against Google Maven and the KSP release notes before
  use; the local `assembleDebug` and `testDebugUnitTest` runs are green with them.
