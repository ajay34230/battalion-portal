# VISION — Feasibility Report

**Scope change (owner request):** VISION must work on *every* Android device, not only the Galaxy S10.
The S10 is just one test device. All model/engine choices are therefore **tiered at runtime** by the
device's measured RAM, free storage, CPU ABI and Android version (see `ANDROID_COMPATIBILITY.md`).

Status of facts: items marked **(verify)** were not confirmed against an official source from this
sandbox and must be re-checked by the Model Manager at download time (licence, size, checksum).
Nothing here claims a feature works on hardware; hardware features are **unverified** until tested on a phone.

## Feature classification

| Feature | Class | Notes |
|---|---|---|
| Local chat (small/standard LLM) | Achievable with limitations | Speed/quality depends on RAM tier; 64-bit ARM strongly preferred |
| Hindi / English / Hinglish | Achievable with limitations | Small models are weak at Hindi; larger tiers and prompt design help; must be tested with real speech |
| Offline speech-to-text | Achievable with limitations | Vosk small Hindi/English models (light) or whisper.cpp tiny/base (better, heavier) |
| Offline text-to-speech | Achievable with limitations | Android TextToSpeech (uses device's installed voices, incl. Hindi if present) as default; Piper optional (**verify** Hindi voice licence/quality) |
| Wake word "Hey Vision" | Requires additional software / limitations | Needs a custom-trained model (openWakeWord can train one); Android restricts background mic (foreground service, microphone type, user-visible notification) |
| Locked-device voice | Restricted by Android | Works only as a visible foreground service; cannot bypass lock screen; sensitive actions need unlock |
| Persistent memory + RAG | Fully achievable | Room/SQLite; embeddings optional and tiered |
| Internet research | Achievable with limitations | On-device: HTTP + HTML extraction (Jsoup) + a search API; SearXNG needs an external server (optional); JS-heavy sites need WebView |
| Download manager | Fully achievable | Checksum + resume + storage checks |
| App launching / settings intents | Fully achievable | Intents; protected settings only open the settings screen |
| Calls / SMS | Achievable with limitations | Prepares call/SMS; user confirms; sending SMS needs permission/Play restrictions |
| Notification reading | Achievable with limitations | NotificationListenerService; only what the notification exposes |
| WhatsApp | Achievable with limitations | Notification previews + open chat with prefilled text; no database access, no silent sending |
| Gmail | Requires additional setup | Google Cloud OAuth client owned by the developer; user consents |
| Termux bridge | Requires additional software | Optional; RUN_COMMAND intent needs user to enable allow-external-apps in Termux |
| Reminders / alarms / tasks | Fully achievable | WorkManager + AlarmManager (exact-alarm permission on 12+) |
| Hybrid online/offline | Fully achievable | ConnectivityManager |
| Accessibility-based UI control | Achievable with limitations | Requires user-enabled AccessibilityService; Play policy limits |
| Not currently feasible | — | Reading other apps' private data, bypassing lock screen, rooting, silent background recording, unlimited always-on listening |

## Constraints of this build environment
- No Android device attached: all hardware tests are **unverified**.
- Sandbox network is allow-listed (package registries/GitHub): large model files cannot be fetched or
  verified here. The app's Model Manager downloads them on the phone, with owner approval, and verifies SHA-256.
- Compilation is verified through GitHub Actions only.
