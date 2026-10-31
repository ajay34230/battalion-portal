package com.vision.assistant.code

import org.json.JSONObject

data class Problem(val file: String, val message: String) { override fun toString() = "$file: $message" }
data class GeneratedFile(val path: String, val content: String)

/** Reads the model's project output: {"files":[{"path":"...","content":"..."}], "notes":"..."} */
object CodeParser {
    fun parse(output: String): List<GeneratedFile>? {
        val s = output.indexOf('{'); val e = output.lastIndexOf('}')
        if (s < 0 || e <= s) return null
        val o = try { JSONObject(output.substring(s, e + 1)) } catch (_: Exception) { return null }
        val arr = o.optJSONArray("files") ?: return null
        val out = ArrayList<GeneratedFile>()
        for (i in 0 until arr.length()) {
            val f = arr.optJSONObject(i) ?: continue
            val p = f.optString("path"); val c = f.optString("content")
            if (p.isNotBlank()) out.add(GeneratedFile(p, c))
        }
        return out.takeIf { it.isNotEmpty() }
    }

    const val SYSTEM = "You are VISION's coding engine. Write small, correct, dependency-free programs. " +
        "Reply with ONLY one JSON object: {\"files\":[{\"path\":\"relative/path.ext\",\"content\":\"full file text\"}],\"notes\":\"one sentence\"}. " +
        "Use relative paths, no secrets or API keys, no network calls unless asked. Always give the COMPLETE content of every file you include."
}

/** Static checks that need no runtime: balanced brackets/quotes, valid JSON, HTML links that point at files that exist. */
object StaticChecker {
    fun check(files: Map<String, String>): List<Problem> {
        val problems = ArrayList<Problem>()
        for ((path, text) in files) {
            when (path.substringAfterLast('.', "").lowercase()) {
                "js", "mjs", "css", "kt", "java" -> balance(text, comments = true)?.let { problems.add(Problem(path, it)) }
                "json" -> try { org.json.JSONTokener(text).nextValue() } catch (e: Exception) { problems.add(Problem(path, "Invalid JSON: ${e.message?.take(120)}")) }
                "py" -> pythonProblems(text)?.let { problems.add(Problem(path, it)) }
                "html", "htm" -> problems.addAll(htmlProblems(path, text, files.keys))
            }
        }
        return problems
    }

    /** Bracket/quote balance that skips strings and comments. Returns a human message or null. */
    fun balance(text: String, comments: Boolean): String? {
        val stack = ArrayDeque<Pair<Char, Int>>()
        var i = 0; var line = 1
        val closers = mapOf(')' to '(', ']' to '[', '}' to '{')
        while (i < text.length) {
            val c = text[i]
            if (c == '\n') line++
            if (comments && c == '/' && i + 1 < text.length && text[i + 1] == '/') { while (i < text.length && text[i] != '\n') i++; continue }
            if (comments && c == '/' && i + 1 < text.length && text[i + 1] == '*') {
                val end = text.indexOf("*/", i + 2); if (end < 0) return "Comment opened on line $line is never closed."
                line += text.substring(i, end).count { it == '\n' }; i = end + 2; continue
            }
            if (c == '"' || c == '\'' || c == '`') {
                val startLine = line; i++
                while (i < text.length && text[i] != c) {
                    if (text[i] == '\\') i++
                    if (i < text.length && text[i] == '\n') { if (c != '`') return "String starting on line $startLine is not closed."; line++ }
                    i++
                }
                if (i >= text.length) return "String starting on line $startLine is not closed."
                i++; continue
            }
            if (c in "([{") stack.addLast(c to line)
            else if (c in ")]}") {
                val top = stack.removeLastOrNull() ?: return "Unexpected '$c' on line $line."
                if (top.first != closers[c]) return "'$c' on line $line does not match '${top.first}' from line ${top.second}."
            }
            i++
        }
        stack.lastOrNull()?.let { return "'${it.first}' opened on line ${it.second} is never closed." }
        return null
    }

    /** Cheap Python checks: bracket balance (with # comments) and block headers missing a colon. Real syntax checking needs Python (Termux). */
    fun pythonProblems(text: String): String? {
        val stripped = text.lines().joinToString("\n") { l -> val h = l.indexOf('#'); if (h >= 0 && !l.substring(0, h).contains('"') && !l.substring(0, h).contains('\'')) l.substring(0, h) else l }
        balance(stripped, comments = false)?.let { return it }
        val header = Regex("^\\s*(def|class|if|elif|else|for|while|try|except|finally|with)\\b.*")
        text.lines().forEachIndexed { idx, raw ->
            val l = raw.trimEnd().let { s -> val h = s.indexOf('#'); if (h > 0) s.substring(0, h).trimEnd() else s }
            if (header.matches(l) && !l.endsWith(":") && !l.endsWith("\\") && !l.endsWith(",") && !l.endsWith("(") && !l.contains(" if ") && l.count { it == '(' } == l.count { it == ')' } && !Regex("^\\s*(if|elif|while|for)\\b.*:\\s*\\S+").matches(l))
                return "Line ${idx + 1} looks like a block header but does not end with ':'."
        }
        return null
    }

    fun htmlProblems(path: String, text: String, all: Set<String>): List<Problem> {
        val doc = org.jsoup.Jsoup.parse(text)
        val dir = path.substringBeforeLast('/', "")
        fun resolve(ref: String): String? {
            val r = ref.substringBefore('#').substringBefore('?')
            if (r.isBlank() || r.startsWith("http") || r.startsWith("//") || r.startsWith("mailto:") || r.startsWith("tel:") || r.startsWith("data:") || r.startsWith("javascript:")) return null
            val parts = ArrayList<String>(); if (dir.isNotEmpty()) parts.addAll(dir.split('/'))
            for (seg in r.split('/')) when (seg) { "", "." -> {}; ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex); else -> parts.add(seg) }
            return parts.joinToString("/")
        }
        val out = ArrayList<Problem>()
        for ((attr, sel) in listOf("src" to "script[src], img[src]", "href" to "link[href], a[href]")) {
            for (el in doc.select(sel)) {
                val target = resolve(el.attr(attr)) ?: continue
                if (target !in all) out.add(Problem(path, "${el.tagName()} refers to '${el.attr(attr)}' but that file does not exist."))
            }
        }
        if (doc.select("title").isEmpty()) out.add(Problem(path, "Page has no <title>."))
        return out
    }
}

data class FixReport(val fixed: Boolean, val rounds: Int, val log: List<String>, val remaining: List<Problem>)

/**
 * The repair loop: check -> ask the model to fix -> apply only the files it returns -> check again.
 * Never repeats a fix for the same set of problems, stops if a round makes things worse (and restores the best version), and always leaves a snapshot to roll back to.
 */
class FixLoop(
    private val store: ProjectStore,
    private val ask: suspend (system: String, prompt: String) -> String?,
    private val extraChecks: suspend (String) -> List<Problem> = { emptyList() },
    private val maxRounds: Int = 3,
) {
    private suspend fun problems(name: String): List<Problem> {
        val textExt = setOf("js", "mjs", "css", "json", "py", "html", "htm", "kt", "java")
        val files = store.files(name).filter { it.substringAfterLast('.', "").lowercase() in textExt }.associateWith { store.read(name, it).orEmpty() }
        // Pass every file name (images etc.) so link checks do not flag existing assets.
        val withAssets = files + store.files(name).filter { it !in files }.associateWith { "" }
        return StaticChecker.check(withAssets) + extraChecks(name)
    }

    suspend fun run(name: String, instruction: String = ""): FixReport {
        val log = ArrayList<String>()
        store.snapshot(name, "Before auto-fix")
        var current = problems(name)
        if (current.isEmpty()) return FixReport(true, 0, listOf("No problems found."), emptyList())
        log.add("Found ${current.size} problem(s): " + current.joinToString("; ") { it.toString() })
        val tried = HashSet<String>()
        var best = current; var bestSnap = store.snapshot(name, "Best so far (${best.size} problems)")
        for (round in 1..maxRounds) {
            val fp = current.map { it.toString() }.sorted().joinToString("|")
            if (!tried.add(fp)) { log.add("Same problems as an earlier attempt; not repeating the same fix."); return FixReport(false, round - 1, log, current) }
            val involved = current.map { it.file }.distinct()
            val prompt = buildString {
                if (instruction.isNotBlank()) append("Task: $instruction\n")
                append("These problems were found:\n"); current.forEach { append("- $it\n") }
                append("\nCurrent files:\n")
                for (f in involved.ifEmpty { store.files(name) }) store.read(name, f)?.let { append("=== $f ===\n$it\n") }
                append("\nReturn the corrected COMPLETE files as JSON. Change only what is needed.")
            }
            val answer = try { ask(CodeParser.SYSTEM, prompt) } catch (t: Throwable) { log.add("Model failed: ${t.message}"); return FixReport(false, round - 1, log, current) }
            val files = answer?.let { CodeParser.parse(it) }
            if (files == null) { log.add("Round $round: the model did not return usable files."); continue }
            var applied = 0
            for (f in files) store.write(name, f.path, f.content).onSuccess { applied++ }.onFailure { log.add("Skipped ${f.path}: ${it.message}") }
            log.add("Round $round: updated $applied file(s).")
            current = problems(name)
            if (current.isEmpty()) { log.add("All checks pass."); return FixReport(true, round, log, emptyList()) }
            if (current.size > best.size) { store.rollback(name, bestSnap.id); log.add("Round $round made things worse; restored the best version."); current = best }
            else if (current.size < best.size) { best = current; bestSnap = store.snapshot(name, "Best so far (${best.size} problems)") }
        }
        return FixReport(false, maxRounds, log, current)
    }
}
