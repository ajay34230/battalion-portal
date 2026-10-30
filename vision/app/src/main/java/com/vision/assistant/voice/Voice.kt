package com.vision.assistant.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.vision.assistant.ai.Lang
import java.util.Locale

/** Speech-to-text using the device recogniser. Offline preference is honoured; actual offline support depends on the installed language pack. */
class SpeechInput(private val ctx: Context) {
    private var recognizer: SpeechRecognizer? = null
    val available: Boolean get() = SpeechRecognizer.isRecognitionAvailable(ctx)

    fun listen(localeTag: String, preferOffline: Boolean, onText: (String) -> Unit, onError: (String) -> Unit) {
        if (!available) { onError("No speech recogniser is installed on this device."); return }
        stop()
        val r = SpeechRecognizer.createSpeechRecognizer(ctx)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (t.isNullOrBlank()) onError("Didn't catch that.") else onText(t)
            }
            override fun onError(error: Int) { onError("Speech recognition error ($error).") }
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        r.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline),
        )
    }

    fun stop() { recognizer?.destroy(); recognizer = null }
}

/** Text-to-speech via the Android engine. Reports honestly when a language voice is missing. */
class Speaker(ctx: Context) {
    private var tts: TextToSpeech? = null
    @Volatile private var ready = false
    var rate = 1.0f
    var onDone: (() -> Unit)? = null

    init {
        tts = TextToSpeech(ctx) { status ->
            ready = status == TextToSpeech.SUCCESS
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) { onDone?.invoke() }
                @Deprecated("Deprecated in Java") override fun onError(utteranceId: String?) { onDone?.invoke() }
            })
        }
    }

    fun hasVoice(lang: Lang): Boolean {
        val t = tts ?: return false
        if (!ready) return false
        val loc = localeFor(lang)
        val r = t.isLanguageAvailable(loc)
        return r >= TextToSpeech.LANG_AVAILABLE
    }

    /** Returns false if no usable voice exists, so the UI can say so instead of staying silent. */
    fun speak(text: String, lang: Lang): Boolean {
        val t = tts ?: return false
        if (!ready || !hasVoice(lang)) return false
        t.language = localeFor(lang)
        t.setSpeechRate(rate)
        t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "vision-${System.nanoTime()}")
        return true
    }

    fun stop() { tts?.stop() }
    fun shutdown() { tts?.shutdown(); tts = null }

    companion object {
        // Romanised Hinglish is pronounced best by an Indian-English voice.
        fun localeFor(lang: Lang): Locale = when (lang) {
            Lang.HI -> Locale("hi", "IN")
            Lang.HINGLISH -> Locale("en", "IN")
            Lang.EN -> Locale("en", "IN")
        }
        fun recognitionTag(hindi: Boolean) = if (hindi) "hi-IN" else "en-IN"
    }
}
