package com.vision.assistant.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.Constraints
import com.vision.assistant.VisionApp
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Downloads run in WorkManager with a foreground notification, so they continue when the app is closed and resume from the .part file. */
object Downloads {
    val status = MutableStateFlow<Map<String, String>>(emptyMap())

    fun manager(app: VisionApp) = ModelManager(File(app.filesDir, "models"), app.deviceProfile)

    fun start(app: VisionApp, e: ResourceEntry) {
        val req = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(Data.Builder().putString("id", e.id).build())
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        setStatus(e.id, "queued…")
        WorkManager.getInstance(app).enqueueUniqueWork("download-${e.id}", ExistingWorkPolicy.KEEP, req)
    }

    internal fun setStatus(id: String, s: String) = status.update { it + (id to s) }
}

class DownloadWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as VisionApp
        val e = ResourceManifest.byId(inputData.getString("id") ?: return Result.failure()) ?: return Result.failure()
        try { setForeground(info(e.title, "Starting…")) } catch (_: Throwable) { /* notifications may be blocked; the download still runs */ }
        val mgr = Downloads.manager(app)
        Downloads.setStatus(e.id, "downloading…")
        var lastNote = 0L
        val r = mgr.download(e, { d, t ->
            val s = if (t > 0) "${d * 100 / t}% (${d / 1_048_576} MB)" else "${d / 1_048_576} MB"
            Downloads.setStatus(e.id, s)
            val now = System.currentTimeMillis()
            if (now - lastNote > 2_000) { lastNote = now; (applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(NOTE_ID, info(e.title, s).notification) }
        }, { Downloads.setStatus(e.id, it) })
        Downloads.setStatus(e.id, when (r) {
            is DownloadResult.Installed -> "installed & verified"
            is DownloadResult.Unverified -> "downloaded but UNVERIFIED"
            is DownloadResult.Failed -> "failed: ${r.reason}"
        })
        if (e.kind == ResourceKind.LLM) app.engines.invalidate()
        return if (r is DownloadResult.Failed) Result.failure() else Result.success()
    }

    private fun info(title: String, text: String): ForegroundInfo {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("vision_download", "Model downloads", NotificationManager.IMPORTANCE_LOW))
        val n = NotificationCompat.Builder(applicationContext, "vision_download")
            .setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle("VISION: $title").setContentText(text)
            .setOngoing(true).setOnlyAlertOnce(true).build()
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(NOTE_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else ForegroundInfo(NOTE_ID, n)
    }

    private companion object { const val NOTE_ID = 42 }
}
