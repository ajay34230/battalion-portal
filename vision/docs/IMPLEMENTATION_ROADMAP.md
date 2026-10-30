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
