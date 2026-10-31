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
    val multimodal: Boolean = false,
    /** Bigger than the safe RAM rule allows. Offered only on explicit user request, with a warning; may be very slow or crash on small-RAM phones. */
    val experimental: Boolean = false,
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
            "qwen3.5-0.8b-int8", ResourceKind.LLM, "Qwen3.5 0.8B (int8) — fast, light",
            "$HF/Qwen3.5-0.8B/resolve/main/Qwen3.5-0.8B_int8.litertlm", "Qwen3.5-0.8B_int8.litertlm",
            "25447e2550082aed6145bbc755ec4da5d535f98f6a0c386865fdae2ccb88aed1", 964_566_992, 3_000,
            "Apache-2.0", "multilingual (incl. Hindi)", "Newer than Qwen3 0.6B. Unverified on real phones.",
        ),
        ResourceEntry(
            "qwen3.5-2b-int8", ResourceKind.LLM, "Qwen3.5 2B (int8) — balanced",
            "$HF/Qwen3.5-2B/resolve/main/Qwen3.5-2B_int8.litertlm", "Qwen3.5-2B_int8.litertlm",
            "fa4ac3259404567c7f82533746549eed94e2e1c8222095706c63cce17653c68a", 2_119_744_416, 5_000,
            "Apache-2.0", "multilingual (incl. Hindi)", "Good middle choice for 6 GB phones. Unverified on real phones.",
        ),
        ResourceEntry(
            "qwen3.5-4b-int4", ResourceKind.LLM, "Qwen3.5 4B (mixed int4) — strongest recommended",
            "$HF/Qwen3.5-4B/resolve/main/Qwen3.5-4B_mixed_int4.litertlm", "Qwen3.5-4B_mixed_int4.litertlm",
            "7243c33c9c6192150ab6da7ac13a9efdc24ff1a653eabc93b2757de4b44b012a", 2_754_070_624, 6_500,
            "Apache-2.0", "multilingual (incl. Hindi)", "For 8 GB+ phones such as iQOO 7. Published benchmark: about 6 tokens/s on an 8 GB Pixel 8a CPU, ~4.4 GB peak memory. Text only.",
        ),
        ResourceEntry(
            "qwen3.5-9b-int4", ResourceKind.LLM, "Qwen3.5 9B (mixed int4) — experimental, strongest",
            "$HF/Qwen3.5-9B/resolve/main/Qwen3.5-9B_mixed_int4.litertlm", "Qwen3.5-9B_mixed_int4.litertlm",
            "61042ebd3c473763972d22310c055f63fab5a29bc2976ab533d6a59c262cac65", 6_060_357_536, 8_000,
            "Apache-2.0", "multilingual (incl. Hindi)", "EXPERIMENTAL: 6 GB file, expected 9–10 GB of memory while running. Only real RAM counts — extended/virtual RAM is slow flash storage and does not make this fast. Likely slow (under ~2 tokens/s) or may close the app on an 8 GB phone; fine to try on a 12 GB+ phone. Text only.", experimental = true,
        ),
        ResourceEntry(
            "gemma4-e2b", ResourceKind.LLM, "Gemma 4 E2B — language + image understanding",
            "$HF/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm", "gemma-4-E2B-it.litertlm",
            "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c", 2_588_147_712, 6_000,
            "Apache-2.0", "multilingual", "Can look at photos. Published guidance: 6 GB+ RAM. Unverified on real phones.", multimodal = true,
        ),
        ResourceEntry(
            "gemma4-e4b", ResourceKind.LLM, "Gemma 4 E4B — stronger language + image understanding",
            "$HF/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm", "gemma-4-E4B-it.litertlm",
            "0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0", 3_659_530_240, 7_000,
            "Apache-2.0", "multilingual", "Can look at photos. Published guidance: 8 GB+ RAM. Large download. Unverified on real phones.", multimodal = true,
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
