package com.vision.assistant.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Asks Hugging Face which compatible models exist. Sends only the search words (and the repository name when listing files): no chats, memory or device identifiers. */
object ModelDiscovery {
    private const val MAX_BODY = 3_000_000

    private fun get(url: String): String {
        require(url.startsWith("https://huggingface.co/")) { "Blocked host" }
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 20_000; instanceFollowRedirects = false
            setRequestProperty("User-Agent", "VISION-Android/0.1"); setRequestProperty("Accept", "application/json")
        }
        try {
            if (c.responseCode != 200) error("Hugging Face answered HTTP ${c.responseCode}")
            val out = java.io.ByteArrayOutputStream(); val buf = ByteArray(8192)
            c.inputStream.use { i -> while (true) { val n = i.read(buf); if (n < 0) break; out.write(buf, 0, n); if (out.size() > MAX_BODY) error("Response too large") } }
            return out.toString(Charsets.UTF_8.name())
        } finally { c.disconnect() }
    }

    suspend fun search(query: String): Result<List<ModelCatalog.Repo>> = withContext(Dispatchers.IO) { runCatching { ModelCatalog.parseSearch(get(ModelCatalog.searchUrl(query))) } }
    suspend fun files(repo: String): Result<List<ModelCatalog.RemoteFile>> = withContext(Dispatchers.IO) {
        runCatching { require(ModelCatalog.validRepo(repo)); ModelCatalog.parseTree(get(ModelCatalog.treeUrl(repo))) }
    }
}
