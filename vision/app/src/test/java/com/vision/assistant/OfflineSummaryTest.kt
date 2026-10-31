package com.vision.assistant

import com.vision.assistant.core.*
import org.junit.Assert.*
import org.junit.Test

class OfflineSummaryTest {
    private fun r(id: String, s: CheckStatus) = CheckResult(id, id, s, "d", 1L, 5L)
    @Test fun headlineAdmitsWhenOnline() {
        val rs = listOf(r("a", CheckStatus.PASSED), r("b", CheckStatus.PASSED), r("c", CheckStatus.BLOCKED))
        assertTrue(OfflineSummary.headline(rs, false).contains("ONLINE")); assertTrue(OfflineSummary.headline(rs, true).startsWith("Run with no internet: 2 passed, 1 blocked"))
        assertEquals("Not run yet.", OfflineSummary.headline(emptyList(), true))
    }
    @Test fun roundTrips() {
        val rs = listOf(r("a", CheckStatus.FAILED), r("b", CheckStatus.NOT_TESTED))
        val back = OfflineSummary.decode(OfflineSummary.encode(rs, true, 99))!!
        assertEquals(rs.map { it.status }, back.first.map { it.status }); assertTrue(back.second); assertEquals(99L, back.third)
        assertNull(OfflineSummary.decode("garbage"))
    }
}
