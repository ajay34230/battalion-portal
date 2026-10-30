package com.vision.assistant.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.vision.assistant.VisionApp
import com.vision.assistant.ai.AgentReply
import com.vision.assistant.ai.Lang
import com.vision.assistant.ai.Orchestrator
import com.vision.assistant.core.NetworkMonitor
import com.vision.assistant.voice.Speaker
import com.vision.assistant.voice.SpeechInput
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(app: VisionApp, back: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val orchestrator = remember { Orchestrator(app) }
    val speaker = remember { Speaker(ctx) }
    val stt = remember { SpeechInput(ctx) }
    val messages by app.database.chat().all().collectAsState(initial = emptyList())
    val listState = rememberLazyListState()

    var input by remember { mutableStateOf("") }
    var hindiMic by remember { mutableStateOf(false) }
    var speakReplies by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<AgentReply?>(null) }
    var pendingPermission by remember { mutableStateOf<String?>(null) }
    var listening by remember { mutableStateOf(false) }

    DisposableEffect(Unit) { onDispose { speaker.shutdown(); stt.stop() } }
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1) }

    fun deliver(r: AgentReply) {
        pending = if (r.confirm != null) r else null
        pendingPermission = r.needsPermission
        if (speakReplies && r.confirm == null) {
            if (!speaker.speak(r.text, r.lang)) status = "No ${if (r.lang == Lang.HI) "Hindi" else "English"} voice is installed for speech output (Settings → Text-to-speech)."
        }
    }

    fun send(text: String) {
        if (text.isBlank()) return
        input = ""; status = ""
        scope.launch { deliver(orchestrator.handle(text.trim())) }
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) status = "Permission denied. The action can't run without it."
        pendingPermission = null
    }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        status = if (granted) "Microphone ready. Tap Speak again." else "Microphone permission denied."
    }

    fun startListening() {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            micLauncher.launch(Manifest.permission.RECORD_AUDIO); return
        }
        listening = true; status = "Listening…"; speaker.stop()
        stt.listen(
            Speaker.recognitionTag(hindiMic), preferOffline = !NetworkMonitor.isOnline(ctx),
            onText = { listening = false; status = ""; send(it) },
            onError = { listening = false; status = it },
        )
    }

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("VISION", style = MaterialTheme.typography.titleLarge)
        Text(
            "${orchestrator.engineName} · ${app.deviceProfile.tier} · ${if (NetworkMonitor.isOnline(ctx)) "online" else "offline"}",
            style = MaterialTheme.typography.bodySmall,
        )
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(messages) { m ->
                Text(
                    (if (m.role == "user") "You: " else "VISION: ") + m.text,
                    color = if (m.role == "user") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        if (status.isNotBlank()) Text(status, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        pending?.let { p ->
            Text("Confirm: ${p.text}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val c = p.confirm!!; pending = null
                    scope.launch { deliver(AgentReply(orchestrator.confirmed(c, p.lang), p.lang)) }
                }) { Text("Confirm") }
                OutlinedButton(onClick = {
                    val c = p.confirm!!; pending = null
                    scope.launch { status = orchestrator.cancelled(c, p.lang) }
                }) { Text("Cancel") }
            }
        }
        pendingPermission?.let { perm ->
            Button(onClick = { permLauncher.launch(perm) }) { Text("Grant permission, then repeat the request") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !hindiMic, onClick = { hindiMic = false }, label = { Text("Mic: English") })
            FilterChip(selected = hindiMic, onClick = { hindiMic = true }, label = { Text("Mic: हिंदी") })
            FilterChip(selected = speakReplies, onClick = { speakReplies = !speakReplies; if (!speakReplies) speaker.stop() }, label = { Text("Speak") })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(input, { input = it }, Modifier.weight(1f), placeholder = { Text("Type in English, हिंदी or Hinglish") }, singleLine = true)
            Button(onClick = { send(input) }) { Text("Send") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { if (listening) { stt.stop(); listening = false; status = "" } else startListening() }) { Text(if (listening) "Stop listening" else "Speak") }
            OutlinedButton(onClick = { speaker.stop() }) { Text("Stop talking") }
            OutlinedButton(onClick = back) { Text("Back") }
        }
    }
}
