package com.vision.assistant.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.vision.assistant.VisionApp
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

enum class DlPhase { QUEUED, DOWNLOADING, VERIFYING, UNPACKING, PAUSED, DONE, FAILED }

data class DlState(val phase: DlPhase, val done: Long = 0, val total: Long = 0, val bytesPerSec: Long = 0, val message: String = "") {
    val fraction: Float get() = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
    val active: Boolean get() = phase == DlPhase.QUEUED || phase == DlPhase.DOWNLOADING || phase == DlPhase.VERIFYING || phase == DlPhase.UNPACKING
    val etaSeconds: Long? get() = if (phase == DlPhase.DOWNLOADING && bytesPerSec > 0 && total > done) (total - done) / bytesPerSec else null
}

/**
 * Downloads run in WorkManager with a foreground notification (Pause / Cancel buttons), so they continue when the app is
 * closed. Pause keeps the partial file; Resume continues from it; Cancel deletes it.
 */
object Downloads {
    val states = MutableStateFlow<Map<String, DlState>>(emptyMap())
    private val intents = ConcurrentHashMap<String, String>()

    fun manager(app: VisionApp) = ModelManager(File(app.filesDir, "models"), app.deviceProfile)
    private fun workName(id: String) = "download-$id"

    internal fun put(id: String, s: DlState) = states.update { it + (id to s) }
    internal fun get(id: String) = states.value[id] ?: DlState(DlPhase.QUEUED)
    internal fun clear(id: String) = states.update { it - id }
    internal fun takeIntent(id: String): String? = intents.remove(id)

    fun start(app: VisionApp, e: ResourceEntry) {
        intents.remove(e.id)
        val mgr = manager(app)
        put(e.id, DlState(DlPhase.QUEUED, mgr.partBytes(e), e.sizeBytes))
        val req = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(Data.Builder().putString("id", e.id).build())
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(app).enqueueUniqueWork(workName(e.id), ExistingWorkPolicy.REPLACE, req)
    }

    fun pause(app: VisionApp, id: String) {
        val e = ResourceManifest.byId(id) ?: return
        intents[id] = "pause"
        WorkManager.getInstance(app).cancelUniqueWork(workName(id))
        put(id, get(id).copy(phase = DlPhase.PAUSED, bytesPerSec = 0, done = manager(app).partBytes(e), total = e.sizeBytes))
    }

    fun cancel(app: VisionApp, id: String) {
        val e = ResourceManifest.byId(id) ?: return
        intents[id] = "cancel"
        WorkManager.getInstance(app).cancelUniqueWork(workName(id))
        manager(app).discardPart(e)
        clear(id)
        (app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(DownloadWorker.NOTE_ID)
    }

    /** After a restart the in-memory state is gone: show partial files as paused so they can be resumed. */
    fun refreshFromDisk(app: VisionApp) {
        val mgr = manager(app)
        for (e in ResourceManifest.entries) {
            val cur = states.value[e.id]
            if (cur != null && cur.phase != DlPhase.PAUSED) continue
            val b = mgr.partBytes(e)
            if (b > 0 && mgr.state(e) != InstallState.INSTALLED) put(e.id, DlState(DlPhase.PAUSED, b, e.sizeBytes))
        }
    }
}

class DownloadActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as VisionApp
        val id = intent.getStringExtra("id") ?: return
        when (intent.action) {
            ACTION_PAUSE -> Downloads.pause(app, id)
            ACTION_CANCEL -> Downloads.cancel(app, id)
        }
    }
    companion object {
        const val ACTION_PAUSE = "com.vision.assistant.DL_PAUSE"
        const val ACTION_CANCEL = "com.vision.assistant.DL_CANCEL"
    }
}

class DownloadWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as VisionApp
        val e = ResourceManifest.byId(inputData.getString("id") ?: return Result.failure()) ?: return Result.failure()
        val mgr = Downloads.manager(app)
        val startBytes = mgr.partBytes(e)
        Downloads.put(e.id, DlState(DlPhase.QUEUED, startBytes, e.sizeBytes))
        try { setForeground(info(e, Downloads.get(e.id))) } catch (_: Throwable) { /* notifications may be blocked; the download still runs */ }

        var lastT = System.nanoTime(); var lastB = startBytes; var speed = 0L; var lastNote = 0L
        try {
            val r = mgr.download(e, { d, t ->
                val now = System.nanoTime()
                if (now - lastT >= 1_000_000_000L) {
                    val inst = (d - lastB) * 1_000_000_000L / (now - lastT)
                    speed = if (speed == 0L) inst else (speed * 0.6 + inst * 0.4).toLong()
                    lastT = now; lastB = d
                }
                val st = DlState(DlPhase.DOWNLOADING, d, if (t > 0) t else e.sizeBytes, speed)
                Downloads.put(e.id, st)
                val ms = System.currentTimeMillis()
                if (ms - lastNote > 1_500) { lastNote = ms; notifier().notify(NOTE_ID, info(e, st).notification) }
            }, { phase -> Downloads.put(e.id, Downloads.get(e.id).copy(phase = phase, bytesPerSec = 0)); notifier().notify(NOTE_ID, info(e, Downloads.get(e.id)).notification) })

            when (r) {
                is DownloadResult.Installed -> Downloads.put(e.id, DlState(DlPhase.DONE, e.sizeBytes, e.sizeBytes))
                is DownloadResult.Unverified -> Downloads.put(e.id, DlState(DlPhase.FAILED, message = "Downloaded but could not be verified"))
                is DownloadResult.Failed -> Downloads.put(e.id, DlState(DlPhase.FAILED, mgr.partBytes(e), e.sizeBytes, message = r.reason))
            }
            if (e.kind == ResourceKind.LLM) app.engines.invalidate()
            notifier().cancel(NOTE_ID)
            if (r is DownloadResult.Installed) done(e)
            return if (r is DownloadResult.Failed) Result.failure() else Result.success()
        } catch (ce: CancellationException) {
            if (Downloads.takeIntent(e.id) == "cancel") { mgr.discardPart(e); Downloads.clear(e.id) }
            else Downloads.put(e.id, Downloads.get(e.id).copy(phase = DlPhase.PAUSED, bytesPerSec = 0))
            throw ce
        }
    }

    private fun notifier() = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun done(e: ResourceEntry) {
        val n = NotificationCompat.Builder(applicationContext, CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("✓ ${e.title}").setContentText("Downloaded and verified. Ready to use.").setAutoCancel(true).build()
        notifier().notify(NOTE_ID + 1, n)
    }

    private fun action(label: String, action: String, id: String, req: Int): NotificationCompat.Action {
        val pi = PendingIntent.getBroadcast(applicationContext, req, Intent(applicationContext, DownloadActionReceiver::class.java).setAction(action).putExtra("id", id), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Action(0, label, pi)
    }

    private fun info(e: ResourceEntry, st: DlState): ForegroundInfo {
        if (Build.VERSION.SDK_INT >= 26) notifier().createNotificationChannel(NotificationChannel(CHANNEL, "Model downloads", NotificationManager.IMPORTANCE_LOW))
        val pct = (st.fraction * 100).toInt()
        val text = when (st.phase) {
            DlPhase.VERIFYING -> "Verifying checksum…"
            DlPhase.UNPACKING -> "Unpacking…"
            else -> "$pct% · ${st.done / 1_048_576} / ${st.total / 1_048_576} MB" + if (st.bytesPerSec > 0) " · %.1f MB/s".format(st.bytesPerSec / 1_048_576.0) else ""
        }
        val b = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle("Downloading ${e.title}").setContentText(text)
            .setOngoing(true).setOnlyAlertOnce(true)
            .setProgress(100, pct, st.phase == DlPhase.VERIFYING || st.phase == DlPhase.UNPACKING || st.total <= 0)
        if (st.phase == DlPhase.DOWNLOADING || st.phase == DlPhase.QUEUED) {
            b.addAction(action("Pause", DownloadActionReceiver.ACTION_PAUSE, e.id, 1))
            b.addAction(action("Cancel", DownloadActionReceiver.ACTION_CANCEL, e.id, 2))
        }
        val n = b.build()
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(NOTE_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else ForegroundInfo(NOTE_ID, n)
    }

    companion object { const val NOTE_ID = 42; private const val CHANNEL = "vision_download" }
}
