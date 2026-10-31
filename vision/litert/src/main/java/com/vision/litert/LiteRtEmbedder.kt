package com.vision.litert

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.EmbeddingEngine
import com.google.ai.edge.litertlm.EmbeddingEngineConfig
import com.google.ai.edge.litertlm.EmbeddingOptions
import com.google.ai.edge.litertlm.InputData

/** Turns text into a meaning vector with an on-device embedding model (for example EmbeddingGemma). Blocking; CPU. */
class LiteRtEmbedder {
    private var engine: EmbeddingEngine? = null

    fun load(modelPath: String) {
        val e = EmbeddingEngine(EmbeddingEngineConfig(modelPath, Backend.CPU()))
        e.initialize()
        engine = e
    }

    /** A unit-length vector; texts with similar meaning give vectors with a high dot product. */
    fun embed(text: String): FloatArray {
        val e = engine ?: error("Embedding model not loaded")
        return e.computeEmbedding(listOf(InputData.Text(text)), EmbeddingOptions(true)).embedding
    }

    fun close() { try { engine?.close() } catch (_: Throwable) { }; engine = null }
}
