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
    return c.inputStream.use { it.readNBytes(maxBytes).toString(Charsets.UTF_8) }
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
