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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object Prefs {
    fun searxngUrl(ctx: Context): String =
        ctx.getSharedPreferences("vision_prefs", Context.MODE_PRIVATE).getString("searxng_url", "") ?: ""
    fun setSearxngUrl(ctx: Context, v: String) =
        ctx.getSharedPreferences("vision_prefs", Context.MODE_PRIVATE).edit().putString("searxng_url", v.trim()).apply()
}

/** Search -> fetch (robots.txt respected) -> extract -> extractive summary with source URLs. No LLM needed. */
class ResearchTool(private val app: VisionApp) {
    suspend fun run(query: String): ToolResult = withContext(Dispatchers.IO) {
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
            val custom = Prefs.searxngUrl(app)
            val provider: SearchProvider = if (custom.isNotBlank()) SearxngSearch(custom) else WikipediaSearch(if (lang == Lang.HI) "hi" else "en")
            val hits = provider.search(query, 4)
            if (hits.isEmpty()) return@withContext ToolResult.Failure(Say("No results for \"$query\" from ${provider.name}.", "\"$query\" ke liye ${provider.name} par kuch nahi mila.", "\"$query\" के लिए ${provider.name} पर कुछ नहीं मिला।"))

            val sb = StringBuilder()
            val used = mutableListOf<String>()
            for (h in hits.take(3)) {
                val page = fetch(h.url)
                val summary = page?.let { TextExtractor.leadSentences(it.text, 3) }.orEmpty().ifBlank { h.snippet }
                if (summary.isBlank()) continue
                sb.append("• ").append(h.title).append(": ").append(summary).append('\n')
                used += h.url
            }
            if (used.isEmpty()) return@withContext ToolResult.Failure(Say("I found results but couldn't read any page (blocked or empty)."))
            val body = sb.toString().trim() + "\n\nSources (via ${provider.name}):\n" + used.joinToString("\n")
            app.database.memory().insert(MemoryItem(category = "research", content = "Q: $query\n$body", source = used.first()))
            ToolResult.Success(Say.plain(body))
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
