package com.vision.assistant

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.vision.assistant.tasks.EventKind
import com.vision.assistant.ui.relativeDay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Home-screen widget: your next reminder/birthday/bill. Tap it to talk to VISION. */
class VisionWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { render(context, manager, ids) } finally { pending.finish() }
        }
    }

    companion object {
        /** Call after reminders change so the widget updates immediately. */
        fun refresh(ctx: Context) {
            val m = AppWidgetManager.getInstance(ctx)
            val ids = m.getAppWidgetIds(ComponentName(ctx, VisionWidget::class.java))
            if (ids.isNotEmpty()) CoroutineScope(Dispatchers.IO).launch { try { render(ctx, m, ids) } catch (_: Throwable) {} }
        }

        private suspend fun render(ctx: Context, m: AppWidgetManager, ids: IntArray) {
            val app = ctx.applicationContext as VisionApp
            val next = app.database.tasks().pending().filter { it.dueAt != null }.minByOrNull { it.dueAt!! }
            val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, AssistActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            for (id in ids) {
                val v = RemoteViews(ctx.packageName, R.layout.vision_widget)
                if (next == null) { v.setTextViewText(R.id.widget_title, "Nothing coming up"); v.setTextViewText(R.id.widget_when, "Tap to talk to VISION") }
                else { val k = EventKind.of(next.kind); v.setTextViewText(R.id.widget_title, "${k.emoji} ${next.title}"); v.setTextViewText(R.id.widget_when, "${k.label} · ${relativeDay(next.dueAt!!)}") }
                v.setOnClickPendingIntent(R.id.widget_root, open)
                m.updateAppWidget(id, v)
            }
        }
    }
}
