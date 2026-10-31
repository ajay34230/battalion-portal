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
