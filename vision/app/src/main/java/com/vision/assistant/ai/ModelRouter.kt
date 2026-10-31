package com.vision.assistant.ai

import kotlin.math.ln

enum class TaskKind { CHAT, CODE, HINDI }
enum class SpeedPref(val label: String) { BALANCED("Balanced"), SPEED("Prefer speed"), QUALITY("Prefer quality") }

/** A cheap guess about what a message needs, used only to pick the best installed model. It never decides what an action does. */
object TaskClassifier {
    private val code = Regex("```|\\b(def|class|function|import|println|console\\.log|kotlin|python|javascript|html|css|sql|stack ?trace|exception|compile|bug|script)\\b", RegexOption.IGNORE_CASE)
    fun classify(text: String): TaskKind {
        val letters = text.count { it.isLetter() }
        val deva = text.count { it in 'ऀ'..'ॿ' }
        return when {
            code.containsMatchIn(text) -> TaskKind.CODE
            letters > 0 && deva * 100 / letters >= 30 -> TaskKind.HINDI
            else -> TaskKind.CHAT
        }
    }
    /** Short, plain messages: a smaller model is enough when the owner prefers speed. */
    fun isSimple(text: String) = text.trim().split(Regex("\\s+")).size <= 8 && classify(text) == TaskKind.CHAT
}

/**
 * Chooses among the installed, usable models. Rules (all explainable, none learned):
 *  1. A model the owner picked in Settings always wins.
 *  2. Otherwise score = size (a proxy for ability) + a bonus when the model suits the task (coding model for code, Hindi-capable for Hindi).
 *  3. Speed mode on a simple message prefers a small model; quality mode prefers the largest.
 *  4. The already loaded model is kept unless another scores clearly better, because loading a model takes seconds and memory.
 */
object ModelRouter {
    data class Candidate(val id: String, val sizeMb: Long, val experimental: Boolean, val languages: String, val categories: List<String>)
    data class Choice(val id: String, val reason: String)

    private const val SWITCH_MARGIN = 8.0

    private fun score(c: Candidate, task: TaskKind, speed: SpeedPref, simple: Boolean): Double {
        val size = ln(c.sizeMb.coerceAtLeast(1).toDouble()) * 10
        var s = if (speed == SpeedPref.SPEED && simple) -size else size
        val weakHindi = c.languages.contains("weak", true) || c.languages.contains("mainly English", true)
        if (task == TaskKind.CODE && c.categories.contains("Programming")) s += 25
        if (task == TaskKind.HINDI) s += if (weakHindi) -20 else if (c.languages.contains("Hindi", true)) 15 else 0
        if (weakHindi && speed != SpeedPref.QUALITY && task == TaskKind.CHAT && !simple) s -= 5
        return s
    }

    fun choose(task: TaskKind, candidates: List<Candidate>, preferredId: String, loadedId: String?, speed: SpeedPref, simple: Boolean): Choice? {
        if (candidates.isEmpty()) return null
        if (preferredId != "auto") candidates.firstOrNull { it.id == preferredId }?.let { return Choice(it.id, "your choice in Settings") }
        val pool = candidates.filter { !it.experimental }.ifEmpty { candidates }
        val best = pool.maxByOrNull { score(it, task, speed, simple) }!!
        val loaded = pool.firstOrNull { it.id == loadedId }
        if (loaded != null && loaded.id != best.id && speed != SpeedPref.QUALITY && score(best, task, speed, simple) - score(loaded, task, speed, simple) < SWITCH_MARGIN) return Choice(loaded.id, "already loaded")
        val why = when {
            task == TaskKind.CODE && best.categories.contains("Programming") -> "best installed model for code"
            task == TaskKind.HINDI && !best.languages.contains("weak", true) -> "best installed model for Hindi"
            speed == SpeedPref.SPEED && simple -> "smallest model, for a quick answer"
            else -> "strongest installed model that fits"
        }
        return Choice(best.id, why)
    }
}
