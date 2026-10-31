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

    private val prefs get() = app.getSharedPreferences("vision_models", android.content.Context.MODE_PRIVATE)
    /** Models that crashed or failed to load on this phone; skipped automatically so a too-big model can never lock the app in a crash loop. */
    fun badModels(): Set<String> = prefs.getStringSet("bad", emptySet()) ?: emptySet()
    private fun markBad(id: String) { prefs.edit().putStringSet("bad", badModels() + id).apply() }
    fun clearBad(id: String) { prefs.edit().putStringSet("bad", badModels() - id).apply() }

    private fun installed(needImage: Boolean): ResourceEntry? {
        val mgr = Downloads.manager(app)
        val bad = badModels()
        val ok = ResourceManifest.entries.filter { it.kind == ResourceKind.LLM && it.id !in bad && mgr.compatible(it) && mgr.state(it) == InstallState.INSTALLED }
        // Experimental (oversized) models are used only when nothing safe is installed or when the user installed them on purpose and they loaded before.
        return if (needImage) ok.filter { it.multimodal }.maxByOrNull { it.sizeBytes }
        else (ok.filter { !it.multimodal }.maxByOrNull { it.sizeBytes } ?: ok.maxByOrNull { it.sizeBytes })
    }

    private suspend fun select(needImage: Boolean): LlmEngine? = mutex.withLock {
        // If the process died while a model was loading (out of memory), blame that model and skip it from now on.
        prefs.getString("loading", null)?.let { markBad(it); prefs.edit().remove("loading").apply() }
        while (true) {
            val entry = installed(needImage)
            if (entry == null) {
                if (needImage) return null
                if (current !is RuleBasedEngine) { (current as? LiteRtLmEngine)?.close(); current = RuleBasedEngine(); currentId = null }
                label.value = current!!.name + if (badModels().isNotEmpty()) " — a model was skipped because it failed to load on this phone" else ""
                return current!!
            }
            if (currentId == entry.id && current != null) return current!!
            (current as? LiteRtLmEngine)?.close()
            label.value = "Loading ${entry.title}…"
            prefs.edit().putString("loading", entry.id).commit()
            try {
                val e = LiteRtLmEngine(app, Downloads.manager(app).file(entry), entry.title, multimodal = entry.multimodal, noThink = entry.id.startsWith("qwen"))
                e.load()
                prefs.edit().remove("loading").apply()
                current = e; currentId = entry.id; lastError = null
                label.value = entry.title
                return e
            } catch (t: Throwable) {
                prefs.edit().remove("loading").apply()
                lastError = t.message ?: t.javaClass.simpleName
                markBad(entry.id)
                current = RuleBasedEngine(); currentId = null
                label.value = "${entry.title} failed to load ($lastError); trying a smaller model"
                if (needImage) return null
            }
        }
        @Suppress("UNREACHABLE_CODE") null
    }

    /** Called after a model download finishes or a model is deleted. */
    fun invalidate() { currentId = null }

    /** Frees the model when Android is short of memory; it reloads on next use. */
    suspend fun unload() = mutex.withLock {
        (current as? LiteRtLmEngine)?.close(); current = null; currentId = null
        label.value = "Model unloaded to free memory (reloads when needed)"
    }
}
