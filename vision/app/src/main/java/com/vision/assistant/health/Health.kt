package com.vision.assistant.health

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.vision.assistant.VisionApp
import java.io.File

enum class Status { OK, ACTION, INFO }

/** One line of the setup/health report. [fix] opens the system screen where the user can resolve it (never changes anything silently). */
data class HealthItem(val title: String, val status: Status, val detail: String, val fix: Intent? = null, val fixLabel: String = "Fix")

object Health {
    fun check(app: VisionApp): List<HealthItem> {
        val ctx: Context = app
        val out = ArrayList<HealthItem>()
        fun settings(action: String, pkgUri: Boolean = true) = Intent(action).apply { if (pkgUri) data = Uri.parse("package:${ctx.packageName}") }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val notif = NotificationManagerCompat.from(ctx).areNotificationsEnabled()
        out.add(HealthItem("Notifications", if (notif) Status.OK else Status.ACTION,
            if (notif) "Reminders can alert you." else "Blocked — reminders and download progress cannot alert you.", settings(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)))

        val mic = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        out.add(HealthItem("Microphone", if (mic) Status.OK else Status.ACTION, if (mic) "Allowed." else "Not allowed — talking to VISION needs it.", settings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)))

        val sr = SpeechRecognizer.isRecognitionAvailable(ctx)
        out.add(HealthItem("Phone speech recogniser", if (sr) Status.OK else Status.ACTION, if (sr) "Found — voice input works with the phone's own engine." else "None found. Install/enable Google or your phone's voice service, or download VISION's offline voice model.", Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)))

        val listener = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
        out.add(HealthItem("Notification access", if (listener) Status.OK else Status.INFO, if (listener) "On — I can read and summarise notifications." else "Off — only needed if you want me to read WhatsApp/other notifications.", Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)))

        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        val ignoring = pm.isIgnoringBatteryOptimizations(ctx.packageName)
        val maker = Build.MANUFACTURER.lowercase()
        val aggressive = maker in setOf("vivo", "iqoo", "xiaomi", "oppo", "realme", "oneplus", "huawei", "honor", "samsung")
        out.add(HealthItem("Battery optimisation", if (ignoring) Status.OK else if (aggressive) Status.ACTION else Status.INFO,
            if (ignoring) "VISION is exempt, so reminders and downloads survive in the background."
            else "On ${Build.MANUFACTURER}, the system may stop VISION in the background (late reminders, interrupted downloads). Set battery to “Unrestricted/No restrictions”, and allow auto-start in the phone's settings.",
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), "Open battery settings"))

        val engine = app.engines.label.value
        val neural = engine.lowercase().let { "no language model" !in it && "checking" !in it && it.isNotBlank() }
        out.add(HealthItem("Language model", if (neural) Status.OK else Status.ACTION, if (neural) engine else "None installed — only basic commands work. Open Models & downloads."))

        val p = app.deviceProfile
        out.add(HealthItem("This phone", Status.INFO, "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${"%.1f".format(p.totalRamMb / 1024.0)} GB RAM · ${p.freeStorageMb / 1024} GB free · tier ${p.tier}"))
        if (p.freeStorageMb < 2_048) out.add(HealthItem("Storage", Status.ACTION, "Under 2 GB free — model downloads will be refused to protect the phone."))

        val termux = try { ctx.packageManager.getPackageInfo("com.termux", 0); true } catch (_: Exception) { false }
        out.add(HealthItem("Termux", if (termux) Status.OK else Status.INFO, if (termux) "Installed. Control from VISION also needs allow-external-apps=true (More → Termux control shows the setup line)." else "Not installed — optional; needed for Python/Node/Git and phone-control commands."))

        val crash = CrashLog.last(ctx)
        out.add(HealthItem("Last crash", if (crash == null) Status.OK else Status.ACTION, crash ?: "None recorded."))
        return out
    }
}

/** Saves the last uncaught exception so a crash can be inspected (and sent to the developer) even after the app restarts. Local only. */
object CrashLog {
    private fun file(ctx: Context) = File(ctx.filesDir, "last-crash.txt")
    fun install(ctx: Context) {
        val app = ctx.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try { file(app).writeText("${java.util.Date()}\n${t.name}\n" + e.stackTraceToString().take(3500)) } catch (_: Throwable) {}
            previous?.uncaughtException(t, e)
        }
    }
    fun last(ctx: Context): String? = try { file(ctx).takeIf { it.exists() }?.readText()?.lineSequence()?.take(6)?.joinToString("\n") } catch (_: Throwable) { null }
    fun clear(ctx: Context) { try { file(ctx).delete() } catch (_: Throwable) {} }
}
