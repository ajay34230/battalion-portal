package com.vision.assistant.memory

import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Offline fuzzy-meaning search over your memory. NOT a neural embedding model: it combines
 * (1) word and character-trigram TF-IDF, which tolerates spelling differences and Hinglish spellings, and
 * (2) small synonym groups (mom/mummy/maa/mother ...). It needs no download and works in Hindi, English and Hinglish.
 */
object SemanticIndex {
    private val groups = listOf(
        setOf("mom", "mummy", "maa", "mother", "mata", "mommy", "मां", "माँ", "मम्मी"),
        setOf("dad", "papa", "father", "pita", "पापा", "पिता"),
        setOf("wife", "biwi", "patni", "पत्नी", "बीवी"),
        setOf("husband", "pati", "पति"),
        setOf("brother", "bhai", "भाई"), setOf("sister", "behen", "behan", "बहन"),
        setOf("birthday", "janamdin", "bday", "जन्मदिन"), setOf("anniversary", "saalgirah", "सालगिरह"),
        setOf("bill", "bijli", "electricity", "बिल", "बिजली"), setOf("friend", "dost", "mitra", "दोस्त"),
        setOf("live", "lives", "rahta", "stay", "stays", "home", "ghar", "घर"),
        setOf("like", "likes", "love", "loves", "pasand", "पसंद"), setOf("name", "naam", "नाम"),
        setOf("work", "job", "kaam", "office", "naukri", "नौकरी"), setOf("medicine", "dawai", "dawa", "दवा", "दवाई"),
        setOf("phone", "mobile", "number", "नंबर"), setOf("money", "paisa", "paise", "rupees", "पैसे"),
    )
    private val canon: Map<String, String> = groups.flatMap { g -> g.map { w -> w to g.first() } }.toMap()

    fun tokens(text: String): List<String> =
        Regex("[\\p{L}\\p{N}]+").findAll(text.lowercase()).map { it.value }.filter { it.length > 1 }.map { canon[it] ?: it }.toList()

    fun features(text: String): Map<String, Double> {
        val out = HashMap<String, Double>()
        for (w in tokens(text)) {
            out.merge("w:$w", 1.0, Double::plus)
            val p = "^$w$"
            for (i in 0..p.length - 3) out.merge("t:${p.substring(i, i + 3)}", 0.35, Double::plus)
        }
        return out
    }

    /** Returns (index, score) for the best matches, score in 0..1, best first. Scores below [minScore] are dropped. */
    fun rank(query: String, docs: List<String>, topK: Int = 5, minScore: Double = 0.08): List<Pair<Int, Double>> {
        if (docs.isEmpty()) return emptyList()
        val feats = docs.map { features(it) }
        val df = HashMap<String, Int>(); feats.forEach { f -> f.keys.forEach { df.merge(it, 1, Int::plus) } }
        val n = docs.size.toDouble()
        fun vec(f: Map<String, Double>) = f.mapValues { (k, v) -> ln(1.0 + v) * (ln((n + 1) / ((df[k] ?: 0) + 1.0)) + 1) }
        val q = vec(features(query)); val qn = sqrt(q.values.sumOf { it * it })
        if (qn == 0.0) return emptyList()
        return feats.mapIndexed { i, f ->
            val d = vec(f); val dn = sqrt(d.values.sumOf { it * it })
            val dot = q.entries.sumOf { (k, v) -> v * (d[k] ?: 0.0) }
            i to if (dn == 0.0) 0.0 else dot / (qn * dn)
        }.filter { it.second >= minScore }.sortedByDescending { it.second }.take(topK)
    }
}
