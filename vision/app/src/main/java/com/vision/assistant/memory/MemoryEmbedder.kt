package com.vision.assistant.memory

import com.vision.assistant.VisionApp
import com.vision.assistant.core.Downloads
import com.vision.assistant.core.InstallState
import com.vision.assistant.core.ResourceManifest
import com.vision.litert.LiteRtEmbedder
import java.io.File
import java.security.MessageDigest

/** Pure helpers for the vector cache (testable on a plain JVM). */
object EmbeddingCodec {
    fun key(text: String): String = MessageDigest.getInstance("SHA-256").digest(text.trim().toByteArray()).joinToString("") { "%02x".format(it) }.take(32)
    fun toBytes(v: FloatArray): ByteArray = java.nio.ByteBuffer.allocate(v.size * 4).also { b -> v.forEach { b.putFloat(it) } }.array()
    fun fromBytes(b: ByteArray): FloatArray? = if (b.isEmpty() || b.size % 4 != 0 || b.size > 64 * 1024) null else java.nio.ByteBuffer.wrap(b).asFloatBuffer().let { f -> FloatArray(f.remaining()) { f.get() } }
}

/**
 * Meaning-based memory search with EmbeddingGemma, running fully on the phone. It is used only when that model has been downloaded and verified;
 * otherwise [forRecall] returns null and memory search stays keyword + fuzzy. Vectors are cached as small files in the app's private storage
 * (derived from your memories, never sent anywhere) and are removed when memory is erased.
 */
object MemoryEmbedder {
    const val MODEL_ID = "embeddinggemma-270m"
    private const val MAX_NEW_PER_CALL = 12
    private val lock = Any()
    private var emb: LiteRtEmbedder? = null
    @Volatile private var failed = false

    private fun dir(app: VisionApp) = File(app.filesDir, "emb").apply { mkdirs() }

    private fun engine(app: VisionApp): LiteRtEmbedder? {
        if (failed) return null
        synchronized(lock) {
            emb?.let { return it }
            val e = ResourceManifest.byId(MODEL_ID) ?: return null
            val mgr = Downloads.manager(app)
            if (mgr.state(e) != InstallState.INSTALLED) return null
            return try { LiteRtEmbedder().also { it.load(mgr.file(e).absolutePath); emb = it } } catch (t: Throwable) { failed = true; com.vision.assistant.diag.DiagLog.log("error", "embedding model failed to load: ${t.message}"); null }
        }
    }

    fun available(app: VisionApp) = engine(app) != null

    fun embedNow(app: VisionApp, text: String): FloatArray? = engine(app)?.let { try { synchronized(lock) { it.embed(text) } } catch (_: Throwable) { null } }

    /** An embedder for one recall: the question is embedded live, memories come from the cache (a few new ones are embedded and cached on the way). */
    fun forRecall(app: VisionApp): HybridRank.Embedder? {
        val e = engine(app) ?: return null
        var fresh = 0
        return HybridRank.Embedder { text ->
            val f = File(dir(app), EmbeddingCodec.key(text) + ".f32")
            if (f.isFile) EmbeddingCodec.fromBytes(f.readBytes())
            else if (fresh >= MAX_NEW_PER_CALL) null // the rest are embedded on later recalls; keyword search covers them meanwhile
            else try {
                fresh++
                val v = synchronized(lock) { e.embed(text) }
                f.writeBytes(EmbeddingCodec.toBytes(v))
                v
            } catch (_: Throwable) { null }
        }
    }

    /** Indexes memories that have no cached vector yet, a few at a time, then frees the embedding model. Does nothing if the model is not installed. */
    suspend fun backfill(app: VisionApp, limit: Int = 300): Int {
        if (ResourceManifest.byId(MODEL_ID)?.let { Downloads.manager(app).state(it) == InstallState.INSTALLED } != true) return 0
        var done = 0
        try {
            for (m in app.database.memory().list().filter { it.category != "creator" }.take(limit)) {
                val f = File(dir(app), EmbeddingCodec.key(m.content) + ".f32")
                if (f.isFile) continue
                val v = embedNow(app, m.content) ?: break
                f.writeBytes(EmbeddingCodec.toBytes(v)); done++
                kotlinx.coroutines.yield()
            }
        } finally { close() }
        if (done > 0) com.vision.assistant.diag.DiagLog.log("info", "embedding backfill indexed $done memories")
        return done
    }

    fun clearCache(app: VisionApp) { try { dir(app).listFiles()?.forEach { it.delete() } } catch (_: Throwable) { } }

    fun close() { synchronized(lock) { emb?.close(); emb = null } }
}
