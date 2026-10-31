# Architecture Audit (Phase 0, 2026-10-31)

Audit of the code as it is on `vision/phase-0` at commit 7aa9db6. Every finding below was read from source; nothing here was measured on a device.

## Layers (package root `com.vision.assistant`)
| Layer | Packages | Notes |
|---|---|---|
| UI | `ui/*`, `VisionWidget`, `MainActivity`, `AssistActivity` | Jetpack Compose, one nav host in `VisionNav` |
| Orchestration | `ai/Orchestrator`, `Intent`, `PlanParser`, `MultiStep`, `AssistantSession` | Rule-parser fast path, else JSON planner via LLM |
| Engines | `ai/EngineProvider`, `LiteRtLmEngine`, `:litert` module | Model router with crash markers and GPU ladder |
| Tools | `tools/*`, `termux/TermuxGateway`, `comms/*`, `finance/*`, `tasks/*`, `health/*` | Each tool is confirmed/audited through the registry |
| Data | `data/*` (Room v4), SharedPreferences (`Prefs`, `vision_voiceprint`), files under app storage | |
| Services | `service/WakeWordService`, `VoiceService`, notification listener, WorkManager workers | |
| Models/downloads | `core/ModelManager`, `ResourceManifest`, `Downloads` | SHA-256 verified, resumable |
| Coding | `code/*` | ProjectStore, TermuxRunner, CodeAgent, VisualCheck |

## Exported components (attack surface)
- `MainActivity` (launcher) - expected.
- `AssistActivity` - ASSIST / VOICE_COMMAND filters, expected for an assistant.
- `VisionNotificationListener` - protected by BIND_NOTIFICATION_LISTENER_SERVICE.
- `AskVisionTile` - protected by BIND_QUICK_SETTINGS_TILE.
- All receivers, `WakeWordService`, `VoiceService`, FileProvider are `exported=false`.
- `allowBackup=false`.

## Storage
- Room `vision.db` v4: chat, memory, tasks, audit, notification log, payments. `exportSchema=false`, `fallbackToDestructiveMigration()` present.
- Voiceprint, preferences, OAuth state: plain SharedPreferences. No Keystore usage anywhere.
- Projects: app files dir, limited by `ProjectStore` (512 KB/file, 200 files).

## Concurrency / lifecycle
- Chat session is app-scoped (`AssistantSession`), one message at a time, 2 min generation timeout.
- Wake word runs as a microphone foreground service; cannot start from background on Android 14.

## Build
- No R8 minify, no resource shrinking, no ABI filter found in `app/build.gradle.kts`. Release APK ~187 MB, mostly native libs (LiteRT-LM, Vosk).
