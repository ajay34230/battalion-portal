package com.vision.assistant.ai

import com.vision.assistant.VisionApp
import com.vision.assistant.core.Downloads
import com.vision.assistant.core.InstallState
import com.vision.assistant.core.ResourceEntry
import com.vision.assistant.core.ResourceKind
import com.vision.assistant.core.ResourceManifest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Model router. Only ONE large model is in memory at a time.
 * Text tasks use the strongest installed text model (a multimodal model only if nothing else is installed);
 * image tasks switch to an installed multimodal model and back afterwards.
 */
class EngineProvider(private val app: VisionApp) {
    private val mutex = Mutex()
    private var current: LlmEngine? = null
    private var currentId: String? = null
    val label = MutableStateFlow("Checking language model…")
    var lastError: String? = null; private set

    suspend fun current(): LlmEngine = select(needImage = false)!!

    /** An engine that can see images, or null if no multimodal model is installed. */
    suspend fun vision(): LlmEngine? = select(needImage = true)

    private fun installed(needImage: Boolean): ResourceEntry? {
        val mgr = Downloads.manager(app)
        val ok = ResourceManifest.entries.filter { it.kind == ResourceKind.LLM && mgr.compatible(it) && mgr.state(it) == InstallState.INSTALLED }
        return if (needImage) ok.filter { it.multimodal }.maxByOrNull { it.sizeBytes }
        else (ok.filter { !it.multimodal }.maxByOrNull { it.sizeBytes } ?: ok.maxByOrNull { it.sizeBytes })
    }

    private suspend fun select(needImage: Boolean): LlmEngine? = mutex.withLock {
        val entry = installed(needImage)
        if (entry == null) {
            if (needImage) return null
            if (current !is RuleBasedEngine) { (current as? LiteRtLmEngine)?.close(); current = RuleBasedEngine(); currentId = null }
            label.value = current!!.name
            return current!!
        }
        if (currentId == entry.id && current != null) return current!!
        (current as? LiteRtLmEngine)?.close()
        label.value = "Loading ${entry.title}…"
        return try {
            val e = LiteRtLmEngine(app, Downloads.manager(app).file(entry), entry.title, multimodal = entry.multimodal, noThink = entry.id.startsWith("qwen"))
            e.load()
            current = e; currentId = entry.id; lastError = null
            label.value = entry.title
            e
        } catch (t: Throwable) {
            lastError = t.message ?: t.javaClass.simpleName
            current = RuleBasedEngine(); currentId = null
            label.value = "Model failed to load ($lastError); basic commands only"
            if (needImage) null else current!!
        }
    }

    /** Called after a model download finishes or a model is deleted. */
    fun invalidate() { currentId = null }

    /** Frees the model when Android is short of memory; it reloads on next use. */
    suspend fun unload() = mutex.withLock {
        (current as? LiteRtLmEngine)?.close(); current = null; currentId = null
        label.value = "Model unloaded to free memory (reloads when needed)"
    }
}
