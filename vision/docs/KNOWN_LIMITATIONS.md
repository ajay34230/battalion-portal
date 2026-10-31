# Known Limitations (honest status)

**Verified:** the app compiles and all JVM unit tests pass in CI (GitHub Actions, JDK 21, Kotlin 2.4.21). **Nothing has been run on a real phone yet.**
Everything below that touches hardware, the lock screen, audio or a downloaded model is *unverified on device*.

## Not built
- Hands-free "Hey Vision" wake word (needs a trained wake-word model + always-on service). Use the Quick Settings tile, the notification Speak button, or set VISION as the default digital assistant.
- Gmail sign-in (OAuth client not configured; API client exists but cannot authenticate).
- Embeddings / semantic memory search, Whisper, Piper TTS, calendar/widget/routines, persistent task engine, data import.

## Unverified on device
- On-device LLM (Qwen3 via LiteRT-LM): speed, memory use and whether a given phone can load it. Reply text is read via `Message.toString()`.
- Vosk offline speech (Hindi/English) after download; Android TTS needs a Hindi voice installed for Hindi speech.
- Over-lock-screen assistant (`showWhenLocked`/`turnScreenOn`), behaviour differs per manufacturer.
- Notification reading, Termux bridge, calls, WhatsApp drafts, reminders.

## By design
- Android never lets an app bypass the PIN/pattern/fingerprint. When an action needs an unlocked phone, VISION asks you to unlock.
- Lock-screen limits are OFF by default (configurable in Settings). One exception: OTP/bank notifications are not read aloud unless you enable it.
- Web/email/notification text is untrusted and cannot trigger actions by itself.
- Downloads (models) require approval, are SHA-256 verified, and are tier-limited by RAM/storage.

## New limitations
- **Whisper / Piper not included.** The needed native runtime (sherpa-onnx / whisper.cpp) is not published as a normal Android dependency, needs hundreds of MB of models and cannot be verified without your phone; adding it blind risks breaking every build. Speech uses the phone's own recogniser/TTS (and Vosk for the wake word).
- **Payments are best-effort.** Only notifications received while Notification access is on are captured; apps phrase them differently; other apps can forge notification text; UPI notifications do not contain GPS location, so "where" is what you confirm (category + place), not an automatic location.
- **Gmail** needs your own Google OAuth client; in Testing mode Google makes you re-approve about weekly. Only subject + snippet are read.
- **Wake word** drains battery, cannot start from the background on Android 14, and false triggers are possible.
- Qwen3.5 9B is experimental on an 8 GB phone.
- GitHub Actions artifact storage quota was hit once; a build can pass but fail to upload artifacts until the quota resets.

- **Voice matching is a convenience, not security.** Recordings can pass; colds, noise and distance can make it miss you. Thresholds are untuned (no phone testing yet), so Loose/Normal/Strict may need adjusting. It never unlocks the phone.
- Android requires you to tap Allow for every permission and to switch on Notification access yourself; VISION can only open the right screen.

- **Model speed.** Models run on the CPU unless the GPU path works on your phone (Settings → GPU, on by default, automatic CPU fallback). Gemma 4 E2B on a Snapdragon 870 CPU is slow; GPU speed-up is unverified on your iQOO 7. Smaller models (Qwen3 1.7B, Qwen3.5 2B) are much faster than Gemma 4 on CPU.

- **Reply speed.** Even on GPU a 2B+ model on a Snapdragon 870 is slower than Google's published numbers (those are for a Galaxy S26 Ultra). Every non-command message costs a prompt read + the answer. Clear commands (open app, time, reminders, spending...) skip the model. For fast chat use Qwen3 1.7B, Qwen3.5 2B or smaller.
