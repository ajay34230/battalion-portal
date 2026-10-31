package com.vision.assistant

import com.vision.assistant.diag.DiagEntry
import com.vision.assistant.diag.DiagFormat
import org.junit.Assert.*
import org.junit.Test

class DiagLogTest {
    @Test fun roundTripKeepsFieldsAndOneLine() {
        val e = DiagEntry(1000, "chat", "42 chars\nsecond line", 8400, "Qwen3 0.6B", "CPU")
        val line = DiagFormat.encode(e); assertFalse(line.contains('\n'))
        val d = DiagFormat.decode(line)!!; assertEquals("chat", d.kind); assertEquals(8400, d.ms); assertEquals("Qwen3 0.6B", d.model); assertEquals("CPU", d.backend)
        assertNull(DiagFormat.decode("not json"))
    }
    @Test fun summaryGroupsByKindModelBackendAndShowsAverageAndSlowest() {
        val es = listOf(DiagEntry(1, "chat", "", 4000, "A", "CPU"), DiagEntry(2, "chat", "", 8000, "A", "CPU"), DiagEntry(3, "plan", "", 3000, "A", "CPU"), DiagEntry(4, "crash", "x"))
        val s = DiagFormat.summarise(es)
        assertEquals(2, s.size); assertTrue(s.any { it.startsWith("chat · A · CPU: 2 run(s), avg 6.0 s, slowest 8.0 s") })
    }
    @Test fun problemsListNewestFirstAndLongMessagesAreCut() {
        val es = listOf(DiagEntry(1, "error", "old"), DiagEntry(2, "chat", "", 1), DiagEntry(3, "crash", "new"))
        assertEquals(listOf("crash", "error"), DiagFormat.problems(es).map { it.kind })
        assertTrue(DiagFormat.encode(DiagEntry(1, "error", "x".repeat(1000))).length < 600)
    }
}
