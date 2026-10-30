package com.vision.assistant.termux

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow

sealed interface Validation {
    data class Ok(val binary: String, val args: List<String>, val sensitive: Boolean) : Validation
    data class Rejected(val reason: String) : Validation
}

/**
 * Secure command gateway. The AI never supplies a raw shell line: only allow-listed templates with validated arguments.
 * Every command is wrapped in `timeout` so it cannot run forever.
 */
object CommandPolicy {
    const val PREFIX = "/data/data/com.termux/files/usr/bin/"
    const val HOME = "/data/data/com.termux/files/home"
    const val TIMEOUT_SECONDS = 30
    private val badChars = Regex("[;|&$`<>\\n\\r\\\\\"'*?(){}\\[\\]]")
    private val scriptName = Regex("^[A-Za-z0-9_-]{1,40}\\.py$")

    fun validate(command: String, args: List<String>): Validation {
        if (args.any { badChars.containsMatchIn(it) || it.contains("..") }) return Validation.Rejected("Arguments contain forbidden characters.")
        return when (command) {
            "uname" -> if (args.all { it == "-a" }) Validation.Ok("uname", args, false) else Validation.Rejected("Only 'uname -a' is allowed.")
            "date" -> if (args.isEmpty()) Validation.Ok("date", args, false) else Validation.Rejected("'date' takes no arguments.")
            "ls" -> {
                val path = args.singleOrNull() ?: return Validation.Rejected("'ls' needs exactly one path.")
                if (path == "$HOME/storage" || path.startsWith("$HOME/storage/") || path == "$HOME/vision-scripts" || path.startsWith("$HOME/vision-scripts/"))
                    Validation.Ok("ls", listOf("-la", path), false) else Validation.Rejected("Path not allowed.")
            }
            "python3" -> {
                val name = args.singleOrNull() ?: return Validation.Rejected("Exactly one approved script name is required.")
                if (scriptName.matches(name)) Validation.Ok("python3", listOf("$HOME/vision-scripts/$name"), true) else Validation.Rejected("Invalid script name.")
            }
            else -> Validation.Rejected("Command '$command' is not in the allowlist.")
        }
    }
}

object TermuxResults { val last = MutableStateFlow("") }

class TermuxResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val b = intent.getBundleExtra("result")
        val out = b?.getString("stdout").orEmpty().take(4000)
        val err = b?.getString("stderr").orEmpty().take(1000)
        val code = b?.getInt("exitCode", -1)
        TermuxResults.last.value = "exit=$code\n$out" + if (err.isNotBlank()) "\nERR: $err" else ""
    }
}

class TermuxGateway(private val ctx: Context) {
    fun isInstalled(): Boolean = try {
        ctx.packageManager.getPackageInfo("com.termux", 0); true
    } catch (_: PackageManager.NameNotFoundException) { false }

    /** Sends a validated command. Termux must have allow-external-apps=true and the RUN_COMMAND permission granted. */
    fun run(v: Validation.Ok): String? {
        if (!isInstalled()) return "Termux is not installed."
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
        val result = PendingIntent.getBroadcast(ctx, 0, Intent(ctx, TermuxResultReceiver::class.java), flags)
        val i = Intent("com.termux.RUN_COMMAND").setClassName("com.termux", "com.termux.app.RunCommandService")
            .putExtra("com.termux.RUN_COMMAND_PATH", CommandPolicy.PREFIX + "timeout")
            .putExtra("com.termux.RUN_COMMAND_ARGUMENTS", (listOf(CommandPolicy.TIMEOUT_SECONDS.toString(), CommandPolicy.PREFIX + v.binary) + v.args).toTypedArray())
            .putExtra("com.termux.RUN_COMMAND_WORKDIR", CommandPolicy.HOME)
            .putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
            .putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", result)
        return try {
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
            null
        } catch (e: Exception) { "Termux refused the command: ${e.message}" }
    }
}
