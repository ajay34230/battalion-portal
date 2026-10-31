package com.vision.assistant.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.vision.assistant.VisionApp
import com.vision.assistant.core.Downloads
import com.vision.assistant.voice.Voiceprint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.SpeakerModel

/** Voice setup: the user says the wake phrases; VISION stores a voiceprint on the phone so only that voice wakes it. */
@SuppressLint("MissingPermission")
@Composable
fun VoiceSetupScreen(app: VisionApp, go: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val mgr = remember { Downloads.manager(app) }
    val en = mgr.voskDir("vosk-small-en-us"); val spk = mgr.voskDir("vosk-spk")
    var hasMic by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasMic = it }
    val samples = remember { mutableStateListOf<DoubleArray>() }
    var step by remember { mutableStateOf(0) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(Voiceprint.enrolled(ctx)) }
    var strict by remember { mutableStateOf(Voiceprint.strictness(ctx)) }
    var only by remember { mutableStateOf(com.vision.assistant.research.Prefs.voiceOnly(ctx)) }

    fun record() {
        if (en == null || spk == null) return
        busy = true; status = "Listening… say it now"
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                try {
                    val rate = 16000
                    val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                    val ar = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, 8000))
                    Model(en.absolutePath).use { m -> SpeakerModel(spk.absolutePath).use { sm -> Recognizer(m, rate.toFloat(), sm).use { rec ->
                        val buf = ShortArray(1600); var total = 0
                        ar.startRecording()
                        while (total < rate * 3) { val n = ar.read(buf, 0, buf.size); if (n > 0) { rec.acceptWaveForm(buf, n); total += n } else break }
                        ar.stop(); ar.release()
                        val json = rec.finalResult
                        val text = org.json.JSONObject(json).optString("text")
                        Triple(text, Voiceprint.vectorFrom(json), null as String?)
                    } } }
                } catch (t: Throwable) { Triple("", null, t.message ?: "recording failed") }
            }
            busy = false
            when {
                r.third != null -> status = "Could not record: ${r.third}"
                !Voiceprint.isWakePhrase(r.first) -> status = "I heard “${r.first.ifBlank { "nothing" }}”. Please say “${Voiceprint.PROMPTS[step]}” clearly and try again."
                r.second == null -> status = "That was too short for a voiceprint. Say it a little longer/louder."
                else -> {
                    samples.add(r.second!!); step++
                    if (step >= Voiceprint.PROMPTS.size) { Voiceprint.save(ctx, samples.toList()); done = true; status = "Voice saved." } else status = "Got it ✓"
                }
            }
        }
    }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Teach VISION your voice", fontSize = 20.sp)
        Text("You say “Hey Vision”, “Vision” and “Hi Vision” a few times. VISION keeps a voiceprint (numbers, not recordings) only on this phone, and the hands-free wake word then answers only to a voice that matches. Nothing is uploaded.", fontSize = 12.sp, color = VisionColors.Muted)
        Text("Honest limits: this tells voices apart reasonably well but is not security. A recording of you can pass it, and a cold, noise or a different distance can make it miss you. Use Loose if it ignores you too often. It never unlocks your phone or bypasses your PIN.", fontSize = 12.sp, color = VisionColors.Muted)
        if (en == null || spk == null) {
            InfoCard {
                Text("Needs two small offline voice models (English speech ~41 MB, speaker ~14 MB).", fontSize = 13.sp)
                Button(onClick = { go(Routes.MODELS) }) { Text("Open Models & downloads") }
            }
        } else if (!hasMic) {
            Button(onClick = { askMic.launch(Manifest.permission.RECORD_AUDIO) }) { Text("Allow microphone") }
        } else if (!done) {
            InfoCard {
                Text("Step ${step + 1} of ${Voiceprint.PROMPTS.size}", fontSize = 12.sp, color = VisionColors.Muted)
                Text("Say: “${Voiceprint.PROMPTS[step]}”", fontSize = 22.sp)
                Button(enabled = !busy, onClick = { record() }) { Text(if (busy) "Listening…" else "Record") }
                if (status.isNotBlank()) Text(status, fontSize = 12.sp)
            }
        } else {
            InfoCard {
                Text("✓ Voice saved on this phone", fontSize = 16.sp, color = VisionColors.Mint)
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    androidx.compose.material3.Switch(only, { only = it; com.vision.assistant.research.Prefs.setVoiceOnly(ctx, it) })
                    Text("  Only respond to my voice", fontSize = 14.sp)
                }
                Text("How strict", fontSize = 12.sp, color = VisionColors.Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { for (s in Voiceprint.Strictness.values()) Pill(s.label, selected = strict == s) { strict = s; Voiceprint.setStrictness(ctx, s) } }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { Voiceprint.clear(ctx); samples.clear(); step = 0; done = false; status = "" }) { Text("Record again") }
                    OutlinedButton(onClick = { Voiceprint.clear(ctx); samples.clear(); step = 0; done = false; status = "Voice deleted." }) { Text("Delete my voiceprint") }
                }
                Text("Turn on “Hey Vision” in Settings to use it.", fontSize = 12.sp, color = VisionColors.Muted)
            }
        }
    }
}
