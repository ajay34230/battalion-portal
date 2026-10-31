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

class WakeWordTest {
    @Test fun matchesPhrases() {
        for (s in listOf("hey vision", "Hey Vision what time", "ok vision", "okay vision", "hello vision")) assertTrue(s, com.vision.assistant.service.WakeWord.matches(s))
    }
    @Test fun ignoresOtherSpeech() {
        for (s in listOf("vision", "television", "hey there", "what is vision", "")) assertFalse(s, com.vision.assistant.service.WakeWord.matches(s))
    }
}

class PaymentParserTest {
    private fun p(t: String, x: String = "") = com.vision.assistant.finance.PaymentParser.parse(t, x)
    @org.junit.Test fun phonepePaid() { val r = p("Paid ₹250.50 to Swiggy", "Payment successful")!!; org.junit.Assert.assertEquals(25050L, r.amountPaise); org.junit.Assert.assertEquals(com.vision.assistant.finance.Direction.PAID, r.direction); org.junit.Assert.assertEquals("Swiggy", r.party) }
    @org.junit.Test fun received() { val r = p("You received ₹1,500 from Rahul Kumar", "")!!; org.junit.Assert.assertEquals(150000L, r.amountPaise); org.junit.Assert.assertEquals(com.vision.assistant.finance.Direction.RECEIVED, r.direction); org.junit.Assert.assertEquals("Rahul Kumar", r.party) }
    @org.junit.Test fun bankDebit() { val r = p("HDFC Bank", "Rs.499.00 debited from A/c XX1234 on 30-10-26 to VPA netflix@upi")!!; org.junit.Assert.assertEquals(49900L, r.amountPaise); org.junit.Assert.assertEquals(com.vision.assistant.finance.Direction.PAID, r.direction); org.junit.Assert.assertFalse(r.party.contains("1234")) }
    @org.junit.Test fun billFlag() = org.junit.Assert.assertTrue(p("Electricity bill paid", "Paid ₹1,200 to BESCOM")!!.bill)
    @org.junit.Test fun otpIgnored() = org.junit.Assert.assertNull(p("Bank", "123456 is your OTP for payment of Rs.500. Do not share"))
    @org.junit.Test fun failedAndRequestsIgnored() { org.junit.Assert.assertNull(p("Payment failed", "₹300 to Amazon could not be paid")); org.junit.Assert.assertNull(p("Rahul requests ₹500", "")) }
    @org.junit.Test fun noAmountIgnored() = org.junit.Assert.assertNull(p("Paid to Swiggy", "thanks"))
    @org.junit.Test fun chatAppsDontCount() = org.junit.Assert.assertFalse(com.vision.assistant.finance.PaymentParser.looksLikeBankAlert("com.whatsapp", "Rs 500 debited from account"))
    @org.junit.Test fun totals() {
        val D = com.vision.assistant.finance.Direction.PAID
        val t = com.vision.assistant.finance.SpendSummary.totals(listOf(com.vision.assistant.finance.SpendSummary.Row(D, 10000, "A", false), com.vision.assistant.finance.SpendSummary.Row(D, 5000, "a", true), com.vision.assistant.finance.SpendSummary.Row(com.vision.assistant.finance.Direction.RECEIVED, 70000, "B", false)))
        org.junit.Assert.assertEquals(15000L, t.paid); org.junit.Assert.assertEquals(70000L, t.received); org.junit.Assert.assertEquals(5000L, t.bills); org.junit.Assert.assertEquals(150L, t.top[0].second / 100)
    }
    @org.junit.Test fun spendingIntent() { org.junit.Assert.assertEquals(com.vision.assistant.ai.Intent.Spending("month"), com.vision.assistant.ai.IntentParser.parse("how much did I spend this month")); org.junit.Assert.assertEquals(com.vision.assistant.ai.Intent.Spending("today"), com.vision.assistant.ai.IntentParser.parse("aaj kitna kharch hua")) }
}

class PaymentReviewTest {
    @org.junit.Test fun categoryGuess() { org.junit.Assert.assertEquals("Food", com.vision.assistant.finance.PaymentParser.suggestCategory("Swiggy", false)); org.junit.Assert.assertEquals("Bills", com.vision.assistant.finance.PaymentParser.suggestCategory("BESCOM", true)); org.junit.Assert.assertEquals("", com.vision.assistant.finance.PaymentParser.suggestCategory("Ramesh", false)) }
    @org.junit.Test fun nextReviewIsInFuture() { val now = System.currentTimeMillis(); val t = com.vision.assistant.finance.PaymentReview.nextAt(20 * 60, now); org.junit.Assert.assertTrue(t > now && t - now <= 24 * 3600_000L) }
    @org.junit.Test fun categoryTotals() {
        val D = com.vision.assistant.finance.Direction.PAID
        val r = com.vision.assistant.finance.SpendSummary.byCategory(listOf(com.vision.assistant.finance.SpendSummary.Row(D, 100, "a", false, "Food"), com.vision.assistant.finance.SpendSummary.Row(D, 300, "b", false, ""), com.vision.assistant.finance.SpendSummary.Row(D, 50, "c", false, "Food")))
        org.junit.Assert.assertEquals(listOf("Unsorted" to 300L, "Food" to 150L), r)
    }
}

class GmailParseTest {
    @org.junit.Test fun parsesMetadata() {
        val m = com.vision.assistant.finance.Gmail.parseMessage("""{"id":"abc","snippet":"Rs.350.00 debited from A/c XX99 to VPA zomato@upi","internalDate":"1760000000000","payload":{"headers":[{"name":"From","value":"x@bank.com"},{"name":"Subject","value":"Transaction alert"}]}}""")!!
        org.junit.Assert.assertEquals("abc", m.id); org.junit.Assert.assertEquals("Transaction alert", m.subject)
        val p = com.vision.assistant.finance.PaymentParser.parse(m.subject, m.snippet)!!; org.junit.Assert.assertEquals(35000L, p.amountPaise)
    }
    @org.junit.Test fun garbageIsNull() = org.junit.Assert.assertNull(com.vision.assistant.finance.Gmail.parseMessage("nope"))
    @org.junit.Test fun explainsClientErrors() = org.junit.Assert.assertTrue(com.vision.assistant.finance.Gmail.explain("10: DEVELOPER_ERROR").contains("GMAIL_SETUP"))
}

class ModelPlanTest {
    private fun p(s: String) = com.vision.assistant.ai.PlanParser.parse(s)?.intent
    @org.junit.Test fun spending() = org.junit.Assert.assertEquals(com.vision.assistant.ai.Intent.Spending("week"), p("""{"intent":"spending","period":"week"}"""))
    @org.junit.Test fun calendar() = org.junit.Assert.assertEquals(com.vision.assistant.ai.Intent.Calendar(2), p("""{"intent":"calendar","days":2}"""))
    @org.junit.Test fun event() = org.junit.Assert.assertEquals(com.vision.assistant.ai.Intent.SetEvent("birthday", "Mom", 10, 5, false, "en"), p("""{"intent":"event","kind":"birthday","title":"Mom","month":10,"day":5,"monthly":false}"""))
    @org.junit.Test fun badEventRejected() = org.junit.Assert.assertNull(p("""{"intent":"event","kind":"birthday","title":"Mom","month":13,"day":5}"""))
    @org.junit.Test fun routine() = org.junit.Assert.assertEquals(com.vision.assistant.ai.Intent.RunRoutine("morning"), p("""{"intent":"routine","name":"morning"}"""))
    @org.junit.Test fun searchMode() = org.junit.Assert.assertEquals(com.vision.assistant.ai.Intent.Search("x", "deep"), p("""{"intent":"search","query":"x","mode":"deep"}"""))
}

class VoiceprintTest {
    private val V = com.vision.assistant.voice.Voiceprint
    @org.junit.Test fun sameDirectionIsClose() = org.junit.Assert.assertEquals(0.0, V.cosineDistance(doubleArrayOf(1.0, 2.0, 3.0), doubleArrayOf(2.0, 4.0, 6.0)), 1e-9)
    @org.junit.Test fun oppositeIsFar() = org.junit.Assert.assertTrue(V.cosineDistance(doubleArrayOf(1.0, 0.0), doubleArrayOf(-1.0, 0.0)) > 1.9)
    @org.junit.Test fun mismatchedSizesAreFar() = org.junit.Assert.assertEquals(2.0, V.cosineDistance(doubleArrayOf(1.0), doubleArrayOf(1.0, 2.0)), 1e-9)
    @org.junit.Test fun matchesOwnerRejectsOther() {
        val owner = listOf(doubleArrayOf(1.0, 0.1, 0.0), doubleArrayOf(0.9, 0.2, 0.1), doubleArrayOf(1.1, 0.0, 0.1), doubleArrayOf(1.0, 0.15, 0.05))
        org.junit.Assert.assertTrue(V.matches(doubleArrayOf(1.0, 0.12, 0.03), owner, com.vision.assistant.voice.Voiceprint.Strictness.NORMAL))
        org.junit.Assert.assertFalse(V.matches(doubleArrayOf(0.0, 1.0, 0.2), owner, com.vision.assistant.voice.Voiceprint.Strictness.NORMAL))
    }
    @org.junit.Test fun noEnrolmentMeansAnyone() = org.junit.Assert.assertTrue(V.matches(doubleArrayOf(1.0), emptyList(), com.vision.assistant.voice.Voiceprint.Strictness.STRICT))
    @org.junit.Test fun readsVectorFromVoskJson() { org.junit.Assert.assertArrayEquals(doubleArrayOf(0.5, -1.0), V.vectorFrom("""{"text":"hey vision","spk":[0.5,-1.0],"spk_frames":40}"""), 1e-9); org.junit.Assert.assertNull(V.vectorFrom("""{"text":"x"}""")) }
    @org.junit.Test fun enrolmentPhraseCheck() { org.junit.Assert.assertTrue(V.isWakePhrase("hey vision")); org.junit.Assert.assertFalse(V.isWakePhrase("good morning")) }
    @org.junit.Test fun bareVisionOnlyWhenEnrolled() { org.junit.Assert.assertTrue(com.vision.assistant.service.WakeWord.matchesEnrolled("vision")); org.junit.Assert.assertFalse(com.vision.assistant.service.WakeWord.matches("vision")) }
}

class WidgetLogicTest {
    @org.junit.Test fun greeting() { org.junit.Assert.assertEquals("Good morning", com.vision.assistant.VisionWidget.greetingFor(8)); org.junit.Assert.assertEquals("Good evening", com.vision.assistant.VisionWidget.greetingFor(18)); org.junit.Assert.assertEquals("Good night", com.vision.assistant.VisionWidget.greetingFor(2)) }
    @org.junit.Test fun paceBar() { org.junit.Assert.assertEquals(0, com.vision.assistant.VisionWidget.pacePercent(100, 0)); org.junit.Assert.assertEquals(50, com.vision.assistant.VisionWidget.pacePercent(100, 100)); org.junit.Assert.assertEquals(100, com.vision.assistant.VisionWidget.pacePercent(900, 100)) }
}
