package com.vision.assistant.ai

import org.json.JSONObject

data class Plan(val intent: Intent, val reply: String?)

/** Turns the language model's JSON output into an [Intent]. Anything malformed returns null (caller falls back). */
object PlanParser {
    const val SYSTEM = """You are the planner of a phone assistant called VISION. The user writes in English, Hindi (Devanagari) or Hinglish (Hindi in Latin letters).
Reply with ONE JSON object and nothing else. Choose the intent:
{"intent":"open_app","app":"<app name>"}
{"intent":"call","contact":"<name or number>"}
{"intent":"reminder","text":"<what to remind>","day_offset":0,"hour":6,"minute":0,"repeat_weekday":null}
{"intent":"search","query":"<web search query>"}
{"intent":"time"} {"intent":"battery"} {"intent":"tasks"} {"intent":"email"}
{"intent":"remember","text":"<fact to save>"} {"intent":"recall","query":"<topic>"}
{"intent":"whatsapp_draft","text":"<message text>"}
{"intent":"notifications","whatsapp_only":false}
{"intent":"chat","reply":"<your answer in the user's language>"}
Rules: hour is 24-hour; day_offset 0=today, 1=tomorrow; repeat_weekday is MON..SUN or null. "wake me up"/"utha dena" is a reminder whose text contains "wake". Plain questions and conversation use chat with a short helpful reply in the same language as the user. Never claim an action was done.
Examples:
"kal subah 6 baje utha dena" -> {"intent":"reminder","text":"wake up","day_offset":1,"hour":6,"minute":0,"repeat_weekday":null}
"open WhatsApp" -> {"intent":"open_app","app":"whatsapp"}
"mujhe batao ki aaj weather kaisa hai" -> {"intent":"search","query":"today weather"}
"मम्मी को फोन करो" -> {"intent":"call","contact":"मम्मी"}
"what is photosynthesis" -> {"intent":"chat","reply":"Photosynthesis is how plants turn sunlight, water and carbon dioxide into sugar and oxygen."}"""

    fun parse(modelOutput: String): Plan? {
        val start = modelOutput.indexOf('{'); val end = modelOutput.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val o = try { JSONObject(modelOutput.substring(start, end + 1)) } catch (_: Exception) { return null }
        fun s(k: String) = o.optString(k, "").trim()
        val intent: Intent = when (s("intent").lowercase()) {
            "open_app" -> s("app").takeIf { it.isNotEmpty() }?.let { Intent.OpenApp(it.lowercase()) }
            "call" -> s("contact").takeIf { it.isNotEmpty() }?.let { Intent.Call(it) }
            "reminder" -> {
                val text = s("text"); if (text.isEmpty() || !o.has("hour")) null else {
                    val h = o.optInt("hour", -1); val m = o.optInt("minute", 0)
                    if (h !in 0..23 || m !in 0..59) null
                    else Intent.SetReminder(text, ParsedTime(o.optInt("day_offset", 0).coerceIn(0, 7), h, m), s("repeat_weekday").uppercase().takeIf { it in setOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN") })
                }
            }
            "search" -> s("query").takeIf { it.isNotEmpty() }?.let { Intent.Search(it) }
            "time" -> Intent.AskTime
            "battery" -> Intent.AskBattery
            "tasks" -> Intent.ListTasks
            "email" -> Intent.ReadEmail
            "remember" -> s("text").takeIf { it.isNotEmpty() }?.let { Intent.Remember(it) }
            "recall" -> s("query").takeIf { it.isNotEmpty() }?.let { Intent.Recall(it) }
            "whatsapp_draft" -> s("text").takeIf { it.isNotEmpty() }?.let { Intent.WhatsAppDraft(it) }
            "notifications" -> Intent.ReadNotifications(o.optBoolean("whatsapp_only", false))
            "chat" -> Intent.Chat("")
            else -> null
        } ?: return null
        return Plan(intent, s("reply").takeIf { it.isNotEmpty() })
    }
}
