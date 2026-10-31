# Changelog
## 0.1.0 (in progress, branch vision/phase-0)
- Phase 0: feasibility, architecture, model/permission/security reports (device-agnostic tiering).
- Phase 1: Compose app, Room, device profiler, CI.
- Phase 2: language detection, rule-based intents, verified model manager.
- Phases 3-5, 9: voice I/O, tool registry with confirmations, reminders, memory, chat orchestrator.
- Phases 6-8, 10, 11: research engine, Termux gateway, notification listener, Gmail client, lock policy, offline research queue.

## Latest
- On-device LLM via LiteRT-LM (isolated in `:litert` module), Qwen3 model entries with verified hashes, Vosk offline STT.
- Jarvis-style animated orb AI screen, aurora/glass UI, slow screen transitions, staggered home tiles.
- `AssistActivity`: glowing assistant sheet over other apps and (optionally) the lock screen; Settings toggles for lock-screen behaviour (no limits by default).
- Build fixes: Kotlin 2.4.21, kapt + Room 2.8.4 with kotlin-metadata-jvm 2.4.21, JDK 21 in CI.
