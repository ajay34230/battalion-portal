package com.vision.assistant

import com.vision.assistant.ai.Intent
import com.vision.assistant.ai.IntentParser
import com.vision.assistant.tasks.EventKind
import com.vision.assistant.tasks.EventParser
import com.vision.assistant.tasks.EventPlanner
import java.util.Calendar
import org.junit.Assert.*
import org.junit.Test

class EventsTest {
    private fun cal(y: Int, m: Int, d: Int) = Calendar.getInstance().apply { clear(); set(y, m - 1, d, 12, 0, 0) }
    private fun date(ms: Long) = Calendar.getInstance().apply { timeInMillis = ms }

    @Test fun yearlyRollsToNextYearWhenPassed() {
        val c = date(EventPlanner.nextYearly(3, 14, 9, 0, cal(2026, 10, 31)))
        assertEquals(2027, c.get(Calendar.YEAR)); assertEquals(2, c.get(Calendar.MONTH)); assertEquals(14, c.get(Calendar.DAY_OF_MONTH))
    }
    @Test fun yearlyStaysThisYearWhenAhead() = assertEquals(2026, date(EventPlanner.nextYearly(12, 25, 9, 0, cal(2026, 10, 31))).get(Calendar.YEAR))
    @Test fun leapDayClampsInNonLeapYear() {
        val c = date(EventPlanner.nextYearly(2, 29, 9, 0, cal(2026, 10, 31)))
        assertEquals(28, c.get(Calendar.DAY_OF_MONTH)); assertEquals(1, c.get(Calendar.MONTH))
    }
    @Test fun monthlyClampsShortMonths() {
        val c = date(EventPlanner.nextMonthly(31, 10, 0, cal(2027, 1, 31).apply { set(Calendar.HOUR_OF_DAY, 23) }))
        assertEquals(1, c.get(Calendar.MONTH)); assertEquals(28, c.get(Calendar.DAY_OF_MONTH))
    }
    @Test fun nextAfterHandlesRules() {
        val after = cal(2026, 3, 14).timeInMillis
        assertEquals(2027, date(EventPlanner.nextAfter("yearly:03-14", after, 9, 0)!!).get(Calendar.YEAR))
        assertNull(EventPlanner.nextAfter(null, after, 9, 0))
    }
    @Test fun parsesBirthday() {
        val e = EventParser.parse("Rahul's birthday on 14 March")!!
        assertEquals(EventKind.BIRTHDAY, e.kind); assertEquals("Rahul", e.title); assertEquals(3, e.month); assertEquals(14, e.day)
    }
    @Test fun parsesHinglishBirthday() {
        val e = EventParser.parse("Mummy ka birthday 5 august ko hai")!!
        assertEquals("Mummy", e.title); assertEquals(8, e.month); assertEquals(5, e.day); assertEquals("hinglish", e.lang)
    }
    @Test fun parsesAnniversary() = assertEquals(EventKind.ANNIVERSARY, EventParser.parse("our anniversary is on 2 feb")!!.kind)
    @Test fun parsesMonthlyBill() {
        val e = EventParser.parse("electricity bill due on the 5th every month")!!
        assertEquals(EventKind.BILL, e.kind); assertTrue(e.monthly); assertEquals(5, e.day)
    }
    @Test fun plainReminderStillReminder() = assertTrue(IntentParser.parse("remind me tomorrow at 6 am to call mom") is Intent.SetReminder)
    @Test fun birthdayRoutesToEvent() = assertTrue(IntentParser.parse("remind me about Rahul's birthday on 14 march") is Intent.SetEvent)
}
