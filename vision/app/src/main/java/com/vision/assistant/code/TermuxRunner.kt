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

    private val SLUG = Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,39}")
    fun validSlug(s: String) = SLUG.matches(s)

    private fun fileLines(files: Map<String, String>): String {
        val b64 = java.util.Base64.getEncoder(); val sb = StringBuilder()
        for ((p, c) in files) {
            val dir = p.substringBeforeLast('/', "")
            if (dir.isNotEmpty()) sb.append("mkdir -p '").append(dir).append("'\n")
            sb.append("printf %s '").append(b64.encodeToString(c.toByteArray())).append("' | base64 -d > '").append(p).append("'\n")
        }
        return sb.toString()
    }

    private fun prune(slug: String) = "ls -1dt \"\$b\"/$slug-* 2>/dev/null | tail -n +6 | while read -r x; do rm -r -- \"\$x\"; done || true\n"

    /**
     * Null when the slug is unsafe or the project is too large to send in one command.
     * The new files are written to a staging folder first; only when that fully succeeds is the old project moved
     * (never deleted) to ~/projects/.vision-backups and the staging folder put in its place. A failure while writing leaves the old project untouched.
     * Backups beyond the newest five for this project are pruned, and only inside the backups folder.
     */
    fun script(projectSlug: String, files: Map<String, String>, command: String): String? {
        if (!validSlug(projectSlug)) return null
        val sb = StringBuilder()
        sb.append("set -e; s=\"\$HOME/projects\"; d=\"\$s/$projectSlug\"; n=\"\$d.vision-new\"; b=\"\$s/.vision-backups\"\n")
        sb.append("mkdir -p \"\$s\" \"\$b\"\n")
        sb.append("if [ -e \"\$n\" ]; then mv \"\$n\" \"\$b/stale-$projectSlug-\$(date +%s)\"; fi\n")
        sb.append("mkdir \"\$n\"; cd \"\$n\"\n").append(fileLines(files))
        sb.append("cd \"\$s\"\nif [ -e \"\$d\" ]; then mv \"\$d\" \"\$b/$projectSlug-\$(date +%s)\"; fi\n")
        sb.append("mv \"\$n\" \"\$d\"; cd \"\$d\"\n").append(prune(projectSlug))
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

    /** Like [script] but keeps the repository (.git) in ~/projects/<name>. A tar backup of the working files (without .git) is taken before they are replaced, so a bad run can be undone. */
    fun gitScript(projectSlug: String, files: Map<String, String>, command: String): String? {
        if (!validSlug(projectSlug)) return null
        val sb = StringBuilder("set -e; s=\"\$HOME/projects\"; d=\"\$s/$projectSlug\"; b=\"\$s/.vision-backups\"; mkdir -p \"\$d\" \"\$b\"; cd \"\$d\"\n")
        sb.append("tar -cf \"\$b/$projectSlug-\$(date +%s).tar\" --exclude=./.git . 2>/dev/null || true\n")
        sb.append("find . -path ./.git -prune -o -type f -exec rm -f {} +\n").append(fileLines(files))
        sb.append(prune(projectSlug)).append(command)
        val s = sb.toString()
        return if (s.toByteArray().size > MAX_SCRIPT_BYTES) null else s
    }

    fun requestFor(script: String) = com.vision.assistant.termux.Validation.Ok("bash", listOf("-c", script), sensitive = true, high = true)
    val homeProjects get() = CommandPolicy.HOME + "/projects"
}
