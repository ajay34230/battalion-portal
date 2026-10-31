# Android AI Engine Compatibility

Status key: VERIFIED = checked in this repo's CI or source; DOCUMENTED = from vendor documentation; UNVERIFIED = needs the phone.

| Engine | Android fit | Model format | Acceleration | Status | Decision |
|---|---|---|---|---|---|
| LiteRT-LM 0.19.0 (in use) | Native Android API, arm64 | `.litertlm` | CPU; GPU works only with Gemma on this phone (other models stalled) | VERIFIED integrated and building; speed UNVERIFIED | Keep as the primary engine |
| llama.cpp | Needs an NDK/CMake build and a JNI layer; arm64 | GGUF (largest ecosystem of open models, many quantisations) | CPU (NEON) reliable; Vulkan on Adreno 650 expected to be unstable or no faster | Not built yet; DOCUMENTED as feasible | Add as second engine, CPU first, Vulkan only if a benchmark proves it |
| ExecuTorch | Official AAR `org.pytorch:executorch-android` (arm64-v8a, x86_64) | `.pte` exported models | XNNPACK CPU; Qualcomm NPU needs the QNN SDK and supported chips; Vulkan only via source build | DOCUMENTED ([docs](https://docs.pytorch.org/executorch/stable/using-executorch-android.html)) | Evaluate only; integrate only if a useful `.pte` model beats LiteRT-LM/llama.cpp in a measured test |
| Colibri | Unknown | Unknown | Unknown | Could not verify what it is or whether it supports Android | Not adopted; revisit if the owner supplies a link |
| Vosk (speech) | Android AAR | Vosk models | CPU | VERIFIED integrated | Keep |
| ML Kit (OCR, labels) | Android | bundled | CPU/NNAPI | VERIFIED integrated | Keep |

## Constraints on the iQOO 7 (Snapdragon 870, 8 GB, Android 14)
- Only real RAM counts for loading; virtual RAM is flash storage.
- Android may kill a large model in the background; models must be unloaded under pressure (exists).
- Sustained inference throttles the SoC; no thermal monitor exists yet.
- Vulkan/OpenCL driver quality on Adreno 650 varies; every GPU path needs a crash-guarded probe (the pattern already used for LiteRT-LM GPU).

## Rules for adding an engine
1. It implements the common `InferenceEngine` interface (streaming, cancel, close).
2. Its native libraries are built for arm64 only and not duplicated.
3. It passes load, generate, stream and cancel tests in CI (logic) and a device benchmark (BLOCKED until run on the phone).

## Update 2026-10-31: llama.cpp build evidence (CI run 41218671645)
- The official `examples/llama.android` library at release tag v0.8.0 (commit 8642541) **builds in GitHub CI** with NDK 29.0.13113456, CMake 3.31.6 and compileSdk 36. VERIFIED (build only; never run on a phone).
- Output: a 19 MB AAR. For arm64-v8a the native files are about 19 MB uncompressed (llama, llama-common, ggml, one CPU variant per ARM generation armv8.0 to armv9.2, OpenMP). The APK compresses them; the final increase is unmeasured. x86_64 files are dropped by keeping arm64 only.
- Library facts that matter for VISION: it declares minSdk 33 (VISION is 26), is a single-instance chat session (`loadModel`, `setSystemPrompt`, `sendUserPrompt(): Flow<String>`, `cleanUp`, `destroy`), keeps the conversation in its KV cache between prompts, and includes a GGUF metadata reader (useful for compatibility checks).
- **Decision: not wired into the app yet.** VISION's planner and reply calls are stateless (different system prompts, no shared history). The library has no call to clear its cache between requests, so a faithful integration means patching its native code and then testing it on a phone, which cannot be done from CI. The mission rule is to replace or add an engine only with measurable evidence. The evidence needed is the owner's Diagnostics log for LiteRT-LM on the iQOO 7. If LiteRT-LM proves too slow or too limited, llama.cpp is the next engine, and this build check shows it is buildable.
- Vulkan is not enabled in that build (CPU only).
