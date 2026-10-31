# Offline Capability Matrix

Honest status. "PASSED (logic)" means a CI unit test passed. Nothing has been verified on the phone in airplane mode, so every device row is NOT TESTED. Statuses: PASSED, FAILED, BLOCKED, NOT SUPPORTED, NOT TESTED.

| # | Capability | Needs | Code exists | Offline test status |
|---|---|---|---|---|
| 1 | App opens | - | yes | NOT TESTED (device) |
| 2 | Installed models load | downloaded model | yes | NOT TESTED (device) |
| 3 | Conversations | model | yes | NOT TESTED (device) |
| 4 | Streaming responses | engine streaming | no (whole-answer only) | NOT SUPPORTED until Phase 1 |
| 5 | Local memory | - | yes, BM25/fuzzy | PASSED (logic); device NOT TESTED |
| 6 | Voice recognition | Vosk model | yes | NOT TESTED (device, needs mic) |
| 7 | Text-to-speech | an installed offline TTS voice | uses Android TTS | NOT TESTED (device) |
| 8 | OCR | bundled ML Kit | yes | NOT TESTED (device) |
| 9 | Image understanding | Gemma 4 E2B/E4B | yes | NOT TESTED (device) |
| 10 | Document processing | model + text | partial (no PDF tables) | NOT TESTED |
| 11 | Task management | - | yes | PASSED (logic); device NOT TESTED |
| 12 | Coding agent | model + Termux | partial | BLOCKED (needs Termux on phone) |
| 13 | Web search, model downloads, Gmail | internet | yes | n/a (online only, must say so) |
| 14 | Neural-embedding memory | embedding model | no | NOT SUPPORTED |
| 15 | Whisper / Piper | sherpa-onnx or similar | no | NOT SUPPORTED |

Planned: an Offline Verification screen that runs checks 1-3, 5, 8, 11 itself on the phone and records PASSED/FAILED with timestamps, plus a visible online/offline indicator (Mission 2 Phase 4).
