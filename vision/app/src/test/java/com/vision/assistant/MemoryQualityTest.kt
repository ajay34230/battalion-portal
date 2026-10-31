package com.vision.assistant

import com.vision.assistant.data.MemoryItem
import com.vision.assistant.memory.*
import com.vision.assistant.memory.MemoryKeeper.Decision
import org.junit.Assert.*
import org.junit.Test

class MemoryQualityTest {
    private fun m(t: String, id: Long = 1) = MemoryItem(id = id, category = "note", content = t)

    @Test fun bm25PrefersTheDocumentWithTheRareMatchingWord() {
        val docs = listOf("I like tea in the morning", "Electricity bill is due on 5th", "Cricket is my favourite game")
        val s = Bm25.scores("electricity bill", docs)
        assertTrue(s[1] > s[0] && s[1] > s[2])
    }
    @Test fun hybridFindsSynonymsAndSpellings() {
        val docs = listOf("Mummy ka birthday 12 March", "Office wifi password is in the drawer", "Doctor appointment on Monday").map { HybridRank.Doc(it) }
        val r = HybridRank.rank("mother's bday", docs, 3, 0.05)
        assertEquals(0, r.first().first)
    }
    @Test fun importanceAndConfidenceChangeOrder() {
        val a = HybridRank.Doc("Pune flat rent is 20000", importance = 1); val b = HybridRank.Doc("Pune flat rent is 22000", importance = 5)
        assertEquals(1, HybridRank.rank("pune rent", listOf(a, b), 2, 0.0).first().first)
        val old = HybridRank.Doc("Pune flat rent is 20000", confidence = 0.3); val cur = HybridRank.Doc("Pune flat rent is 22000")
        assertEquals(1, HybridRank.rank("pune rent", listOf(old, cur), 2, 0.0).first().first)
    }
    @Test fun usesEmbedderWhenGiven() {
        val docs = listOf("alpha beta", "gamma delta").map { HybridRank.Doc(it) }
        val emb = HybridRank.Embedder { t -> if (t.contains("zzz") || t.contains("gamma")) floatArrayOf(1f, 0f) else floatArrayOf(0f, 1f) }
        assertEquals(1, HybridRank.rank("zzz", docs, 2, 0.0, emb).first().first)
    }
    @Test fun detectsDuplicates() {
        assertTrue(MemoryKeeper.decide("My mother is Sita", listOf(m("mother is Sita"))) is Decision.Duplicate)
        assertTrue(MemoryKeeper.decide("I like cricket", listOf(m("I love cricket!"))) is Decision.Duplicate)
        assertTrue(MemoryKeeper.decide("buy milk", listOf(m("Doctor on Monday"))) is Decision.Add)
    }
    @Test fun detectsContradictionsButNotUnrelatedFacts() {
        assertTrue(MemoryKeeper.decide("My mom is Geeta", listOf(m("my mother is Sita"))) is Decision.Conflict)
        assertTrue(MemoryKeeper.decide("I live in Delhi", listOf(m("I stay in Pune"))) is Decision.Conflict)
        assertTrue(MemoryKeeper.decide("My father is Ram", listOf(m("my mother is Sita"))) is Decision.Add)
        assertTrue(MemoryKeeper.decide("I live in Pune", listOf(m("I live in Pune"))) is Decision.Duplicate)
    }
    @Test fun factExtraction() {
        assertEquals("pune", MemoryKeeper.fact("I live in Pune.")!!.value); assertNull(MemoryKeeper.fact("remember the milk"))
    }
}
