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

    /** Safe git subcommands the Studio offers. Commit messages are reduced to plain characters so they cannot break out of the command. */
    object Git {
        fun clean(msg: String) = msg.replace(Regex("[^\\p{L}\\p{N} .,:_()/+-]"), "").trim().take(80).ifBlank { "Update from VISION" }
        fun commit(msg: String) = "git -c user.name=VISION -c user.email=vision@localhost commit -q -m '${clean(msg)}' && git log --oneline -n 5"
        const val INIT_AND_COMMIT_PREFIX = "git init -q && git add -A && "
        const val STATUS = "git status --short --branch"
        const val LOG = "git log --oneline -n 15"
        const val DIFF = "git add -A -N && git diff --stat"
        fun initialCommit(msg: String) = INIT_AND_COMMIT_PREFIX + commit(msg)
        fun addCommit(msg: String) = "git add -A && " + commit(msg)
        fun branch(name: String) = "git checkout -q -b '${name.replace(Regex("[^A-Za-z0-9._/-]"), "").take(40).ifBlank { "work" }}' && git branch --show-current"
    }

    /** Like [script] but keeps the repository (.git) in ~/projects/<name>: only working files are replaced, so history survives between runs. */
    fun gitScript(projectSlug: String, files: Map<String, String>, command: String): String? {
        val b64 = java.util.Base64.getEncoder()
        val sb = StringBuilder("set -e; d=\"${'$'}HOME/projects/$projectSlug\"; mkdir -p \"${'$'}d\"; cd \"${'$'}d\"\nfind . -path ./.git -prune -o -type f -exec rm -f {} +\n")
        for ((p, c) in files) {
            val dir = p.substringBeforeLast('/', "")
            if (dir.isNotEmpty()) sb.append("mkdir -p '").append(dir).append("'\n")
            sb.append("printf %s '").append(b64.encodeToString(c.toByteArray())).append("' | base64 -d > '").append(p).append("'\n")
        }
        sb.append(command)
        val s = sb.toString()
        return if (s.toByteArray().size > MAX_SCRIPT_BYTES) null else s
    }

    fun requestFor(script: String) = com.vision.assistant.termux.Validation.Ok("bash", listOf("-c", script), sensitive = true, high = true)
    val homeProjects get() = CommandPolicy.HOME + "/projects"
}
