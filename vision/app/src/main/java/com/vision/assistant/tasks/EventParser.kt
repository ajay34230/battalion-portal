package com.vision.assistant.tasks

/** A recurring life event: birthday, anniversary, monthly bill. */
data class ParsedEvent(val kind: EventKind, val title: String, val month: Int?, val day: Int, val monthly: Boolean, val lang: String)

object EventParser {
    private val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    private val monthHi = mapOf("janvari" to 1, "farvari" to 2, "march" to 3, "april" to 4, "mai" to 5, "jun" to 6, "julai" to 7, "agast" to 8, "sitambar" to 9, "aktubar" to 10, "navambar" to 11, "disambar" to 12)

    private fun monthOf(w: String): Int? = months.indexOfFirst { w.startsWith(it) }.takeIf { it >= 0 }?.plus(1) ?: monthHi[w]

    private fun date(l: String): Pair<Int?, Int>? {
        Regex("(\\d{1,2})(?:st|nd|rd|th)?\\s+(?:of\\s+)?([a-z]{3,9})").find(l)?.let { m -> monthOf(m.groupValues[2])?.let { mo -> m.groupValues[1].toInt().takeIf { it in 1..31 }?.let { return mo to it } } }
        Regex("([a-z]{3,9})\\s+(\\d{1,2})(?:st|nd|rd|th)?\\b").find(l)?.let { m -> monthOf(m.groupValues[1])?.let { mo -> m.groupValues[2].toInt().takeIf { it in 1..31 }?.let { return mo to it } } }
        Regex("(\\d{1,2})[/-](\\d{1,2})\\b").find(l)?.let { m -> val d = m.groupValues[1].toInt(); val mo = m.groupValues[2].toInt(); if (d in 1..31 && mo in 1..12) return mo to d }
        return null
    }

    private fun lang(raw: String) = when {
        Regex("[\\u0900-\\u097F]").containsMatchIn(raw) -> "hi"
        Regex("\\b(ka|ki|ke|hai|kal|mujhe|yaad|janamdin|shaadi|bijli|ko)\\b", RegexOption.IGNORE_CASE).containsMatchIn(raw) -> "hinglish"
        else -> "en"
    }

    private fun name(l: String, kind: String): String {
        val n = Regex("(?:$kind\\s+(?:of|for)\\s+|([a-z\\u0900-\\u097F]+(?:\\s+[a-z\\u0900-\\u097F]+)?)(?:'s|\\s+ka|\\s+ki|\\s+ke)\\s+$kind)").find(l)
        val g = n?.groupValues?.drop(1)?.firstOrNull { it.isNotBlank() }
        if (g != null) return g.trim()
        return Regex("(?:$kind\\s+(?:of|for)\\s+)([a-z]+(?:\\s+[a-z]+)?)").find(l)?.groupValues?.get(1)?.trim().orEmpty()
    }

    fun parse(raw: String): ParsedEvent? {
        val l = raw.lowercase().trim().trimEnd('.', '!', '?')
        val lang = lang(raw)
        fun cap(s: String) = s.split(' ').joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        if (Regex("birthday|janamdin|जन्मदिन|b'?day").containsMatchIn(l)) {
            val (mo, d) = date(l) ?: return null
            val who = name(l, "(?:birthday|b'?day|janamdin|जन्मदिन)").ifBlank { return null }
            return ParsedEvent(EventKind.BIRTHDAY, cap(who), mo, d, false, lang)
        }
        if (Regex("anniversary|saalgirah|सालगिरह").containsMatchIn(l)) {
            val (mo, d) = date(l) ?: return null
            val who = name(l, "(?:anniversary|saalgirah|सालगिरह)").ifBlank { "Our" }
            return ParsedEvent(EventKind.ANNIVERSARY, cap(who), mo, d, false, lang)
        }
        if (Regex("\\b(bill|emi|rent|premium|recharge|bijli|बिल)\\b").containsMatchIn(l) && Regex("every month|monthly|har mahine|हर महीने|due|on the \\d|\\d+(?:st|nd|rd|th)").containsMatchIn(l)) {
            val d = Regex("(\\d{1,2})(?:st|nd|rd|th)?").findAll(l).mapNotNull { it.groupValues[1].toIntOrNull() }.firstOrNull { it in 1..31 } ?: return null
            val what = Regex("(electricity|bijli|water|phone|mobile|internet|wifi|gas|rent|emi|insurance|premium|credit card|recharge|loan)").find(l)?.value ?: "bill"
            return ParsedEvent(EventKind.BILL, cap(what) + if (what == "bill") "" else " bill".takeIf { !what.endsWith("emi") && what != "rent" && what != "premium" && what != "recharge" } .orEmpty(), null, d, true, lang)
        }
        return null
    }
}
