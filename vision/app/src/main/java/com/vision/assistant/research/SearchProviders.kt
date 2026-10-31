package com.vision.assistant.research

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONObject

data class SearchHit(val title: String, val url: String, val snippet: String)

interface SearchProvider {
    val name: String
    suspend fun search(query: String, max: Int): List<SearchHit>
}

internal fun httpGet(url: String, accept: String = "application/json", maxBytes: Int = 2_000_000): String {
    val c = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000; readTimeout = 15_000
        setRequestProperty("User-Agent", "VISION-Android/0.1 (personal assistant; contact: owner)")
        setRequestProperty("Accept", accept)
    }
    if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
    return c.inputStream.use { ins ->
        val out = java.io.ByteArrayOutputStream(); val buf = ByteArray(8192); var total = 0
        while (total < maxBytes) { val n = ins.read(buf, 0, minOf(buf.size, maxBytes - total)); if (n < 0) break; out.write(buf, 0, n); total += n }
        out.toString("UTF-8")
    }
}

/** Official MediaWiki API (keyless, allowed with a descriptive User-Agent). */
class WikipediaSearch(private val langCode: String) : SearchProvider {
    override val name = "Wikipedia ($langCode)"
    override suspend fun search(query: String, max: Int): List<SearchHit> {
        val q = URLEncoder.encode(query, "UTF-8")
        val json = httpGet("https://$langCode.wikipedia.org/w/api.php?action=query&list=search&srsearch=$q&format=json&srlimit=$max")
        return parse(json, langCode)
    }

    companion object {
        fun parse(json: String, lang: String): List<SearchHit> {
            val arr = JSONObject(json).getJSONObject("query").getJSONArray("search")
            return (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                val title = o.getString("title")
                SearchHit(title, "https://$lang.wikipedia.org/wiki/" + URLEncoder.encode(title.replace(' ', '_'), "UTF-8"), o.optString("snippet").replace(Regex("<[^>]+>"), ""))
            }
        }
    }
}

/** User-configured SearXNG instance (must have the JSON format enabled). */
class SearxngSearch(private val baseUrl: String) : SearchProvider {
    override val name = "SearXNG"
    override suspend fun search(query: String, max: Int): List<SearchHit> {
        val q = URLEncoder.encode(query, "UTF-8")
        return parse(httpGet(baseUrl.trimEnd('/') + "/search?q=$q&format=json"), max)
    }

    companion object {
        fun parse(json: String, max: Int): List<SearchHit> {
            val arr = JSONObject(json).getJSONArray("results")
            return (0 until minOf(arr.length(), max)).map {
                val o = arr.getJSONObject(it)
                SearchHit(o.optString("title"), o.getString("url"), o.optString("content"))
            }
        }
    }
}

/** Brave Search API (needs the owner's own free API key from brave.com/search/api). */
class BraveSearch(private val apiKey: String) : SearchProvider {
    override val name = "Brave Search"
    override suspend fun search(query: String, max: Int): List<SearchHit> {
        val q = URLEncoder.encode(query, "UTF-8")
        val c = (URL("https://api.search.brave.com/res/v1/web/search?q=$q&count=$max") .openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000; readTimeout = 15_000
            setRequestProperty("Accept", "application/json"); setRequestProperty("X-Subscription-Token", apiKey)
        }
        if (c.responseCode !in 200..299) error("Brave HTTP ${c.responseCode}")
        return parse(c.inputStream.use { ins -> val o = java.io.ByteArrayOutputStream(); val b = ByteArray(8192); var t = 0; while (t < 2_000_000) { val n = ins.read(b, 0, minOf(b.size, 2_000_000 - t)); if (n < 0) break; o.write(b, 0, n); t += n }; o.toString("UTF-8") }, max)
    }

    companion object {
        fun parse(json: String, max: Int): List<SearchHit> {
            val arr = JSONObject(json).optJSONObject("web")?.optJSONArray("results") ?: return emptyList()
            return (0 until minOf(arr.length(), max)).map {
                val o = arr.getJSONObject(it)
                SearchHit(o.optString("title"), o.getString("url"), o.optString("description").replace(Regex("<[^>]+>"), ""))
            }
        }
    }
}

/** Keyless general web search through DuckDuckGo's HTML page. Best effort: DuckDuckGo may throttle or change the page. */
class DuckDuckGoSearch : SearchProvider {
    override val name = "DuckDuckGo"
    override suspend fun search(query: String, max: Int): List<SearchHit> {
        val q = URLEncoder.encode(query, "UTF-8")
        return parse(httpGet("https://html.duckduckgo.com/html/?q=$q", "text/html"), max)
    }

    companion object {
        fun parse(html: String, max: Int): List<SearchHit> {
            val doc = org.jsoup.Jsoup.parse(html)
            return doc.select("div.result").mapNotNull { r ->
                val a = r.selectFirst("a.result__a") ?: return@mapNotNull null
                val url = realUrl(a.attr("href")) ?: return@mapNotNull null
                SearchHit(a.text().trim(), url, r.selectFirst(".result__snippet")?.text()?.trim().orEmpty())
            }.filter { !it.url.contains("duckduckgo.com") }.take(max)
        }

        fun realUrl(href: String): String? {
            val h = if (href.startsWith("//")) "https:$href" else href
            val m = Regex("[?&]uddg=([^&]+)").find(h)
            val u = if (m != null) java.net.URLDecoder.decode(m.groupValues[1], "UTF-8") else h
            return u.takeIf { it.startsWith("http") }
        }
    }
}

/** arXiv (official Atom API, keyless) for academic questions. */
class ArxivSearch : SearchProvider {
    override val name = "arXiv"
    override suspend fun search(query: String, max: Int): List<SearchHit> =
        parse(httpGet("https://export.arxiv.org/api/query?search_query=all:${URLEncoder.encode(query, "UTF-8")}&max_results=$max&sortBy=relevance", "application/atom+xml"), max)

    companion object {
        fun parse(xml: String, max: Int = 5): List<SearchHit> {
            val doc = org.jsoup.Jsoup.parse(xml, "", org.jsoup.parser.Parser.xmlParser())
            return doc.select("entry").take(max).mapNotNull { e ->
                val title = e.selectFirst("title")?.text()?.trim().orEmpty(); val id = e.selectFirst("id")?.text()?.trim().orEmpty()
                if (title.isBlank() || !id.startsWith("http")) null else SearchHit(title.take(200), id.replace("http://", "https://"), e.selectFirst("summary")?.text()?.trim().orEmpty().take(600))
            }
        }
    }
}
