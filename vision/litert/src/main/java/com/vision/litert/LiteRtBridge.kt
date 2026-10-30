package com.vision.litert

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig

/** Thin, dependency-free facade over Google LiteRT-LM (public API uses only Kotlin/JDK types). Blocking calls. */
class LiteRtBridge {
    private var engine: Engine? = null

    fun load(modelPath: String, cacheDir: String) {
        val e = Engine(EngineConfig(modelPath = modelPath, backend = Backend.CPU(), cacheDir = cacheDir))
        e.initialize()
        engine = e
    }

    fun generate(system: String, prompt: String): String {
        val e = engine ?: error("Model not loaded")
        return e.createConversation(ConversationConfig(systemInstruction = Contents.of(system))).use { c ->
            c.sendMessage(prompt).text
        }
    }

    fun close() { try { engine?.close() } catch (_: Throwable) {}; engine = null }
}
