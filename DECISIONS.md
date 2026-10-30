# Decisions

## 2026-10-30 — Project start and prior work

- The working directory `/sdcard/JARVIS2` was empty. A previous Phase 0 attempt existed at `/sdcard/JARVIS`, plus the Brahma AI prototype at `/sdcard/jarves/Brahma-Ai-Evo-main` and the earlier web prototype at `/root/projects/jarvis-web`.
- The previous Phase 0 attempt was not reused as-is: its Git directory (`/root/JARVIS.git`) no longer exists, its source stopped at a single screen, and its CI installed `platforms;android-37`, which is not the package name published by the current Android SDK repository. Phase 0 was rebuilt cleanly so the first pushed commit is the first buildable commit.
- Brahma AI's native `brahma-connect-android` project was inspected for later phases (foreground service, accessibility service and device command patterns). No web code is being carried over. Useful Kotlin patterns will be ported when the relevant phase starts.

## 2026-10-30 — Toolchain

- **Android Gradle Plugin 9.5.0**, released October 2026. It supports API 37 and uses built-in Kotlin, so the obsolete `org.jetbrains.kotlin.android` plugin is not applied.
- **Gradle 9.6.0**, the minimum and default Gradle version documented for AGP 9.5. CI installs it explicitly with `gradle/actions/setup-gradle`; no Gradle wrapper JAR is committed.
- **JDK 17**, the documented AGP 9.5 minimum and default.
- **Kotlin 2.4.10** and the matching Compose Compiler Gradle plugin. AGP 9 bundles an older Kotlin Gradle plugin, so the root build declares KGP 2.4.10 explicitly. This is the version shown in the current official Compose compiler setup documentation.
- **Compose BOM 2026.09.00**, the latest stable BOM in Google's Maven repository and the version named in the current official Compose setup docs. It maps to Compose UI 1.12.1 and Material3 1.4.0.
- **compileSdk 37**, required by Compose UI 1.12.0 and newer according to the official Compose setup page. The SDK package is installed as `platforms;android-37.0`, the package name currently published in the Android SDK repository (the platform uses minor-version packages for API 37).
- **targetSdk 36** and **minSdk 31**. API 36 is the stable target used for Phase 0; compileSdk is intentionally newer than targetSdk, which Android documents as supported and normal. The owner's Galaxy A54 runs Android 14 or newer.
- **Build Tools 36.0.0**, the default for AGP 9.5.

## 2026-10-30 — CI and signing

- CI uses the current major action versions: `actions/checkout@v7`, `actions/setup-java@v6`, `android-actions/setup-android@v4`, `gradle/actions/setup-gradle@v6` and `actions/upload-artifact@v7`.
- A bootstrap APK is produced when the four signing secrets are absent, using the ephemeral CI debug key. This keeps the first build green before the owner has run the keystore workflow, but it cannot update over a differently signed build. The README tells the owner to configure signing before installing the first APK.
- When `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD` all exist, CI decodes the keystore to `$RUNNER_TEMP`, passes the path and passwords through environment variables, and signs both debug and release variants with it. Values are never echoed.
- The keystore workflow uses PKCS12 with one random password for both the store and key, because PKCS12 does not reliably support separate key passwords. It uploads the keystore and a plain-text secrets sheet as a one-day artifact. The owner must add the four secrets and delete the artifact and downloaded files.
- Debug builds do not use an `applicationIdSuffix`, so a debug APK can update the installed app without changing its application ID.
- `android:allowBackup="false"` is set because an Android cloud backup would restore encrypted preference blobs without the hardware-backed Keystore key, causing undecryptable settings. Jarvis will provide explicit JSON export/import later.

## 2026-10-30 — Phase 1 implementation

- Phase 1 uses one shared `LlmProvider` interface and `AgentLoop` instead of provider-specific orchestration.
- The OpenAI-compatible adapter covers OpenRouter, OpenAI, Groq, Together, Ollama and compatible endpoints; it emits text deltas and accumulates streamed tool calls.
- Gemini uses `streamGenerateContent` with `systemInstruction` and `functionDeclarations`, converting the shared JSON schema to Gemini's dialect.
- Anthropic uses the Messages API with a top-level `system` field, `tool_use`, and `tool_result` handling.
- Phase 1 keeps conversation history in the UI layer for the current turn; Room-backed conversations and messages arrive with Phase 4 memory.
- The greeting is generated locally by `GreetingEngine` and stored through the persona settings; spoken TTS arrives in Phase 2.

## 2026-10-30 — GitHub access deferred by the owner

- The coding environment initially had no pre-existing GitHub authentication, no `gh`, and no local Java/Gradle/Android SDK; a local JDK 17, Gradle 9.6.0 and Android SDK platform 37 were provisioned later for compile checks.
- The owner explicitly directed that GitHub setup, repository creation, pushing and cloud builds be deferred until the full app is complete. The pending device-login process was stopped; no GitHub repository was created and nothing was pushed.
- Consequence: no physical phone install or device test has happened yet; no APK is produced until the first cloud build, and no phase can be installed or tested on the phone before that. Compile errors will accumulate until the first CI run. The source will still be written to the documented stable versions and kept as build-ready as possible.
- The workflows and phone-only signing instructions remain in `.github/workflows/` and `README.md`, ready to use when the owner chooses to publish the repository.
- All work is confined to `/sdcard/JARVIS2`. The earlier `/sdcard/JARVIS` project and the Brahma AI prototype were read once for context, then left untouched.
