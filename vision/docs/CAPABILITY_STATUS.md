# Capability status (honest matrix)

Legend: **Tested in CI** = unit/acceptance tests executed on GitHub Actions. **Built, unverified on phone** = compiles, never run on a real device. **Not built** = does not exist.
Nothing in this project has yet been run on the iQOO 7. CI success is not proof that something works on the phone.

## Coding module (from the "Coding Engine" and "Ultimate Software Engine" prompts)
| Capability | Status |
|---|---|
| Project store: create/open/save/reopen, safe paths, 512 KB file cap, snapshots + rollback | Tested in CI (JVM) |
| ZIP export and import | Tested in CI (JVM) |
| Templates: calculator, Python CSV utility, 3-page responsive website | Tested in CI: calculator logic run with Node, Python utility + its tests run with Python 3, website links checked |
| Static checks (brackets/quotes, JSON, HTML links, Python block headers) | Tested in CI |
| Fix loop: detect → ask model → apply → re-check; never repeats the same fix; rolls back if worse | Tested in CI with a *fake* model. Not tested with a real on-device model |
| Writing code from a description with the local model | Built, unverified on phone. Small models often fail to return valid multi-file JSON |
| Coding Studio UI (files, editor with highlighting/find/outline, preview, run, ask, tools, history) | Built, unverified on phone |
| Live preview (sandboxed WebView, network blocked, console capture) | Built, unverified on phone |
| Run Python/Node/shell | Built, unverified; needs Termux + Advanced mode + per-run confirmation |
| Dev environment inventory (statuses, versions) and requirement detection | Parsing/logic tested in CI; the Termux probe is unverified on phone |
| Guarded installer with full permission summary | Summary text tested in CI; installation itself unverified on phone |
| Developer research (GitHub with licence/maintenance warnings, Stack Overflow, MDN) | Parsers tested in CI; live network calls unverified |
| Compare two approaches (model) | Built, unverified; advice only, tests must still be run |
| Android APK build on the phone | **Unsupported**: official SDK tools are not available for ARM phones. Use GitHub Actions (cloud build) |
| Git integration (commit/diff/branches) | Not built in the Studio. Git can be installed via Dev tools and used in Termux |
| Search across all files | Built, unverified on phone (Files tab) |
| Restricted terminal | Not built; Run tab runs one confirmed command at a time |
| Monaco/CodeMirror editor | Not used: native editor with its own highlighter (WebView editors are heavy on phones) |
| Visual screenshot testing | Not built |
| Model benchmarks on the iQOO 7 | Not run (needs the phone) |

## Assistant
| Capability | Status |
|---|---|
| Smart reminders: bills (monthly), birthdays/anniversaries (yearly), lead-time notices, per-kind channels, Done/Snooze/WhatsApp-wish actions | Date maths and parsing tested in CI; notifications, alarms under Doze/vivo background limits **unverified on phone** |
| Learning from what you say (name, home, likes, family) → Memory screen → used in replies | Rules tested in CI; effect on real model replies unverified |
| Suggestions from repeated requests | Built, unverified on phone |
| UI v2: bottom tabs, animated face avatar, reminders/memory/health screens | Compiles; **visual quality never inspected on a phone or screenshot** |
| Setup & health screen, crash log | Built, unverified on phone |
| Multi-step commands ("A then B"), stops at first confirmation | Splitting tested in CI; end-to-end unverified. Not persisted across app restarts |
| Talk with the phone's built-in speech recogniser + voice (default); offline Vosk model optional | Built, unverified on phone |
| Deep and academic research modes (arXiv, Wikipedia, web) | Parsers/intents tested in CI; live network unverified |
| Export my data (JSON) | Round-trip tested in CI; import screen not built |
| Hey-Vision wake word | **Not built** (needs always-on microphone service; battery and privacy cost; use the tile/assistant gesture) |
| Gmail sign-in | **Not built**: needs your own Google OAuth client |
| Whisper / Piper voices, embeddings/semantic memory | **Not built** |
| Qwen3.5 9B | **Not offered**: ~6 GB file, ~9-10 GB RAM at run time; does not fit an 8 GB phone |
| Calendar integration, home-screen widget, routines | **Not built** |
| Persistent multi-step task engine with pause/recovery | **Not built** (only in-turn multi-step) |

See KNOWN_LIMITATIONS.md and TEST_REPORT.md.

## Added in the widget/wake-word/money batch
| Feature | Status |
|---|---|
| Home-screen widget | Code complete, compiles in CI; not seen on a real launcher |
| Hey Vision wake word | Code + unit tests of phrase logic; microphone spotting NOT verified on a phone; Android 14 requires starting from the app UI |
| Git buttons / restricted Terminal | Script generation and command policy unit-tested; running needs Termux + not tested on a phone |
| Visual test (screenshots, layout checks) | Interpretation logic unit-tested; WebView capture not run on a phone |
| Per-project notes | Works as a file; fed to the model on build |
| Payment capture from notifications | Parser unit-tested on sample texts; real PhonePe/Paytm/bank wording varies and is NOT verified on your phone |
| Daily payment confirmation prompt | WorkManager; time-of-day logic unit-tested; delivery not verified on device |
| Gmail payment sync | Code complete; cannot work until you create the OAuth client (docs/GMAIL_SETUP.md); not tested against real Gmail |
| Whisper / Piper voices | **Not built** (see KNOWN_LIMITATIONS.md); phone's own voice engines are used |
| On-phone APK building | Not supported; cloud builds only |

| Voice enrolment + speaker-checked wake word | Matching maths unit-tested; real recording/recognition NOT tested on a phone; thresholds untuned |
| Startup permission centre | Compiles; not seen on a device |
