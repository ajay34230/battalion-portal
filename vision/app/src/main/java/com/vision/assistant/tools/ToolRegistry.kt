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
            is Intent.WhatsAppDraft -> device.whatsappDraft(intent.text)
            is Intent.ReadNotifications -> notifications(intent.whatsappOnly)
            Intent.ReadEmail -> ToolResult.Failure(Say(
                "Gmail isn't connected. Sign-in needs a Google OAuth client that hasn't been set up in this build.",
                "Gmail connected nahi hai. Sign-in ke liye Google OAuth client chahiye jo is build mein set nahi hai.",
                "Gmail जुड़ा नहीं है। साइन-इन के लिए Google OAuth क्लाइंट चाहिए जो इस बिल्ड में सेट नहीं है।"))
            is Intent.Chat -> ToolResult.Failure(Say("no tool"))
        }
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
        val at = DeviceTools.triggerMillis(t.dayOffset, t.hour, t.minute)
        val id = app.database.tasks().insert(TaskItem(title = i.text, dueAt = at, repeat = i.repeatWeekday?.let { "weekly:$it" }))
        ReminderScheduler.schedule(app, id, i.text, at, weekly = i.repeatWeekday != null)
        val res = ToolResult.Success(
            Say("Reminder set for %02d:%02d: ${i.text}.".format(t.hour, t.minute), "Reminder %02d:%02d ke liye laga diya: ${i.text}.".format(t.hour, t.minute), "रिमाइंडर %02d:%02d के लिए लगा दिया: ${i.text}।".format(t.hour, t.minute)),
        )
        audit("reminder", i.text, res)
        return res
    }

    suspend fun audit(tool: String, summary: String, r: ToolResult) {
        val outcome = when (r) { is ToolResult.Success -> "success"; is ToolResult.Failure -> "failure"; else -> "pending" }
        app.database.audit().insert(AuditEntry(tool = tool, summary = summary.take(200), outcome = outcome))
    }
}
