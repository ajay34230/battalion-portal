# Known Limitations (honest status)

Nothing in this build has been run on a physical phone. "Implemented" means code exists and compiles in CI;
hardware behaviour is **unverified** until tested on a device.

| Area | Status |
|---|---|
| Local language model | **Not implemented.** No native inference engine (llama.cpp / LiteRT-LM) is bundled and no model is downloaded. Free chat answers with an honest "no language model installed" message. Commands (open app, reminder, call, search, memory, notifications) work through the rule-based parser. |
| Hindi/Hinglish understanding | Rule-based parser + language detector only; limited vocabulary. Needs real-speech testing. |
| Offline speech-to-text | Uses Android SpeechRecognizer with an offline preference. Works offline only if the device has the offline language pack. Vosk/whisper.cpp are listed in the manifest but **not integrated**. |
| Text-to-speech | Android TTS. If a Hindi voice isn't installed the app says so instead of staying silent. Piper not integrated. |
| Wake word "Hey Vision" | **Not implemented** (needs a trained custom model + always-on mic service). Alternative provided: foreground "Speak" notification and Quick Settings tile. |
| Locked-device voice | The Speak notification/tile works with a visible service; VISION refuses sensitive actions when locked and never bypasses the lock screen. Behaviour on real lock screens is unverified. |
| Embeddings / RAG | Keyword search over Room only; vector search not implemented. |
| Research | Wikipedia API (keyless) or user-configured SearXNG; extractive summaries, robots.txt respected. No JS rendering, no paywall/CAPTCHA bypass, no PDF reading yet. SearXNG, Trafilatura, BeautifulSoup, Playwright cannot run inside a stock Android app (server/Python tools). |
| Downloads | Resumable with SHA-256 check; manifest hashes are blank, so downloads stay **UNVERIFIED and unusable** until official hashes are recorded. |
| Gmail | REST client + parser exist; **OAuth sign-in is not built** (needs a Google Cloud OAuth client owned by the developer). |
| WhatsApp | Notification previews (after the user enables Notification access) and a prefilled draft that the user sends. No access to chat history; none planned. |
| Termux | Allow-listed gateway with validation + 30 s timeout wrapper; requires Termux with `allow-external-apps=true` and the RUN_COMMAND permission. No UI screen yet beyond the policy code. |
| Reminders | WorkManager (survives reboot, may drift under Doze). Wake-up alarms go to the system clock app. |
| UI | Screens exist for Home, Chat, Tasks, Memory, Models, Device, Permissions only; the other screens in the original spec are not built. |
| Artifacts | GitHub's artifact storage quota for this account was exhausted, so APK upload from CI may fail until the quota resets; compile/test results are still reported. |
