package com.vision.assistant.comms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CalEvent(val title: String, val begin: Long, val end: Long, val allDay: Boolean, val where: String)

/** Reads upcoming events from the calendars on the phone (read-only; needs the READ_CALENDAR permission the user grants). */
object CalendarReader {
    const val PERMISSION = Manifest.permission.READ_CALENDAR
    fun hasPermission(ctx: Context) = ContextCompat.checkSelfPermission(ctx, PERMISSION) == PackageManager.PERMISSION_GRANTED

    fun upcoming(ctx: Context, days: Int, now: Long = System.currentTimeMillis(), max: Int = 25): List<CalEvent> {
        if (!hasPermission(ctx)) return emptyList()
        val end = now + days * 86_400_000L
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also { android.content.ContentUris.appendId(it, now); android.content.ContentUris.appendId(it, end) }.build()
        val out = ArrayList<CalEvent>()
        ctx.contentResolver.query(uri, arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.EVENT_LOCATION), null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
            while (c.moveToNext() && out.size < max) out.add(CalEvent(c.getString(0).orEmpty().ifBlank { "(no title)" }, c.getLong(1), c.getLong(2), c.getInt(3) == 1, c.getString(4).orEmpty()))
        }
        return out
    }

    fun format(events: List<CalEvent>, days: Int): String {
        if (events.isEmpty()) return if (days <= 1) "Nothing on your calendar today." else "Nothing on your calendar in the next $days days."
        val day = SimpleDateFormat("EEE d MMM", Locale.getDefault()); val time = SimpleDateFormat("h:mm a", Locale.getDefault())
        return events.joinToString("\n") { e -> "• ${day.format(Date(e.begin))} ${if (e.allDay) "(all day)" else time.format(Date(e.begin))} — ${e.title}" + if (e.where.isNotBlank()) " @ ${e.where}" else "" }
    }
}
