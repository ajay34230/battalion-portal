package com.vision.assistant.ai

data class ChatTurn(val role: String, val text: String)

interface LlmEngine {
    val name: String
    /** True only when a verified model file is loaded. */
    val isNeuralModelLoaded: Boolean
    suspend fun reply(history: List<ChatTurn>, userText: String, lang: Lang): String
    /** Neural engines understand the request themselves; the rule-based engine returns null. */
    suspend fun plan(userText: String): Plan? = null
    /** Summarises untrusted source material for a question. Null = engine can't (caller uses an extractive summary). */
    suspend fun summarize(query: String, material: String, lang: Lang): String? = null
}

/** Fallback used only when no model is installed (or loading failed). */
class RuleBasedEngine : LlmEngine {
    override val name = "No language model installed (basic commands only)"
    override val isNeuralModelLoaded = false

    override suspend fun reply(history: List<ChatTurn>, userText: String, lang: Lang): String =
        Replies.forIntent(IntentParser.parse(userText), lang)
}

object Replies {
    private fun pick(lang: Lang, en: String, hinglish: String, hi: String) = when (lang) {
        Lang.EN -> en; Lang.HINGLISH -> hinglish; Lang.HI -> hi
    }

    fun forIntent(i: Intent, lang: Lang): String = when (i) {
        is Intent.Chat -> pick(
            lang,
            "I can't chat freely yet: no language model is installed. Open Models & downloads to install one.",
            "Abhi free baat nahi kar sakta: koi language model installed nahi hai. Models & downloads mein jaakar install karein.",
            "अभी सामान्य बातचीत नहीं कर सकता: कोई भाषा मॉडल नहीं है। Models & downloads में जाकर इंस्टॉल करें।",
        )
        else -> pick(lang, "Working on it.", "Kar raha hoon.", "कर रहा हूँ।")
    }
}
