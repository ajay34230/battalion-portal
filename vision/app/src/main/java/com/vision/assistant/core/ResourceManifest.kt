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

    /** The built-in, hand-checked catalogue. */
    val base: List<ResourceEntry> = listOf(
        ResourceEntry(
            "qwen3-0.6b-int4", ResourceKind.LLM, "Qwen3 0.6B (int4) — small language model",
            "$HF/Qwen3-0.6B/resolve/main/Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm",
            "Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm",
            "bf1aba9ce8d963124407353c6aa4cbf72eba78db21ae133de983eac672ce83e0", 343_490_560, 2_500,
            "Apache-2.0", "multilingual (incl. Hindi)", "Runs on most 64-bit phones.",
        ),
        ResourceEntry(
            "qwen3-4b-int4", ResourceKind.LLM, "Qwen3 4B (mixed int4) — advanced language model",
            "$HF/Qwen3-4B/resolve/main/qwen3_4b_mixed_int4.litertlm",
            "qwen3_4b_mixed_int4.litertlm",
            "4877b236f09c0b519958612afeb3c6fc6575c7ce56080d0d3a5406150e0ff9ce", 2_272_935_936, 6_000,
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
            "granite-4.0-350m", ResourceKind.LLM, "IBM Granite 4.0 350M (int8) — tiny, fast",
            "https://huggingface.co/litert-community/granite-4.0-350m-litert-lm/resolve/main/granite-4.0-350m_q8_ekv1280.litertlm", "granite-4.0-350m_q8_ekv1280.litertlm",
            "c8e9a29493f62b7c44461fb36980987c4c1454c75e95f57ba0539a8edc9dce76", 468_209_584, 2_000,
            "Apache-2.0", "mainly English; weak Hindi", "Very small and quick for simple commands and short answers. Verified file on Hugging Face; not tested on a phone.",
        ),
        ResourceEntry(
            "qwen3-1.7b", ResourceKind.LLM, "Qwen3 1.7B — balanced, multilingual",
            "https://huggingface.co/litert-community/Qwen3-1.7B/resolve/main/Qwen3_1.7B.litertlm", "Qwen3_1.7B.litertlm",
            "e40599625f3e446d6522674586a40566d6a3a117af608f6e9ea46ac86d326461", 1_754_890_240, 4_000,
            "Apache-2.0", "multilingual (incl. Hindi)", "A good middle size for 6–8 GB phones: smarter than 0.6B, lighter than 4B. Verified file on Hugging Face; not tested on a phone.",
        ),
        ResourceEntry(
            "gemma4-12b", ResourceKind.LLM, "Gemma 4 12B — experimental, very large",
            "https://huggingface.co/litert-community/gemma-4-12B-it-litert-lm/resolve/main/gemma-4-12B-it.litertlm", "gemma-4-12B-it.litertlm",
            "58fd31b778ca2c21c80d634fb34fc5a89d11d563a38dfd3cbf1b40dbf252a8b6", 6_883_278_368, 10_000,
            "Apache-2.0", "multilingual", "EXPERIMENTAL: 6.9 GB file; needs about 10 GB+ of real RAM (virtual/extended RAM does not help). Expect it to be slow or to close the app on an 8 GB phone. Used as a text model here.", experimental = true,
        ),
        ResourceEntry(
            "qwen3.5-9b-int4", ResourceKind.LLM, "Qwen3.5 9B (mixed int4) — experimental, strongest",
            "$HF/Qwen3.5-9B/resolve/main/Qwen3.5-9B_mixed_int4.litertlm", "Qwen3.5-9B_mixed_int4.litertlm",
            "61042ebd3c473763972d22310c055f63fab5a29bc2976ab533d6a59c262cac65", 6_060_357_536, 8_000,
            "Apache-2.0", "multilingual (incl. Hindi)", "EXPERIMENTAL: 6 GB file, expected 9–10 GB of memory while running. Only real RAM counts — extended/virtual RAM is slow flash storage and does not make this fast. Likely slow (under ~2 tokens/s) or may close the app on an 8 GB phone; fine to try on a 12 GB+ phone. Text only.", experimental = true,
        ),
        ResourceEntry(
            "embeddinggemma-270m", ResourceKind.EMBEDDING, "EmbeddingGemma 270M — meaning-based memory search",
            "https://huggingface.co/litert-community/embeddinggemma-2-text-270m-litert-lm/resolve/main/embeddinggemma-2-text-270m.litertlm", "embeddinggemma-2-text-270m.litertlm",
            "2d079ee2f6f066b1f368e8d7c819f55214eaef1d0513b312321901f30ab286fb", 164_626_432, 1_500,
            "Apache-2.0", "multilingual", "Lets memory search match meaning (for example \"mother's birthday\" finds \"Mummy ka janamdin\"). Small and local. Unverified on real phones.",
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
            "vosk-spk", ResourceKind.STT, "Vosk speaker model — recognises YOUR voice for Hey Vision",
            "https://alphacephei.com/vosk/models/vosk-model-spk-0.4.zip", "vosk-model-spk-0.4.zip",
            "a74d8f51144484813e16af689bb0f916b7a111e2347f467c4933c1166097b5a7", 13_869_103, 300,
            "Apache-2.0", "any",
        ),
        ResourceEntry(
            "vosk-small-hi", ResourceKind.STT, "Vosk small Hindi — offline speech",
            "https://alphacephei.com/vosk/models/vosk-model-small-hi-0.22.zip", "vosk-model-small-hi-0.22.zip",
            "7c50a10866889f0ac21d912c20537a055a597ed09fc1d3e5bcd798f9f0017e48", 44_458_845, 1_000,
            "Apache-2.0", "hi",
        ),
    )

    /** Models the owner found online and added; kept in preferences and re-validated on every load. */
    @Volatile private var extra: List<ResourceEntry> = emptyList()
    val entries: List<ResourceEntry> get() = base + extra.filter { x -> base.none { it.id == x.id } }

    fun loadExtra(c: android.content.Context) {
        extra = try {
            val a = org.json.JSONArray(c.getSharedPreferences("vision_discovered", android.content.Context.MODE_PRIVATE).getString("list", "[]"))
            (0 until a.length()).mapNotNull { ModelCatalog.decode(a.getJSONObject(it)) }
        } catch (_: Exception) { emptyList() }
    }
    fun addExtra(c: android.content.Context, e: ResourceEntry) {
        if (extra.any { it.id == e.id } || base.any { it.id == e.id }) return
        extra = extra + e; saveExtra(c)
    }
    fun removeExtra(c: android.content.Context, id: String) { extra = extra.filter { it.id != id }; saveExtra(c) }
    private fun saveExtra(c: android.content.Context) {
        c.getSharedPreferences("vision_discovered", android.content.Context.MODE_PRIVATE).edit().putString("list", org.json.JSONArray(extra.map { ModelCatalog.encode(it) }).toString()).apply()
    }
    fun isDiscovered(id: String) = extra.any { it.id == id }

    fun byId(id: String) = entries.firstOrNull { it.id == id }
}
