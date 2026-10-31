# Implementation Roadmap

| Phase | Scope | Verification available here | Needs device |
|---|---|---|---|
| 0 | Reports (this folder) | review | — |
| 1 | Gradle/Compose app, navigation, Room, DeviceProfiler, CI APK | CI compile + unit tests | install/launch |
| 2 | LlmEngine interface, RuleBasedEngine (Hindi/English/Hinglish intents), ModelManager + manifest + verified downloader | unit tests | real LLM speed, native engine |
| 3 | SpeechRecognizer + Vosk interface, Android TTS, wake-word service shell | compile | mic, TTS voices, wake word |
| 4 | Memory: Room entities, FTS search, RAG hook | unit tests | — |
| 5 | Device control tools (apps, settings, calls, SMS draft, reminders) | compile | actual operations |
| 6 | Termux gateway | unit tests (validator) | Termux execution |
| 7 | Research: search provider, fetch, extract, citations, downloads | unit tests (extractor) | live network |
| 8 | Notification listener, WhatsApp summaries, Gmail (OAuth scaffold) | compile | OAuth client, notifications |
| 9 | Orchestrator, planner, tool registry, confirmation | unit tests | — |
| 10 | Locked-mode policy (OTP filter, privacy rules) | unit tests | lock-screen behaviour |
| 11 | Hybrid mode, resume | unit tests | airplane-mode test |
| 12 | Optimisation hooks, test report, known limitations | CI | battery/thermal |

Each phase: branch commit, docs updated, CI green before moving on.

## Mission 2 (hybrid online/offline engine platform), approved 2026-10-31 to run phase by phase
| Phase | Scope | Verified here by | Needs the phone |
|---|---|---|---|
| 0 | Seven audit documents | review | - |
| 1 | `InferenceEngine` interface with streaming and cancel; LiteRT-LM adapter; llama.cpp (GGUF, CPU) module | CI build + unit tests | load/benchmark |
| 2 | Model manager: Hugging Face discovery, compatibility checks, categories | unit tests (parsers, compatibility) | live download |
| 3 | Task-based router with manual override and change notices | unit tests | - |
| 4 | Online/offline orchestration, offline indicator, Offline Verification screen | unit tests | airplane-mode run |
| 5 | Memory embeddings, Whisper/Piper evaluation | unit tests | speed, audio |
| 6 | Vision and document pipeline | unit tests | camera, OCR quality |
| 7 | Coding agent on the new engines | unit tests | Termux |
| 8 | Model Manager and AI Engine settings screens | CI compile | look and feel |
| 9 | Security and performance pass | tests | battery, thermal |
| 10 | Release engineering | CI | install, real-device tests |
Mission 1's remaining phases (4 orchestration, 3 coding, 5 image, 6 voice, 9-12) are folded into the matching Mission 2 phases to avoid duplicate work.
