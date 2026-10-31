package com.vision.assistant.diag

import android.content.Context
import org.json.JSONObject
import java.io.File

/** One diagnostic record. Never contains what the user said or what VISION answered: only kinds, timings, model names and error text. */
data class DiagEntry(val t: Long, val kind: String, val msg: String = "", val ms: Long = -1, val model: String = "", val backend: String = "")

/** Pure encoding / summary code so it can be tested without Android. */
object DiagFormat {
    fun encode(e: DiagEntry): String = JSONObject().put("t", e.t).put("k", e.kind).put("m", e.msg.take(400).replace('\n', ' ')).put("ms", e.ms).put("model", e.model).put("be", e.backend).toString()
    fun decode(line: String): DiagEntry? = try { JSONObject(line).let { DiagEntry(it.getLong("t"), it.getString("k"), it.optString("m"), it.optLong("ms", -1), it.optString("model"), it.optString("be")) } } catch (_: Exception) { null }

    private val timed = setOf("plan", "chat", "generate", "summarize", "complete", "load", "reply_total")

    /** Human lines such as "chat · Qwen3 0.6B · CPU: 12 runs, avg 8.4 s, slowest 21.0 s". */
    fun summarise(entries: List<DiagEntry>): List<String> {
        val groups = entries.filter { it.kind in timed && it.ms >= 0 }.groupBy { Triple(it.kind, it.model, it.backend) }
        return groups.entries.sortedByDescending { (_, v) -> v.sumOf { it.ms } }.map { (k, v) ->
            val avg = v.sumOf { it.ms } / v.size
            "${k.first}${if (k.second.isNotBlank()) " · " + k.second else ""}${if (k.third.isNotBlank()) " · " + k.third else ""}: ${v.size} run(s), avg ${sec(avg)}, slowest ${sec(v.maxOf { it.ms })}"
        }
    }
    fun problems(entries: List<DiagEntry>, n: Int = 8): List<DiagEntry> = entries.filter { it.kind in setOf("crash", "error", "stall", "load_failed", "model_crash") }.takeLast(n).reversed()
    fun sec(ms: Long) = String.format(java.util.Locale.US, "%.1f s", ms / 1000.0)
    fun render(entries: List<DiagEntry>, header: String): String = buildString {
        append(header).append('\n').append("---- summary ----\n"); summarise(entries).forEach { append(it).append('\n') }
        append("---- events (newest last) ----\n")
        for (e in entries) append(java.util.Date(e.t)).append(' ').append(e.kind).append(' ').append(if (e.ms >= 0) sec(e.ms) + " " else "").append(e.model).append(' ').append(e.backend).append(' ').append(e.msg).append('\n')
    }
}

/** Local diagnostic log (ring buffer file, about 400 KB). Nothing is sent anywhere: the user decides whether to share the exported text. */
object DiagLog {
    private const val MAX_BYTES = 400_000L
    @Volatile private var file: File? = null
    private val lock = Any()

    fun init(ctx: Context) { file = File(ctx.applicationContext.filesDir, "diagnostics.log") }

    fun log(kind: String, msg: String = "", ms: Long = -1, model: String = "", backend: String = "") {
        val f = file ?: return
        try {
            synchronized(lock) {
                f.appendText(DiagFormat.encode(DiagEntry(System.currentTimeMillis(), kind, msg, ms, model, backend)) + "\n")
                if (f.length() > MAX_BYTES) { val keep = f.readLines().takeLast(1500); f.writeText(keep.joinToString("\n") + "\n") }
            }
        } catch (_: Throwable) { }
    }

    /** Times [block], logs it under [kind] and returns its result (also logs failures as "error" and rethrows). */
    inline fun <T> timed(kind: String, model: String = "", backend: String = "", block: () -> T): T {
        val t0 = System.currentTimeMillis()
        try { return block().also { log(kind, ms = System.currentTimeMillis() - t0, model = model, backend = backend) } }
        catch (e: Throwable) { log("error", "$kind failed: ${e.message ?: e.javaClass.simpleName}", System.currentTimeMillis() - t0, model, backend); throw e }
    }

    fun crash(thread: String, e: Throwable) = log("crash", "$thread: ${e.javaClass.simpleName}: ${e.message} @ ${e.stackTrace.take(4).joinToString(" <- ")}")

    fun read(): List<DiagEntry> = try { synchronized(lock) { file?.takeIf { it.exists() }?.readLines()?.mapNotNull { DiagFormat.decode(it) }.orEmpty() } } catch (_: Throwable) { emptyList() }
    fun clear() { try { synchronized(lock) { file?.delete() } } catch (_: Throwable) { } }

    fun exportText(ctx: Context): String {
        val dm = ctx.resources.displayMetrics
        val header = "VISION diagnostics · ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} · Android ${android.os.Build.VERSION.RELEASE} · ${dm.widthPixels}x${dm.heightPixels}\n" +
            "Contains timings, model names and errors only. It never contains your messages or replies."
        return DiagFormat.render(read(), header)
    }
}
