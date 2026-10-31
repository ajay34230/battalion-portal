# VISION Architecture Audit (Mission 2, Phase 0, 2026-10-31)

Read from source at commit 44b99ec (CI green, release vision-dev-74). Earlier audit: `ARCHITECTURE_AUDIT.md`, `BUGS_AND_RISKS.md`. The earlier APK-audit findings were re-checked against source; see "Verified findings".

## Build facts
- minSdk 26, targetSdk 35, compileSdk 35. Kotlin 2.4.21, AGP 8.7.2, Compose BOM 2024.10.01.
- Modules: `:app`, `:litert` (isolates `com.google.ai.edge.litertlm:litertlm-android:0.19.0`).
- No C/C++ code and no NDK/CMake build in the project today. No ABI filter is set; native libraries come from LiteRT-LM, Vosk (JNA) and ML Kit.
- Libraries: Room 2.8.4, WorkManager 2.9.1, DataStore (declared; SharedPreferences is what the code uses), Vosk 0.3.47, ML Kit text-recognition (Latin + Devanagari) and image-labeling, jsoup, play-services-auth.
- 17 unit-test files run in CI. No instrumented (on-device) tests exist.

## Inference path (the part this mission changes)
`AssistantSession` -> `Orchestrator.handle` -> `EngineProvider.current()` -> one `LlmEngine` (`LiteRtLmEngine` or `RuleBasedEngine`) -> `LiteRtBridge` -> LiteRT-LM `Engine`.
- `LlmEngine` methods are blocking, whole-answer calls: `reply`, `plan`, `complete`, `summarize`, `describeImage`. There is no token streaming and no cancel call; a stuck native call is abandoned after 2 minutes (`LiteRtLmEngine.TIMEOUT_MS`) and the engine is rebuilt.
- A message that is not a rule-parser fast command costs two generations: `plan` (about 6 KB planner prompt) then `reply`.
- `EngineProvider` chooses the strongest installed model that fits (`TierPolicy`), honours a user preference, tracks `bad` and `gpu_bad` models, and unloads on memory pressure.
- GPU is tried only for ids starting with "gemma" (ladder GPU+MTP -> GPU -> CPU).

## Model management (exists)
`ResourceManifest` (hand-written catalogue: 11 LLM entries from Qwen3 0.6B to Gemma 4 12B, Vosk models, Vosk speaker model) with SHA-256 and size per file; `ModelManager` does resumable downloads with retries, verification, safe unzip (`SafeZip`), delete; `DownloadsUi` shows states; `ModelLifecycle` labels Not downloaded / Downloaded / Installed / Loaded / Ready / Failed.
Gaps: no online discovery, no format other than `.litertlm`, no per-task capability tags, no router beyond "strongest that fits" plus user preference.

## Other subsystems
Voice (Android SpeechRecognizer + Vosk, TTS, wake-word service with speaker check), memory (Room + BM25/fuzzy hybrid, importance/confidence, duplicates, contradictions), vision (`SeeTool`: ML Kit OCR and labels; multimodal Gemma via LiteRT-LM when installed), coding studio (ProjectStore, TermuxRunner with staged writes and backups), payments, reminders, widget, Termux gateway, diagnostics log.

## Verified findings against the earlier APK audit
| Earlier claim | Source check |
|---|---|
| Destructive DB fallback | Fixed in Mission 1 Phase 1 (explicit migrations, backups). |
| `rm -rf` in Termux script | Fixed (staging + backups, test forbids it). |
| No encryption | Voiceprint now sealed with Keystore; other prefs are not sensitive tokens. |
| Memory is TF-IDF only | Now BM25 + fuzzy; neural embeddings still absent. |
| APK about 187 MB | Now 181 MB (release vision-dev-74); no shrinking enabled yet. |

## Retain / change
Retain: LiteRT-LM engine and bridge, model manager, TierPolicy, Room data, memory, Termux/coding studio, Vosk, ML Kit, security work, diagnostics.
Change: introduce an engine interface with streaming and cancellation; add a second engine (llama.cpp); add discovery, compatibility checks, task router, offline verification; add embeddings; fix the two-calls-per-message latency.
