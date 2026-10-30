package com.vision.assistant.core

enum class ResourceKind { LLM, STT, TTS, WAKEWORD, EMBEDDING }

/**
 * Downloadable trained models. Hashes and sizes were resolved from the official hosts
 * (Hugging Face LFS metadata; alphacephei.com download) by the CI workflow "VISION resolve resource hashes".
 * A resource is usable only when the downloaded file's SHA-256 equals [sha256].
 */
data class ResourceEntry(
    val id: String,
    val kind: ResourceKind,
    val title: String,
    val url: String,
    val fileName: String,
    val sha256: String,
    val sizeBytes: Long,
    val minRamMb: Long,
    val license: String,
    val languages: String,
    val note: String = "",
) {
    val sizeMb: Long get() = sizeBytes / 1_048_576
}

object ResourceManifest {
    private const val HF = "https://huggingface.co/litert-community"

    val entries: List<ResourceEntry> = listOf(
        ResourceEntry(
            "qwen3-0.6b-int4", ResourceKind.LLM, "Qwen3 0.6B (int4) — small language model",
            "$HF/Qwen3-0.6B/resolve/main/Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm",
            "Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm",
            "03e7da1eb1108b50dffaa9bb52cc7bcbad2eb0c66ca990267f480c1e545d2856", 344_671_744, 2_500,
            "Apache-2.0", "multilingual (incl. Hindi)", "Runs on most 64-bit phones.",
        ),
        ResourceEntry(
            "qwen3-4b-int4", ResourceKind.LLM, "Qwen3 4B (mixed int4) — advanced language model",
            "$HF/Qwen3-4B/resolve/main/qwen3_4b_mixed_int4.litertlm",
            "qwen3_4b_mixed_int4.litertlm",
            "569fe029f51e55b6377f98ebd7f6d26431bc73e1d632034f5389f2fd720da701", 2_659_053_760, 6_000,
            "Apache-2.0", "multilingual (incl. Hindi)", "Needs a phone with about 8 GB RAM; large download.",
        ),
        ResourceEntry(
            "vosk-small-en-us", ResourceKind.STT, "Vosk small English (US) — offline speech",
            "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip", "vosk-model-small-en-us-0.15.zip",
            "30f26242c4eb449f948e42cb302dd7a686cb29a3423a8367f99ff41780942498", 41_205_931, 1_000,
            "Apache-2.0", "en",
        ),
        ResourceEntry(
            "vosk-small-hi", ResourceKind.STT, "Vosk small Hindi — offline speech",
            "https://alphacephei.com/vosk/models/vosk-model-small-hi-0.22.zip", "vosk-model-small-hi-0.22.zip",
            "7c50a10866889f0ac21d912c20537a055a597ed09fc1d3e5bcd798f9f0017e48", 44_458_845, 1_000,
            "Apache-2.0", "hi",
        ),
    )

    fun byId(id: String) = entries.firstOrNull { it.id == id }
}
