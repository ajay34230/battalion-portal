package com.vision.assistant.ai

import android.app.KeyguardManager
import android.content.Context
import com.vision.assistant.VisionApp
import com.vision.assistant.core.Downloads
import com.vision.assistant.core.NetworkMonitor
import com.vision.assistant.privacy.LockPrefs
import com.vision.assistant.voice.Speaker
import com.vision.assistant.voice.SpeechInput
import com.vision.assistant.voice.VoskInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class OrbState { IDLE, LISTENING, THINKING, SPEAKING }

/** One voice/text conversation, shared by the in-app AI screen and the over-the-lock-screen assistant. */
class AssistantSession(private val app: VisionApp, private val ctx: Context, private val scope: CoroutineScope) {
    private val orchestrator = Orchestrator(app)
    private val speaker = Speaker(ctx)
    private val stt = SpeechInput(ctx)
    private val mgr = Downloads.manager(app)
    private var vosk: VoskInput? = null

    val orb = MutableStateFlow(OrbState.IDLE)
    val level = MutableStateFlow(0f)
    val heard = MutableStateFlow("")
    val reply = MutableStateFlow("")
    val status = MutableStateFlow("")
    val pending = MutableStateFlow<AgentReply?>(null)
    val permission = MutableStateFlow<String?>(null)
    /** Text waiting for the owner to unlock the phone. */
    val unlock = MutableStateFlow<String?>(null)
    /** Set when the user must pick or take an image (question, isDocument). */
    val imageRequest = MutableStateFlow<Pair<String, Boolean>?>(null)
    private val see by lazy { com.vision.assistant.vision.SeeTool(app) }
    var hindiMic = false
    var speakReplies = true

    init { speaker.onDone = { if (orb.value == OrbState.SPEAKING) orb.value = OrbState.IDLE } }

    private fun isLocked() = (ctx.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked

    private var job: kotlinx.coroutines.Job? = null

    /** Stops waiting for a stuck or too-slow answer and resets the model. */
    fun cancelThinking() {
        job?.cancel(); job = null
        app.engines.abandon()
        orb.value = OrbState.IDLE; status.value = "Stopped. The model was reset and will reload on the CPU."
    }

    fun send(text: String, forceUnlocked: Boolean = false) {
        if (text.isBlank()) return
        if (orb.value == OrbState.THINKING) { status.value = "Still working on your last message. Tap the orb to stop it."; return }
        heard.value = text.trim(); status.value = ""; reply.value = ""; unlock.value = null
        orb.value = OrbState.THINKING
        job = scope.launch {
            val locked = !forceUnlocked && isLocked()
            val t0 = System.currentTimeMillis()
            val r = try { orchestrator.handle(text.trim(), locked, LockPrefs.load(ctx), recordUser = !forceUnlocked) } catch (t: Throwable) { AgentReply("Something went wrong: ${t.message}", Lang.EN) }
            com.vision.assistant.diag.DiagLog.log("reply_total", "", System.currentTimeMillis() - t0, app.engines.label.value.substringBefore(" · "), app.engines.label.value.substringAfter(" · ", "").substringBefore(" ("))
            deliver(r, text.trim())
        }
    }

    private fun deliver(r: AgentReply, original: String) {
        reply.value = r.text
        pending.value = if (r.confirm != null) r else null
        permission.value = r.needsPermission
        if (r.needsUnlock) unlock.value = original
        imageRequest.value = r.needsImage?.let { it to r.needsDocument }
        orb.value = OrbState.IDLE
        if (speakReplies && r.confirm == null) {
            if (speaker.speak(r.text, r.lang)) orb.value = OrbState.SPEAKING
            else status.value = "No ${if (r.lang == Lang.HI) "Hindi" else "English"} voice installed for speech (Settings → Text-to-speech)."
        }
    }

    /** Caller must already hold RECORD_AUDIO. */
    fun listen() {
        speaker.stop(); stopListening()
        orb.value = OrbState.LISTENING; status.value = ""; heard.value = ""
        val voskDir = if (com.vision.assistant.research.Prefs.phoneVoice(ctx)) null else mgr.voskDir(if (hindiMic) "vosk-small-hi" else "vosk-small-en-us")
        if (voskDir != null) {
            scope.launch {
                vosk?.release()
                val v = VoskInput(voskDir); vosk = v
                val ok = withContext(Dispatchers.IO) { try { v.prepare(); true } catch (t: Throwable) { status.value = "Offline speech model failed: ${t.message}"; false } }
                if (!ok) { orb.value = OrbState.IDLE; return@launch }
                v.listen(onText = { level.value = 0f; send(it) }, onError = { orb.value = OrbState.IDLE; level.value = 0f; status.value = it }, onActivity = { level.value = it })
            }
            return
        }
        stt.listen(
            Speaker.recognitionTag(hindiMic), preferOffline = !NetworkMonitor.isOnline(ctx),
            onText = { send(it) }, onError = { orb.value = OrbState.IDLE; level.value = 0f; status.value = it },
            onLevel = { level.value = it },
        )
    }

    /** Looks at a photo, or reads a PDF when [document], and answers [question]. */
    fun analyze(uri: android.net.Uri, question: String, document: Boolean) {
        imageRequest.value = null
        heard.value = (if (document) "📄 " else "📷 ") + question.ifBlank { if (document) "Summarise this document" else "What is this?" }
        reply.value = ""; status.value = ""; orb.value = OrbState.THINKING
        scope.launch {
            val lang = LanguageDetector.detect(question)
            val text = try {
                if (document) see.readPdf(uri, question, lang) else see.describeImage(uri, question, lang)
            } catch (t: Throwable) { "I couldn't analyse that: ${t.message}" }
            app.database.chat().insert(com.vision.assistant.data.ChatMessage(role = "user", text = heard.value, lang = lang.code))
            app.database.chat().insert(com.vision.assistant.data.ChatMessage(role = "assistant", text = text, lang = lang.code))
            deliver(AgentReply(text, lang), "")
        }
    }

    fun stopListening() { stt.stop(); vosk?.stop(); level.value = 0f; if (orb.value == OrbState.LISTENING) orb.value = OrbState.IDLE }
    fun stopSpeaking() { speaker.stop(); if (orb.value == OrbState.SPEAKING) orb.value = OrbState.IDLE }

    fun confirm(yes: Boolean) {
        val p = pending.value ?: return
        pending.value = null
        scope.launch {
            val c = p.confirm ?: return@launch
            val t = if (yes) orchestrator.confirmed(c, p.lang) else orchestrator.cancelled(c, p.lang)
            deliver(AgentReply(t, p.lang), "")
        }
    }

    fun shutdown() { stt.stop(); vosk?.release(); speaker.shutdown() }
}
