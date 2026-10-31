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

## Since last entry
- Termux control (allow-listed commands, confirm tiers, Advanced mode), creator identity, stronger web search with summaries, background downloads with pause/resume/cancel.
- Qwen3.5 0.8B/2B/4B and Gemma 4 E2B/E4B (image understanding), ML Kit OCR (English+Hindi), PDF reading.
- Smart reminders (bills, birthdays, anniversaries); learning from the user; UI v2 with bottom navigation and animated avatar.
- Coding Studio: project store with snapshots/rollback/ZIP, templates, static checks, safe fix loop, preview, Termux runner, developer research, tool inventory and guarded installer.
- Setup & health screen, crash log, multi-step commands, deep/academic research, JSON export, phone-voice default.

## Widget, wake word, Coding Studio extras, Money (this batch)
- Home-screen widget (next reminders). Not verified on a launcher.
- "Hey Vision" wake word: foreground microphone service with offline Vosk keyword spotting, off by default (Settings). Battery/privacy cost; unverified on device.
- Coding Studio: Git buttons (Termux), restricted Terminal tab (allow-list, no Advanced mode), Notes tab (NOTES.md), Visual test panel (screenshots at 360/412/768 px + layout checks).
- Money: payment capture from notifications (PhonePe, Paytm, GPay, bank alerts), category + place confirmation, daily review prompt at a time you choose, spend summaries by voice/chat, Gmail read-only sync (needs your OAuth client, docs/GMAIL_SETUP.md).
- Fixed: calendar phrases no longer swallow "remind me ... my schedule".

## Voice enrolment and startup permissions
- Voice setup (Home banner / More → Voice setup): say Hey Vision / Vision / Hi Vision; a voiceprint (numbers only) is kept on the phone and the wake word can answer only to a matching voice (Vosk speaker model, 14 MB, SHA-256 verified). Strictness Loose/Normal/Strict; delete anytime.
- Startup permission centre: one button for microphone, notifications, contacts, calendar; guided links for Notification access, battery and Termux control.
