package com.vision.assistant.tools

import com.vision.assistant.ai.Lang

enum class Risk { READ, PREPARE, SEND, DESTRUCTIVE }

/** A localised message. */
class Say(private val en: String, private val hinglish: String = en, private val hi: String = en) {
    fun `in`(lang: Lang) = when (lang) { Lang.EN -> en; Lang.HINGLISH -> hinglish; Lang.HI -> hi }
    companion object { fun plain(s: String) = Say(s) }
}

sealed interface ToolResult {
    data class Success(val say: Say) : ToolResult
    data class Failure(val say: Say) : ToolResult
    data class NeedsPermission(val permission: String, val say: Say) : ToolResult
    /** The action runs only after the user confirms in the UI. */
    class NeedsConfirmation(val say: Say, val toolName: String, val run: suspend () -> ToolResult) : ToolResult
}
