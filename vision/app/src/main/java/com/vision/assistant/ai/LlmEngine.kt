package com.vision.assistant.ai

data class ChatTurn(val role: String, val text: String)

interface LlmEngine {
    val name: String
    /** True only when real model files are installed and verified. */
    val isNeuralModelLoaded: Boolean
    suspend fun reply(history: List<ChatTurn>, userText: String, lang: Lang): String
}

/** Always available fallback. Does not claim to be a language model. */
class RuleBasedEngine : LlmEngine {
    override val name = "Rule-based (no language model installed)"
    override val isNeuralModelLoaded = false

    override suspend fun reply(history: List<ChatTurn>, userText: String, lang: Lang): String {
        val answer = Replies.forIntent(IntentParser.parse(userText), lang)
        return answer
    }
}

object Replies {
    private fun pick(lang: Lang, en: String, hinglish: String, hi: String) = when (lang) {
        Lang.EN -> en; Lang.HINGLISH -> hinglish; Lang.HI -> hi
    }

    fun forIntent(i: Intent, lang: Lang): String = when (i) {
        is Intent.OpenApp -> pick(lang, "Opening ${i.app}.", "${i.app} khol raha hoon.", "${i.app} खोल रहा हूँ।")
        is Intent.Call -> pick(lang, "Ready to call ${i.contact}. Please confirm.", "${i.contact} ko call lagane ke liye confirm karein.", "${i.contact} को कॉल करने के लिए पुष्टि करें।")
        is Intent.SetReminder -> pick(lang, "Setting a reminder: ${i.text}.", "Reminder laga raha hoon: ${i.text}.", "रिमाइंडर लगा रहा हूँ: ${i.text}।")
        is Intent.Search -> pick(lang, "Searching for: ${i.query}.", "Dhundh raha hoon: ${i.query}.", "खोज रहा हूँ: ${i.query}।")
        is Intent.Remember -> pick(lang, "Saving that.", "Yeh save kar raha hoon.", "यह सहेज रहा हूँ।")
        is Intent.Recall -> pick(lang, "Looking through memory.", "Memory mein dekh raha hoon.", "स्मृति में देख रहा हूँ।")
        is Intent.WhatsAppDraft -> pick(lang, "Preparing a WhatsApp draft.", "WhatsApp draft bana raha hoon.", "WhatsApp ड्राफ़्ट बना रहा हूँ।")
        is Intent.ReadNotifications -> pick(lang, "Checking notifications.", "Notifications dekh raha hoon.", "सूचनाएँ देख रहा हूँ।")
        Intent.ReadEmail -> pick(lang, "Checking email.", "Email dekh raha hoon.", "ईमेल देख रहा हूँ।")
        Intent.AskTime -> pick(lang, "Checking the time.", "Time dekh raha hoon.", "समय देख रहा हूँ।")
        Intent.AskBattery -> pick(lang, "Checking the battery.", "Battery dekh raha hoon.", "बैटरी देख रहा हूँ।")
        Intent.ListTasks -> pick(lang, "Fetching your tasks.", "Aapke tasks la raha hoon.", "आपके कार्य ला रहा हूँ।")
        is Intent.Chat -> pick(
            lang,
            "I can't hold a free conversation yet: no language model is installed on this phone. I can still open apps, set reminders, call, and search.",
            "Abhi free baat-cheet nahi kar sakta: is phone mein koi language model installed nahi hai. Main apps khol sakta hoon, reminder laga sakta hoon, call aur search kar sakta hoon.",
            "अभी सामान्य बातचीत नहीं कर सकता: इस फ़ोन में कोई भाषा मॉडल नहीं है। मैं ऐप खोल सकता हूँ, रिमाइंडर लगा सकता हूँ, कॉल और खोज कर सकता हूँ।",
        )
    }
}
