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
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.vision.assistant.VisionApp
import com.vision.assistant.ai.Lang
import com.vision.assistant.ai.Orchestrator
import com.vision.assistant.core.Downloads
import com.vision.assistant.core.NetworkMonitor
import com.vision.assistant.voice.Speaker
import com.vision.assistant.voice.SpeechInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService

object WakeWord {
    private val phrase = Regex("\\b(hey|hay|ok|okay|hello|hi|hai)\\s+(vision|visions|vishan|vizhan)\\b")
    /** True when [heard] contains the wake phrase ("hey vision", "ok vision" ...). */
    fun matches(heard: String) = phrase.containsMatchIn(heard.lowercase().trim())
    const val GRAMMAR = "[\"hey vision\",\"ok vision\",\"okay vision\",\"hello vision\",\"hi vision\",\"[unk]\"]"
    /** Once the user has taught VISION their voice, plain "Vision" also wakes it (the voiceprint check keeps false triggers down). */
    const val GRAMMAR_ENROLLED = "[\"hey vision\",\"ok vision\",\"okay vision\",\"hello vision\",\"hi vision\",\"vision\",\"[unk]\"]"
    private val bare = Regex("^\\s*vision\\s*$")
    fun matchesEnrolled(heard: String) = matches(heard) || bare.containsMatchIn(heard.lowercase())
}

/**
 * Opt-in hands-free mode. A visible notification stays on while the microphone is open and a small OFFLINE Vosk model listens
 * only for the phrase "Hey Vision" (nothing is recorded or sent anywhere). After the phrase it says "Yes?", listens once with the phone's
 * speech recogniser, runs the command and speaks the answer, then goes back to waiting.
 * Costs battery (continuous microphone + a small model) and Android may stop it on some phones unless battery optimisation is off.
 */
class WakeWordService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var model: Model? = null
    private var spkModel: org.vosk.SpeakerModel? = null
    private var voiceprint: List<DoubleArray> = emptyList()
    private var strictness = com.vision.assistant.voice.Voiceprint.Strictness.NORMAL
    private var speech: SpeechService? = null
    private var session: Job? = null
    private lateinit var stt: SpeechInput
    private lateinit var speaker: Speaker
    private lateinit var orchestrator: Orchestrator
    @Volatile private var busy = false
    @Volatile private var speakingReply = false

    override fun onCreate() {
        super.onCreate()
        stt = SpeechInput(this); speaker = Speaker(this); orchestrator = Orchestrator(application as VisionApp)
        speaker.onDone = { if (speakingReply) { speakingReply = false; scope.launch { delay(300); busy = false; spot() } } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { com.vision.assistant.research.Prefs.setWakeWord(this, false); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return START_NOT_STICKY }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { stopSelf(); return START_NOT_STICKY }
        startForeground(2, notification("Listening for “Hey Vision”"))
        scope.launch {
            val dir = Downloads.manager(application as VisionApp).voskDir("vosk-small-en-us")
            if (dir == null) { updateNotification("Needs the offline English voice model (Models & downloads)"); delay(4000); stopSelf(); return@launch }
            model = model ?: withContext(Dispatchers.IO) { try { Model(dir.absolutePath) } catch (_: Throwable) { null } }
            if (model == null) { updateNotification("Could not load the voice model"); delay(4000); stopSelf(); return@launch }
            voiceprint = com.vision.assistant.voice.Voiceprint.load(this@WakeWordService)
            strictness = com.vision.assistant.voice.Voiceprint.strictness(this@WakeWordService)
            if (voiceprint.size >= 4 && com.vision.assistant.research.Prefs.voiceOnly(this@WakeWordService)) {
                val sd = Downloads.manager(application as VisionApp).voskDir("vosk-spk")
                spkModel = if (sd == null) null else withContext(Dispatchers.IO) { try { org.vosk.SpeakerModel(sd.absolutePath) } catch (_: Throwable) { null } }
                if (spkModel == null) updateNotification("Voice check unavailable (speaker model missing): answering to any voice")
            }
            spot()
        }
        return START_STICKY
    }

    private fun spot() {
        val m = model ?: return
        try { speech?.stop(); speech?.shutdown() } catch (_: Throwable) {}
        try {
            val verify = spkModel != null && voiceprint.isNotEmpty()
            val rec = Recognizer(m, 16000f, if (verify || voiceprint.size >= 4) WakeWord.GRAMMAR_ENROLLED else WakeWord.GRAMMAR)
            if (verify) rec.setSpeakerModel(spkModel)
            val s = SpeechService(rec, 16000f)
            speech = s
            val l = object : RecognitionListener {
                private fun check(h: String?, final: Boolean) {
                    val t = try { JSONObject(h ?: "{}").let { it.optString("text").ifBlank { it.optString("partial") } } } catch (_: Exception) { "" }
                    if (busy) return
                    if (!verify) { if (WakeWord.matches(t)) onWake(); return }
                    // With "only my voice" on, wait for the end of the phrase (final result carries the speaker vector) and compare.
                    if (!final || !WakeWord.matchesEnrolled(t)) return
                    val v = com.vision.assistant.voice.Voiceprint.vectorFrom(h ?: "") ?: return
                    if (com.vision.assistant.voice.Voiceprint.matches(v, voiceprint, strictness)) onWake()
                }
                override fun onPartialResult(hypothesis: String?) = check(hypothesis, false)
                override fun onResult(hypothesis: String?) = check(hypothesis, true)
                override fun onFinalResult(hypothesis: String?) = check(hypothesis, true)
                override fun onError(exception: Exception?) { scope.launch { delay(2000); if (!busy) spot() } }
                override fun onTimeout() { if (!busy) scope.launch { spot() } }
            }
            s.startListening(l)
        } catch (t: Throwable) { updateNotification("Microphone busy; retrying…"); scope.launch { delay(5000); if (!busy) spot() } }
    }

    private fun onWake() {
        busy = true
        try { speech?.stop(); speech?.shutdown() } catch (_: Throwable) {}; speech = null
        scope.launch {
            speaker.speak("Yes?", Lang.EN)
            delay(900)
            val locked = (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked
            val timeout = scope.launch { delay(25_000); if (busy) { stt.stop(); busy = false; spot() } }
            session = timeout
            stt.listen("en-IN", preferOffline = !NetworkMonitor.isOnline(this@WakeWordService), onText = { text ->
                timeout.cancel()
                scope.launch {
                    val r = orchestrator.handle(text, locked, com.vision.assistant.privacy.LockPrefs.load(this@WakeWordService))
                    speakingReply = true
                    if (!speaker.speak(if (r.confirm != null) "Please confirm on your phone." else r.text, r.lang)) { speakingReply = false; busy = false; spot() }
                }
            }, onError = { timeout.cancel(); busy = false; spot() })
        }
    }

    private fun channel(): String {
        if (Build.VERSION.SDK_INT >= 26) (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(NotificationChannel("vision_wake", "Hey Vision (hands-free)", NotificationManager.IMPORTANCE_LOW))
        return "vision_wake"
    }

    private fun notification(text: String): Notification {
        val stop = PendingIntent.getService(this, 21, Intent(this, WakeWordService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, channel()).setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("VISION hands-free is on")
            .setContentText(text).setOngoing(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).addAction(0, "Turn off", stop).build()
    }

    private fun updateNotification(text: String) { (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(2, notification(text)) }

    override fun onDestroy() { scope.cancel(); try { speech?.stop(); speech?.shutdown() } catch (_: Throwable) {}; try { model?.close() } catch (_: Throwable) {}; try { spkModel?.close() } catch (_: Throwable) {}; stt.stop(); speaker.shutdown(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP = "com.vision.assistant.WAKE_STOP"
        fun start(ctx: Context) { try { ContextCompat.startForegroundService(ctx, Intent(ctx, WakeWordService::class.java)) } catch (_: Throwable) {} }
        fun stop(ctx: Context) { ctx.startService(Intent(ctx, WakeWordService::class.java).setAction(ACTION_STOP)) }
    }
}
