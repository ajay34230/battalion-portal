package com.vision.assistant.memory

import kotlin.math.ln
import kotlin.math.sqrt

/** Classic BM25 keyword scoring over the same tokens as [SemanticIndex] (so synonyms and Hinglish spellings are shared). */
object Bm25 {
    fun scores(query: String, docs: List<String>, k1: Double = 1.5, b: Double = 0.75): DoubleArray {
        if (docs.isEmpty()) return DoubleArray(0)
        val toks = docs.map { SemanticIndex.tokens(it) }
        val avg = toks.map { it.size }.average().let { if (it.isNaN() || it <= 0.0) 1.0 else it }
        val n = docs.size
        val df = HashMap<String, Int>(); toks.forEach { t -> t.toSet().forEach { df.merge(it, 1, Int::plus) } }
        val q = SemanticIndex.tokens(query).toSet()
        return DoubleArray(n) { i ->
            val t = toks[i]; val tf = t.groupingBy { it }.eachCount(); var s = 0.0
            for (w in q) {
                val f = tf[w] ?: continue; val d = df[w] ?: 0
                s += ln(1 + (n - d + 0.5) / (d + 0.5)) * f * (k1 + 1) / (f + k1 * (1 - b + b * t.size / avg))
            }
            s
        }
    }
}

/**
 * Hybrid memory search: BM25 + the fuzzy TF-IDF index, plus neural embeddings when an [Embedder] is supplied.
 * No neural embedding model is bundled or downloaded yet, so [embedder] is null in the app today and the result is keyword + fuzzy only.
 * Importance (1..5) and confidence (0..1) scale the score, so pinned facts rank higher and facts that were contradicted rank lower.
 */
object HybridRank {
    data class Doc(val text: String, val importance: Int = 3, val confidence: Double = 1.0)
    fun interface Embedder { fun embed(text: String): FloatArray? }

    private fun cosine(a: FloatArray, b: FloatArray): Double {
        if (a.size != b.size || a.isEmpty()) return 0.0
        var d = 0.0; var na = 0.0; var nb = 0.0
        for (i in a.indices) { d += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i] }
        return if (na == 0.0 || nb == 0.0) 0.0 else (d / (sqrt(na) * sqrt(nb))).coerceIn(0.0, 1.0)
    }

    fun rank(query: String, docs: List<Doc>, topK: Int = 5, minScore: Double = 0.1, embedder: Embedder? = null): List<Pair<Int, Double>> {
        if (docs.isEmpty()) return emptyList()
        val texts = docs.map { it.text }
        val tfidf = DoubleArray(docs.size).also { a -> SemanticIndex.rank(query, texts, docs.size, 0.0).forEach { (i, s) -> a[i] = s } }
        val bm = Bm25.scores(query, texts).let { a -> val m = a.maxOrNull() ?: 0.0; if (m > 0) DoubleArray(a.size) { a[it] / m } else a }
        val qv = embedder?.embed(query)
        return docs.indices.map { i ->
            val base = if (qv != null) {
                val dv = embedder.embed(texts[i]); if (dv != null) 0.4 * cosine(qv, dv) + 0.3 * bm[i] + 0.3 * tfidf[i] else 0.5 * bm[i] + 0.5 * tfidf[i]
            } else 0.5 * bm[i] + 0.5 * tfidf[i]
            i to base * (0.7 + 0.06 * docs[i].importance.coerceIn(1, 5)) * docs[i].confidence.coerceIn(0.1, 1.0)
        }.filter { it.second >= minScore }.sortedByDescending { it.second }.take(topK)
    }
}
