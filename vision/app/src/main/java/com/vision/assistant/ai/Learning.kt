package com.vision.assistant.ai

import com.vision.assistant.data.MemoryDao
import com.vision.assistant.data.MemoryItem

/** A fact VISION picked up from what the user said. */
data class Learned(val category: String, val content: String)

/**
 * Learns from what the user tells it. Rule-based on purpose: transparent, instant, offline, and every learned
 * line is visible and deletable on the Memory screen. Only explicit first-person statements are stored.
 */
object Learning {
    @Volatile var profile: String = ""
        private set
    @Volatile var userName: String = ""
        private set

    private val rules: List<Triple<Regex, String, (MatchResult) -> String>> = listOf(
        Triple(Regex("\\b(?:my name is|i am called|i'm called|mera naam|mera nam)\\s+([\\p{L}]+)(?:\\s(?!hai\\b)([\\p{L}]+))?", RegexOption.IGNORE_CASE), "fact") { "User's name is ${cap((it.groupValues[1] + " " + it.groupValues[2]).trim())}" },
        Triple(Regex("\\b(?:i live in|i stay in|i am from|i'm from|main\\s+([\\p{L}]+)\\s+(?:mein|me)\\s+rahta)\\s*([\\p{L}]+(?:\\s[\\p{L}]+)?)?", RegexOption.IGNORE_CASE), "fact") { m -> "User lives in ${cap((m.groupValues[1].ifBlank { m.groupValues[2] }).trim())}" },
        Triple(Regex("\\b(?:i work as|i work at)\\s+([a-z ]{3,30}?)(?:\\.|,|$| and )", RegexOption.IGNORE_CASE), "fact") { "User works ${it.groupValues[0].trim().lowercase().replace("i work ", "")}" },
        Triple(Regex("\\b(?:i (?:really )?(?:like|love|prefer|enjoy)|mujhe\\s+(.{2,30}?)\\s+pasand)\\s*([a-z ]{2,40})?", RegexOption.IGNORE_CASE), "preference") { m -> "User likes ${(m.groupValues[1].ifBlank { m.groupValues[2] }).trim()}" },
        Triple(Regex("\\b(?:i (?:hate|dislike|don't like|do not like)|mujhe\\s+(.{2,30}?)\\s+pasand nahi)\\s*([a-z ]{2,40})?", RegexOption.IGNORE_CASE), "preference") { m -> "User dislikes ${(m.groupValues[1].ifBlank { m.groupValues[2] }).trim()}" },
        Triple(Regex("\\bmy (wife|husband|mother|mom|father|dad|brother|sister|son|daughter|friend|boss)(?:'s)? (?:name )?is ([\\p{L}]+)", RegexOption.IGNORE_CASE), "person") { "User's ${it.groupValues[1].lowercase()} is ${cap(it.groupValues[2])}" },
        Triple(Regex("\\bmeri (maa|mummy|behen|biwi|patni|beti)\\s+ka naam\\s+([\\p{L}]+)", RegexOption.IGNORE_CASE), "person") { "User's ${it.groupValues[1].lowercase()} is ${cap(it.groupValues[2])}" },
        Triple(Regex("\\bmere (papa|pita|bhai|pati|beta|dost)\\s+ka naam\\s+([\\p{L}]+)", RegexOption.IGNORE_CASE), "person") { "User's ${it.groupValues[1].lowercase()} is ${cap(it.groupValues[2])}" },
    )

    private fun cap(s: String) = s.trim().split(' ').joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

    fun extract(text: String): List<Learned> {
        val t = text.trim()
        if (t.length < 6 || t.endsWith("?")) return emptyList()
        return rules.mapNotNull { (re, cat, make) ->
            re.find(t)?.let { m -> make(m).trim().takeIf { it.length in 8..90 && !it.endsWith("likes") && !it.endsWith("dislikes") }?.let { Learned(cat, it) } }
        }
    }

    /** Stores new facts (skipping duplicates) and refreshes the profile used in prompts. Returns what was newly learned. */
    suspend fun learn(dao: MemoryDao, text: String): List<Learned> {
        val existing = dao.list().map { it.content.lowercase() }.toSet()
        val fresh = extract(text).filter { it.content.lowercase() !in existing }
        for (f in fresh) dao.insert(MemoryItem(category = f.category, content = f.content, source = "learned"))
        refresh(dao)
        return fresh
    }

    suspend fun refresh(dao: MemoryDao) {
        val items = dao.list().filter { it.category in setOf("fact", "preference", "person") }.take(25)
        profile = items.joinToString("; ") { it.content }
        userName = items.firstOrNull { it.content.startsWith("User's name is ") }?.content?.removePrefix("User's name is ")?.trim().orEmpty()
    }
}
