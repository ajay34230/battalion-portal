package com.vision.assistant.core

enum class ResourceKind { LLM, STT, TTS, WAKEWORD, EMBEDDING }

/**
 * Candidate downloadable resources. A resource is installable ONLY when [sha256] is filled with a hash
 * confirmed from the official source. Blank hash => the downloader refuses to mark it installed.
 */
data class ResourceEntry(
    val id: String,
    val kind: ResourceKind,
    val title: String,
    val url: String,
    val sha256: String,
    val sizeMb: Long,
    val minRamMb: Long,
    val license: String,
    val languages: String,
    val note: String = "",
)

object ResourceManifest {
    val entries: List<ResourceEntry> = listOf(
        ResourceEntry(
            "vosk-small-en-us", ResourceKind.STT, "Vosk small English (US)",
            "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip", "", 40, 1_000,
            "Apache-2.0", "en", "Size approximate; hash to be confirmed.",
        ),
        ResourceEntry(
            "vosk-small-hi", ResourceKind.STT, "Vosk small Hindi",
            "https://alphacephei.com/vosk/models/vosk-model-small-hi-0.22.zip", "", 40, 1_000,
            "Apache-2.0", "hi", "Size approximate; hash to be confirmed.",
        ),
    )

    fun byId(id: String) = entries.firstOrNull { it.id == id }
}
