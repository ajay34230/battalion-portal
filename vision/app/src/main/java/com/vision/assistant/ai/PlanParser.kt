package com.vision.assistant.ai

import org.json.JSONObject

data class Plan(val intent: Intent, val reply: String?)

/** Turns the language model's JSON output into an [Intent]. Anything malformed returns null (caller falls back). */
object PlanParser {
    const val SYSTEM = """You are the planner of a phone assistant called VISION, built by Ajay Singh Shekhawat (always mention him with gratitude if asked who made you). The user writes in English, Hindi (Devanagari) or Hinglish (Hindi in Latin letters).
Reply with ONE JSON object and nothing else. Choose the intent:
{"intent":"open_app","app":"<app name>"}
{"intent":"call","contact":"<name or number>"}
{"intent":"reminder","text":"<what to remind>","day_offset":0,"hour":6,"minute":0,"repeat_weekday":null}
{"intent":"search","query":"<web search query>","mode":"quick|deep|academic"}
{"intent":"calendar","days":1}  (what is on my calendar/schedule; days 1=today, 2=tomorrow, 7=week)
{"intent":"spending","period":"today|week|month|year"}  (how much money I spent/received/paid, expenses, payment summary)
{"intent":"event","kind":"birthday|anniversary|bill","title":"<who or what>","month":10,"day":5,"monthly":false}  (yearly birthday/anniversary or monthly bill reminder; month null if monthly)
{"intent":"routine","name":"<routine name>"}  (run one of the user's saved routines)
{"intent":"time"} {"intent":"battery"} {"intent":"tasks"} {"intent":"email"}
{"intent":"remember","text":"<fact to save>"} {"intent":"recall","query":"<topic>"}
{"intent":"whatsapp_draft","text":"<message text>"}
{"intent":"notifications","whatsapp_only":false}
{"intent":"termux","command":"<one command line>"}  (phone control and tools, e.g.: termux-torch on|off, termux-volume music 0-15, termux-brightness 0-255, termux-location, termux-battery-status, termux-wifi-connectioninfo, termux-camera-photo -c 0 /data/data/com.termux/files/home/vision-output/photo.jpg, termux-tts-speak <text>, termux-notification --title T --content C, termux-clipboard-get, termux-clipboard-set <text>, termux-sms-send -n <number> <text>, df -h, free -m, uptime, python3 <script.py>)
{"intent":"chat","reply":"<your answer in the user's language>"}
Rules: hour is 24-hour; day_offset 0=today, 1=tomorrow; repeat_weekday is MON..SUN or null. "wake me up"/"utha dena" is a reminder whose text contains "wake". Plain questions and conversation use chat with a short helpful reply in the same language as the user. Never claim an action was done.
Examples:
"kal subah 6 baje utha dena" -> {"intent":"reminder","text":"wake up","day_offset":1,"hour":6,"minute":0,"repeat_weekday":null}
"open WhatsApp" -> {"intent":"open_app","app":"whatsapp"}
"mujhe batao ki aaj weather kaisa hai" -> {"intent":"search","query":"today weather"}
"मम्मी को फोन करो" -> {"intent":"call","contact":"मम्मी"}
"what is photosynthesis" -> {"intent":"chat","reply":"Photosynthesis is how plants turn sunlight, water and carbon dioxide into sugar and oxygen."}"""

    private val termuxDetail = Regex("""\n\{"intent":"termux"[^\n]*\n""")
    private val deviceWords = Regex("termux|torch|flash|volume|brightness|location|wifi|wi-fi|camera|photo|clipboard|sms|storage|memory|ram|uptime|python|script|speak|notification|battery|command|shell", RegexOption.IGNORE_CASE)
    /** The long list of phone-control examples is sent only when the message looks like device control; it makes the prompt a lot shorter for everything else. */
    val SHORT: String = termuxDetail.replace(SYSTEM, "\n{\"intent\":\"termux\",\"command\":\"<one command line>\"}  (phone control and tools)\n")
    fun systemFor(userText: String): String = if (deviceWords.containsMatchIn(userText)) SYSTEM else SHORT

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
            "search" -> s("query").takeIf { it.isNotEmpty() }?.let { Intent.Search(it, s("mode").lowercase().takeIf { m -> m in setOf("quick", "deep", "academic") } ?: "quick") }
            "calendar" -> Intent.Calendar(o.optInt("days", 1).coerceIn(1, 14))
            "spending" -> Intent.Spending(s("period").lowercase().takeIf { it in setOf("today", "week", "month", "year") } ?: "month")
            "routine" -> s("name").takeIf { it.isNotEmpty() }?.let { Intent.RunRoutine(it) }
            "event" -> {
                val day = o.optInt("day", 0); val monthly = o.optBoolean("monthly", false); val month = if (o.isNull("month")) null else o.optInt("month", 0)
                val kind = s("kind").lowercase().takeIf { it in setOf("birthday", "anniversary", "bill") }
                if (kind == null || s("title").isEmpty() || day !in 1..31 || (!monthly && month !in 1..12)) null
                else Intent.SetEvent(kind, s("title"), if (monthly) null else month, day, monthly, "en")
            }
            "time" -> Intent.AskTime
            "battery" -> Intent.AskBattery
            "tasks" -> Intent.ListTasks
            "email" -> Intent.ReadEmail
            "remember" -> s("text").takeIf { it.isNotEmpty() }?.let { Intent.Remember(it) }
            "recall" -> s("query").takeIf { it.isNotEmpty() }?.let { Intent.Recall(it) }
            "whatsapp_draft" -> s("text").takeIf { it.isNotEmpty() }?.let { Intent.WhatsAppDraft(it) }
            "notifications" -> Intent.ReadNotifications(o.optBoolean("whatsapp_only", false))
            "termux" -> s("command").takeIf { it.isNotEmpty() }?.let { Intent.Termux(it) }
            "chat" -> Intent.Chat("")
            else -> null
        } ?: return null
        return Plan(intent, s("reply").takeIf { it.isNotEmpty() })
    }
}

/** Pulls the chat answer out of the planner's JSON while it is still being written, so streamed words can be shown at once. */
object PlanStream {
    private val chatIntent = Regex("\"intent\"\\s*:\\s*\"chat\"")
    private val replyKey = Regex("\"reply\"\\s*:\\s*\"")

    /** The decoded answer so far, or null when this is not a chat answer yet (an action plan is never shown as text). */
    fun partialReply(acc: String): String? {
        if (!chatIntent.containsMatchIn(acc)) return null
        val m = replyKey.find(acc) ?: return null
        val sb = StringBuilder(); var i = m.range.last + 1
        while (i < acc.length) {
            val c = acc[i]
            when {
                c == '"' -> break
                c == '\\' -> {
                    if (i + 1 >= acc.length) break
                    when (val n = acc[i + 1]) {
                        'n' -> sb.append('\n'); 't' -> sb.append('\t'); '"' -> sb.append('"'); '\\' -> sb.append('\\'); '/' -> sb.append('/')
                        'u' -> { if (i + 5 < acc.length + 0 && i + 6 <= acc.length) { sb.append(acc.substring(i + 2, i + 6).toIntOrNull(16)?.toChar() ?: '?'); i += 4 } else return sb.toString().ifEmpty { null } }
                        else -> sb.append(n)
                    }
                    i += 2; continue
                }
                else -> sb.append(c)
            }
            i++
        }
        return sb.toString().ifEmpty { null }
    }
}
