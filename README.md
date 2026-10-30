# Jarvis for Android

Jarvis is a private, native Kotlin personal assistant for Android 14+ (minSdk 31). It is designed to be sideloaded onto its owner's Samsung Galaxy A54, never published on Google Play.

This is **Phase 2**: the app now has a native streaming chat, provider settings, onboarding, persona, greeting, and voice. You can tap the mic, speak, and Jarvis answers aloud using Fish Audio, ElevenLabs, or Android system TTS. Voice settings, audio focus, interruption, and launch greeting are included. Phone tools, memory, and later phases are still ahead.

**Current status:** Phase 2 source is implemented and a local `assembleDebug` plus unit tests are green. The owner has chosen to defer creating the GitHub repository and running the cloud build until the full app is complete, so no APK artifact exists yet. The build and signing steps below are ready for when that happens.

## Phase 1 test checklist

Before moving to Phase 2, the owner should confirm on the phone:

1. Open **Jarvis**. Complete the short onboarding and save the profile.
2. Add an OpenRouter API key (or Gemini key), choose a model, and tap **Save** and **Test**.
3. Send a text message from the chat composer.
4. Confirm the reply streams into the chat and the provider name is shown.

## Phase 2 test checklist

## Set up the permanent signing key first

Do this **before installing the first APK**, once the repository exists. It keeps every future build signed with the same key, so Android updates Jarvis in place instead of forcing an uninstall that wipes his data.

1. Open the repository in your phone browser and tap **Actions**.
2. In the left-hand workflow list, tap **Generate Jarvis signing keystore**.
3. Tap **Run workflow**. Leave the branch selected and tap the green **Run workflow** button.
4. Wait for a green check, then open the completed run.
5. Scroll to **Artifacts** and tap **jarvis-signing-setup**. Your browser downloads a ZIP file. If GitHub asks you to sign in, sign in first.
6. Open the phone's **Files** app, find the downloaded ZIP (usually **Downloads**), and extract it. It contains `jarvis.keystore`, `KEYSTORE_BASE64.txt` and `SIGNING_SECRETS.txt`.
7. Open `KEYSTORE_BASE64.txt` in a text editor and copy its **entire single-line contents**. Do not add spaces or line breaks.
8. Return to the repository page. Tap **Settings** (on a phone, open the repository menu if the tab is hidden).
9. Tap **Secrets and variables**, then **Actions**, then **New repository secret**.
10. Add the four secrets below, one at a time. For each one, type the exact name, paste the value, and tap **Add secret**.

    | Secret name | Value |
    |---|---|
    | `KEYSTORE_BASE64` | The complete contents of `KEYSTORE_BASE64.txt` |
    | `KEYSTORE_PASSWORD` | The text after `KEYSTORE_PASSWORD=` in `SIGNING_SECRETS.txt` |
    | `KEY_ALIAS` | The text after `KEY_ALIAS=` in `SIGNING_SECRETS.txt` (normally `jarvis`) |
    | `KEY_PASSWORD` | The text after `KEY_PASSWORD=` in `SIGNING_SECRETS.txt` |

11. Go back to **Actions**, open **Generate Jarvis signing keystore**, open its run menu, and **delete the `jarvis-signing-setup` artifact**.
12. Delete the downloaded ZIP, the extracted folder and the two text files from your phone, including the Files app's trash or recycle bin.

The keystore and passwords are now only in GitHub's encrypted Actions secrets and in your memory. Never commit them to the repository.

## Build and install the APK

1. In the repository, tap **Actions**.
2. Tap **Build Jarvis APK** in the workflow list.
3. Tap **Run workflow**, leave the branch selected, and tap **Run workflow**.
4. Wait for the green check. A full first build normally takes a few minutes.
5. Open the completed run, scroll to **Artifacts**, and tap **jarvis-debug-apk**.
6. Extract the downloaded ZIP. It contains `app-debug.apk`.
7. Tap `app-debug.apk` in the Files app. If Android blocks it, allow **Install unknown apps** for Files or your browser, then tap the APK again.
8. Open **Jarvis**. The dark screen with the blue arc reactor should say **Jarvis online**.

Every push to the repository starts the same build automatically. You can also start it manually with **Run workflow**.

## Test that updates preserve data

1. With the signed APK already installed, open **Actions → Build Jarvis APK → Run workflow** again.
2. Download and install the new `app-debug.apk`.
3. Android should offer an **Update** and must not ask you to uninstall Jarvis first.
4. Open Jarvis again; it should still say **Jarvis online**.

If Android says the APK conflicts with the installed app, the builds used different signing keys. That happens if the first APK was installed before the four secrets were added. Uninstall Jarvis once, install the signed build, and future signed builds will update in place.

To compare builds, open a successful **Build Jarvis APK** run and expand **Verify APK is signed**. The certificate SHA-256 line must be the same in every signed run.

## If the build is red

Open the failed run, tap the failed job, and read the first error in the log. The build is intentionally performed only in GitHub Actions because the owner has no PC and this coding environment has no local Java or Android SDK. Do not add a Gradle wrapper JAR; the workflow installs an explicit Gradle version with `gradle/actions/setup-gradle`.

## Security boundary

Provider API keys will be entered by the owner inside Jarvis in Phase 1 and stored with an Android Keystore AES-GCM key. Signing material, API keys and decoded secrets must never be added to this repository.