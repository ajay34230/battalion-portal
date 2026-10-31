# Test Report

Source of truth: GitHub Actions workflow "VISION Android build" (`gradle testDebugUnitTest assembleDebug`).
Latest result: **success** (annotation `GRADLE_BUILD_AND_TESTS_OK`), debug APK published as a private prerelease.

Also executed in CI (real Node/Python on exported templates): calculator logic tests, Python CSV utility + its unit tests, website link check.
Covered by JVM unit tests: tier policy, intent parsing (English/Hindi/Hinglish), LLM plan parsing, research extraction/robots rules, Gmail client parsing, lock policy and OTP filter.
Not covered: any on-device behaviour (UI, audio, models, lock screen, Termux) — requires manual testing on a phone.

Added since: reminders/event planner, event and learning parsers, coding module (store, ZIP, snapshots, static checks, fix loop with fake model, Termux script builder), developer research parsers, multi-step splitting, backup round-trip, research modes. Check the latest workflow run for the current pass/fail state — this file does not claim a result.

## Batch: widget / wake word / Coding extras / Money
CI run 41176404049 (commit fe1d319): everything compiled; 130 of 131 unit tests passed; 1 failed (`reminderWeeklySunday`) because the calendar phrase matcher caught "my schedule" inside a reminder. Fixed in the next commit (see CI result below once recorded). New tests: PaymentParserTest, PaymentReviewTest, GmailParseTest, VisualAndGitTest, WakeWordTest. Nothing here has been run on a phone.

CI run 41177132780 (commit 207c27a): build and all unit tests passed (GRADLE_BUILD_AND_TESTS_OK); the earlier failing reminder test is fixed. Release `vision-dev-48` published. Still nothing run on a phone.
