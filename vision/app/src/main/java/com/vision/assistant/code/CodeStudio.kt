package com.vision.assistant.code

import android.content.Context
import com.vision.assistant.VisionApp

/** One place where the app-level pieces of the coding engine are wired together. */
class CodeStudio(private val app: VisionApp, private val ctx: Context) {
    val store = ProjectStore(java.io.File(ctx.filesDir, "projects"))
    private val logFile = java.io.File(ctx.filesDir, "code-log.txt")

    fun log(line: String) {
        try {
            val stamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())
            if (logFile.length() > 200_000) logFile.writeText(logFile.readLines().takeLast(500).joinToString("\n") + "\n")
            logFile.appendText("$stamp  $line\n")
        } catch (_: Exception) {}
    }
    fun readLog(): String = try { logFile.takeIf { it.exists() }?.readLines()?.takeLast(80)?.joinToString("\n").orEmpty() } catch (_: Exception) { "" }

    fun createFromTemplate(name: String, templateId: String): Result<Unit> {
        val t = Templates.byId(templateId) ?: return Result.failure(IllegalArgumentException("Unknown template"))
        val c = store.create(name); if (c.isFailure) return Result.failure(c.exceptionOrNull()!!)
        t.files.forEach { (p, text) -> store.write(name, p, text) }
        log("Created project '$name' from template ${t.id}")
        return Result.success(Unit)
    }

    /** Asks the installed model to write or change files. Everything it returns is validated by [ProjectStore]; a snapshot is taken first. */
    suspend fun build(name: String, request: String): String {
        val engine = app.engines.current("kotlin code")
        if (!engine.isNeuralModelLoaded) return "No language model is installed, so I can't write new code from a description. Templates, editing, preview and export still work offline. Install a model in Models & downloads."
        store.snapshot(name, "Before: ${request.take(40)}")
        val existing = store.files(name).take(12).joinToString("\n") { f -> "=== $f ===\n" + (store.read(name, f)?.take(4000) ?: "") }
        val prompt = "Request: $request\n" + if (existing.isNotBlank()) "Existing project files:\n$existing\n\nReturn only the files you add or change, each COMPLETE." else "Create a new small project."
        val out = engine.complete(CodeParser.SYSTEM, prompt) ?: return "The model did not answer. Try a smaller request."
        val files = CodeParser.parse(out) ?: return "The model's answer wasn't usable project files (small on-device models sometimes can't). Try rephrasing or a simpler request."
        val results = files.map { f -> store.write(name, f.path, f.content).fold({ "✓ ${f.path}" }, { "✗ ${f.path}: ${it.message}" }) }
        log("Build '$name': $request -> ${results.joinToString()}")
        val fix = FixLoop(store, { s, p -> engine.complete(s, p) }).run(name, request)
        log("Check/fix '$name': fixed=${fix.fixed} rounds=${fix.rounds} remaining=${fix.remaining}")
        return results.joinToString("\n") + "\n" + if (fix.fixed) "Checks pass (static checks only — I have not run the program)." else "Some problems remain:\n" + fix.remaining.joinToString("\n") { "• $it" }
    }

    suspend fun autoFix(name: String): FixReport {
        val engine = app.engines.current("kotlin code")
        val loop = FixLoop(store, { s, p -> engine.complete(s, p) })
        return loop.run(name).also { log("Auto-fix '$name': fixed=${it.fixed} rounds=${it.rounds}") }
    }

    /** Researches a programming question on GitHub, Stack Overflow and MDN, remembers what was found, and logs it. */
    suspend fun research(question: String): String {
        val (sources, problems) = com.vision.assistant.research.DevResearch.search(question)
        val text = com.vision.assistant.research.DevResearch.format(sources, problems)
        log("Research: $question -> ${sources.size} sources")
        if (sources.isNotEmpty()) app.database.memory().insert(com.vision.assistant.data.MemoryItem(category = "research", content = "Coding research: ${question.take(80)} — " + sources.take(3).joinToString("; ") { it.title + " " + it.url }, source = "dev-research"))
        return text
    }

    /** Critical-logic step: the model compares two approaches (complexity, risks) and proposes tests. Plain advice — the tests still have to be run. */
    suspend fun compareApproaches(problem: String): String {
        val engine = app.engines.current("kotlin code")
        if (!engine.isNeuralModelLoaded) return "No language model is installed, so I can't compare approaches. Install one in Models & downloads."
        return engine.complete("You are a careful senior engineer. Be precise and honest about uncertainty.", com.vision.assistant.research.DevResearch.comparePrompt(problem)) ?: "The model did not answer."
    }

    data class Match(val file: String, val line: Int, val text: String)
    /** Case-insensitive text search across every readable file of a project. */
    fun searchFiles(name: String, query: String, max: Int = 60): List<Match> {
        if (query.length < 2) return emptyList()
        val out = ArrayList<Match>()
        for (f in store.files(name)) {
            val t = store.read(name, f) ?: continue
            t.lineSequence().forEachIndexed { i, l -> if (out.size < max && l.contains(query, ignoreCase = true)) out.add(Match(f, i + 1, l.trim().take(120))) }
        }
        return out
    }

    fun problems(name: String): List<Problem> {
        val files = store.files(name)
        return StaticChecker.check(files.associateWith { store.read(name, it).orEmpty() })
    }
}
