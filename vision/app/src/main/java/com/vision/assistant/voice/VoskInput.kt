package com.vision.assistant.voice

import java.io.File
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService

/** Fully offline speech recognition with a downloaded Vosk model. Call [prepare] off the main thread, [listen] on it. */
class VoskInput(private val modelDir: File) {
    private var model: Model? = null
    private var service: SpeechService? = null

    fun prepare() { if (model == null) model = Model(modelDir.absolutePath) }

    fun listen(onText: (String) -> Unit, onError: (String) -> Unit) {
        try {
            prepare()
            val rec = Recognizer(model, 16000f)
            val s = SpeechService(rec, 16000f)
            service = s
            var delivered = false
            fun finish(h: String?) {
                val t = try { JSONObject(h ?: "{}").optString("text") } catch (_: Exception) { "" }
                if (t.isNotBlank() && !delivered) { delivered = true; stop(); onText(t) }
            }
            s.startListening(object : RecognitionListener {
                override fun onPartialResult(hypothesis: String?) {}
                override fun onResult(hypothesis: String?) = finish(hypothesis)
                override fun onFinalResult(hypothesis: String?) = finish(hypothesis)
                override fun onError(exception: Exception?) { stop(); onError(exception?.message ?: "Offline speech error.") }
                override fun onTimeout() { stop(); if (!delivered) onError("Didn't hear anything.") }
            }, 12_000)
        } catch (t: Throwable) {
            stop(); onError("Offline speech failed: ${t.message}")
        }
    }

    fun stop() { try { service?.stop(); service?.shutdown() } catch (_: Throwable) {}; service = null }
    fun release() { stop(); try { model?.close() } catch (_: Throwable) {}; model = null }
}
