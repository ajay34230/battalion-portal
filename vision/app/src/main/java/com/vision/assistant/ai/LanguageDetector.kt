package com.vision.assistant.ai

enum class Lang(val code: String) { EN("en"), HI("hi"), HINGLISH("hinglish") }

/** Script + lexicon based detector for English / Hindi (Devanagari) / Hinglish (romanised Hindi). */
object LanguageDetector {
    private val hinglishWords = setOf(
        "hai", "hain", "kya", "kal", "mujhe", "batao", "bata", "karo", "kar", "kholo", "khol", "laga", "subah",
        "baje", "aaj", "nahi", "nahin", "mere", "meri", "mera", "ghar", "dena", "dila", "yaad", "chal", "sake",
        "jo", "ki", "ka", "ke", "ko", "par", "mein", "se", "kaisa", "kaise", "kitne", "raat", "shaam", "utha",
        "jaga", "dhundo", "khojo", "karne", "karna", "chahiye", "abhi", "phir", "bhi", "aur", "toh", "wala",
    )

    fun detect(text: String): Lang {
        val devanagari = text.count { it.code in 0x0900..0x097F }
        val letters = text.count { it.isLetter() }
        if (letters == 0) return Lang.EN
        val words = text.lowercase().split(Regex("[^a-z]+")).filter { it.isNotEmpty() }
        val hits = words.count { it in hinglishWords }
        return when {
            devanagari * 2 >= letters -> Lang.HI
            devanagari > 0 -> Lang.HINGLISH
            hits >= 2 -> Lang.HINGLISH
            else -> Lang.EN
        }
    }
}
