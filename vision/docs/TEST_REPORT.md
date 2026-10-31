# Test Report

Source of truth: GitHub Actions workflow "VISION Android build" (`gradle testDebugUnitTest assembleDebug`).
Latest result: **success** (annotation `GRADLE_BUILD_AND_TESTS_OK`), debug APK published as a private prerelease.

Covered by JVM unit tests: tier policy, intent parsing (English/Hindi/Hinglish), LLM plan parsing, research extraction/robots rules, Gmail client parsing, lock policy and OTP filter.
Not covered: any on-device behaviour (UI, audio, models, lock screen, Termux) — requires manual testing on a phone.
