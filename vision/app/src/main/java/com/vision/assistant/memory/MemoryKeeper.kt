package com.vision.assistant.memory

import com.vision.assistant.data.MemoryDao
import com.vision.assistant.data.MemoryItem

/**
 * Decides what to do with something new the user asks VISION to remember: skip a duplicate, flag a contradiction, or add it.
 * Contradictions are only detected for simple statements ("my mother is Sita", "I live in Pune"); anything else is stored as-is.
 * Nothing is overwritten or deleted automatically: a contradicted older fact just has its confidence lowered, and the user can edit or forget either.
 */
object MemoryKeeper {
    sealed interface Decision {
        data object Add : Decision
        data class Duplicate(val existing: MemoryItem) : Decision
        data class Conflict(val existing: MemoryItem) : Decision
    }
    data class Fact(val key: String, val value: String)

    private val filler = setOf("my", "is", "are", "the", "a", "an", "mera", "meri", "mere", "hai", "hain", "ka", "ki", "ke")
    private fun content(t: String) = SemanticIndex.tokens(t).filter { it !in filler }
    private fun norm(t: String) = content(t).joinToString(" ")

    fun fact(text: String): Fact? {
        val s = text.trim().trimEnd('.', '!', '।')
        Regex("^(?:my|mera|meri|mere)\\s+([\\p{L} ]{2,30}?)\\s+(?:is|are|hai|hain)\\s+(.+)$", RegexOption.IGNORE_CASE).find(s)?.let {
            return Fact(SemanticIndex.tokens(it.groupValues[1]).joinToString(" "), norm(it.groupValues[2]))
        }
        Regex("^i\\s+(live in|stay in|work at|work in|am from)\\s+(.+)$", RegexOption.IGNORE_CASE).find(s)?.let {
            return Fact(SemanticIndex.tokens(it.groupValues[1]).joinToString(" "), norm(it.groupValues[2]))
        }
        return null
    }

    fun decide(text: String, existing: List<MemoryItem>): Decision {
        val n = norm(text); val tokens = content(text).toSet()
        if (n.isBlank()) return Decision.Add
        for (e in existing) {
            val et = content(e.content).toSet()
            val union = (tokens + et).size
            if (norm(e.content) == n || (union > 0 && tokens.intersect(et).size.toDouble() / union >= 0.85)) return Decision.Duplicate(e)
        }
        val f = fact(text) ?: return Decision.Add
        existing.firstOrNull { e -> fact(e.content)?.let { it.key == f.key && it.value != f.value } == true }?.let { return Decision.Conflict(it) }
        return Decision.Add
    }

    /** Applies [decide] and performs the write. Returns what happened so callers can tell the user. */
    suspend fun save(dao: MemoryDao, text: String, category: String = "note", now: Long = System.currentTimeMillis()): Decision {
        val d = decide(text, dao.list().filter { it.category != "creator" })
        when (d) {
            is Decision.Duplicate -> Unit
            is Decision.Conflict -> { dao.update(d.existing.copy(confidence = 0.3, updatedAt = now)); dao.insert(MemoryItem(category = category, content = text.trim(), createdAt = now, updatedAt = now)) }
            Decision.Add -> dao.insert(MemoryItem(category = category, content = text.trim(), createdAt = now, updatedAt = now))
        }
        return d
    }
}
