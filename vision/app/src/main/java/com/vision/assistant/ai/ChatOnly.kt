package com.vision.assistant.ai

/**
 * Recognises plain small talk and general "explain" questions so they can skip the long planner prompt (about 7 KB, which a phone CPU
 * must read before it can answer; measured 16-20 s on the 0.6B model). Anything that could be an action, a lookup or time-sensitive is
 * NOT matched and still goes through the planner.
 */
object ChatOnly {
    private val smallTalk = Regex(
        "^(hi+|hello+|hey+|hii+|namaste|namaskar|good (morning|afternoon|evening|night)|how are you|how r u|how do you do|what'?s up|sup|kaise ho|kese ho|kya haal hai|kaisa hai|thanks?( you)?|thank you( so much)?|shukriya|dhanyavaad|ok(ay)?|cool|nice|great|bye|good ?bye|who are you|what are you|what is your name|what's your name|tum kaun ho|aap kaun ho|tumhara naam kya hai|aapka naam kya hai)( vision)?[ !?.]*$",
        RegexOption.IGNORE_CASE,
    )
    private val explain = Regex("^(explain|why|how does|how do|what is the difference|define|meaning of|tell me a (joke|story)|samjhao|kyun)\\b", RegexOption.IGNORE_CASE)
    private val needsLookupOrAction = Regex(
        "\\b(today|tomorrow|tonight|now|latest|news|weather|score|price|stock|near me|nearby|open|call|remind|alarm|timer|send|email|mail|whatsapp|message|calendar|schedule|search|google|play|set|turn|note|remember|save|delete|run|install|photo|pdf|image|my |mera|meri|mere)\\b",
        RegexOption.IGNORE_CASE,
    )

    fun matches(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty() || t.length > 120) return false
        if (smallTalk.matches(t)) return true
        return t.split(Regex("\\s+")).size <= 14 && explain.containsMatchIn(t) && !needsLookupOrAction.containsMatchIn(t)
    }
}
