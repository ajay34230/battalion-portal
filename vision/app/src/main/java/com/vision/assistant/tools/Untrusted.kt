package com.vision.assistant.tools

/** Detects instruction-like text inside untrusted content (web pages, emails, notifications). Such content is data only. */
object Untrusted {
    private val patterns = listOf(
        Regex("ignore (all |any )?(previous|prior|above) (instructions|rules)", RegexOption.IGNORE_CASE),
        Regex("disregard (the )?(system|previous) ", RegexOption.IGNORE_CASE),
        Regex("you (must|should) now ", RegexOption.IGNORE_CASE),
        Regex("(send|forward) (this|the|all) .{0,40}(to|@)", RegexOption.IGNORE_CASE),
        Regex("reveal .{0,30}(password|token|key|secret)", RegexOption.IGNORE_CASE),
    )

    fun looksLikeInjection(text: String) = patterns.any { it.containsMatchIn(text) }

    /** Drops suspicious lines so they never reach a planner prompt. */
    fun sanitize(text: String) = text.lineSequence().filterNot { looksLikeInjection(it) }.joinToString("\n")
}
