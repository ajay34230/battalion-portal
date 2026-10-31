package com.vision.assistant.code

import org.json.JSONObject

enum class ToolStatus { INSTALLED_AND_READY, INSTALLED_BUT_OUTDATED, INSTALLED_BUT_INCOMPATIBLE, PARTIALLY_INSTALLED, NOT_INSTALLED, INSTALLATION_REQUIRED, PERMISSION_REQUIRED, UNSUPPORTED }

/** What VISION knows about a development tool. Sizes are rough estimates and are labelled as such in the UI. */
data class ToolSpec(
    val id: String, val name: String, val probe: String, val minVersion: String,
    val termuxPackage: String?, val purpose: String, val license: String, val approxMb: Int,
    val unsupportedNote: String? = null, val dependsOn: List<String> = emptyList(),
)

data class ToolState(val id: String, val status: ToolStatus, val version: String, val checkedAt: Long)

object ToolCatalogue {
    const val SOURCE = "Termux package repository (packages.termux.dev) — installed with `pkg`"
    val all = listOf(
        ToolSpec("python", "Python 3", "python3 --version", "3.9", "python", "Run Python scripts and tests", "PSF License (free, open source)", 60),
        ToolSpec("pip", "pip (Python packages)", "pip --version", "21.0", null, "Install Python libraries (comes with the python package)", "MIT", 0, dependsOn = listOf("python")),
        ToolSpec("node", "Node.js", "node --version", "18", "nodejs", "Run JavaScript outside the browser and use npm", "MIT (free, open source)", 55),
        ToolSpec("npm", "npm", "npm --version", "8", null, "Install JavaScript libraries (comes with nodejs)", "Artistic-2.0", 0, dependsOn = listOf("node")),
        ToolSpec("git", "Git", "git --version", "2.30", "git", "Version control: history, branches, diffs, revert", "GPL-2.0 (free, open source)", 25),
        ToolSpec("java", "OpenJDK 17", "java -version", "17", "openjdk-17", "Needed for Kotlin/Java and Gradle", "GPL-2.0 with Classpath Exception", 150),
        ToolSpec("gradle", "Gradle", "gradle --version", "8.0", "gradle", "Builds Kotlin/Java and Android projects", "Apache-2.0", 130, dependsOn = listOf("java")),
        ToolSpec("clang", "C/C++ compiler (clang)", "clang --version", "14", "clang", "Compile C and C++ programs", "Apache-2.0 with LLVM exception", 120),
        ToolSpec("go", "Go", "go version", "1.20", "golang", "Compile and run Go programs", "BSD-3-Clause", 150),
        ToolSpec("rust", "Rust (rustc)", "rustc --version", "1.70", "rust", "Compile Rust programs", "MIT / Apache-2.0", 350),
        ToolSpec("php", "PHP", "php --version", "8.0", "php", "Run PHP scripts and a local PHP server", "PHP License", 40),
        ToolSpec("ruby", "Ruby", "ruby --version", "3.0", "ruby", "Run Ruby scripts", "Ruby License / BSD-2", 40),
        ToolSpec("sqlite", "SQLite", "sqlite3 --version", "3.35", "sqlite", "Local SQL database and inspection shell", "Public domain", 3),
        ToolSpec("typescript", "TypeScript (tsc)", "tsc --version", "5.0", null, "Compile TypeScript (installed with npm — a download from the npm registry)", "Apache-2.0", 25, dependsOn = listOf("node")),
        ToolSpec("android-sdk", "Android SDK + build-tools", "sdkmanager --version", "34", null, "Build Android APKs on the device", "Android SDK License (free, must be accepted)", 3000,
            unsupportedNote = "The official Android SDK build tools are x86_64 programs and are not supported on ARM phones. Use the free GitHub Actions cloud build instead (needs your approval and a repository)."),
        ToolSpec("godot", "Godot game engine", "godot --version", "4.0", null, "Build games", "MIT", 0, unsupportedNote = "No supported on-phone export pipeline here. Use a computer or GitHub Actions."),
    )
    fun byId(id: String) = all.firstOrNull { it.id == id }
}

object VersionUtil {
    /** First dotted number in the output: "Python 3.11.6" -> "3.11.6"; `openjdk version "17.0.9"` -> "17.0.9". */
    fun parse(output: String): String? = Regex("(\\d+(?:\\.\\d+){0,3})").find(output)?.groupValues?.get(1)

    fun compare(a: String, b: String): Int {
        val x = a.split('.').map { it.toIntOrNull() ?: 0 }; val y = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) { val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }; if (d != 0) return d }
        return 0
    }
}

object DevInventory {
    fun status(spec: ToolSpec, probeOutput: String?, termuxAvailable: Boolean, states: Map<String, ToolState> = emptyMap()): ToolState {
        val now = System.currentTimeMillis()
        if (spec.unsupportedNote != null && probeOutput.isNullOrBlank()) return ToolState(spec.id, ToolStatus.UNSUPPORTED, "", now)
        if (!termuxAvailable) return ToolState(spec.id, ToolStatus.PERMISSION_REQUIRED, "", now)
        if (probeOutput.isNullOrBlank() || probeOutput.contains("not found", true) || probeOutput.contains("No such file", true)) {
            val missingDeps = spec.dependsOn.filter { states[it]?.status.let { s -> s == null || s == ToolStatus.NOT_INSTALLED || s == ToolStatus.INSTALLATION_REQUIRED } }
            return ToolState(spec.id, if (missingDeps.isNotEmpty()) ToolStatus.INSTALLATION_REQUIRED else ToolStatus.NOT_INSTALLED, "", now)
        }
        val v = VersionUtil.parse(probeOutput) ?: return ToolState(spec.id, ToolStatus.PARTIALLY_INSTALLED, "", now)
        val st = if (VersionUtil.compare(v, spec.minVersion) >= 0) ToolStatus.INSTALLED_AND_READY else ToolStatus.INSTALLED_BUT_OUTDATED
        return ToolState(spec.id, st, v, now)
    }

    /** One shell script that prints "id=<first line of output>" for every probe. Run once, with confirmation (Category A: inspection). */
    fun probeScript(): String = ToolCatalogue.all.joinToString("\n") {
        "echo \"${it.id}=\$( ( ${it.probe} ) 2>&1 | head -n 1 )\""
    }

    fun parseProbe(output: String): Map<String, String> =
        output.lineSequence().mapNotNull { l -> val i = l.indexOf('='); if (i > 0 && l.substring(0, i).matches(Regex("[a-z-]+"))) l.substring(0, i) to l.substring(i + 1).trim() else null }.toMap()

    fun toJson(states: Collection<ToolState>): String = JSONObject().also { o -> states.forEach { o.put(it.id, JSONObject().put("s", it.status.name).put("v", it.version).put("t", it.checkedAt)) } }.toString()
    fun fromJson(s: String): Map<String, ToolState> = try {
        val o = JSONObject(s); o.keys().asSequence().associateWith { k -> val e = o.getJSONObject(k); ToolState(k, ToolStatus.valueOf(e.getString("s")), e.optString("v"), e.optLong("t")) }
    } catch (_: Exception) { emptyMap() }
}

/** Decides which tools a request or project needs. Dynamic: depends on the request and the files, not a fixed list. */
object RequirementDetector {
    fun detect(request: String, files: List<String>): List<String> {
        val l = request.lowercase()
        val ext = files.map { it.substringAfterLast('.', "").lowercase() }.toSet()
        val need = linkedSetOf<String>()
        if ("py" in ext || Regex("\\bpython|\\.py\\b|pandas|flask|django|script").containsMatchIn(l)) { need.add("python"); need.add("pip") }
        if (ext.any { it in setOf("js", "mjs", "ts", "tsx", "jsx") } && Regex("node|npm|react|vue|express|server|backend|vite|typescript").containsMatchIn(l) || Regex("\\bnode(js)?\\b|\\bnpm\\b|express|react|vite").containsMatchIn(l)) { need.add("node"); need.add("npm") }
        if ("ts" in ext || "tsx" in ext || "typescript" in l) need.add("typescript")
        if (ext.any { it in setOf("kt", "java") } || Regex("kotlin|\\bjava\\b|gradle").containsMatchIn(l)) { need.add("java"); need.add("gradle") }
        if (Regex("android|apk|compose").containsMatchIn(l)) { need.add("java"); need.add("gradle"); need.add("android-sdk") }
        if (ext.any { it in setOf("c", "cpp", "cc", "h", "hpp") } || Regex("\\bc\\+\\+|\\bcpp\\b|\\bc program|clang|gcc").containsMatchIn(l)) need.add("clang")
        if ("go" in ext || Regex("\\bgolang\\b|\\bgo (program|app|server)").containsMatchIn(l)) need.add("go")
        if ("rs" in ext || Regex("\\brust\\b|cargo").containsMatchIn(l)) need.add("rust")
        if ("php" in ext || Regex("\\bphp\\b").containsMatchIn(l)) need.add("php")
        if ("rb" in ext || Regex("\\bruby\\b").containsMatchIn(l)) need.add("ruby")
        if ("sql" in ext || Regex("\\bsql\\b|sqlite|database").containsMatchIn(l)) need.add("sqlite")
        if (Regex("\\bgit\\b|repository|version control|commit").containsMatchIn(l)) need.add("git")
        if (Regex("\\bgame\\b").containsMatchIn(l) && Regex("godot|unity|3d").containsMatchIn(l)) need.add("godot")
        return need.toList() // HTML/CSS/JS-only projects need nothing: the built-in WebView preview is enough
    }
}

/** The plain-language summary shown before anything is downloaded or installed. */
object InstallSummary {
    fun build(spec: ToolSpec, reason: String, freeStorageMb: Long): String {
        if (spec.unsupportedNote != null) return "${spec.name} cannot be installed on this phone.\n\n${spec.unsupportedNote}"
        val pkg = spec.termuxPackage ?: return "${spec.name} comes with another tool (${spec.dependsOn.joinToString()}) or is installed by it."
        val enough = freeStorageMb <= 0 || freeStorageMb > spec.approxMb + 1024
        return buildString {
            appendLine("Resource: ${spec.name}")
            appendLine("Purpose: ${spec.purpose}")
            appendLine("Why now: $reason")
            appendLine("Source: ${ToolCatalogue.SOURCE}")
            appendLine("Version: whatever the Termux repository currently offers (will be shown after install)")
            appendLine("License: ${spec.license}")
            appendLine("Cost: free")
            appendLine("Download/storage: about ${spec.approxMb} MB (rough estimate; the real size is shown by pkg)")
            appendLine("Permissions: runs inside Termux only; needs Termux Advanced mode and internet")
            appendLine("Method: pkg install -y $pkg (packages are signed by the Termux repository)")
            appendLine("Security: nothing runs until you approve; installed files stay in Termux's private folder")
            if (!enough) append("⚠ Free storage looks too low for this install.")
        }.trim()
    }
    fun command(spec: ToolSpec): String? = spec.termuxPackage?.let { "pkg install -y $it" }
}
