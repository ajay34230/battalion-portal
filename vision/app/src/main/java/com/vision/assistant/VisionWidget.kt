package com.vision.assistant

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.vision.assistant.finance.SpendSummary
import com.vision.assistant.tasks.EventKind
import com.vision.assistant.ui.relativeDay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Home-screen widget "Daily Pulse": time-of-day colours, greeting, today's spending/received and a pace bar against your daily average
 * (only when payments were captured), the next bill, and your next reminder. Everything that has no data is hidden. Tap it to talk to VISION.
 */
class VisionWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = launchRender(context, manager, ids)
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) = launchRender(context, manager, intArrayOf(id))

    private fun launchRender(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch { try { render(context, manager, ids) } finally { pending.finish() } }
    }

    companion object {
        /** Call after reminders or payments change so the widget updates immediately. */
        fun refresh(ctx: Context) {
            val m = AppWidgetManager.getInstance(ctx)
            val ids = m.getAppWidgetIds(ComponentName(ctx, VisionWidget::class.java))
            if (ids.isNotEmpty()) CoroutineScope(Dispatchers.IO).launch { try { render(ctx, m, ids) } catch (_: Throwable) {} }
        }

        fun greetingFor(hour: Int) = when (hour) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; in 17..20 -> "Good evening"; else -> "Good night" }
        fun backgroundFor(hour: Int) = when (hour) { in 5..10 -> R.drawable.widget_bg_morning; in 11..16 -> R.drawable.widget_bg_day; in 17..20 -> R.drawable.widget_bg_evening; else -> R.drawable.widget_bg_night }
        /** 0..100: today's spending against your average day this month. */
        fun pacePercent(today: Long, avgPerDay: Long): Int = if (avgPerDay <= 0) 0 else ((today * 50) / avgPerDay).coerceIn(0, 100).toInt()

        private suspend fun render(ctx: Context, m: AppWidgetManager, ids: IntArray) {
            val app = ctx.applicationContext as VisionApp
            val now = Calendar.getInstance()
            val hour = now.get(Calendar.HOUR_OF_DAY)
            val tasks = app.database.tasks().pending().filter { it.dueAt != null }.sortedBy { it.dueAt!! }
            val next = tasks.firstOrNull()
            val bill = tasks.firstOrNull { it.kind == EventKind.BILL.id }

            fun startOfDay() = (now.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
            val dayStart = startOfDay().timeInMillis
            val monthStart = startOfDay().apply { set(Calendar.DAY_OF_MONTH, 1) }.timeInMillis
            val monthRows = app.database.payments().since(monthStart)
            val todayRows = monthRows.filter { it.at >= dayStart }
            fun sum(rows: List<com.vision.assistant.data.PaymentRecord>, dir: String) = rows.filter { it.direction == dir }.sumOf { it.paise }
            val spent = sum(todayRows, "PAID"); val recv = sum(todayRows, "RECEIVED")
            val daysSoFar = now.get(Calendar.DAY_OF_MONTH).coerceAtLeast(1)
            val avg = sum(monthRows, "PAID") / daysSoFar
            val hasMoney = todayRows.isNotEmpty()

            val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, AssistActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val moneyIntent = PendingIntent.getActivity(ctx, 1, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("route", "money"), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val name = com.vision.assistant.ai.Learning.userName

            for (id in ids) {
                val wide = (m.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)) >= 200 && (m.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 140)) >= 110
                val v = RemoteViews(ctx.packageName, if (wide) R.layout.vision_widget else R.layout.vision_widget_small)
                v.setInt(R.id.widget_root, "setBackgroundResource", backgroundFor(hour))
                if (next == null) { v.setTextViewText(R.id.widget_title, "Nothing coming up"); v.setTextViewText(R.id.widget_when, "Tap to talk to VISION") }
                else { val k = EventKind.of(next.kind); v.setTextViewText(R.id.widget_title, "${k.emoji} ${next.title}"); v.setTextViewText(R.id.widget_when, "${k.label} · ${relativeDay(next.dueAt!!)}") }
                if (wide) {
                    v.setTextViewText(R.id.widget_greeting, greetingFor(hour) + if (name.isNotBlank()) ", $name" else "")
                    v.setTextViewText(R.id.widget_date, java.text.SimpleDateFormat("EEEE, d MMMM", java.util.Locale.getDefault()).format(now.time))
                    v.setViewVisibility(R.id.chip_spent, if (hasMoney) View.VISIBLE else View.GONE)
                    v.setViewVisibility(R.id.chip_recv, if (recv > 0) View.VISIBLE else View.GONE)
                    v.setViewVisibility(R.id.chip_bill, if (bill != null) View.VISIBLE else View.GONE)
                    v.setViewVisibility(R.id.widget_chips, if (hasMoney || bill != null) View.VISIBLE else View.GONE)
                    v.setTextViewText(R.id.spent_value, SpendSummary.rupees(spent))
                    v.setTextViewText(R.id.recv_value, SpendSummary.rupees(recv))
                    if (bill != null) v.setTextViewText(R.id.bill_value, "${bill.title}\n${relativeDay(bill.dueAt!!)}")
                    val showPace = hasMoney && avg > 0
                    v.setViewVisibility(R.id.widget_pace, if (showPace) View.VISIBLE else View.GONE)
                    if (showPace) {
                        v.setProgressBar(R.id.pace_bar, 100, pacePercent(spent, avg), false)
                        v.setTextViewText(R.id.pace_label, "Today vs your daily average of ${SpendSummary.rupees(avg)}")
                    }
                    v.setOnClickPendingIntent(R.id.chip_spent, moneyIntent); v.setOnClickPendingIntent(R.id.chip_recv, moneyIntent)
                    v.setOnClickPendingIntent(R.id.widget_talk, open)
                }
                v.setOnClickPendingIntent(R.id.widget_root, open)
                m.updateAppWidget(id, v)
            }
        }
    }
}
