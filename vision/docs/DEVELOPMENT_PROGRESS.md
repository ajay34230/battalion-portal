# Development Progress
| Phase | Code | CI compile/tests | Device-verified |
|---|---|---|---|
| 0 Feasibility | done | n/a | n/a |
| 1 Foundation | done | see TEST_REPORT | no |
| 2 AI brain | rule-based only; LLM engine missing | see TEST_REPORT | no |
| 3 Voice | push-to-talk + TTS; wake word missing | see TEST_REPORT | no |
| 4 Memory | keyword memory; embeddings missing | see TEST_REPORT | no |
| 5 Android control | open app, call (dialer), alarm, reminders, battery, time | see TEST_REPORT | no |
| 6 Termux | gateway policy + sender | see TEST_REPORT | no |
| 7 Research | Wikipedia/SearXNG + extraction | see TEST_REPORT | no |
| 8 Email/WhatsApp | notifications, draft; Gmail OAuth missing | see TEST_REPORT | no |
| 9 Agent | intent → tool → confirm → audit | see TEST_REPORT | no |
| 10 Locked mode | policy + visible service | see TEST_REPORT | no |
| 11 Hybrid | network check + offline research queue | see TEST_REPORT | no |
| 12 Optimisation | not started: needs real device profiling | — | no |

## Engineering mission (started 2026-10-31)
| Mission phase | Status |
|---|---|
| 0 Audit docs | done (ARCHITECTURE_AUDIT, BUGS_AND_RISKS, FEATURE_IMPLEMENTATION_MAP, UPGRADE_ROADMAP, REGRESSION_TEST_PLAN) |
| 1 Data/file/ZIP safety + encryption | done: CI green on 03a25e6 (new unit tests SafeZip, AesGcm, rotation, Termux safety passed). Device checks BLOCKED |
| 7 Security hardening | done, CI green (exports guarded by test, FileProvider narrowed, no cleartext, WebView locked down) |
| 8 Model states | done, CI green (Not downloaded / Downloaded / Installed / Loaded / Ready / Failed in Settings) |
| 2 Memory | done, CI green: BM25 + fuzzy hybrid, importance/confidence, duplicate + contradiction handling, Edit; DB migration 4->5 BLOCKED until tested on the phone. Neural embeddings NOT built (hook only) |
| Diagnostics (user request) | done, CI green: reply/plan/load timings, crashes, stalls; Settings > Diagnostics > Share log |
| 4, 3, 5, 6, 9-12 | not started |

### Phase 1 changes
- Room: destructive fallback removed; explicit migrations only; schema export on; backup before upgrade; `quick_check` after open; unusable DB moved to `files/db_backups` (never deleted) with a notice. API for manual backup / restore-on-next-start exists (`DbSafety`); Settings UI for it is not built yet.
- Termux: slug validated; project written to a staging folder, old project moved (not deleted) to `~/projects/.vision-backups`, newest 5 kept; git runs take a tar backup first. No `rm -rf`.
- ZIP: `SafeZip` (path escape, symlink, entry count, per-entry, total, ratio) used by model unpack and project import.
- Keystore AES-256-GCM (`SecureStore`) now seals voiceprint samples; old plain copy is migrated on first read. Gmail access tokens are only held in memory, so there is nothing to encrypt.
- BLOCKED: real-device Keystore behaviour, upgrade from the installed v4 database, Termux run on the phone.

## Mission 2 (engine platform)
| Phase | Status |
|---|---|
| 0 Audit docs | done (VISION_ARCHITECTURE_AUDIT, ANDROID_AI_ENGINE_COMPATIBILITY, MODEL_SUPPORT_MATRIX, OFFLINE_CAPABILITY_MATRIX, SECURITY_AND_PRIVACY_REVIEW, TESTING_AND_BENCHMARK_REPORT, roadmap section) |
| 1a Streaming, cancel, Qwen thinking switch | done, CI green (release below); device behaviour NOT TESTED |
| 1b llama.cpp (GGUF) engine | build verified in CI (19 MB AAR); integration deferred pending device evidence, see ANDROID_AI_ENGINE_COMPATIBILITY.md |
| 2 Model discovery + compatibility | in progress |
