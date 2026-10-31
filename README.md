# Jarvis for Android

Jarvis is a private, native Kotlin personal assistant for Android 14+ (minSdk 31), built for one
owner's Samsung Galaxy A54. It is sideloaded, never published on Google Play.

**Current status: Phase 4 complete, plus background listening and single-step screen control.**
Jarvis chats with your own AI provider, speaks in your Fish Audio or ElevenLabs voice, controls the
phone by voice, listens in the background for its wake word, remembers you across restarts, and can
read and tap the screen once you enable accessibility.

Local `assembleDebug` and `testDebugUnitTest` are green (JDK 17, Gradle 9.6.0, AGP 9.5.0, SDK 37.0).
No signed APK has been installed on the phone yet, so every phone checklist below is still pending.

## What works now

- **Brain:** OpenRouter, OpenAI, Groq, Together, Ollama or any OpenAI-compatible endpoint, plus Google
  Gemini and Anthropic, with streaming, fallback order and tool calling.
- **Voice:** Fish Audio and ElevenLabs streaming PCM into one gapless player, Android system voice as
  fallback, barge-in, spoken greeting, hands-free follow-up.
- **Phone tools:** open any app, YouTube search, web search, media, volume, ringer, torch, timers,
  alarms, calls, SMS, navigation, calendar, share, settings screens, battery and network status.
- **Memory:** every fact, preference and conversation is stored on the phone, retrievable by keyword,
  and injected into the prompt. Background extraction adds facts after a conversation goes quiet.
- **Always-on:** a microphone foreground service with a wake word, an ongoing Stop notification and a
  Quick Settings tile.
- **Screen control:** read the screen, tap a labelled control, type, scroll, and press system keys,
  with a blocklist and confirmations on anything that could send, pay or delete.

## The interface

- **Home:** arc reactor (tap to talk), what Jarvis is doing, transcript, composer and Stop.
- **Memory:** search, add, edit and delete notes; profile facts; export/import JSON; forget everything.
- **Tools:** every tool with its real availability on this phone.
- **Settings:** providers, voice, persona, permissions, safety and the action log.
- **Quick panel:** swipe from the right edge for live battery, memory, storage, network, temperature and
  ringer mode, with working torch and playback switches.

Bottom navigation is Home, Memory, the reactor microphone, Tools and Settings.

## Fish Audio and ElevenLabs

1. Open **Settings, Voice**.
2. Choose **Fish Audio**. Paste your API key from `fish.audio/app/api-keys`.
3. Paste the **voice reference id**. That is the id of a cloned voice or a voice from your Fish Audio
   library, not its display name.
4. Leave the model field blank for `s2.1-pro-free` (the only model that works with a zero balance), or
   type `s2.1-pro` if your account has credit.
5. Tap **Save voice**, then **Test voice**. The sentence must come out in your voice.

ElevenLabs works the same way with `xi-api-key` and a voice id from your library; Jarvis uses the
lowest-latency Flash model and `pcm_24000`, which is the same format as Fish Audio.

If the cloud voice fails, Jarvis falls back to the Android system voice (unless you turn the fallback
off) and shows the reason in **Voice status**. The common ones:

| Message | Meaning |
|---|---|
| Fish Audio rejected the API key | The key is wrong or revoked. Copy it again. |
| Fish Audio has no credit for this model | `s2.1-pro` needs credit; use `s2.1-pro-free`. |
| Fish Audio could not find that voice reference id | The id is not a voice on your account. |
| Fish Audio is busy (rate limit) | 429 concurrency limit; Jarvis retries once, then falls back. |
| Fish Audio returned no audio | The request worked but produced nothing; check the voice id. |
| ElevenLabs could not find that voice id | The id is wrong or belongs to another account. |

Notes: audio is 16-bit mono PCM at 24 kHz served over HTTPS. Text is sent to the provider only when
Jarvis speaks; nothing is cached by the app. A failed sentence falls back individually, so a long
reply can mix voices mid-way rather than stop.

## Background listening

1. Grant the microphone in **Settings, Permissions**.
2. Turn on **Listen in the background** (Settings, Voice extras) or the **Permissions** switch. The
   service starts with an ongoing **Jarvis is listening** notification.
3. Say **"Jarvis"**. He answers "Yes, sir?" (or a fresh greeting after a pause), then waits for the
   request: "Jarvis, open YouTube and search lo-fi."
4. Stop it from the notification action, the Quick Settings tile, or the switch in the app.

Android 14 only allows a microphone foreground service to start from a visible app, its notification
or a tile, so open Jarvis once after a reboot. Set Jarvis's battery usage to **Unrestricted** and
remove him from Samsung's **Sleeping apps** or the service will be killed with the screen off.

The wake word is matched from the recogniser's text, so a second Picovoice key is not needed. You can
change the wake word, or turn the requirement off so every sentence is treated as a request, in
**Settings, Voice extras**.

## Accessibility screen control

1. Open **Settings, Permissions, Accessibility**.
2. If the switch is greyed out: Android **Settings, Apps, Jarvis**, three-dot menu, **Allow restricted
   settings**. Samsung may call it **More options**.
3. Return to **Accessibility, Downloaded apps, Jarvis screen control** and enable it.
4. Come back to Jarvis. The wizard shows "Enabled" once the service is connected.

Then, for example: "Jarvis, open Settings and turn on dark mode" (he reads, taps, and verifies one
step at a time), "scroll down", "type hello in the search box", or "go back".

Safety: banking, payment, wallet, password-manager and authenticator apps are refused before anything
is read. Taps whose label contains send, pay, buy, delete, confirm, transfer, post or call ask for your
yes first. Password fields are reported as `[password field]`. Every action lands in the action log.
If Android Advanced Protection Mode is on, it blocks accessibility and sideloading for apps like this
one; the wizard warns about that too.

## Memory

Jarvis stores profile facts, free-form memories (with type and importance), conversations, messages,
summaries, skills and the action log in a local Room database. Before each reply he includes the core
profile and up to eight memories that match your request. After a conversation goes quiet for about
four minutes, a background job asks the model for durable facts and saves them, skipping secrets.

- **Remember something now:** "Jarvis, remember that my sister is called Anya."
- **Ask him:** "What do you remember about my work?"
- **Forget one thing:** "Forget what I told you about my sister" (he asks first).
- **Forget everything:** Memory screen, **Forget everything**. It clears facts, memories,
  conversations, summaries and skills, and the on-screen transcript; the action log is kept as the
  safety record.
- **Export/import:** Memory screen, **Export** writes JSON through the system file picker; **Import**
  reads it back and merges by content.

## Phase 4 test checklist (phone)

1. Say or type: "Remember that my sister is called Anya and I prefer tea to coffee."
2. Force-stop Jarvis from Android settings and reopen it.
3. Ask: "What is my sister called?" and "What do I prefer?" Both answers must come from memory, with
   no re-typing.
4. Open **Memory** and confirm the notes appear; edit one, delete one.
5. Start a conversation about a lasting detail, wait five minutes, reopen **Memory**. A new extracted
   note should be there (or press **Extract now**).
6. Export to JSON, **Forget everything**, then import the file; the notes return.
7. Check **Settings, Permissions** and grant anything marked as missing.
8. Turn on background listening, lock the screen, and say "Jarvis".

## Phase 3 and voice checklist (phone)

1. "Open YouTube and search lo-fi" — YouTube (or the browser) opens on results.
2. "Call <a contact>" — Jarvis asks; tap **Yes, do it** and the call starts.
3. "Set a timer for ten minutes" — the Clock app shows a running timer.
4. "Turn on the flashlight" — torch on; the quick panel agrees.
5. "Pause the music" — playback pauses.
6. "Navigate to <a place>" — Maps opens with directions ready.
7. "What's my battery at?" — a real reading, no invented numbers.
8. **Test voice** in Settings, then interrupt Jarvis mid-sentence by speaking; he must stop at once.

## Build and install the APK

### Set up the permanent signing key first

Do this **before installing the first APK**, once the repository exists. It keeps every future build
signed with the same key, so Android updates Jarvis in place instead of forcing an uninstall that
wipes his data.

1. Open the repository in your phone browser and tap **Actions**.
2. In the workflow list, tap **Generate Jarvis signing keystore**.
3. Tap **Run workflow**, leave the branch selected, tap the green **Run workflow** button.
4. Wait for a green check, then open the completed run.
5. Scroll to **Artifacts** and tap **jarvis-signing-setup**. If GitHub asks you to sign in, sign in first.
6. Open **Files**, find the downloaded ZIP (usually **Downloads**) and extract it. It contains
   `jarvis.keystore`, `KEYSTORE_BASE64.txt` and `SIGNING_SECRETS.txt`.
7. Open `KEYSTORE_BASE64.txt` and copy its entire single-line contents. Do not add spaces or breaks.
8. Return to the repository. Tap **Settings**, **Secrets and variables**, **Actions**, then
   **New repository secret**.
9. Add the four secrets below, one at a time, typing the exact name and pasting the value.

    | Secret name | Value |
    |---|---|
    | `KEYSTORE_BASE64` | The complete contents of `KEYSTORE_BASE64.txt` |
    | `KEYSTORE_PASSWORD` | The text after `KEYSTORE_PASSWORD=` in `SIGNING_SECRETS.txt` |
    | `KEY_ALIAS` | The text after `KEY_ALIAS=` in `SIGNING_SECRETS.txt` (normally `jarvis`) |
    | `KEY_PASSWORD` | The text after `KEY_PASSWORD=` in `SIGNING_SECRETS.txt` |

10. Open **Actions, Generate Jarvis signing keystore**, open its run menu and **delete the
    `jarvis-signing-setup` artifact**.
11. Delete the downloaded ZIP, the extracted folder and the two text files from the phone, including
    the Files trash.

### Build

1. **Actions, Build Jarvis APK, Run workflow.**
2. Wait for the green check (a first build takes a few minutes).
3. Open the run, scroll to **Artifacts**, tap **jarvis-debug-apk**, extract `app-debug.apk`.
4. Tap the APK in Files. If Android blocks it, allow **Install unknown apps** for Files, then tap again.
5. Open **Jarvis**, finish onboarding, add a provider key, then add your voice key.

Every push builds automatically. A second build installs over the first without wiping data as long
as both were signed with the same key. The **Verify APK is signed** step prints one certificate
SHA-256; compare it across runs.

### If the build is red

Open the failed run, tap the failed job and read the first `e:` line. The build is intentionally done
only in GitHub Actions because the owner has no PC. Do not commit a Gradle wrapper JAR; the workflow
installs Gradle 9.6.0 explicitly.

## Permissions Jarvis asks for

| Permission | When | Why |
|---|---|---|
| Microphone | First mic tap or background listening | Wake word and speech recognition |
| Notifications | First background listening start | Ongoing listening card with Stop |
| Accessibility | When you open the wizard | Reading and tapping the screen |
| Battery unrestricted | Wizard step | Keeps background listening alive |
| Contacts / Phone / SMS | First call or message | Lookup, dial, send |
| Camera / torch | First torch request | Flashlight control |

## Security boundary

API keys are typed into the app and stored with AES-GCM using an Android Keystore key. They are never
written to the repository, `BuildConfig` or logs. Voice audio, prompts and memory stay on the phone
except for what must be sent to your chosen AI and voice providers. Signing material must never be
committed. The accessibility service collects no events and sends nothing anywhere by itself.

## Where things live

```
app/src/main/java/com/jarvis/assistant/
  brain/        providers, agent loop, HTTP and SSE
  core/         JarvisCore runtime and shared state
  memory/       Room entities, DAO, repository, extraction worker
  voice/        STT, wake word, Fish Audio, ElevenLabs, system TTS, player, barge-in
  tools/        every phone action, schema helpers and the action log
  service/      listening service, notification helper, Quick Settings tile
  accessibility/ service and screen primitives
  settings/     encrypted keys and preferences
  persona/      prompt builder and greetings
  permissions/  live permission snapshot and settings deep links
  ui/           Compose HUD, memory, permissions and settings screens
```
