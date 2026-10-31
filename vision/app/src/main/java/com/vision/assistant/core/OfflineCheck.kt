package com.vision.assistant.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class CheckStatus(val label: String) { PASSED("Passed"), FAILED("Failed"), BLOCKED("Blocked"), NOT_SUPPORTED("Not supported"), NOT_TESTED("Not tested") }

data class CheckResult(val id: String, val title: String, val status: CheckStatus, val detail: String, val at: Long, val ms: Long = -1)

/** Pure helpers (testable): how results are summarised and how honest the "works offline" claim is. */
object OfflineSummary {
    fun counts(r: List<CheckResult>) = CheckStatus.values().associateWith { s -> r.count { it.status == s } }.filterValues { it > 0 }
    /** Offline is only "proven" when the phone had no validated internet both before and after the checks. */
    fun headline(r: List<CheckResult>, offlineThroughout: Boolean): String {
        if (r.isEmpty()) return "Not run yet."
        val c = counts(r).entries.joinToString(", ") { "${it.value} ${it.key.label.lowercase()}" }
        return if (offlineThroughout) "Run with no internet: $c." else "Run while the phone was ONLINE: $c. Turn on airplane mode and run again to prove offline operation."
    }
    fun encode(r: List<CheckResult>, offlineThroughout: Boolean, at: Long): String = JSONObject().put("at", at).put("offline", offlineThroughout)
        .put("r", JSONArray(r.map { JSONObject().put("id", it.id).put("t", it.title).put("s", it.status.name).put("d", it.detail.take(300)).put("at", it.at).put("ms", it.ms) })).toString()
    fun decode(json: String): Triple<List<CheckResult>, Boolean, Long>? = try {
        val o = JSONObject(json); val a = o.getJSONArray("r")
        Triple((0 until a.length()).map { val x = a.getJSONObject(it); CheckResult(x.getString("id"), x.getString("t"), CheckStatus.valueOf(x.getString("s")), x.optString("d"), x.optLong("at"), x.optLong("ms", -1)) }, o.optBoolean("offline"), o.optLong("at"))
    } catch (_: Exception) { null }
}

object OfflineStore {
    private fun sp(c: Context) = c.getSharedPreferences("vision_offline", Context.MODE_PRIVATE)
    fun save(c: Context, r: List<CheckResult>, offlineThroughout: Boolean) { sp(c).edit().putString("last", OfflineSummary.encode(r, offlineThroughout, System.currentTimeMillis())).apply() }
    fun load(c: Context) = sp(c).getString("last", null)?.let { OfflineSummary.decode(it) }
}
