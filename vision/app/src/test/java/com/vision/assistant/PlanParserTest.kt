package com.vision.assistant

import com.vision.assistant.ai.Intent
import com.vision.assistant.ai.LlmText
import com.vision.assistant.ai.PlanParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanParserTest {
    @Test fun reminder() {
        val p = PlanParser.parse("""{"intent":"reminder","text":"call home","day_offset":1,"hour":6,"minute":0,"repeat_weekday":null}""")!!
        val i = p.intent as Intent.SetReminder
        assertEquals(1, i.time!!.dayOffset); assertEquals(6, i.time!!.hour); assertNull(i.repeatWeekday)
    }

    @Test fun weeklyReminder() {
        val i = PlanParser.parse("""{"intent":"reminder","text":"review","hour":9,"minute":30,"repeat_weekday":"sun"}""")!!.intent as Intent.SetReminder
        assertEquals("SUN", i.repeatWeekday)
    }

    @Test fun openApp() = assertEquals(Intent.OpenApp("whatsapp"), PlanParser.parse("""Sure: {"intent":"open_app","app":"WhatsApp"} done""")!!.intent)
    @Test fun chatWithReply() { val p = PlanParser.parse("""{"intent":"chat","reply":"Hello!"}""")!!; assertEquals("Hello!", p.reply) }
    @Test fun badHour() = assertNull(PlanParser.parse("""{"intent":"reminder","text":"x","hour":30,"minute":0}"""))
    @Test fun garbage() = assertNull(PlanParser.parse("no json here"))
    @Test fun unknownIntent() = assertNull(PlanParser.parse("""{"intent":"launch_missiles"}"""))
    @Test fun shortPromptDropsPhoneControlExamplesOnlyWhenNotNeeded() {
        assertTrue(PlanParser.SHORT.length < PlanParser.SYSTEM.length - 300)
        assertTrue(PlanParser.SHORT.contains("\"intent\":\"termux\""))
        assertEquals(PlanParser.SHORT, PlanParser.systemFor("remind me tomorrow at 6"))
        assertEquals(PlanParser.SYSTEM, PlanParser.systemFor("turn on the torch"))
    }
    @Test fun thinkingRemoved() { assertEquals("Hi", LlmText.clean("<think>hmm</think>Hi")); assertEquals("Hi", LlmText.clean("Hi<think>unfinished")); assertNotNull(PlanParser.SYSTEM) }
}
