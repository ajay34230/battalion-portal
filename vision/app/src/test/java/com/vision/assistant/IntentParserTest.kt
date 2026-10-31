package com.vision.assistant

import com.vision.assistant.ai.Intent
import com.vision.assistant.ai.IntentParser
import com.vision.assistant.ai.Lang
import com.vision.assistant.ai.LanguageDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentParserTest {
    @Test fun detectsEnglish() = assertEquals(Lang.EN, LanguageDetector.detect("Vision, open WhatsApp."))
    @Test fun detectsHinglish() = assertEquals(Lang.HINGLISH, LanguageDetector.detect("Vision, kal subah mujhe 6 baje utha dena."))
    @Test fun detectsHindi() = assertEquals(Lang.HI, LanguageDetector.detect("मुझे आज का मौसम बताओ"))

    @Test fun openAppEnglish() = assertEquals(Intent.OpenApp("whatsapp"), IntentParser.parse("Vision, open WhatsApp."))
    @Test fun openAppHinglish() = assertEquals(Intent.OpenApp("youtube"), IntentParser.parse("youtube kholo"))

    @Test fun reminderTomorrow6am() {
        val i = IntentParser.parse("Vision, remind me tomorrow at 6 AM to call home") as Intent.SetReminder
        assertEquals(1, i.time!!.dayOffset); assertEquals(6, i.time!!.hour); assertEquals("call home", i.text)
    }

    @Test fun reminderHinglishMorning() {
        val i = IntentParser.parse("Vision, kal subah mujhe 6 baje utha dena") as Intent.SetReminder
        assertEquals(1, i.time!!.dayOffset); assertEquals(6, i.time!!.hour)
    }

    @Test fun reminderWeeklySunday() {
        val i = IntentParser.parse("remind me every Sunday to review my schedule") as Intent.SetReminder
        assertEquals("SUN", i.repeatWeekday)
    }

    @Test fun pmTime() {
        val i = IntentParser.parse("remind me at 7 pm to study") as Intent.SetReminder
        assertEquals(19, i.time!!.hour)
    }

    @Test fun callEnglish() = assertEquals(Intent.Call("mom"), IntentParser.parse("call mom"))
    @Test fun searchEnglish() = assertTrue(IntentParser.parse("research best offline AI models") is Intent.Search)
    @Test fun batteryQuery() = assertEquals(Intent.AskBattery, IntentParser.parse("what's my battery"))
    @Test fun unknownIsChat() = assertTrue(IntentParser.parse("tell me a joke") is Intent.Chat)
}

class ResearchModesTest {
    @org.junit.Test fun deepAndAcademicModes() {
        val d = IntentParser.parse("deep research on solar panels") as Intent.Search
        org.junit.Assert.assertEquals("deep", d.mode); org.junit.Assert.assertEquals("solar panels", d.query)
        org.junit.Assert.assertEquals("academic", (IntentParser.parse("academic research on graph neural networks") as Intent.Search).mode)
        org.junit.Assert.assertEquals("quick", (IntentParser.parse("search weather today") as Intent.Search).mode)
    }
    @org.junit.Test fun arxivParsing() {
        val xml = "<feed><entry><id>http://arxiv.org/abs/1234.5678v1</id><title>A Paper</title><summary>About things.</summary></entry></feed>"
        val r = com.vision.assistant.research.ArxivSearch.parse(xml)
        org.junit.Assert.assertEquals("https://arxiv.org/abs/1234.5678v1", r[0].url); org.junit.Assert.assertEquals("A Paper", r[0].title)
    }
}
