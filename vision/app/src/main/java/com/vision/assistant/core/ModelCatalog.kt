package com.vision.assistant.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * Parsing, validation and compatibility rules for models discovered online. Pure code (no network, no Android) so it can be unit tested.
 * Everything that comes from the internet is treated as untrusted text: ids and file names must match strict patterns, links are built here
 * (never taken from the response), and a file is only offered when the host published a SHA-256 for it.
 */
object ModelCatalog {
    data class Repo(val id: String, val downloads: Long, val likes: Long, val tags: List<String>, val license: String)
    data class RemoteFile(val path: String, val sizeBytes: Long, val sha256: String)
    enum class Compat(val label: String) { COMPATIBLE("Compatible"), LIMITED("Compatible with limitations"), EXPERIMENTAL("Experimental"), INCOMPATIBLE("Incompatible") }
    data class Assessment(val level: Compat, val reasons: List<String>)

    /** Only this publisher is searched: it is the one whose files the installed engine (LiteRT-LM) can open. */
    const val AUTHOR = "litert-community"
    private val repoRe = Regex("$AUTHOR/[A-Za-z0-9._-]{1,100}")
    private val fileRe = Regex("[A-Za-z0-9._-]{1,150}\\.litertlm")
    private val shaRe = Regex("[0-9a-f]{64}")
    const val MAX_BYTES = 12L * 1024 * 1024 * 1024

    fun validRepo(id: String) = repoRe.matches(id)
    fun validFile(name: String) = fileRe.matches(name)

    fun searchUrl(query: String): String {
        val q = query.trim().take(60).filter { it.isLetterOrDigit() || it in " ._-" }
        return "https://huggingface.co/api/models?author=$AUTHOR&sort=downloads&direction=-1&limit=30" + if (q.isNotBlank()) "&search=" + java.net.URLEncoder.encode(q, "UTF-8") else ""
    }
    fun treeUrl(repo: String) = "https://huggingface.co/api/models/$repo/tree/main"
    fun fileUrl(repo: String, file: String) = "https://huggingface.co/$repo/resolve/main/$file"

    fun parseSearch(json: String): List<Repo> = try {
        val a = JSONArray(json)
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id"); if (!validRepo(id)) return@mapNotNull null
            val tags = o.optJSONArray("tags")?.let { t -> (0 until t.length()).map { t.optString(it).take(40) } }.orEmpty().take(40)
            val license = tags.firstOrNull { it.startsWith("license:") }?.removePrefix("license:").orEmpty().take(40)
            Repo(id, o.optLong("downloads"), o.optLong("likes"), tags, license)
        }
    } catch (_: Exception) { emptyList() }

    /** `.litertlm` files with a published SHA-256 and a plausible size. */
    fun parseTree(json: String): List<RemoteFile> = try {
        val a = JSONArray(json)
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            if (o.optString("type") != "file") return@mapNotNull null
            val path = o.optString("path"); if (!validFile(path)) return@mapNotNull null
            val lfs: JSONObject = o.optJSONObject("lfs") ?: return@mapNotNull null
            val sha = lfs.optString("oid").lowercase(); val size = lfs.optLong("size", o.optLong("size"))
            if (!shaRe.matches(sha) || size <= 0 || size > MAX_BYTES) return@mapNotNull null
            RemoteFile(path, size, sha)
        }
    } catch (_: Exception) { emptyList() }

    /** Rough capability tags guessed from the model's name and tags. A guess, shown as such. */
    fun categories(name: String, tags: List<String> = emptyList()): List<String> {
        val t = (name + " " + tags.joinToString(" ")).lowercase(); val out = mutableListOf("General conversation")
        if (Regex("coder|code").containsMatchIn(t)) out += "Programming"
        if (Regex("math").containsMatchIn(t)) out += "Mathematics"
        if (Regex("(^|[^a-z])(vl|vision)([^a-z]|$)|gemma-?3n|gemma-?4|multimodal|image-text").containsMatchIn(t)) out += "Vision (images)"
        if (Regex("embed").containsMatchIn(t)) out += "Embeddings"
        if (Regex("qwen|gemma|llama|multilingual|granite").containsMatchIn(t)) out += "Multilingual (Hindi quality unmeasured)"
        return out
    }

    /**
     * Whether a model file of [sizeMb] is expected to run on this phone. The memory estimate is the file size plus working memory;
     * real use also needs the context cache and tokenizer, so "Compatible" is only claimed with a clear margin.
     */
    fun assess(sizeMb: Long, totalRamMb: Long, freeStorageMb: Long, is64Bit: Boolean): Assessment {
        val r = mutableListOf<String>()
        if (!is64Bit) return Assessment(Compat.INCOMPATIBLE, listOf("This model needs a 64-bit phone."))
        if (sizeMb + 1_024 > freeStorageMb) return Assessment(Compat.INCOMPATIBLE, listOf("Not enough free storage (needs ${sizeMb + 1_024} MB including a 1 GB reserve, have $freeStorageMb MB)."))
        val pct = if (totalRamMb > 0) sizeMb * 100 / totalRamMb else 100
        return when {
            pct <= 25 -> Assessment(Compat.COMPATIBLE, listOf("Uses about $pct% of RAM for the weights."))
            pct <= 40 -> Assessment(Compat.LIMITED, listOf("Uses about $pct% of RAM for the weights; expect slower replies and Android may close it when memory is short."))
            pct <= 60 -> Assessment(Compat.EXPERIMENTAL, listOf("Uses about $pct% of RAM for the weights; likely very slow or unstable on this phone."))
            else -> Assessment(Compat.INCOMPATIBLE, listOf("Needs about $pct% of this phone's RAM; it would not stay loaded."))
        }
    }

    /** Turns a discovered file into a downloadable entry. The download itself still asks for the user's approval and is SHA-256 verified. */
    fun toEntry(repo: Repo, f: RemoteFile, totalRamMb: Long): ResourceEntry? {
        if (!validRepo(repo.id) || !validFile(f.path)) return null
        val name = repo.id.substringAfter('/')
        val id = "hf-" + (name + "-" + f.path.removeSuffix(".litertlm")).lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(70)
        val sizeMb = f.sizeBytes / 1_048_576
        return ResourceEntry(
            id, ResourceKind.LLM, "$name — ${f.path.removeSuffix(".litertlm")}", fileUrl(repo.id, f.path), f.path, f.sha256, f.sizeBytes,
            minRamMb = sizeMb * 2, license = repo.license.ifBlank { "see model page" }, languages = categories(name, repo.tags).joinToString(", "),
            note = "Found online (Hugging Face, ${repo.downloads} downloads). Not tested on a phone by VISION. Capabilities are guessed from the name.",
            multimodal = categories(name, repo.tags).contains("Vision (images)"),
            experimental = sizeMb * 100 > totalRamMb * 40,
        )
    }

    // ---- saved discoveries ----
    fun encode(e: ResourceEntry): JSONObject = JSONObject().put("id", e.id).put("title", e.title).put("url", e.url).put("file", e.fileName).put("sha", e.sha256).put("size", e.sizeBytes)
        .put("minRam", e.minRamMb).put("license", e.license).put("lang", e.languages).put("note", e.note).put("mm", e.multimodal).put("exp", e.experimental)

    /** Re-validates everything read back from storage, so a tampered preferences file cannot add an arbitrary download link. */
    fun decode(o: JSONObject): ResourceEntry? = try {
        val url = o.getString("url"); val file = o.getString("file"); val sha = o.getString("sha")
        val m = Regex("https://huggingface\\.co/($AUTHOR/[A-Za-z0-9._-]{1,100})/resolve/main/([A-Za-z0-9._-]{1,150}\\.litertlm)").matchEntire(url)
        if (m == null || m.groupValues[2] != file || !shaRe.matches(sha) || !o.getString("id").startsWith("hf-") || o.getLong("size") !in 1..MAX_BYTES) null
        else ResourceEntry(o.getString("id"), ResourceKind.LLM, o.getString("title").take(120), url, file, sha, o.getLong("size"), o.getLong("minRam"), o.optString("license").take(60), o.optString("lang").take(200), o.optString("note").take(300), o.optBoolean("mm"), o.optBoolean("exp"))
    } catch (_: Exception) { null }
}
