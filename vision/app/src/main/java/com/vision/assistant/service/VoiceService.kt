package com.vision.assistant.service

import android.Manifest
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.service.quicksettings.TileService
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.vision.assistant.VisionApp
import com.vision.assistant.ai.Orchestrator
import com.vision.assistant.core.NetworkMonitor
import com.vision.assistant.voice.Speaker
import com.vision.assistant.voice.SpeechInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Visible foreground voice session: a persistent notification with a "Speak" action (and a Quick Settings tile).
 * It listens ONLY after the user taps Speak: there is no hidden recording and no wake word in this build
 * (wake word needs a trained "Hey Vision" model; see KNOWN_LIMITATIONS.md).
 */
class VoiceService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var stt: SpeechInput
    private lateinit var speaker: Speaker
    private lateinit var orchestrator: Orchestrator

    override fun onCreate() {
        super.onCreate()
        stt = SpeechInput(this); speaker = Speaker(this)
        orchestrator = Orchestrator(application as VisionApp)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { stopSelf(); return START_NOT_STICKY }
        startForeground(1, notification())
        when (intent?.action) {
            ACTION_STOP -> { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
            ACTION_SPEAK -> listenOnce()
        }
        return START_NOT_STICKY
    }

    private fun listenOnce() {
        speaker.stop()
        val locked = (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked
        stt.listen("en-IN", preferOffline = !NetworkMonitor.isOnline(this), onText = { text ->
            scope.launch {
                val r = orchestrator.handle(text, locked)
                // Never read confirmations or sensitive content aloud while locked: just speak the short reply.
                speaker.speak(if (r.confirm != null) "Please confirm on your phone." else r.text, r.lang)
            }
        }, onError = { speaker.speak(it, com.vision.assistant.ai.Lang.EN) })
    }

    private fun notification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("vision_voice", "Voice session", NotificationManager.IMPORTANCE_LOW))
        fun action(a: String) = PendingIntent.getService(this, a.hashCode(), Intent(this, VoiceService::class.java).setAction(a), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, "vision_voice")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("VISION is ready").setContentText("Tap Speak to talk")
            .setOngoing(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .addAction(0, "Speak", PendingIntent.getActivity(this, 7, Intent(this, com.vision.assistant.AssistActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)).addAction(0, "Stop", action(ACTION_STOP)).build()
    }

    override fun onDestroy() { scope.cancel(); stt.stop(); speaker.shutdown(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_SPEAK = "com.vision.assistant.SPEAK"
        const val ACTION_STOP = "com.vision.assistant.STOP"
    }
}

class AskVisionTile : TileService() {
    override fun onClick() {
        val i = Intent(this, com.vision.assistant.AssistActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE))
        else @Suppress("DEPRECATION") startActivityAndCollapse(i)
    }
}
