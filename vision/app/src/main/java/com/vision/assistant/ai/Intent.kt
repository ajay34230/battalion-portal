package com.vision.assistant.ai

import com.vision.assistant.termux.CommandPolicy

data class ParsedTime(val dayOffset: Int, val hour: Int, val minute: Int)

sealed interface Intent {
    data class OpenApp(val app: String) : Intent
    data class SetReminder(val text: String, val time: ParsedTime?, val repeatWeekday: String?) : Intent
    data class Call(val contact: String) : Intent
    data class Search(val query: String) : Intent
    data class Remember(val text: String) : Intent
    data class Recall(val query: String) : Intent
    data class WhatsAppDraft(val text: String) : Intent
    data class ReadNotifications(val whatsappOnly: Boolean) : Intent
    data class Termux(val line: String) : Intent
    data object ReadEmail : Intent
    data object AskTime : Intent
    data object AskBattery : Intent
    data object ListTasks : Intent
    data class Chat(val text: String) : Intent
}

/** Rule-based Hindi/English/Hinglish intent parser. Used on every device and as fallback when no LLM is loaded. */
object IntentParser {
    private val wake = Regex("^\\s*(hey\\s+vision|ok\\s+vision|vision)\\s*[,:]?\\s*", RegexOption.IGNORE_CASE)
    private val stop = setOf("my", "the", "mera", "meri", "mere", "app", "ko", "please", "plz", "karo", "kar", "do", "de", "na")

    private val weekdays = mapOf(
        "monday" to "MON", "somvar" to "MON", "tuesday" to "TUE", "mangalvar" to "TUE",
        "wednesday" to "WED", "budhvar" to "WED", "thursday" to "THU", "guruvar" to "THU", "veervar" to "THU",
        "friday" to "FRI", "shukravar" to "FRI", "saturday" to "SAT", "shanivar" to "SAT",
        "sunday" to "SUN", "ravivar" to "SUN", "itwar" to "SUN",
    )

    fun parse(raw: String): Intent {
        val t = wake.replace(raw, "").trim().trimEnd('.', '!', '?', '।')
        val l = t.lowercase()
        if (l.isEmpty()) return Intent.Chat(raw)

        Regex("^(?:remember|note|yaad rakho|yaad rakhna|note kar)\\s+(?:that\\s+|ki\\s+)?(.+)$").find(l)?.let { return Intent.Remember(it.groupValues[1].trim()) }
        Regex("^(?:what do you remember about|recall|mujhe yaad dilao|kya yaad hai)\\s+(.+)$").find(l)?.let { return Intent.Recall(it.groupValues[1].trim()) }

        if (Regex("remind|reminder|yaad dila|utha dena|utha do|jaga dena|alarm|याद दिला|उठा").containsMatchIn(l)) {
            val repeat = if (Regex("every|har ").containsMatchIn(l)) weekdays.entries.firstOrNull { l.contains(it.key) }?.value else null
            return Intent.SetReminder(reminderText(t), parseTime(l), repeat)
        }
        if (Regex("what('s| is)? the time|time kya|kitne baje|abhi kitna|समय क्या|कितने बजे").containsMatchIn(l)) return Intent.AskTime
        if (Regex("battery|बैटरी").containsMatchIn(l)) return Intent.AskBattery
        if (Regex("pending tasks|my tasks|to-?do|mere kaam|tasks kya|कौन से काम").containsMatchIn(l)) return Intent.ListTasks

        Regex("^(?:send\\s+)?whatsapp(?:\\s+message)?(?:\\s+saying|\\s+ki)?\\s+(.+)$").find(l)?.let { if (!Regex("^(open|kholo)").containsMatchIn(l)) return Intent.WhatsAppDraft(it.groupValues[1].trim()) }
        if (Regex("whatsapp (messages|notifications)|whatsapp ke messages|whatsapp ke notification").containsMatchIn(l)) return Intent.ReadNotifications(true)
        if (Regex("read (my )?notifications|notifications (batao|padho|sunao)|meri notifications").containsMatchIn(l)) return Intent.ReadNotifications(false)
        if (Regex("(unread )?e-?mails?|gmail|mail (batao|padho)").containsMatchIn(l)) return Intent.ReadEmail

        Regex("^(?:termux|run in termux|run command|terminal)\\s+(.+)$", RegexOption.IGNORE_CASE).find(t)?.let { return Intent.Termux(it.groupValues[1].trim()) }
        val home = CommandPolicy.HOME
        if (Regex("torch|flash ?light|टॉर्च").containsMatchIn(l)) {
            val on = Regex("\\bon\\b|jala|chalu|start|चालू|जला").containsMatchIn(l)
            val off = Regex("\\boff\\b|band|bujha|stop|बंद|बुझा").containsMatchIn(l)
            if (on != off) return Intent.Termux("termux-torch " + if (on) "on" else "off")
        }
        if (Regex("my location|where am i|meri location|location batao|main kahan|मेरी लोकेशन").containsMatchIn(l)) return Intent.Termux("termux-location")
        if (Regex("wi-?fi (info|status|name)|which wi-?fi|kaunsa wi-?fi").containsMatchIn(l)) return Intent.Termux("termux-wifi-connectioninfo")
        if (Regex("clipboard|copied text|kya copy kiya").containsMatchIn(l)) return Intent.Termux("termux-clipboard-get")
        if (Regex("take a (photo|picture|selfie)|photo (kheench|le)|selfie").containsMatchIn(l)) return Intent.Termux("termux-camera-photo -c ${if (l.contains("selfie")) 1 else 0} $home/vision-output/photo.jpg")
        Regex("volume\\s+(\\d{1,2})").find(l)?.let { return Intent.Termux("termux-volume music ${it.groupValues[1].toInt().coerceIn(0, 15)}") }
        Regex("brightness\\s+(\\d{1,3})").find(l)?.let { return Intent.Termux("termux-brightness ${(it.groupValues[1].toInt().coerceIn(0, 100) * 255 / 100)}") }
        if (Regex("disk space|storage left|kitni storage|free space").containsMatchIn(l)) return Intent.Termux("df -h")
        if (Regex("\\bram\\b|memory usage").containsMatchIn(l)) return Intent.Termux("free -m")
        if (Regex("uptime|phone kab se on").containsMatchIn(l)) return Intent.Termux("uptime")

        Regex("^(?:call|dial|phone)\\s+(.+)$").find(l)?.let { return Intent.Call(clean(it.groupValues[1])) }
        Regex("^(.+?)\\s+(?:ko\\s+)?(?:phone|call)\\s*(?:karo|kar do|lagao|laga do)$").find(l)?.let { return Intent.Call(clean(it.groupValues[1])) }

        Regex("^(?:open|launch|start|kholo|khol do)\\s+(.+)$").find(l)?.let { return Intent.OpenApp(clean(it.groupValues[1])) }
        Regex("^(.+?)\\s+(?:kholo|khol do|open karo|khol|chalu karo|खोलो|खोल दो)$").find(l)?.let { return Intent.OpenApp(clean(it.groupValues[1])) }

        Regex("^(?:search|research|find|look up|summari[sz]e|dhundo|khojo|dhoondo)\\s+(.+)$").find(l)?.let { return Intent.Search(it.groupValues[1].trim()) }
        Regex("^(?:mujhe\\s+)?batao\\s+(?:ki\\s+)?(.+)$").find(l)?.let { return Intent.Search(it.groupValues[1].trim()) }

        return Intent.Chat(t)
    }

    private fun clean(s: String) = s.split(Regex("\\s+")).filter { it !in stop }.joinToString(" ").trim()

    fun parseTime(l: String): ParsedTime? {
        val m = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm|baje|बजे)?").find(l) ?: return null
        var hour = m.groupValues[1].toInt()
        val minute = m.groupValues[2].ifEmpty { "0" }.toInt()
        if (hour > 23 || minute > 59) return null
        val mark = m.groupValues[3]
        val pmWord = Regex("pm|shaam|evening|raat|night|dopahar|afternoon").containsMatchIn(l)
        val amWord = Regex("am|subah|morning|सुबह").containsMatchIn(l)
        if (mark == "pm" || (mark != "am" && pmWord && !amWord)) { if (hour < 12) hour += 12 }
        if ((mark == "am" || amWord) && hour == 12) hour = 0
        val day = when {
            Regex("day after tomorrow|parso").containsMatchIn(l) -> 2
            Regex("tomorrow|kal|कल").containsMatchIn(l) -> 1
            else -> 0
        }
        return ParsedTime(day, hour, minute)
    }

    private fun reminderText(t: String): String {
        val m = Regex("(?:to|ki|ke liye|कि)\\s+(.+)$", RegexOption.IGNORE_CASE).find(t)
        return (m?.groupValues?.get(1) ?: t).trim()
    }
}
