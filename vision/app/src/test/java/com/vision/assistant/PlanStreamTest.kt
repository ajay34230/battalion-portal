package com.vision.assistant

import com.vision.assistant.ai.PlanStream
import org.junit.Assert.*
import org.junit.Test

class PlanStreamTest {
    @Test fun showsChatTextAsItGrows() {
        assertNull(PlanStream.partialReply("{\"intent\":\"cha"))
        assertNull(PlanStream.partialReply("{\"intent\":\"chat\",\"reply\":\""))
        assertEquals("Hel", PlanStream.partialReply("{\"intent\":\"chat\",\"reply\":\"Hel"))
        assertEquals("Hello there", PlanStream.partialReply("{\"intent\":\"chat\",\"reply\":\"Hello there\"}"))
    }
    @Test fun decodesEscapesAndStopsAtPartialEscape() {
        assertEquals("a\nb \"q\"", PlanStream.partialReply("{\"intent\":\"chat\",\"reply\":\"a\\nb \\\"q\\\"\"}"))
        assertEquals("ab", PlanStream.partialReply("{\"intent\":\"chat\",\"reply\":\"ab\\"))
        assertEquals("\u0928", PlanStream.partialReply("{\"intent\":\"chat\",\"reply\":\"\\u0928\"}"))
        assertEquals("x", PlanStream.partialReply("{\"intent\":\"chat\",\"reply\":\"x\\u09"))
    }
    @Test fun neverShowsActionPlansAsText() {
        assertNull(PlanStream.partialReply("{\"intent\":\"reminder\",\"text\":\"milk\",\"reply\":\"x\"}"))
    }
}
