package com.vision.assistant.ai

import com.vision.assistant.VisionApp
import com.vision.assistant.core.Downloads
import com.vision.assistant.core.InstallState
import com.vision.assistant.core.ResourceKind
import com.vision.assistant.core.ResourceManifest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Picks the best verified, compatible language model for this device and loads it once. Falls back to rule-based only if none works. */
class EngineProvider(private val app: VisionApp) {
    private val mutex = Mutex()
    private var current: LlmEngine? = null
    private var currentId: String? = null
    val label = MutableStateFlow("Checking language model…")
    var lastError: String? = null; private set

    suspend fun current(): LlmEngine = mutex.withLock {
        val mgr = Downloads.manager(app)
        val entry = ResourceManifest.entries.filter { it.kind == ResourceKind.LLM }
            .sortedByDescending { it.sizeBytes }
            .firstOrNull { mgr.compatible(it) && mgr.state(it) == InstallState.INSTALLED }
        if (entry == null) {
            if (current !is RuleBasedEngine) { (current as? LiteRtLmEngine)?.close(); current = RuleBasedEngine(); currentId = null }
            label.value = current!!.name
            return current!!
        }
        if (currentId == entry.id && current != null) return current!!
        (current as? LiteRtLmEngine)?.close()
        label.value = "Loading ${entry.title}…"
        return try {
            val e = LiteRtLmEngine(app, mgr.file(entry), entry.title)
            e.load()
            current = e; currentId = entry.id; lastError = null
            label.value = entry.title
            e
        } catch (t: Throwable) {
            lastError = t.message ?: t.javaClass.simpleName
            current = RuleBasedEngine(); currentId = null
            label.value = "Model failed to load ($lastError); basic commands only"
            current!!
        }
    }

    /** Called after a model download finishes or a model is deleted. */
    fun invalidate() { currentId = null }
}
