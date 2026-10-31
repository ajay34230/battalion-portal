# Model Support Matrix

Source of truth: `core/ResourceManifest.kt` (SHA-256 and size checked against Hugging Face by the "resolve resource hashes" workflow). "Device-tested" means someone ran it on the phone; none are device-tested by this project yet.

| Model | Format | Size | Capabilities | Fit on 8 GB phone | Device-tested |
|---|---|---|---|---|---|
| Granite 4.0 350M | .litertlm | 0.47 GB | English chat, simple commands | Yes | No |
| Qwen3 0.6B int4 | .litertlm | 0.34 GB | Multilingual chat incl. Hindi (weak) | Yes | Reported stuck on GPU; CPU only |
| Qwen3.5 0.8B int8 | .litertlm | 0.96 GB | Multilingual chat | Yes | No |
| Qwen3 1.7B | .litertlm | 1.75 GB | Multilingual chat | Yes | No |
| Qwen3.5 2B int8 | .litertlm | 2.1 GB | Multilingual chat | Yes | No |
| Gemma 4 E2B | .litertlm | 2.6 GB | Chat + image understanding | Yes (6 GB+) | Reported very slow on CPU; GPU path under test |
| Qwen3 4B int4 | .litertlm | 2.3 GB | Stronger chat, reasoning | Marginal | No |
| Qwen3.5 4B int4 | .litertlm | 2.75 GB | Strongest recommended; ~6 tok/s published on a Pixel 8a | Marginal | No |
| Gemma 4 E4B | .litertlm | 3.7 GB | Chat + image understanding | Marginal (8 GB+) | No |
| Qwen3.5 9B / Gemma 4 12B | .litertlm | 6.1 / 6.9 GB | Experimental | No, will likely be killed | No |

## Categories requested by the mission
| Category | Available today | Gap |
|---|---|---|
| General, reasoning, Hindi/English/Hinglish chat | Qwen/Gemma/Granite above | Hindi quality unmeasured |
| Programming | General models only | No code-specialised model in the catalogue |
| Mathematics | General models only | None |
| Vision / image understanding | Gemma 4 E2B/E4B | Untested on device |
| OCR | ML Kit (not a model download) | - |
| Document understanding | OCR + text models | No PDF table extraction |
| Translation | General models | No dedicated model |
| Embeddings / semantic search | None (hook exists in `HybridRank`) | Needs an embedding model and runtime |
| Speech recognition | Vosk (small models) | Whisper not built |
| Text-to-speech | Android TTS | Piper not built |

GGUF models (llama.cpp) are not in the catalogue yet; they arrive with the discovery feature (Mission 2 Phase 2).
