# AI Model Selection

All sizes/licences below are **starting candidates (verify)**; the Model Manager must read the licence and
file size from the official source (Hugging Face model card / vendor page) and compute SHA-256 before use.
No model is bundled in git. No model is claimed installed until the file exists and its hash matches the manifest.

## Inference engines (evaluated)
| Engine | Use | Notes |
|---|---|---|
| llama.cpp (GGUF) | Primary candidate for broad device coverage | CPU, many quantizations, works on arm64 and (slowly) arm32; JNI/NDK build needed |
| LiteRT-LM (Google) | Candidate for Gemma-family `.litertlm` bundles | arm64 devices; check current AAR/availability |
| ONNX Runtime Mobile | Used for embeddings / small speech models | Good for BERT-style embedding models |

Decision for implementation: define a Kotlin `LlmEngine` interface; first implementation targets
llama.cpp via a thin JNI layer (widest device coverage); LiteRT-LM added as a second `LlmEngine` implementation.
Until a native engine is built and tested on a device, the app runs in **rule-based mode** and says so.

## Model families
| Family | Why | Hindi | Licence note |
|---|---|---|---|
| Gemma (small: 270M/1B/3n class) | Mobile-focused, multilingual | Moderate | Gemma Terms of Use (accept in app) |
| Qwen2.5 / Qwen3 small (0.5B–4B) | Strong instruction following, tool use | Moderate | Apache-2.0 for most sizes **(verify per model)** |
| Llama 3.2 1B/3B | Common GGUF availability | Limited official Hindi | Llama community licence |

Profiles map to tiers in `ANDROID_COMPATIBILITY.md`: SMALL / STANDARD / ADVANCED. The manifest
(`vision/app/src/main/assets/resource_manifest.json`) lists candidates with `url`, `sha256` (filled when verified),
`sizeBytes`, `minRamMb`, `license`, `engine`. Empty `sha256` ⇒ cannot be installed (safety rule).

## Embeddings (RAG)
Candidates: multilingual-e5-small, bge-m3 (too large for low tiers). LITE tier uses keyword (FTS) search only.
Hindi retrieval quality **unverified**.
