package com.vision.assistant.tasks

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.vision.assistant.VisionApp
import com.vision.assistant.data.TaskItem
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** Wording per kind and language ("en", "hinglish", "hi"). */
object ReminderText {
    private fun whenText(lead: Int, lang: String) = when (lang) {
        "hi" -> if (lead == 1) "कल" else "$lead दिन में"
        "hinglish" -> if (lead == 1) "kal" else "$lead din mein"
        else -> if (lead == 1) "tomorrow" else "in $lead days"
    }

    /** (title, body) */
    fun compose(kind: EventKind, title: String, lead: Int, lang: String): Pair<String, String> {
        val today = lead <= 0
        return when (kind) {
            EventKind.BIRTHDAY -> when (lang) {
                "hi" -> if (today) "🎂 आज $title का जन्मदिन है!" to "बधाई देना न भूलें।" else "🎂 $title का जन्मदिन ${whenText(lead, lang)}" to "पहले से तैयारी कर लें।"
                "hinglish" -> if (today) "🎂 Aaj $title ka birthday hai!" to "Wish karna na bhoolein." else "🎂 $title ka birthday ${whenText(lead, lang)}" to "Pehle se gift ya wish soch lein."
                else -> if (today) "🎂 It's $title's birthday today!" to "Don't forget to wish them." else "🎂 $title's birthday is ${whenText(lead, lang)}" to "Time to plan a wish or a gift."
            }
            EventKind.ANNIVERSARY -> when (lang) {
                "hi" -> if (today) "💍 आज $title की सालगिरह है!" to "इसे ख़ास बनाइए।" else "💍 $title की सालगिरह ${whenText(lead, lang)}" to "तैयारी का समय।"
                "hinglish" -> if (today) "💍 Aaj $title ki anniversary hai!" to "Isse khaas banayein — wish karna na bhoolein." else "💍 $title ki anniversary ${whenText(lead, lang)}" to "Plan ya surprise sochne ka time."
                else -> if (today) "💍 Anniversary today: $title" to "Make it a special day." else "💍 $title — anniversary ${whenText(lead, lang)}" to "Time to plan something special."
            }
            EventKind.BILL -> when (lang) {
                "hi" -> if (today) "💳 $title आज देय है" to "देर से शुल्क से बचने के लिए आज ही भुगतान करें।" else "💳 $title ${whenText(lead, lang)} देय है" to "समय पर भुगतान की तैयारी रखें।"
                "hinglish" -> if (today) "💳 $title aaj due hai" to "Late fee se bachne ke liye aaj hi pay karein." else "💳 $title ${whenText(lead, lang)} due hai" to "Time par pay karne ki taiyari rakhein."
                else -> if (today) "💳 $title is due today" to "Pay it today to avoid a late fee." else "💳 $title is due ${whenText(lead, lang)}" to "Get ready to pay on time."
            }
            EventKind.MEDICINE -> when (lang) {
                "hi" -> "💊 दवा का समय" to title
                "hinglish" -> "💊 Dawai ka time" to title
                else -> "💊 Medicine time" to title
            }
            EventKind.REMINDER -> (when (lang) { "hi" -> "⏰ रिमाइंडर"; "hinglish" -> "⏰ Reminder"; else -> "⏰ Reminder" }) to title
        }
    }

    fun wish(kind: EventKind, title: String) = when (kind) {
        EventKind.BIRTHDAY -> "Happy Birthday $title! 🎂🎉 Wishing you a wonderful year ahead!"
        EventKind.ANNIVERSARY -> "Happy Anniversary $title! 💍✨ Wishing you love and happiness always!"
        else -> ""
    }
}

class ReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as VisionApp
        val id = inputData.getLong("id", 0)
        val lead = inputData.getInt("lead", 0)
        val text = inputData.getString("text") ?: return Result.failure()
        val kind = EventKind.of(inputData.getString("kind") ?: "reminder")
        val lang = inputData.getString("lang") ?: "en"
        val task = if (id > 0) app.database.tasks().byId(id) else null
        if (task != null && task.done) return Result.success() // finished or deleted since scheduling
        ReminderScheduler.notify(applicationContext, id, text, kind, lead, lang)
        // After the day-of notification, roll a repeating event forward to its next occurrence.
        if (task != null && lead == 0) {
            val c = Calendar.getInstance().apply { timeInMillis = task.dueAt ?: System.currentTimeMillis() }
            val next = EventPlanner.nextAfter(task.repeat, (task.dueAt ?: System.currentTimeMillis()) + 60_000, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
            if (next != null) { val n = task.copy(dueAt = next); app.database.tasks().update(n); ReminderScheduler.scheduleTask(applicationContext, n) }
            else if (task.repeat.isNullOrBlank()) app.database.tasks().update(task.copy(done = true))
        }
        return Result.success()
    }
}

class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as VisionApp
        val id = intent.getLongExtra("id", 0)
        val nid = intent.getIntExtra("nid", 0)
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(nid)
        when (intent.action) {
            ACTION_SNOOZE -> ReminderScheduler.snooze(context, id, intent.getStringExtra("text").orEmpty(), intent.getStringExtra("kind").orEmpty(), intent.getStringExtra("lang").orEmpty())
            ACTION_DONE -> kotlinx.coroutines.runBlocking {
                val t = app.database.tasks().byId(id)
                if (t != null && t.repeat.isNullOrBlank()) app.database.tasks().update(t.copy(done = true))
            }
        }
    }
    companion object { const val ACTION_SNOOZE = "com.vision.assistant.REMINDER_SNOOZE"; const val ACTION_DONE = "com.vision.assistant.REMINDER_DONE" }
}

object ReminderScheduler {
    private data class Style(val channel: String, val name: String, val importance: Int, val color: String, val vibration: LongArray)
    private fun style(k: EventKind) = when (k) {
        EventKind.BILL -> Style("vision_bills", "Bills & payments", NotificationManager.IMPORTANCE_HIGH, "#FF9100", longArrayOf(0, 500, 200, 500, 200, 500))
        EventKind.BIRTHDAY -> Style("vision_birthdays", "Birthdays", NotificationManager.IMPORTANCE_HIGH, "#FF4081", longArrayOf(0, 200, 100, 200, 100, 400))
        EventKind.ANNIVERSARY -> Style("vision_anniversaries", "Anniversaries", NotificationManager.IMPORTANCE_HIGH, "#B388FF", longArrayOf(0, 300, 150, 300))
        EventKind.MEDICINE -> Style("vision_health", "Medicine", NotificationManager.IMPORTANCE_HIGH, "#3DFFC0", longArrayOf(0, 800, 300, 800))
        EventKind.REMINDER -> Style("vision_reminders", "Reminders", NotificationManager.IMPORTANCE_HIGH, "#00E5FF", longArrayOf(0, 250, 100, 250))
    }

    fun ensureChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        for (k in EventKind.values()) { val s = style(k); nm.createNotificationChannel(NotificationChannel(s.channel, s.name, s.importance).apply { enableVibration(true); vibrationPattern = s.vibration; enableLights(true); lightColor = Color.parseColor(s.color) }) }
    }

    fun notify(ctx: Context, id: Long, text: String, kind: EventKind, lead: Int, lang: String) {
        ensureChannels(ctx)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val s = style(kind)
        val (title, body) = ReminderText.compose(kind, text, lead, lang)
        val nid = (id * 10 + (lead.coerceAtLeast(0) % 10)).toInt() + 1000
        fun action(label: String, act: String, req: Int) = NotificationCompat.Action(0, label, PendingIntent.getBroadcast(ctx, nid * 4 + req,
            Intent(ctx, ReminderActionReceiver::class.java).setAction(act).putExtra("id", id).putExtra("nid", nid).putExtra("text", text).putExtra("kind", kind.id).putExtra("lang", lang),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        val b = NotificationCompat.Builder(ctx, s.channel)
            .setSmallIcon(android.R.drawable.ic_popup_reminder).setContentTitle(title).setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body)).setColor(Color.parseColor(s.color))
            .setCategory(if (kind == EventKind.REMINDER || kind == EventKind.MEDICINE) NotificationCompat.CATEGORY_REMINDER else NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true)
            .addAction(action(if (lang == "en") "Done" else "Ho gaya", ReminderActionReceiver.ACTION_DONE, 0))
            .addAction(action(if (lang == "en") "Snooze 10 min" else "10 min baad", ReminderActionReceiver.ACTION_SNOOZE, 1))
        if (lead <= 0 && (kind == EventKind.BIRTHDAY || kind == EventKind.ANNIVERSARY)) {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, ReminderText.wish(kind, text)).setPackage("com.whatsapp").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            b.addAction(0, "🎉 Wish on WhatsApp", PendingIntent.getActivity(ctx, nid * 4 + 2, send, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        }
        NotificationManagerCompat.from(ctx).notify(nid, b.build())
    }

    /** Plain informational notification (routine results). */
    fun notifyText(ctx: Context, nid: Int, title: String, body: String) {
        ensureChannels(ctx)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val s = style(EventKind.REMINDER)
        NotificationManagerCompat.from(ctx).notify(nid, NotificationCompat.Builder(ctx, s.channel).setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title).setContentText(body.lineSequence().firstOrNull().orEmpty()).setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setColor(Color.parseColor(s.color)).setAutoCancel(true).build())
    }

    private fun enqueue(ctx: Context, name: String, atMillis: Long, id: Long, text: String, kind: String, lead: Int, lang: String) {
        val delay = (atMillis - System.currentTimeMillis()).coerceAtLeast(0)
        val data = Data.Builder().putLong("id", id).putString("text", text).putString("kind", kind).putInt("lead", lead).putString("lang", lang).build()
        val req = OneTimeWorkRequestBuilder<ReminderWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS).setInputData(data).build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, req)
    }

    /** Schedules the day-of alert plus every advance notice (e.g. 3 and 1 day before a bill). WorkManager survives restarts and reboots; delivery can drift a few minutes under Doze. */
    fun scheduleTask(ctx: Context, t: TaskItem) {
        val due = t.dueAt ?: return
        val leads = (t.leadDays.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it > 0 } + 0).distinct()
        for (l in leads) {
            val at = due - TimeUnit.DAYS.toMillis(l.toLong())
            if (at > System.currentTimeMillis() || l == 0) enqueue(ctx, "reminder-${t.id}-$l", at, t.id, t.title, t.kind, l, t.lang)
        }
    }

    fun cancelTask(ctx: Context, t: TaskItem) {
        val wm = WorkManager.getInstance(ctx)
        for (l in listOf(0, 1, 2, 3, 5, 7, 14, 30)) wm.cancelUniqueWork("reminder-${t.id}-$l")
    }

    fun snooze(ctx: Context, id: Long, text: String, kind: String, lang: String) =
        enqueue(ctx, "reminder-snooze-$id", System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(10), 0L, text, kind, -1, lang.ifBlank { "en" })
}
