# Bugs and Risks (Phase 0)

Severity: H = data loss/security, M = wrong behaviour, L = polish. Status: OPEN until a later phase closes it.

| ID | Sev | Finding | Evidence | Phase |
|---|---|---|---|---|
| R1 | H | `fallbackToDestructiveMigration()` wipes chat, memory, payments on any unhandled schema change | `data/VisionDatabase.kt:24` | 1 |
| R2 | H | `TermuxRunner.script` runs `rm -rf "$HOME/projects/<slug>"` before every run, no backup, no confirmation of what is deleted | `code/TermuxRunner.kt:22` | 1 |
| R3 | M | Slug is not validated inside `TermuxRunner` itself (relies on caller) | same file | 1 |
| R4 | M | `ModelManager.unzip` has a zip-slip guard but no entry-count, total-size or ratio limit | `core/ModelManager.kt` | 1 |
| R5 | H | Voiceprint, OAuth/Gmail state stored unencrypted in SharedPreferences | no Keystore use | 1 |
| R6 | M | Room schema not exported, so migrations cannot be tested against real schemas | `exportSchema=false` | 1 |
| R7 | M | Memory "semantic" search is TF-IDF, not embeddings; no dedupe or contradiction handling | `memory/SemanticIndex.kt` | 2 |
| R8 | M | Code agent reports success from static checks only unless Termux ran the command | `code/CodeAgent.kt` | 3 |
| R9 | M | Speaker match is a convenience filter but the UI wording can read as authentication | `voice/Voiceprint.kt`, `VoiceSetupScreen` | 6 |
| R10 | H | Repo is public and contains `personal-sideload.jks` with its password in `build.gradle.kts` | repo root | 12 (user decision) |
| R11 | L | `isTrivial` helper unused in Orchestrator | `ai/Orchestrator.kt` | 4 |
| R12 | M | APK ~187 MB, no shrinking | build config | 10 |

Not bugs but limits: wake word, GPU speed, widget look, Termux flow, payment capture on real notifications and Gmail sign-in are all unverified on a device.
