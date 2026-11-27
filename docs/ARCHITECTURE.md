# Saqr Code architecture · البنية

A map for contributors. Source lives in `app/src/main/java/com/ctodrs/saqrcode/`. About 36k lines of Kotlin, Jetpack Compose UI, and a test class per feature under `app/src/test/`.

## Layers

| Package | What it holds |
|---|---|
| `agent/` | The coding agent. `AgentSession` runs a turn: build context, call the model, run tools, gate risky ones, loop. `LlmClient` speaks OpenAI-compatible, Anthropic and Gemini wire formats. `CommandPolicy` and `SafetyPolicy` decide which shell commands and file writes need approval. `SessionStore` saves chats. Team mode (planner, coder, reviewer), memory banks, the Falcon router and skills also live here. |
| `ui/` | Compose screens: chat, editor, settings, git, build, onboarding. `ui/components` has the shared building blocks, `ui/theme` the colors, type and haptics, `ui/icons/Lucide.kt` the icon set. |
| `data/` | Settings and secrets (`SettingsStore`, `SecureStore`), projects, accounts, templates, repository sync (`Sync.kt`), chat backup (`ChatBackup.kt`), the local crash recorder (`CrashLog.kt`). |
| `git/` | JGit wrapper (`GitService`), the git controller used by the UI, GitHub and forge APIs, pull requests, the merge-conflict resolver, cloud build. |
| `terminal/` | The Debian environment inside proot (`LinuxEnv`) and the terminal emulator. |
| `ondevice/` | On-device Android builds: SDK downloads with SHA-256 checks, build cache, signing, APK verification, logcat. |
| `service/` | Foreground service for background runs, notifications, the Quick Settings tile, the home-screen widget, the morning digest. |
| `billing/`, `payments/` | Plans, entitlements, Play Billing, license codec. Owner-controlled; do not change pricing here without the owner. |
| `editor/` | Tokenizer, syntax highlighting, find and replace, edit history. |

## Rules the code follows

1. **Pure logic first.** Anything that decides something (policies, codecs, parsers, search, backup) is plain Kotlin with no Android types, so it is unit-tested on the JVM. Compose code stays thin.
2. **Secrets stay on the device.** Keys are in `SecureStore`. Sync and backup use an explicit whitelist and encrypt with a passphrase that is never stored.
3. **Risky actions need a yes.** Shell commands and writes go through `CommandPolicy` and `SafetyPolicy`; tool output from the web or MCP servers is data, never an instruction.
4. **Arabic first.** Every string exists in `values/` and `values-ar/`; layouts are RTL-safe; touch targets are at least 48dp with accessibility labels.
5. **One feature, one release.** Each release adds a test class, a bilingual CHANGELOG entry, and a ROADMAP line. `versionCode` only goes up.

## Verification

The build is verified by CI on the public mirror (unit tests plus a debug APK). Local builds are possible on a desktop with the Android SDK: `./gradlew testDebugUnitTest assembleDebug`.
