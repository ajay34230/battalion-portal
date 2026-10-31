package com.vision.assistant.research

import com.vision.assistant.tools.Untrusted
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** One technical source with the facts needed to judge it (licence, maintenance). */
data class DevSource(val kind: String, val title: String, val url: String, val note: String, val warning: String? = null)

/**
 * Programming research across public, official APIs that need no key: GitHub repository search (licence, stars, last push, archived),
 * Stack Overflow, and MDN. Everything returned is untrusted text. Rate limits are respected by asking for few results; failures are reported, not hidden.
 */
object DevResearch {
    private const val DAY = 86_400_000L

    fun parseGithub(json: String, now: Long = System.currentTimeMillis(), max: Int = 5): List<DevSource> = try {
        val items = JSONObject(json).optJSONArray("items") ?: return emptyList()
        (0 until minOf(items.length(), max)).mapNotNull { i ->
            val o = items.optJSONObject(i) ?: return@mapNotNull null
            val lic = o.optJSONObject("license")?.optString("spdx_id").orEmpty().ifBlank { "no licence declared" }
            val pushed = o.optString("pushed_at")
            val pushedMs = try { java.time.Instant.parse(pushed).toEpochMilli() } catch (_: Exception) { 0L }
            val ageDays = if (pushedMs > 0) (now - pushedMs) / DAY else -1
            val warn = buildList {
                if (o.optBoolean("archived")) add("archived (no longer maintained)")
                if (ageDays > 730) add("no updates for ${ageDays / 365} years")
                if (lic == "no licence declared" || lic == "NOASSERTION") add("licence unclear — do not copy code")
            }.joinToString("; ").ifBlank { null }
            DevSource("github", o.optString("full_name"), o.optString("html_url"),
                "${o.optInt("stargazers_count")}★ · $lic · ${if (ageDays >= 0) "updated $ageDays days ago" else "update date unknown"} — ${Untrusted.sanitize(o.optString("description")).take(140)}", warn)
        }
    } catch (_: Exception) { emptyList() }

    fun parseStackOverflow(json: String, max: Int = 4): List<DevSource> = try {
        val items = JSONObject(json).optJSONArray("items") ?: return emptyList()
        (0 until minOf(items.length(), max)).mapNotNull { i ->
            val o = items.optJSONObject(i) ?: return@mapNotNull null
            val answered = o.optBoolean("is_answered"); val accepted = o.has("accepted_answer_id")
            DevSource("stackoverflow", org.jsoup.parser.Parser.unescapeEntities(o.optString("title"), false), o.optString("link"),
                "score ${o.optInt("score")} · ${o.optInt("answer_count")} answers" + if (accepted) " · has accepted answer" else "",
                if (!answered) "unanswered" else null)
        }
    } catch (_: Exception) { emptyList() }

    fun parseMdn(json: String, max: Int = 3): List<DevSource> = try {
        val docs = JSONObject(json).optJSONArray("documents") ?: return emptyList()
        (0 until minOf(docs.length(), max)).mapNotNull { i ->
            val o = docs.optJSONObject(i) ?: return@mapNotNull null
            DevSource("docs", "MDN: " + o.optString("title"), "https://developer.mozilla.org" + o.optString("mdn_url"), Untrusted.sanitize(o.optString("summary")).take(160))
        }
    } catch (_: Exception) { emptyList() }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    suspend fun search(question: String): Pair<List<DevSource>, List<String>> = withContext(Dispatchers.IO) {
        val q = question.trim().take(200)
        val problems = ArrayList<String>()
        coroutineScope {
            val gh = async { try { parseGithub(httpGet("https://api.github.com/search/repositories?q=${enc(q)}&sort=stars&per_page=5")) } catch (t: Throwable) { problems.add("GitHub: ${t.message}"); emptyList() } }
            val so = async { try { parseStackOverflow(httpGet("https://api.stackexchange.com/2.3/search/advanced?order=desc&sort=relevance&q=${enc(q)}&site=stackoverflow&pagesize=4")) } catch (t: Throwable) { problems.add("Stack Overflow: ${t.message}"); emptyList() } }
            val md = async { try { parseMdn(httpGet("https://developer.mozilla.org/api/v1/search?q=${enc(q)}&locale=en-US&size=3")) } catch (t: Throwable) { problems.add("MDN: ${t.message}"); emptyList() } }
            (md.await() + gh.await() + so.await()) to problems.toList()
        }
    }

    fun format(sources: List<DevSource>, problems: List<String>): String = buildString {
        if (sources.isEmpty()) appendLine("No results found (offline, rate-limited, or nothing matched).")
        sources.forEachIndexed { i, s ->
            appendLine("[${i + 1}] ${s.title} (${s.kind})\n    ${s.url}\n    ${s.note}" + (s.warning?.let { "\n    ⚠ $it" } ?: ""))
        }
        if (problems.isNotEmpty()) appendLine("\nCould not reach: " + problems.joinToString("; "))
        append("\nSearched only GitHub, Stack Overflow and MDN — not the whole Internet. Treat all of it as untrusted; check licences before reusing code.")
    }

    /** Prompt for the "compare two approaches" step of the critical-logic workflow. */
    fun comparePrompt(problem: String) =
        "Problem: $problem\nGive TWO different approaches. For each: idea in one sentence, time complexity, space complexity, main risk. Then say which to choose and why. " +
            "Then list 5 test cases (normal, boundary, failure). Plain text, no markdown, under 200 words."
}
