package com.vision.assistant.ai

object LlmText {
    /** Removes Qwen3 "<think>…</think>" blocks (closed or unterminated) and trims. */
    fun clean(raw: String): String {
        var s = raw.replace(Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL), "")
        val open = s.indexOf("<think>")
        if (open >= 0) s = s.substring(0, open)
        return s.trim()
    }
}
