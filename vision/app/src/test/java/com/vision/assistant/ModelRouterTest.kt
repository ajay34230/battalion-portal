package com.vision.assistant

import com.vision.assistant.ai.*
import com.vision.assistant.ai.ModelRouter.Candidate
import org.junit.Assert.*
import org.junit.Test

class ModelRouterTest {
    private val small = Candidate("small", 500, false, "mainly English; weak Hindi", listOf("General conversation"))
    private val mid = Candidate("mid", 1750, false, "multilingual (incl. Hindi)", listOf("General conversation"))
    private val big = Candidate("big", 2750, false, "multilingual (incl. Hindi)", listOf("General conversation"))
    private val coder = Candidate("coder", 1500, false, "multilingual", listOf("General conversation", "Programming"))
    private val huge = Candidate("huge", 6900, true, "multilingual", listOf("General conversation"))
    private fun pick(t: TaskKind, c: List<Candidate>, pref: String = "auto", loaded: String? = null, s: SpeedPref = SpeedPref.BALANCED, simple: Boolean = false) = ModelRouter.choose(t, c, pref, loaded, s, simple)!!

    @Test fun classifiesMessages() {
        assertEquals(TaskKind.CODE, TaskClassifier.classify("fix this python function"))
        assertEquals(TaskKind.HINDI, TaskClassifier.classify("मुझे कल सुबह उठाओ और बताओ"))
        assertEquals(TaskKind.CHAT, TaskClassifier.classify("what is photosynthesis"))
        assertTrue(TaskClassifier.isSimple("hi how are you")); assertFalse(TaskClassifier.isSimple("explain in detail how the immune system fights viral infections in the body"))
    }
    @Test fun ownerChoiceAlwaysWins() { assertEquals("small", pick(TaskKind.CODE, listOf(small, big, coder), pref = "small").id) }
    @Test fun defaultsToStrongestSafeModelAndSkipsExperimental() {
        assertEquals("big", pick(TaskKind.CHAT, listOf(small, mid, big, huge)).id)
        assertEquals("huge", pick(TaskKind.CHAT, listOf(huge)).id)
    }
    @Test fun codeUsesCodingModelAndHindiAvoidsWeakHindi() {
        assertEquals("coder", pick(TaskKind.CODE, listOf(mid, coder)).id)
        assertEquals("mid", pick(TaskKind.HINDI, listOf(small, mid)).id)
    }
    @Test fun keepsLoadedModelUnlessClearlyBetter() {
        assertEquals("mid", pick(TaskKind.CHAT, listOf(mid, big), loaded = "mid").id)           // small gain: stay
        assertEquals("coder", pick(TaskKind.CODE, listOf(mid, coder), loaded = "mid").id)        // big gain for code: switch
        assertEquals("big", pick(TaskKind.CHAT, listOf(mid, big), loaded = "mid", s = SpeedPref.QUALITY).id)
    }
    @Test fun speedModePicksSmallForSimpleOnly() {
        assertEquals("mid", pick(TaskKind.CHAT, listOf(mid, big), s = SpeedPref.SPEED, simple = true).id)
        assertEquals("big", pick(TaskKind.CHAT, listOf(mid, big), s = SpeedPref.SPEED, simple = false).id)
    }
    @Test fun balancedColdStartPicksSmallForShortMessages() { assertEquals("small", pick(TaskKind.CHAT, listOf(small, big), simple = true).id) }
    @Test fun balancedShortMessageKeepsLoadedModel() { assertEquals("big", pick(TaskKind.CHAT, listOf(small, big), loaded = "big", simple = true).id) }
    @Test fun balancedLongMessageStillGetsStrongModel() { assertEquals("big", pick(TaskKind.CHAT, listOf(small, big), loaded = "small", simple = false).id) }
    @Test fun emptyGivesNull() { assertNull(ModelRouter.choose(TaskKind.CHAT, emptyList(), "auto", null, SpeedPref.BALANCED, false)) }
}
