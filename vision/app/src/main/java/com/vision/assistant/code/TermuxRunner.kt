package com.vision.assistant.code

import com.vision.assistant.termux.CommandPolicy

/** Builds the one shell script that copies a project into Termux (~/projects/<name>) and runs a command there. Needs Termux Advanced mode and the user's confirmation. */
object TermuxRunner {
    const val MAX_SCRIPT_BYTES = 90_000

    fun runnerFor(path: String): String? {
        val q = "'" + path.replace("'", "") + "'"
        return when (path.substringAfterLast('.', "").lowercase()) {
            "py" -> if (path.startsWith("test_") || path.substringAfterLast('/').startsWith("test_")) "python3 $q -v" else "python3 $q"
            "js", "mjs" -> "node $q"
            "sh" -> "bash $q"
            else -> null
        }
    }

    /** Null when the project is too large to send in one command. */
    fun script(projectSlug: String, files: Map<String, String>, command: String): String? {
        val b64 = java.util.Base64.getEncoder()
        val sb = StringBuilder("set -e; d=\"${'$'}HOME/projects/$projectSlug\"; rm -rf \"${'$'}d\"; mkdir -p \"${'$'}d\"; cd \"${'$'}d\"\n")
        for ((p, c) in files) {
            val q = "'" + p + "'"
            val dir = p.substringBeforeLast('/', "")
            if (dir.isNotEmpty()) sb.append("mkdir -p '").append(dir).append("'\n")
            sb.append("printf %s '").append(b64.encodeToString(c.toByteArray())).append("' | base64 -d > ").append(q).append('\n')
        }
        sb.append(command)
        val s = sb.toString()
        return if (s.toByteArray().size > MAX_SCRIPT_BYTES) null else s
    }

    fun requestFor(script: String) = com.vision.assistant.termux.Validation.Ok("bash", listOf("-c", script), sensitive = true, high = true)
    val homeProjects get() = CommandPolicy.HOME + "/projects"
}
