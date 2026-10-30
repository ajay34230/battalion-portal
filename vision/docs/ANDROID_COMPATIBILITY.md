# Android Compatibility — all devices

| Item | Decision |
|---|---|
| minSdk | 26 (Android 8.0) — foreground-service + notification channel model; covers the vast majority of active devices |
| targetSdk / compileSdk | 35 |
| ABIs | `arm64-v8a` (full feature set), `armeabi-v7a` (reduced: no big LLM, STT/TTS only), `x86_64` (emulator/CI) |
| UI | Jetpack Compose, adaptive to phone/tablet widths |

## Runtime device profiling (`DeviceProfiler`)
Measured at first launch and on demand: total/available RAM (`ActivityManager.MemoryInfo`), free storage
(`StatFs`), `Build.SUPPORTED_ABIS`, SDK level, CPU core count, battery/thermal state (`PowerManager`).

## Tiers
| Tier | Condition | Default LLM class | STT | TTS |
|---|---|---|---|---|
| LITE | RAM < 3 GB or 32-bit only | none or ≤0.3B quantized **(verify)** — rules + intent parser primarily | Vosk small | Android TTS |
| STANDARD | 3–6 GB, arm64 | ~1B int4/Q4 class **(verify)** | Vosk small or whisper tiny/base | Android TTS |
| PERFORMANCE | ≥ 6 GB, arm64 | 2–4B-class int4 **(verify)** | whisper base/small | Android TTS / Piper |

Rules: only one LLM loaded at a time; unload on low memory (`onTrimMemory`); never auto-select a model
whose file size exceeds 40 % of total RAM or free storage minus a 1 GB reserve; Battery Saver profile
forces the next tier down.

## Version-specific behaviour handled in code
- Android 8–9: no foreground-service type; plain foreground service.
- Android 10+: background activity launch limits → use notifications / full-screen intent sparingly.
- Android 11+: package visibility (`<queries>`) needed to list/launch apps.
- Android 12+: exact alarms permission, notification trampoline limits.
- Android 13+: runtime `POST_NOTIFICATIONS`.
- Android 14+: foreground service **type** (`microphone`, `dataSync`) required and declared.
