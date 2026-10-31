package com.vision.assistant.ai

/** Splits "open maps then call mom and remind me at 6" style requests into ordered steps. Only splits when every part is a recognised command. */
object MultiStep {
    private val sep = Regex("\\s*(?:,?\\s+and then\\s+|,?\\s+then\\s+|\\s+phir\\s+|\\s+fir\\s+|\\s+uske baad\\s+|\\s+uske bad\\s+|\\s+उसके बाद\\s+|\\s+फिर\\s+)\\s*", RegexOption.IGNORE_CASE)

    fun split(text: String): List<String> {
        val parts = text.trim().split(sep).map { it.trim().trimEnd('.', '!', '।') }.filter { it.isNotEmpty() }
        if (parts.size !in 2..5) return listOf(text)
        return if (parts.all { IntentParser.parse(it) !is Intent.Chat }) parts else listOf(text)
    }
}
