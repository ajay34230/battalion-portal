package com.vision.assistant.tasks

import java.util.Calendar

enum class EventKind(val id: String, val emoji: String, val label: String, val defaultLeads: List<Int>) {
    REMINDER("reminder", "⏰", "Reminder", emptyList()),
    BILL("bill", "💳", "Bill", listOf(3, 1)),
    BIRTHDAY("birthday", "🎂", "Birthday", listOf(1)),
    ANNIVERSARY("anniversary", "💍", "Anniversary", listOf(7, 1)),
    MEDICINE("medicine", "💊", "Medicine", emptyList());

    companion object { fun of(id: String) = values().firstOrNull { it.id == id } ?: REMINDER }
}

/** Date maths for one-off and repeating events. All times are in the phone's time zone. */
object EventPlanner {
    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Calendar {
        val c = Calendar.getInstance()
        c.clear(); c.set(year, month - 1, 1, hour, minute, 0)
        c.set(Calendar.DAY_OF_MONTH, day.coerceAtMost(c.getActualMaximum(Calendar.DAY_OF_MONTH))) // 29 Feb / 31st clamp
        return c
    }

    /** Next occurrence of month/day strictly after [now]. */
    fun nextYearly(month: Int, day: Int, hour: Int, minute: Int, now: Calendar = Calendar.getInstance()): Long {
        val y = now.get(Calendar.YEAR)
        for (yy in y..y + 1) { val c = at(yy, month, day, hour, minute); if (c.timeInMillis > now.timeInMillis) return c.timeInMillis }
        return at(y + 2, month, day, hour, minute).timeInMillis
    }

    /** Next occurrence of the given day-of-month strictly after [now] (short months use their last day). */
    fun nextMonthly(day: Int, hour: Int, minute: Int, now: Calendar = Calendar.getInstance()): Long {
        var y = now.get(Calendar.YEAR); var m = now.get(Calendar.MONTH) + 1
        repeat(14) {
            val c = at(y, m, day, hour, minute)
            if (c.timeInMillis > now.timeInMillis) return c.timeInMillis
            m++; if (m > 12) { m = 1; y++ }
        }
        return at(y, m, day, hour, minute).timeInMillis
    }

    /** The occurrence after [after] for a stored repeat rule ("weekly:MON", "monthly:05", "yearly:03-14"), or null when it does not repeat. */
    fun nextAfter(repeat: String?, after: Long, hour: Int, minute: Int): Long? {
        if (repeat.isNullOrBlank()) return null
        val now = Calendar.getInstance().apply { timeInMillis = after }
        val v = repeat.substringAfter(':', "")
        return when (repeat.substringBefore(':')) {
            "weekly" -> com.vision.assistant.tools.DeviceTools.nextWeekdayMillis(v, hour, minute, now)
            "monthly" -> v.toIntOrNull()?.let { nextMonthly(it, hour, minute, now) }
            "yearly" -> v.split('-').let { p -> if (p.size == 2) nextYearly(p[0].toIntOrNull() ?: return null, p[1].toIntOrNull() ?: return null, hour, minute, now) else null }
            else -> null
        }
    }
}
