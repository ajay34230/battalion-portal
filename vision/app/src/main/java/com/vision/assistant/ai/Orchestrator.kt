package com.vision.assistant.ai

import com.vision.assistant.VisionApp
import com.vision.assistant.data.ChatMessage
import com.vision.assistant.privacy.LockPolicy
import com.vision.assistant.tools.Say
import com.vision.assistant.tools.ToolRegistry
import com.vision.assistant.tools.ToolResult

data class AgentReply(
    val text: String,
    val lang: Lang,
    val confirm: ToolResult.NeedsConfirmation? = null,
    val needsPermission: String? = null,
    val needsUnlock: Boolean = false,
)

class Orchestrator(private val app: VisionApp) {
    private val tools = ToolRegistry(app)

    suspend fun handle(userText: String, locked: Boolean = false, settings: com.vision.assistant.privacy.LockSettings = com.vision.assistant.privacy.LockSettings(), recordUser: Boolean = true): AgentReply {
        val lang = LanguageDetector.detect(userText)
        val db = app.database
        if (recordUser) db.chat().insert(ChatMessage(role = "user", text = userText, lang = lang.code))
        val engine = app.engines.current()

        // The trained model understands the request; the rule parser is only the fallback.
        // Short, unambiguous commands skip the model entirely: instant answer, no battery/RAM cost.
        val ruleIntent = IntentParser.parse(userText)
        val fast = isFastCommand(ruleIntent)
        val plan = if (fast) null else try { engine.plan(userText) } catch (_: Throwable) { null }
        val intent = if (fast) ruleIntent else plan?.intent?.let { if (it is Intent.Chat) Intent.Chat(userText) else it } ?: ruleIntent

        val reply = if (locked && !LockPolicy.allowedWhenLocked(intent, settings)) {
            AgentReply(Say("That is turned off while the phone is locked (see Settings).", "Locked phone par yeh band hai (Settings dekhein).", "फ़ोन लॉक होने पर यह बंद है (सेटिंग्स देखें)।").`in`(lang), lang)
        } else if (locked && LockPolicy.needsUnlock(intent)) {
            AgentReply(Say("Unlock your phone to continue.", "Aage badhne ke liye phone unlock kijiye.", "आगे बढ़ने के लिए फ़ोन अनलॉक कीजिए।").`in`(lang), lang, needsUnlock = true)
        } else if (intent is Intent.Chat) {
            val text = plan?.reply ?: run {
                val history = db.chat().recent(10).reversed().dropLast(1).map { ChatTurn(it.role, it.text) }
                try { engine.reply(history, userText, lang) } catch (t: Throwable) { "The language model failed: ${t.message}" }
            }
            AgentReply(text, lang)
        } else when (val r = tools.execute(intent)) {
            is ToolResult.Success -> AgentReply(r.say.`in`(lang), lang)
            is ToolResult.Failure -> AgentReply(r.say.`in`(lang), lang)
            is ToolResult.NeedsPermission -> AgentReply(r.say.`in`(lang), lang, needsPermission = r.permission)
            is ToolResult.NeedsConfirmation -> AgentReply(r.say.`in`(lang), lang, confirm = r)
        }
        db.chat().insert(ChatMessage(role = "assistant", text = reply.text, lang = lang.code))
        return reply
    }

    private fun isFastCommand(i: Intent) = when (i) {
        Intent.AskTime, Intent.AskBattery, Intent.ListTasks, Intent.ReadEmail, is Intent.ReadNotifications, is Intent.Remember, is Intent.Recall -> true
        is Intent.OpenApp -> i.app.trim().split(Regex("\\s+")).size <= 3
        is Intent.Call -> i.contact.trim().split(Regex("\\s+")).size <= 3
        else -> false
    }

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
