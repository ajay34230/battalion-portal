package com.vision.assistant.ai

import android.content.Context
import com.vision.litert.LiteRtBridge
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** On-device language model via Google's LiteRT-LM (CPU backend: works on any 64-bit ARM phone). */
class LiteRtLmEngine(private val ctx: Context, private val modelFile: File, label: String) : LlmEngine {
    override val name = label
    override val isNeuralModelLoaded = true
    private val bridge = LiteRtBridge()
    private val lock = Mutex()

    suspend fun load() = withContext(Dispatchers.IO) {
        bridge.load(modelFile.absolutePath, ctx.cacheDir.path)
    }

    fun close() = bridge.close()

    private suspend fun generate(system: String, prompt: String): String = lock.withLock {
        withContext(Dispatchers.IO) {
            // "/no_think" turns off Qwen3's long reasoning so replies stay fast.
            LlmText.clean(bridge.generate(system, "$prompt /no_think"))
        }
    }

    override suspend fun plan(userText: String): Plan? =
        PlanParser.parse(generate(PlanParser.SYSTEM, "\"$userText\" ->"))

    override suspend fun reply(history: List<ChatTurn>, userText: String, lang: Lang): String {
        val langName = when (lang) { Lang.HI -> "Hindi (Devanagari)"; Lang.HINGLISH -> "Hinglish (Hindi in Latin letters)"; Lang.EN -> "English" }
        val system = "You are VISION, a friendly, concise personal assistant on the user's Android phone. Reply in $langName, in at most 3 short sentences. " +
            "You can't perform phone actions in this reply; never claim you did something."
        val context = history.takeLast(6).joinToString("\n") { (if (it.role == "user") "User: " else "VISION: ") + it.text }
        return generate(system, if (context.isBlank()) userText else "$context\nUser: $userText")
    }
}
