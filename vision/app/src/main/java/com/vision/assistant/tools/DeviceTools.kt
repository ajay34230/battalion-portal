package com.vision.assistant.tools

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.vision.assistant.ai.Intent as AiIntent
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

class DeviceTools(private val ctx: Context) {

    fun openApp(name: String): ToolResult {
        val pm = ctx.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(launcher, 0)
        val q = name.lowercase().trim()
        val match = apps.firstOrNull { it.loadLabel(pm).toString().lowercase() == q }
            ?: apps.firstOrNull { it.loadLabel(pm).toString().lowercase().contains(q) }
            ?: return ToolResult.Failure(Say("I couldn't find an app called \"$name\".", "\"$name\" naam ka app nahi mila.", "\"$name\" नाम का ऐप नहीं मिला।"))
        val intent = pm.getLaunchIntentForPackage(match.activityInfo.packageName)
            ?: return ToolResult.Failure(Say("That app can't be opened.", "Yeh app khul nahi sakta.", "यह ऐप खुल नहीं सकता।"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            ctx.startActivity(intent)
            ToolResult.Success(Say("Opened ${match.loadLabel(pm)}.", "${match.loadLabel(pm)} khol diya.", "${match.loadLabel(pm)} खोल दिया।"))
        } catch (e: Exception) {
            ToolResult.Failure(Say("Android blocked opening the app: ${e.message}", "Android ne app kholne se roka: ${e.message}", "Android ने ऐप खोलने से रोका: ${e.message}"))
        }
    }

    /** Resolves a contact (needs READ_CONTACTS) and prepares the dialer. The user presses the call button. */
    fun call(contact: String): ToolResult {
        val number: String = if (contact.any { it.isDigit() } && contact.all { it.isDigit() || it in "+ -()" }) {
            contact
        } else {
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                return ToolResult.NeedsPermission(
                    Manifest.permission.READ_CONTACTS,
                    Say("I need contacts permission to find \"$contact\".", "\"$contact\" dhundhne ke liye contacts permission chahiye.", "\"$contact\" खोजने के लिए संपर्क अनुमति चाहिए।"),
                )
            }
            lookupNumber(contact) ?: return ToolResult.Failure(Say("No contact named \"$contact\".", "\"$contact\" naam ka contact nahi mila.", "\"$contact\" नाम का संपर्क नहीं मिला।"))
        }
        return ToolResult.NeedsConfirmation(
            Say("Open the dialer for $contact ($number)?", "$contact ($number) ke liye dialer kholun?", "$contact ($number) के लिए डायलर खोलूँ?"),
            "call",
        ) {
            try {
                ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                ToolResult.Success(Say("Dialer is open. Press call to place it.", "Dialer khul gaya. Call dabaiye.", "डायलर खुल गया। कॉल दबाइए।"))
            } catch (e: Exception) { ToolResult.Failure(Say("Couldn't open the dialer: ${e.message}")) }
        }
    }

    private fun lookupNumber(name: String): String? {
        val cursor = ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$name%"), null,
        ) ?: return null
        cursor.use { return if (it.moveToFirst()) it.getString(0) else null }
    }

    /** Wake-up style requests go to the system clock app; it owns exact alarms. */
    fun setAlarm(time: AiIntent.SetReminder): ToolResult {
        val t = time.time ?: return ToolResult.Failure(Say("I couldn't understand the time.", "Time samajh nahi aaya.", "समय समझ नहीं आया।"))
        val i = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, t.hour).putExtra(AlarmClock.EXTRA_MINUTES, t.minute)
            .putExtra(AlarmClock.EXTRA_MESSAGE, time.text).putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            ctx.startActivity(i)
            ToolResult.Success(Say("Alarm sent to your clock app for %02d:%02d.".format(t.hour, t.minute), "Alarm clock app mein %02d:%02d ke liye bhej diya.".format(t.hour, t.minute), "अलार्म %02d:%02d के लिए घड़ी ऐप को भेजा।".format(t.hour, t.minute)))
        } catch (e: Exception) { ToolResult.Failure(Say("No clock app accepted the alarm: ${e.message}")) }
    }

    /** Opens WhatsApp with the text prefilled. The user chooses the chat and presses send; VISION never sends. */
    fun whatsappDraft(text: String): ToolResult = ToolResult.NeedsConfirmation(
        Say("Open WhatsApp with this draft: \"$text\"?", "WhatsApp mein yeh draft kholun: \"$text\"?", "WhatsApp में यह ड्राफ़्ट खोलूँ: \"$text\"?"),
        "whatsapp_draft",
    ) {
        val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text).setPackage("com.whatsapp").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { ctx.startActivity(i); ToolResult.Success(Say("WhatsApp is open. Pick the chat and press send.", "WhatsApp khul gaya. Chat chuniye aur send dabaiye.", "WhatsApp खुल गया। चैट चुनें और भेजें दबाएँ।")) }
        catch (e: Exception) { ToolResult.Failure(Say("WhatsApp isn't available: ${e.message}")) }
    }

    fun time(): ToolResult {
        val s = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())
        return ToolResult.Success(Say("It's $s.", "Abhi $s hua hai.", "अभी $s बजे हैं।"))
    }

    fun battery(): ToolResult {
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return ToolResult.Success(Say("Battery is at $pct%.", "Battery $pct% hai.", "बैटरी $pct% है।"))
    }

    fun canScheduleExact(): Boolean =
        (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).let { android.os.Build.VERSION.SDK_INT < 31 || it.canScheduleExactAlarms() }

    companion object {
        fun triggerMillis(dayOffset: Int, hour: Int, minute: Int, now: Calendar = Calendar.getInstance()): Long {
            val c = now.clone() as Calendar
            c.add(Calendar.DAY_OF_YEAR, dayOffset)
            c.set(Calendar.HOUR_OF_DAY, hour); c.set(Calendar.MINUTE, minute); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
            if (c.timeInMillis <= now.timeInMillis && dayOffset == 0) c.add(Calendar.DAY_OF_YEAR, 1)
            return c.timeInMillis
        }
    }
}
