package com.vision.assistant.finance

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.vision.assistant.VisionApp
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** A daily prompt (default 8 pm, changeable) asking the user to confirm category and place of payments seen that day, so the records end up accurate. */
object PaymentReview {
    private const val NAME = "payment-review"
    private fun sp(c: Context) = c.getSharedPreferences("vision_prefs", Context.MODE_PRIVATE)
    fun enabled(c: Context) = sp(c).getBoolean("money_review_on", true)
    fun minutes(c: Context) = sp(c).getInt("money_review_min", 20 * 60)
    fun set(c: Context, on: Boolean, minutes: Int) { sp(c).edit().putBoolean("money_review_on", on).putInt("money_review_min", minutes).apply(); schedule(c) }

    fun nextAt(minutes: Int, now: Long = System.currentTimeMillis()): Long {
        val c = Calendar.getInstance().apply { timeInMillis = now; set(Calendar.HOUR_OF_DAY, minutes / 60); set(Calendar.MINUTE, minutes % 60); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        if (c.timeInMillis <= now) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    fun schedule(c: Context) {
        val wm = WorkManager.getInstance(c)
        if (!enabled(c)) { wm.cancelUniqueWork(NAME); return }
        wm.enqueueUniqueWork(NAME, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<PaymentReviewWorker>().setInitialDelay(nextAt(minutes(c)) - System.currentTimeMillis(), TimeUnit.MILLISECONDS).build())
    }
}

class PaymentReviewWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result {
        val app = applicationContext as VisionApp
        val open = app.database.payments().unconfirmed()
        if (open.isNotEmpty()) com.vision.assistant.tasks.ReminderScheduler.notifyText(app, 7001, "Confirm today's spending",
            "${open.size} payment(s) worth ${SpendSummary.rupees(open.sumOf { it.paise })} need a category/place. Open VISION → More → Money.")
        PaymentReview.schedule(app)
        return Result.success()
    }
}
