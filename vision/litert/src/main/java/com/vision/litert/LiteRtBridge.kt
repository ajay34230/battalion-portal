package com.vision.litert

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig

/** Thin, dependency-free facade over Google LiteRT-LM (public API uses only Kotlin/JDK types). Blocking calls. */
class LiteRtBridge {
    private var engine: Engine? = null

    /** "GPU" or "CPU": what the loaded model actually runs on. */
    var backendName: String = "CPU"; private set

    /** Tries the phone's GPU first when [tryGpu] (much faster for big models) and falls back to the CPU if that fails to initialise or to answer a test prompt. */
    fun load(modelPath: String, cacheDir: String, vision: Boolean = false, tryGpu: Boolean = false) {
        if (tryGpu) {
            try {
                val cfg = if (vision) EngineConfig(modelPath = modelPath, backend = Backend.GPU(), visionBackend = Backend.GPU(), cacheDir = cacheDir)
                else EngineConfig(modelPath = modelPath, backend = Backend.GPU(), cacheDir = cacheDir)
                val e = Engine(cfg); e.initialize(); engine = e
                generate("Reply with one word.", "Hi") // compile the GPU kernels now, inside the crash-guarded window
                backendName = "GPU"; return
            } catch (_: Throwable) { close() }
        }
        val cfg = if (vision) EngineConfig(modelPath = modelPath, backend = Backend.CPU(), visionBackend = Backend.CPU(), cacheDir = cacheDir)
        else EngineConfig(modelPath = modelPath, backend = Backend.CPU(), cacheDir = cacheDir)
        val e = Engine(cfg)
        e.initialize()
        engine = e; backendName = "CPU"
    }

    fun generate(system: String, prompt: String): String {
        val e = engine ?: error("Model not loaded")
        return e.createConversation(ConversationConfig(systemInstruction = Contents.of(system))).use { c ->
            c.sendMessage(prompt).toString()
        }
    }

    /** Needs a multimodal model loaded with vision = true. */
    fun generateWithImage(system: String, prompt: String, imagePath: String): String {
        val e = engine ?: error("Model not loaded")
        return e.createConversation(ConversationConfig(systemInstruction = Contents.of(system))).use { c ->
            c.sendMessage(Contents.of(Content.ImageFile(imagePath), Content.Text(prompt))).toString()
        }
    }

    fun close() { try { engine?.close() } catch (_: Throwable) {}; engine = null }
}
