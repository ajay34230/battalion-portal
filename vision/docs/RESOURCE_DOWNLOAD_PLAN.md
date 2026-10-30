# Resource Download Plan

Principle: smallest combination that works for the detected tier; owner approves every download above 50 MB;
Wi-Fi-only by default; resumable; SHA-256 verified; never executed automatically.

| Resource | Tier | Mandatory? | Approx size (verify) | Source |
|---|---|---|---|---|
| Android TTS (system) | all | Yes | 0 (system) | Device |
| Vosk small English | all | Optional (offline STT) | ~40–50 MB | alphacephei.com/vosk/models |
| Vosk small Hindi | all | Optional | ~40–50 MB | alphacephei.com/vosk/models |
| whisper.cpp tiny/base multilingual | STANDARD+ | Optional | ~75–150 MB | huggingface.co/ggerganov/whisper.cpp |
| LLM (see AI_MODEL_SELECTION) | STANDARD+ | Optional until chosen | ~0.3–2.5 GB | Official model cards |
| openWakeWord feature models + custom "Hey Vision" model | all | Optional | few MB | github.com/dscripka/openWakeWord (custom model must be trained) |
| Multilingual embedding model | STANDARD+ | Optional | ~120 MB+ | HF official |
| Piper voices | PERFORMANCE | Optional | ~60 MB each | github.com/OHF-Voice/piper1-gpl / HF voices (check licence) |

SearXNG, Trafilatura, BeautifulSoup and Playwright are **server/desktop** tools and cannot run inside a stock
Android app. On-device replacements: Jsoup (HTML parsing), a readability-style extractor, Android WebView for
JS pages, and a pluggable search provider (user-configured SearXNG URL, or a keyless public API). Python tools
are available only through the optional Termux bridge.
