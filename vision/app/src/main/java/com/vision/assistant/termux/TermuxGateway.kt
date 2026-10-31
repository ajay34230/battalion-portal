package com.vision.assistant.termux

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withTimeoutOrNull

sealed interface Validation {
    /** [sensitive] = the owner must confirm first (shown with the exact command). [high] = messages/calls/deletes/raw shell. */
    data class Ok(val binary: String, val args: List<String>, val sensitive: Boolean, val high: Boolean = false) : Validation
    data class Rejected(val reason: String) : Validation
}

object TermuxPrefs {
    private fun p(ctx: Context) = ctx.getSharedPreferences("vision_termux", Context.MODE_PRIVATE)
    /** Advanced mode: any shell command is allowed, but ALWAYS after the owner confirms the exact text. Off by default. */
    fun advanced(ctx: Context) = p(ctx).getBoolean("advanced", false)
    fun setAdvanced(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("advanced", v).apply()
}

/**
 * Secure command gateway. Commands are passed to Termux as an argument list (never through a shell), wrapped in `timeout`.
 * Read-only and harmless actions run directly; anything that reveals private data, leaves the phone, changes files or
 * costs money needs the owner's confirmation showing the exact command. Raw shell exists only in Advanced mode.
 */
object CommandPolicy {
    const val PREFIX = "/data/data/com.termux/files/usr/bin/"
    const val HOME = "/data/data/com.termux/files/home"
    const val TIMEOUT_SECONDS = 30
    val ROOTS = listOf("$HOME/storage", "$HOME/vision-scripts", "$HOME/projects", "$HOME/vision-output")

    private val badChars = Regex("[;|&$`<>\\n\\r\\\\\"'*?(){}\\[\\]]")
    private val scriptName = Regex("^[A-Za-z0-9_-]{1,40}\\.py$")
    private val hostName = Regex("^[A-Za-z0-9.-]{1,100}$")
    private val phone = Regex("^\\+?[0-9]{3,15}$")
    private val httpsUrl = Regex("^https://[A-Za-z0-9.-]+(:\\d+)?(/[A-Za-z0-9._~%/+=&?#-]*)?$")
    private val anyUrl = Regex("^https?://\\S{4,500}$")
    private val streams = setOf("music", "ring", "alarm", "notification", "system", "call")

    private fun pathError(p: String): String? = when {
        badChars.containsMatchIn(p) || p.contains("..") -> "Arguments contain forbidden characters."
        ROOTS.none { p == it || p.startsWith("$it/") } -> "Path not allowed."
        else -> null
    }
    private fun ok(bin: String, args: List<String>, confirm: Boolean = false, high: Boolean = false) = Validation.Ok(bin, args, confirm, high)
    private fun no(r: String) = Validation.Rejected(r)
    private fun text(s: String) = s.length in 1..500

    fun validate(command: String, args: List<String>): Validation {
        if (command == "ls" || command == "cat" || command == "head" || command == "tail" || command == "wc" ||
            command == "mkdir" || command == "rm" || command == "cp" || command == "mv" || command == "termux-media-scan") {
            if (args.any { badChars.containsMatchIn(it) || it.contains("..") }) return no("Arguments contain forbidden characters.")
        }
        return when (command) {
            "uname" -> if (args.all { it == "-a" }) ok("uname", args) else no("Only 'uname -a' is allowed.")
            "date" -> if (args.isEmpty()) ok("date", args) else no("'date' takes no arguments.")
            "uptime", "whoami" -> if (args.isEmpty()) ok(command, args) else no("'$command' takes no arguments.")
            "df" -> if (args.all { it == "-h" }) ok("df", args) else no("Only 'df -h' is allowed.")
            "free" -> if (args.all { it == "-m" || it == "-h" }) ok("free", args) else no("Only 'free -m' is allowed.")
            "ls" -> {
                val path = args.singleOrNull() ?: return no("'ls' needs exactly one path.")
                pathError(path)?.let { return no(it) }
                ok("ls", listOf("-la", path))
            }
            "cat", "head", "tail", "wc" -> {
                val path = args.singleOrNull() ?: return no("'$command' needs exactly one file path.")
                pathError(path)?.let { return no(it) }
                ok(command, if (command == "head" || command == "tail") listOf("-n", "60", path) else listOf(path), confirm = true)
            }
            "mkdir" -> { val p = args.singleOrNull() ?: return no("'mkdir' needs one path."); pathError(p)?.let { return no(it) }; ok("mkdir", listOf("-p", p), confirm = true) }
            "rm" -> {
                val p = args.singleOrNull() ?: return no("'rm' needs exactly one path (no flags).")
                pathError(p)?.let { return no(it) }
                if (p in ROOTS) return no("Refusing to delete a top-level folder.")
                ok("rm", listOf(p), confirm = true, high = true)
            }
            "cp", "mv" -> {
                if (args.size != 2) return no("'$command' needs source and destination.")
                args.forEach { p -> pathError(p)?.let { return no(it) } }
                ok(command, args, confirm = true, high = command == "mv")
            }
            "ping" -> {
                if (args.size == 3 && args[0] == "-c" && args[1].toIntOrNull() in 1..5 && hostName.matches(args[2])) ok("ping", args, confirm = true)
                else no("Use: ping -c <1-5> <host>.")
            }
            "curl" -> {
                val url = args.singleOrNull() ?: return no("'curl' takes one https URL.")
                if (!httpsUrl.matches(url)) return no("Only plain https URLs are allowed.")
                ok("curl", listOf("-sS", "-L", "--max-time", "20", "--max-filesize", "1000000", url), confirm = true)
            }
            "git" -> {
                if (args.size == 3 && args[0] == "-C" && args[2] in setOf("status", "pull", "log")) {
                    pathError(args[1])?.let { return no(it) }
                    val extra = if (args[2] == "log") listOf("--oneline", "-n", "10") else emptyList()
                    return ok("git", listOf("-C", args[1], args[2]) + extra, confirm = args[2] == "pull")
                }
                if (args.size == 3 && args[0] == "clone" && httpsUrl.matches(args[1])) { pathError(args[2])?.let { return no(it) }; return ok("git", args, confirm = true) }
                no("Allowed: git -C <path> status|log|pull, git clone <https url> <path>.")
            }
            "pip" -> if (args == listOf("list")) ok("pip", args) else no("Only 'pip list' is allowed.")
            "pkg" -> when {
                args == listOf("list-installed") -> ok("pkg", args)
                args.size == 2 && args[0] == "search" && hostName.matches(args[1]) -> ok("pkg", args)
                else -> no("Allowed: pkg list-installed, pkg search <word>.")
            }
            "python3" -> {
                val name = args.singleOrNull() ?: return no("Exactly one approved script name is required.")
                if (scriptName.matches(name)) ok("python3", listOf("$HOME/vision-scripts/$name"), confirm = true) else no("Invalid script name.")
            }

            // ---- Termux:API (needs the Termux:API app + `pkg install termux-api`) ----
            "termux-battery-status", "termux-wifi-connectioninfo", "termux-telephony-deviceinfo" -> if (args.isEmpty()) ok(command, args) else no("'$command' takes no arguments.")
            "termux-sensor" -> if (args == listOf("-l")) ok(command, args) else no("Only 'termux-sensor -l' is allowed.")
            "termux-torch" -> if (args.size == 1 && args[0] in setOf("on", "off")) ok(command, args) else no("Use: termux-torch on|off.")
            "termux-volume" -> when {
                args.isEmpty() -> ok(command, args)
                args.size == 2 && args[0] in streams && args[1].toIntOrNull() in 0..30 -> ok(command, args)
                else -> no("Use: termux-volume [<stream> <0-30>].")
            }
            "termux-brightness" -> if (args.size == 1 && (args[0] == "auto" || args[0].toIntOrNull() in 0..255)) ok(command, args) else no("Use: termux-brightness auto|0-255.")
            "termux-vibrate" -> when {
                args.isEmpty() -> ok(command, args)
                args.size == 2 && args[0] == "-d" && args[1].toIntOrNull() in 1..2000 -> ok(command, args)
                else -> no("Use: termux-vibrate [-d <1-2000>].")
            }
            "termux-toast", "termux-tts-speak", "termux-clipboard-set" -> { val t = args.singleOrNull() ?: return no("'$command' takes one text argument."); if (text(t)) ok(command, args) else no("Text must be 1-500 characters.") }
            "termux-notification" -> if (args.size == 4 && args[0] == "--title" && args[2] == "--content" && text(args[1]) && text(args[3])) ok(command, args) else no("Use: termux-notification --title T --content C.")
            "termux-clipboard-get" -> if (args.isEmpty()) ok(command, args, confirm = true) else no("'termux-clipboard-get' takes no arguments.")
            "termux-location" -> when {
                args.isEmpty() -> ok(command, args, confirm = true)
                args.size == 2 && args[0] == "-p" && args[1] in setOf("gps", "network", "passive") -> ok(command, args, confirm = true)
                else -> no("Use: termux-location [-p gps|network|passive].")
            }
            "termux-camera-photo" -> {
                if (args.size != 3 || args[0] != "-c" || args[1] !in setOf("0", "1")) return no("Use: termux-camera-photo -c 0|1 <path in vision-output>.")
                pathError(args[2])?.let { return no(it) }
                if (!args[2].startsWith("$HOME/vision-output/")) return no("Photos can only be saved in ~/vision-output.")
                ok(command, args, confirm = true)
            }
            "termux-contact-list" -> if (args.isEmpty()) ok(command, args, confirm = true) else no("'termux-contact-list' takes no arguments.")
            "termux-sms-list", "termux-call-log" -> when {
                args.isEmpty() -> ok(command, args, confirm = true)
                args.size == 2 && args[0] == "-l" && args[1].toIntOrNull() in 1..20 -> ok(command, args, confirm = true)
                else -> no("Use: $command [-l <1-20>].")
            }
            "termux-sms-send" -> if (args.size == 3 && args[0] == "-n" && phone.matches(args[1]) && text(args[2])) ok(command, args, confirm = true, high = true) else no("Use: termux-sms-send -n <number> <text>.")
            "termux-telephony-call" -> if (args.size == 1 && phone.matches(args[0])) ok(command, args, confirm = true, high = true) else no("Use: termux-telephony-call <number>.")
            "termux-wifi-enable" -> if (args.size == 1 && args[0] in setOf("true", "false")) ok(command, args, confirm = true) else no("Use: termux-wifi-enable true|false.")
            "termux-open-url" -> if (args.size == 1 && anyUrl.matches(args[0])) ok(command, args, confirm = true) else no("Use: termux-open-url <http(s) url>.")
            "termux-media-scan" -> { val p = args.singleOrNull() ?: return no("One path required."); pathError(p)?.let { return no(it) }; ok(command, args) }
            else -> no("Command '$command' is not in the allowlist.")
        }
    }

    /** Splits a command line like a shell would for quotes (no expansion, no pipes). Null on unbalanced quotes. */
    fun tokenize(line: String): List<String>? {
        val out = mutableListOf<String>(); val cur = StringBuilder(); var quote: Char? = null; var any = false
        for (c in line) {
            when {
                quote != null -> if (c == quote) quote = null else cur.append(c)
                c == '"' || c == '\'' -> { quote = c; any = true }
                c.isWhitespace() -> if (cur.isNotEmpty() || any) { out += cur.toString(); cur.clear(); any = false }
                else -> cur.append(c)
            }
        }
        if (quote != null) return null
        if (cur.isNotEmpty() || any) out += cur.toString()
        return out
    }

    /** A full command line from the owner, the model or a button. Raw shell is possible only when [advanced] is on, and always confirmed. */
    fun validateLine(line: String, advanced: Boolean = false): Validation {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return no("Empty command.")
        val toks = tokenize(trimmed) ?: return no("Unbalanced quotes.")
        val strict = validate(toks.first().substringAfterLast('/'), toks.drop(1))
        if (strict is Validation.Ok || !advanced) return strict
        return Validation.Ok("bash", listOf("-c", trimmed), sensitive = true, high = true)
    }
}

object TermuxResults {
    val last = MutableStateFlow("")
    internal val waiting = ConcurrentHashMap<Int, CompletableDeferred<String>>()
}

class TermuxResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val b = intent.getBundleExtra("result")
        val out = b?.getString("stdout").orEmpty().take(4000)
        val err = b?.getString("stderr").orEmpty().take(1000)
        val code = b?.getInt("exitCode", -1)
        val errmsg = b?.getString("errmsg").orEmpty()
        val text = "exit=$code\n$out" + (if (err.isNotBlank()) "\nERR: $err" else "") + (if (errmsg.isNotBlank()) "\nERR: $errmsg" else "")
        TermuxResults.last.value = text
        TermuxResults.waiting.remove(intent.getIntExtra("rid", -1))?.complete(text)
    }
}

class TermuxGateway(private val ctx: Context) {
    private val ids = AtomicInteger(1000)

    fun isInstalled(): Boolean = pkg("com.termux")
    fun apiInstalled(): Boolean = pkg("com.termux.api")
    private fun pkg(name: String) = try { ctx.packageManager.getPackageInfo(name, 0); true } catch (_: PackageManager.NameNotFoundException) { false }

    /** Sends a validated command. Termux must have allow-external-apps=true and the RUN_COMMAND permission granted. */
    fun run(v: Validation.Ok): String? = send(v, ids.incrementAndGet())

    /** Runs the command and waits (max ~40 s) for its output. */
    suspend fun execute(v: Validation.Ok, timeoutSeconds: Int = CommandPolicy.TIMEOUT_SECONDS): Result<String> {
        val id = ids.incrementAndGet()
        val d = CompletableDeferred<String>()
        TermuxResults.waiting[id] = d
        send(v, id, timeoutSeconds)?.let { TermuxResults.waiting.remove(id); return Result.failure(IllegalStateException(it)) }
        val r = withTimeoutOrNull((timeoutSeconds + 10) * 1000L) { d.await() }
        TermuxResults.waiting.remove(id)
        return if (r == null) Result.failure(IllegalStateException("Termux did not answer. Is allow-external-apps=true set and Termux restarted?")) else Result.success(r)
    }

    private fun send(v: Validation.Ok, id: Int, timeoutSeconds: Int = CommandPolicy.TIMEOUT_SECONDS): String? {
        if (!isInstalled()) return "Termux is not installed."
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
        val result = PendingIntent.getBroadcast(ctx, id, Intent(ctx, TermuxResultReceiver::class.java).putExtra("rid", id), flags)
        val i = Intent("com.termux.RUN_COMMAND").setClassName("com.termux", "com.termux.app.RunCommandService")
            .putExtra("com.termux.RUN_COMMAND_PATH", CommandPolicy.PREFIX + "timeout")
            .putExtra("com.termux.RUN_COMMAND_ARGUMENTS", (listOf(timeoutSeconds.toString(), CommandPolicy.PREFIX + v.binary) + v.args).toTypedArray())
            .putExtra("com.termux.RUN_COMMAND_WORKDIR", CommandPolicy.HOME)
            .putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
            .putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", result)
        return try {
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
            null
        } catch (e: Exception) { "Termux refused the command: ${e.message}" }
    }

    companion object {
        const val SETUP = "pkg update -y && pkg install -y termux-api python git curl && mkdir -p ~/.termux ~/vision-scripts ~/projects ~/vision-output && grep -q allow-external-apps ~/.termux/termux.properties 2>/dev/null || echo 'allow-external-apps=true' >> ~/.termux/termux.properties; termux-setup-storage"
    }
}
