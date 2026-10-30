package com.vision.assistant.ai

import com.vision.assistant.VisionApp
import com.vision.assistant.data.ChatMessage
import com.vision.assistant.tools.Say
import com.vision.assistant.tools.ToolRegistry
import com.vision.assistant.tools.ToolResult

data class AgentReply(
    val text: String,
    val lang: Lang,
    val confirm: ToolResult.NeedsConfirmation? = null,
    val needsPermission: String? = null,
)

class Orchestrator(private val app: VisionApp, private val engine: LlmEngine = RuleBasedEngine()) {
    private val tools = ToolRegistry(app)
    val engineName: String get() = engine.name

    suspend fun handle(userText: String, locked: Boolean = false): AgentReply {
        val lang = LanguageDetector.detect(userText)
        val db = app.database
        db.chat().insert(ChatMessage(role = "user", text = userText, lang = lang.code))
        val intent = IntentParser.parse(userText)

        val reply = if (locked && !com.vision.assistant.privacy.LockPolicy.allowedWhenLocked(intent)) {
            AgentReply(Say("Please unlock your phone first.", "Pehle phone unlock kijiye.", "पहले फ़ोन अनलॉक कीजिए।").`in`(lang), lang)
        } else if (intent is Intent.Chat) {
            val history = db.chat().recent(10).reversed().map { ChatTurn(it.role, it.text) }
            AgentReply(engine.reply(history, userText, lang), lang)
        } else when (val r = tools.execute(intent)) {
            is ToolResult.Success -> AgentReply(r.say.`in`(lang), lang)
            is ToolResult.Failure -> AgentReply(r.say.`in`(lang), lang)
            is ToolResult.NeedsPermission -> AgentReply(r.say.`in`(lang), lang, needsPermission = r.permission)
            is ToolResult.NeedsConfirmation -> AgentReply(r.say.`in`(lang), lang, confirm = r)
        }
        db.chat().insert(ChatMessage(role = "assistant", text = reply.text, lang = lang.code))
        return reply
    }

    /** Runs a confirmed action and records the outcome in the audit log. */
    suspend fun confirmed(c: ToolResult.NeedsConfirmation, lang: Lang): String {
        val r = c.run()
        tools.audit(c.toolName, "confirmed", r)
        val text = when (r) {
            is ToolResult.Success -> r.say.`in`(lang)
            is ToolResult.Failure -> r.say.`in`(lang)
            is ToolResult.NeedsPermission -> r.say.`in`(lang)
            is ToolResult.NeedsConfirmation -> r.say.`in`(lang)
        }
        app.database.chat().insert(ChatMessage(role = "assistant", text = text, lang = lang.code))
        return text
    }

    suspend fun cancelled(c: ToolResult.NeedsConfirmation, lang: Lang): String {
        tools.audit(c.toolName, "cancelled by user", ToolResult.Failure(Say("cancelled")))
        return Say("Cancelled.", "Cancel kar diya.", "रद्द कर दिया।").`in`(lang)
    }
}
