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
    /** Set when the user must pick/take an image (or choose a PDF when [needsDocument]) for this question. */
    val needsImage: String? = null,
    val needsDocument: Boolean = false,
)

class Orchestrator(private val app: VisionApp) {
    private val tools = ToolRegistry(app)

    suspend fun handle(userText: String, locked: Boolean = false, settings: com.vision.assistant.privacy.LockSettings = com.vision.assistant.privacy.LockSettings(), recordUser: Boolean = true): AgentReply {
        val lang = LanguageDetector.detect(userText)
        val db = app.database
        if (recordUser) db.chat().insert(ChatMessage(role = "user", text = userText, lang = lang.code))
        if (recordUser && PlanState.cancelWords.matches(userText.trim())) { PlanState.clear(app); return AgentReply(Say("Okay, dropped the remaining steps.", "Theek hai, baaki steps hata diye.", "ठीक है, बाकी चरण हटा दिए।").`in`(lang), lang) }
        val resumed = if (recordUser && PlanState.resumeWords.matches(userText.trim())) PlanState.load(app).takeIf { it.isNotEmpty() } else null
        // "do A then B": run each recognised step in order; stop at the first one that fails or needs a confirmation/permission.
        if (recordUser) {
            val steps = resumed ?: MultiStep.split(userText)
            if (resumed != null) PlanState.clear(app)
            if (steps.size > 1 || resumed != null) {
                val replies = ArrayList<String>(); var last: AgentReply? = null
                for ((i, step) in steps.withIndex()) {
                    val r = handle(step, locked, settings, recordUser = false)
                    replies.add("${i + 1}. ${r.text}"); last = r
                    if (r.confirm != null || r.needsPermission != null || r.needsUnlock || r.needsImage != null) { val rest = steps.drop(i + 1); if (rest.isNotEmpty()) PlanState.save(app, rest); replies.add(Say("Paused here. Say \"continue\" to run the remaining ${rest.size} step(s), or \"cancel plan\".", "Yahin ruka. Baaki ${rest.size} step ke liye \"continue\" bolein, ya \"cancel plan\".", "यहीं रुका। बाकी ${rest.size} चरण के लिए \"continue\" कहें, या \"cancel plan\"।").`in`(lang)); break }
                }
                val joined = AgentReply(replies.joinToString("\n"), lang, last?.confirm, last?.needsPermission, last?.needsUnlock ?: false, last?.needsImage, last?.needsDocument ?: false)
                db.chat().insert(ChatMessage(role = "assistant", text = joined.text, lang = lang.code))
                return joined
            }
        }
        val engine = app.engines.current()
        val learned = if (recordUser) try { Learning.learn(db.memory(), userText) } catch (_: Throwable) { emptyList() } else emptyList()

        // The trained model understands the request; the rule parser is only the fallback.
        if (Identity.isCreatorQuestion(userText)) {
            val r = AgentReply(Identity.creatorAnswer(lang), lang)
            db.chat().insert(ChatMessage(role = "assistant", text = r.text, lang = lang.code))
            return r
        }
        // Short, unambiguous commands skip the model entirely: instant answer, no battery/RAM cost.
        val ruleIntent = IntentParser.parse(userText)
        val fast = isFastCommand(ruleIntent)
        val plan = if (fast) null else try { engine.plan(userText) } catch (_: Throwable) { null }
        val intent = if (fast) ruleIntent else plan?.intent?.let { if (it is Intent.Chat) Intent.Chat(userText) else it } ?: ruleIntent

        val reply = if (locked && !LockPolicy.allowedWhenLocked(intent, settings)) {
            AgentReply(Say("That is turned off while the phone is locked (see Settings).", "Locked phone par yeh band hai (Settings dekhein).", "फ़ोन लॉक होने पर यह बंद है (सेटिंग्स देखें)।").`in`(lang), lang)
        } else if (locked && LockPolicy.needsUnlock(intent)) {
            AgentReply(Say("Unlock your phone to continue.", "Aage badhne ke liye phone unlock kijiye.", "आगे बढ़ने के लिए फ़ोन अनलॉक कीजिए।").`in`(lang), lang, needsUnlock = true)
        } else if (intent is Intent.See) {
            AgentReply(
                Say("Take a photo or choose ${if (intent.document) "a PDF" else "an image"} and I'll look at it.", "${if (intent.document) "PDF" else "Image"} chuniye ya photo kheeniye, main dekhta hoon.", "${if (intent.document) "PDF" else "तस्वीर"} चुनिए या फ़ोटो लीजिए, मैं देखता हूँ।").`in`(lang),
                lang, needsImage = intent.question, needsDocument = intent.document,
            )
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
        if (learned.isNotEmpty()) return reply.copy(text = reply.text + "\n\n" + Say("(I'll remember: ${learned.joinToString("; ") { it.content }})", "(Yaad rakhunga: ${learned.joinToString("; ") { it.content }})", "(याद रखूँगा: ${learned.joinToString("; ") { it.content }})").`in`(lang))
        return reply
    }

    private fun isFastCommand(i: Intent) = when (i) {
        is Intent.Termux, is Intent.See, Intent.AskTime, Intent.AskBattery, Intent.ListTasks, Intent.ReadEmail, is Intent.ReadNotifications, is Intent.Remember, is Intent.Recall -> true
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
