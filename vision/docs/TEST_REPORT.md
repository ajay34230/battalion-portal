# Test Report

## Automated (GitHub Actions, run 41104213473, commit 37a06ec)
Result: `gradle testDebugUnitTest assembleDebug` **passed** (marker GRADLE_BUILD_AND_TESTS_OK).
Unit tests: TierPolicy (7), IntentParser/LanguageDetector (12), Research/robots/extractor/injection (6),
Policy: lock policy, OTP filter, Termux validator (4), Gmail parser (1).

## Mandatory device tests (30) — all UNVERIFIED
No physical phone was available. Every item below is **not tested on hardware**:
English/Hindi/Hinglish chat on device, voice input, spoken output, wake word (not built), speech interruption,
offline conversation (no LLM), online research on device, website extraction on live sites, PDF reading (not built),
model downloading/verification, memory persistence across restarts, app launching, reminder delivery, notification access,
Gmail authorisation (not built), WhatsApp notification handling, Termux execution, permission denial paths, internet loss,
app restart, device reboot, locked-device behaviour, low storage, battery, thermal, invalid commands, unauthorised commands.
Logic-level coverage for the parsing, policy and extraction parts is in the unit tests above.
