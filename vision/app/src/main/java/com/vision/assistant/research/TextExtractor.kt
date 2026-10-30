package com.vision.assistant.research

import org.jsoup.Jsoup

data class Page(val url: String, val title: String, val headings: List<String>, val text: String, val links: List<String>)

/** Readability-style extraction. Output is plain text only; page content is never treated as instructions. */
object TextExtractor {
    fun extract(html: String, url: String): Page {
        val doc = Jsoup.parse(html, url)
        val title = doc.title().trim()
        doc.select("script,style,noscript,nav,footer,header,aside,form,iframe,svg").remove()
        val root = doc.selectFirst("article") ?: doc.selectFirst("main") ?: doc.body() ?: doc
        val headings = root.select("h1,h2,h3").map { it.text().trim() }.filter { it.isNotEmpty() }.take(20)
        val text = root.select("p,li,td,pre").map { it.text().trim() }.filter { it.length > 25 }.joinToString("\n")
        val links = root.select("a[href]").map { it.absUrl("href") }.filter { it.startsWith("http") }.distinct().take(30)
        return Page(url, title, headings, text, links)
    }

    /** First [n] sentences, for extractive summaries without a language model. */
    fun leadSentences(text: String, n: Int): String =
        text.replace("\n", " ").split(Regex("(?<=[.!?।])\\s+")).filter { it.length > 20 }.take(n).joinToString(" ")
}
