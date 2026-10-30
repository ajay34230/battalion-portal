package com.vision.assistant

import com.vision.assistant.comms.GmailApi
import org.junit.Assert.assertEquals
import org.junit.Test

class GmailTest {
    @Test fun parsesMetadata() {
        val j = """{"snippet":"Hello there","payload":{"headers":[{"name":"From","value":"a@b.com"},{"name":"Subject","value":"Hi"}]}}"""
        val m = GmailApi.parse(j)
        assertEquals("a@b.com", m.from); assertEquals("Hi", m.subject); assertEquals("Hello there", m.snippet)
    }
}
