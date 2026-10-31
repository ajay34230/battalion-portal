package com.vision.assistant

import com.vision.assistant.ai.Intent
import com.vision.assistant.ai.IntentParser
import com.vision.assistant.memory.SemanticIndex
import com.vision.assistant.tasks.Routines
import java.util.Calendar
import org.junit.Assert.*
import org.junit.Test

class RoutineSearchTest {
    private fun cal(y: Int, m: Int, d: Int, h: Int, mi: Int) = Calendar.getInstance().apply { clear(); set(y, m - 1, d, h, mi, 0) }
    @Test fun nextTriggerSameDayIfLater() {
        val t = Routines.nextTrigger(8 * 60, 0b1111111, cal(2026, 11, 2, 7, 0))!! // Mon 2 Nov 2026 07:00
        assertEquals(8, Calendar.getInstance().apply { timeInMillis = t }.get(Calendar.HOUR_OF_DAY)); assertEquals(2, Calendar.getInstance().apply { timeInMillis = t }.get(Calendar.DAY_OF_MONTH))
    }
    @Test fun nextTriggerSkipsExcludedDays() {
        val mondayOnly = 0b0000001
        val t = Routines.nextTrigger(8 * 60, mondayOnly, cal(2026, 11, 2, 9, 0))!! // after Monday's time -> next Monday
        assertEquals(9, Calendar.getInstance().apply { timeInMillis = t }.get(Calendar.DAY_OF_MONTH))
    }
    @Test fun noDaysMeansNoTrigger() = assertNull(Routines.nextTrigger(480, 0, cal(2026, 11, 2, 7, 0)))

    @Test fun rankingFindsSynonymsAndSpellingVariants() {
        val docs = listOf("User's mother is Sunita", "User likes cricket", "Electricity bill is due on the 5th")
        assertEquals(0, SemanticIndex.rank("mummy", docs).first().first)
        assertEquals(2, SemanticIndex.rank("bijli ka bill", docs).first().first)
        assertEquals(1, SemanticIndex.rank("kya pasand hai cricket", docs).first().first)
        assertTrue(SemanticIndex.rank("zzzz qqqq", docs).isEmpty())
    }
    @Test fun parsesRoutineAndCalendarIntents() {
        assertEquals("good morning", (IntentParser.parse("run good morning routine") as Intent.RunRoutine).name)
        assertEquals("good morning", (IntentParser.parse("good morning routine chalao") as Intent.RunRoutine).name)
        assertEquals(1, (IntentParser.parse("what's on my calendar") as Intent.Calendar).days)
        assertEquals(7, (IntentParser.parse("my schedule this week") as Intent.Calendar).days)
    }
}
