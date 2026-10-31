package com.vision.assistant.research

import android.content.Context
import com.vision.assistant.VisionApp
import com.vision.assistant.ai.Lang
import com.vision.assistant.ai.LanguageDetector
import com.vision.assistant.core.NetworkMonitor
import com.vision.assistant.data.MemoryItem
import com.vision.assistant.tools.Say
import com.vision.assistant.tools.ToolResult
import java.net.URL
import com.vision.assistant.tools.Untrusted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

object Prefs {
    fun searxngUrl(ctx: Context): String =
        ctx.getSharedPreferences("vision_prefs", Context.MODE_PRIVATE).getString("searxng_url", "") ?: ""
    /** true (default): talk with the phone's own speech recogniser and voice. false: VISION's downloaded offline Vosk model, if installed. */
    fun phoneVoice(ctx: Context): Boolean = ctx.getSharedPreferences("vision_prefs", Context.MODE_PRIVATE).getBoolean("phone_voice", true)
    fun setPhoneVoice(ctx: Context, v: Boolean) = ctx.getSharedPreferences("vision_prefs", Context.MODE_PRIVATE).edit().putBoolean("phone_voice", v).apply()
    fun braveKey(ctx: Context): String =
        ctx.getSharedPreferences("vision_prefs", Context.MODE_PRIVATE).getString("brave_key", "") ?: ""
    fun setBraveKey(ctx: Context, v: String) =
        ctx.getSharedPreferences("vision_prefs", Context.MODE_PRIVATE).edit().putString("brave_key", v.trim()).apply()
    fun setSearxngUrl(ctx: Context, v: String) =
        ctx.getSharedPreferences("vision_prefs", Context.MODE_PRIVATE).edit().putString("searxng_url", v.trim()).apply()
}

/** Search -> fetch (robots.txt respected) -> extract -> summary (language model if installed, else extractive) with numbered sources. */
class ResearchTool(private val app: VisionApp) {
    suspend fun run(query: String, mode: String = "quick"): ToolResult = withContext(Dispatchers.IO) {
        if (!NetworkMonitor.isOnline(app)) {
            app.database.memory().insert(MemoryItem(category = "pending_research", content = query))
            return@withContext ToolResult.Failure(
                Say(
                    "You're offline. I saved \"$query\" and will research it when you're back online.",
                    "Aap offline hain. \"$query\" save kar liya, online aate hi research karunga.",
                    "आप ऑफ़लाइन हैं। \"$query\" सहेज लिया, ऑनलाइन आते ही खोजूँगा।",
                ),
            )
        }
        try {
            val lang = LanguageDetector.detect(query)
            val hits = LinkedHashMap<String, SearchHit>()
            val used = mutableListOf<String>()
            // Web providers in order of quality; the first that answers wins. Wikipedia is always added for grounding.
            val web = buildList<SearchProvider> {
                Prefs.searxngUrl(app).takeIf { it.isNotBlank() }?.let { add(SearxngSearch(it)) }
                Prefs.braveKey(app).takeIf { it.isNotBlank() }?.let { add(BraveSearch(it)) }
                add(DuckDuckGoSearch())
            }
            for (p in web) {
                val r = try { p.search(query, if (mode == "deep") 10 else 6) } catch (_: Exception) { emptyList() }
                if (r.isNotEmpty()) { r.forEach { hits.putIfAbsent(it.url, it) }; used += p.name; break }
            }
            val wiki = WikipediaSearch(if (lang == Lang.HI) "hi" else "en")
            if (mode == "academic") try { ArxivSearch().search(query, 5).forEach { hits.putIfAbsent(it.url, it) }; used += "arXiv" } catch (_: Exception) {}
            try { wiki.search(query, 2).forEach { hits.putIfAbsent(it.url, it) }; used += wiki.name } catch (_: Exception) {}
            if (hits.isEmpty()) return@withContext ToolResult.Failure(Say("No results for \"$query\".", "\"$query\" ke liye kuch nahi mila.", "\"$query\" के लिए कुछ नहीं मिला।"))

            // Read up to 6 pages in parallel (robots.txt respected).
            val pages = coroutineScope {
                hits.values.take(if (mode == "deep") 10 else 6).map { h -> async { h to fetch(h.url) } }.awaitAll()
            }
            val sources = pages.mapNotNull { (h, page) ->
                val body = Untrusted.sanitize(page?.text.orEmpty()).take(if (mode == "deep") 2600 else 1800).ifBlank { h.snippet }
                if (body.isBlank()) null else Triple(h, body, page?.title?.ifBlank { null } ?: h.title)
            }
            if (sources.isEmpty()) return@withContext ToolResult.Failure(Say("I found results but couldn't read any page (blocked or empty)."))

            val material = sources.mapIndexed { i, (h, body, title) -> "[${i + 1}] $title (${URL(h.url).host})\n$body" }.joinToString("\n\n").take(if (mode == "deep") 12000 else 7000)
            val engine = app.engines.current()
            val summary = try { engine.summarize(query, material, lang) } catch (_: Throwable) { null }
                ?: sources.take(3).joinToString("\n") { (_, body, title) -> "• $title: ${TextExtractor.leadSentences(body, 3)}" }
            val list = sources.mapIndexed { i, (h, _, title) -> "[${i + 1}] $title — ${h.url}" }.joinToString("\n")
            val out = summary.trim() + "\n\nSources (via ${used.joinToString(" + ")}):\n" + list
            app.database.memory().insert(MemoryItem(category = "research", content = "Q: $query\n$out", source = sources.first().first.url))
            ToolResult.Success(Say.plain(out))
        } catch (e: Exception) {
            ToolResult.Failure(Say("Research failed: ${e.message}", "Research fail ho gaya: ${e.message}", "खोज विफल: ${e.message}"))
        }
    }

    private fun fetch(url: String): Page? = try {
        val u = URL(url)
        val robots = try { httpGet("${u.protocol}://${u.host}/robots.txt", "text/plain", 200_000) } catch (_: Exception) { null }
        if (!RobotsPolicy(robots).isAllowed(u.path.ifEmpty { "/" })) null
        else TextExtractor.extract(httpGet(url, "text/html"), url)
    } catch (_: Exception) { null }

    /** Re-runs research queued while offline. Called when connectivity returns. */
    suspend fun runPending(): List<Pair<String, ToolResult>> {
        val pending = app.database.memory().search("", 200).filter { it.category == "pending_research" }
        val out = mutableListOf<Pair<String, ToolResult>>()
        for (p in pending) {
            if (!NetworkMonitor.isOnline(app)) break
            app.database.memory().delete(p)
            out += p.content to run(p.content)
        }
        return out
    }
}
