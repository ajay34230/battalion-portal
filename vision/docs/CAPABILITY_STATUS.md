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
| Search across all files | Not built (find-in-file only) |
| Restricted terminal | Not built; Run tab runs one confirmed command at a time |
| Monaco/CodeMirror editor | Not used: native editor with its own highlighter (WebView editors are heavy on phones) |
| Visual screenshot testing | Not built |
| Model benchmarks on the iQOO 7 | Not run (needs the phone) |

## Assistant
See KNOWN_LIMITATIONS.md and TEST_REPORT.md.
