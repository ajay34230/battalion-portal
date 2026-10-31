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
    /** Why the GPU was not used (null when it was, or not attempted). */
    var gpuError: String? = null; private set

    /** Tries the phone's GPU first when [tryGpu] (much faster for big models) and falls back to the CPU if that fails to initialise or to answer a test prompt. */
    fun load(modelPath: String, cacheDir: String, vision: Boolean = false, tryGpu: Boolean = false) {
        gpuError = null
        if (tryGpu) {
            // Ladder: GPU with multi-token prediction -> plain GPU -> CPU. MTP needs a model file made for it; without one GPU init can fail.
            for (mtp in listOf(true, false)) {
                try {
                    setSpeculativeDecoding(mtp)
                    val cfg = if (vision) EngineConfig(modelPath = modelPath, backend = Backend.GPU(), visionBackend = Backend.GPU(), cacheDir = cacheDir)
                    else EngineConfig(modelPath = modelPath, backend = Backend.GPU(), cacheDir = cacheDir)
                    val e = Engine(cfg); e.initialize(); engine = e
                    generate("Reply with one word.", "Hi") // compile the GPU kernels now, inside the crash-guarded window
                    backendName = "GPU"; speculative = mtp && speculativeApplied; gpuError = null; return
                } catch (t: Throwable) {
                    val why = (t.message ?: t.javaClass.simpleName).take(120)
                    gpuError = (if (mtp) "MTP: " else "plain: ") + why + (gpuError?.let { " | $it" } ?: "")
                    close()
                }
            }
            setSpeculativeDecoding(false)
        }
        val cfg = if (vision) EngineConfig(modelPath = modelPath, backend = Backend.CPU(), visionBackend = Backend.CPU(), cacheDir = cacheDir)
        else EngineConfig(modelPath = modelPath, backend = Backend.CPU(), cacheDir = cacheDir)
        val e = Engine(cfg)
        e.initialize()
        engine = e; backendName = "CPU"
    }

    /** Multi-token prediction: Google recommends it on GPU (faster replies). Set by reflection so an older library simply ignores it. */
    private var speculativeApplied = false
    private fun setSpeculativeDecoding(on: Boolean) {
        speculativeApplied = try {
            val c = Class.forName("com.google.ai.edge.litertlm.ExperimentalFlags")
            val inst = c.getField("INSTANCE").get(null)
            c.getMethod("setEnableSpeculativeDecoding", Boolean::class.javaPrimitiveType).invoke(inst, on)
            on
        } catch (_: Throwable) { false }
    }
    var speculative = false; private set

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
