package com.vision.assistant.tools

import com.vision.assistant.VisionApp
import com.vision.assistant.ai.Intent
import com.vision.assistant.data.AuditEntry
import com.vision.assistant.data.MemoryItem
import com.vision.assistant.data.TaskItem
import com.vision.assistant.research.ResearchTool
import com.vision.assistant.tasks.ReminderScheduler
import kotlinx.coroutines.flow.first

/** Single place where intents become actions. Nothing here reports success unless the action really happened. */
class ToolRegistry(private val app: VisionApp) {
    private val device = DeviceTools(app)
    private val research = ResearchTool(app)
    private val termuxGateway = com.vision.assistant.termux.TermuxGateway(app)

    suspend fun execute(intent: Intent): ToolResult {
        val db = app.database
        return when (intent) {
            is Intent.OpenApp -> device.openApp(intent.app).also { audit("open_app", intent.app, it) }
            is Intent.Call -> device.call(intent.contact)
            Intent.AskTime -> device.time()
            Intent.AskBattery -> device.battery()
            is Intent.SetReminder -> setReminder(intent)
            Intent.ListTasks -> {
                val pending = db.tasks().pending()
                if (pending.isEmpty()) ToolResult.Success(Say("You have no pending tasks.", "Koi pending task nahi hai.", "कोई लंबित कार्य नहीं है।"))
                else ToolResult.Success(Say.plain("Pending: " + pending.joinToString("; ") { it.title }))
            }
            is Intent.Remember -> {
                db.memory().insert(MemoryItem(category = "note", content = intent.text))
                ToolResult.Success(Say("Saved to memory.", "Memory mein save kar diya.", "स्मृति में सहेज दिया।"))
            }
            is Intent.Recall -> {
                val found = db.memory().search(intent.query, 5)
                if (found.isEmpty()) ToolResult.Failure(Say("Nothing saved about \"${intent.query}\".", "\"${intent.query}\" ke baare mein kuch save nahi hai.", "\"${intent.query}\" के बारे में कुछ सहेजा नहीं है।"))
                else ToolResult.Success(Say.plain(found.joinToString("\n") { "• " + it.content }))
            }
            is Intent.Search -> research.run(intent.query)
            is Intent.Termux -> termux(intent.line)
            is Intent.WhatsAppDraft -> device.whatsappDraft(intent.text)
            is Intent.ReadNotifications -> notifications(intent.whatsappOnly)
            Intent.ReadEmail -> ToolResult.Failure(Say(
                "Gmail isn't connected. Sign-in needs a Google OAuth client that hasn't been set up in this build.",
                "Gmail connected nahi hai. Sign-in ke liye Google OAuth client chahiye jo is build mein set nahi hai.",
                "Gmail जुड़ा नहीं है। साइन-इन के लिए Google OAuth क्लाइंट चाहिए जो इस बिल्ड में सेट नहीं है।"))
            is Intent.See, is Intent.Chat -> ToolResult.Failure(Say("no tool"))
        }
    }

    private suspend fun termux(line: String): ToolResult {
        val v = when (val r = com.vision.assistant.termux.CommandPolicy.validateLine(line, com.vision.assistant.termux.TermuxPrefs.advanced(app))) {
            is com.vision.assistant.termux.Validation.Rejected -> return ToolResult.Failure(Say.plain("Termux command not allowed: ${r.reason} (Advanced mode in the Termux screen allows any command, always with your confirmation.)"))
            is com.vision.assistant.termux.Validation.Ok -> r
        }
        if (!termuxGateway.isInstalled()) return ToolResult.Failure(Say.plain("Termux isn't installed. Install it from F-Droid or termux/termux-app on GitHub, then open the Termux screen in VISION for setup."))
        if (androidx.core.content.ContextCompat.checkSelfPermission(app, "com.termux.permission.RUN_COMMAND") != android.content.pm.PackageManager.PERMISSION_GRANTED)
            return ToolResult.NeedsPermission("com.termux.permission.RUN_COMMAND", Say.plain("I need permission to send commands to Termux."))
        val shown = (listOf(v.binary) + v.args).joinToString(" ")
        if (v.sensitive) return ToolResult.NeedsConfirmation(Say.plain((if (v.high) "⚠ " else "") + "Run in Termux: $shown ?"), "termux") { runTermux(v, shown) }
        return runTermux(v, shown)
    }

    private suspend fun runTermux(v: com.vision.assistant.termux.Validation.Ok, shown: String): ToolResult {
        val res = termuxGateway.execute(v)
        val out = res.getOrNull()
        val r = when {
            out == null -> ToolResult.Failure(Say.plain(res.exceptionOrNull()?.message ?: "Termux failed."))
            out.startsWith("exit=0") -> ToolResult.Success(Say.plain(out.removePrefix("exit=0").trim().ifBlank { "Done." }.take(1500)))
            else -> ToolResult.Failure(Say.plain("Command failed:\n" + out.take(1500) + (if (!termuxGateway.apiInstalled() && v.binary.startsWith("termux-")) "\n(Termux:API app is not installed.)" else "")))
        }
        audit("termux", shown, r)
        return r
    }

    private suspend fun notifications(whatsappOnly: Boolean): ToolResult {
        if (!android.provider.Settings.Secure.getString(app.contentResolver, "enabled_notification_listeners").orEmpty().contains(app.packageName))
            return ToolResult.Failure(Say(
                "Notification access is off. Enable VISION under Settings > Notifications > Notification access.",
                "Notification access band hai. Settings > Notifications > Notification access mein VISION ko on karein.",
                "सूचना एक्सेस बंद है। सेटिंग्स > सूचनाएँ > सूचना एक्सेस में VISION चालू करें।"))
        val all = app.database.notifications().recent(30)
            .filter { !whatsappOnly || com.vision.assistant.comms.NotificationSummary.isWhatsApp(it.pkg) }
        if (all.isEmpty()) return ToolResult.Success(Say("No recent notifications.", "Koi nayi notification nahi.", "कोई नई सूचना नहीं।"))
        val shown = all.take(8).joinToString("\n") { "• ${it.title}: ${it.text}" }
        return ToolResult.Success(Say.plain(shown))
    }

    private suspend fun setReminder(i: Intent.SetReminder): ToolResult {
        val t = i.time ?: return ToolResult.Failure(Say("Tell me the time too, e.g. \"tomorrow at 6 AM\".", "Time bhi batao, jaise \"kal subah 6 baje\".", "समय भी बताइए, जैसे \"कल सुबह 6 बजे\"।"))
        val wake = Regex("wake|utha|jaga|alarm|उठा").containsMatchIn(i.text.lowercase())
        if (wake && i.repeatWeekday == null) return device.setAlarm(i).also { audit("alarm", i.text, it) }
        val at = i.repeatWeekday?.let { DeviceTools.nextWeekdayMillis(it, t.hour, t.minute) } ?: DeviceTools.triggerMillis(t.dayOffset, t.hour, t.minute)
        val id = app.database.tasks().insert(TaskItem(title = i.text, dueAt = at, repeat = i.repeatWeekday?.let { "weekly:$it" }))
        ReminderScheduler.schedule(app, id, i.text, at, weekly = i.repeatWeekday != null)
        val res = ToolResult.Success(
            Say("Reminder set for %02d:%02d: ${i.text}.".format(t.hour, t.minute), "Reminder %02d:%02d ke liye laga diya: ${i.text}.".format(t.hour, t.minute), "रिमाइंडर %02d:%02d के लिए लगा दिया: ${i.text}।".format(t.hour, t.minute)),
        )
        audit("reminder", i.text, res)
        val blocked = !androidx.core.app.NotificationManagerCompat.from(app).areNotificationsEnabled()
        if (blocked) return ToolResult.Success(Say(
            "Reminder saved, but notifications are blocked for VISION, so it cannot alert you. Allow notifications on the Permissions screen.",
            "Reminder save ho gaya, par VISION ki notifications band hain, isliye alert nahi aayega. Permissions screen par allow karein.",
            "रिमाइंडर सहेज लिया, पर VISION की सूचनाएँ बंद हैं, इसलिए अलर्ट नहीं आएगा। Permissions स्क्रीन पर अनुमति दें।"))
        return res
    }

    suspend fun audit(tool: String, summary: String, r: ToolResult) {
        val outcome = when (r) { is ToolResult.Success -> "success"; is ToolResult.Failure -> "failure"; else -> "pending" }
        app.database.audit().insert(AuditEntry(tool = tool, summary = summary.take(200), outcome = outcome))
    }
}
