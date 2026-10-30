package com.vision.assistant.tasks

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

private const val CHANNEL = "vision_reminders"

class ReminderWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        val text = inputData.getString("text") ?: return Result.failure()
        ReminderScheduler.notify(applicationContext, inputData.getLong("id", 0).toInt(), text)
        return Result.success()
    }
}

object ReminderScheduler {
    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Reminders", NotificationManager.IMPORTANCE_HIGH))
        }
    }

    fun notify(ctx: Context, id: Int, text: String) {
        ensureChannel(ctx)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("VISION reminder").setContentText(text).setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH).build()
        NotificationManagerCompat.from(ctx).notify(id, n)
    }

    /** WorkManager survives restarts and reboots; delivery time may drift by minutes under Doze. */
    fun schedule(ctx: Context, taskId: Long, text: String, triggerAtMillis: Long, weekly: Boolean) {
        val delay = (triggerAtMillis - System.currentTimeMillis()).coerceAtLeast(0)
        val data = Data.Builder().putString("text", text).putLong("id", taskId).build()
        val wm = WorkManager.getInstance(ctx)
        if (weekly) {
            val req = PeriodicWorkRequestBuilder<ReminderWorker>(7, TimeUnit.DAYS)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS).setInputData(data).build()
            wm.enqueueUniquePeriodicWork("reminder-$taskId", ExistingPeriodicWorkPolicy.REPLACE, req)
        } else {
            val req = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS).setInputData(data).build()
            wm.enqueueUniqueWork("reminder-$taskId", ExistingWorkPolicy.REPLACE, req)
        }
    }
}
