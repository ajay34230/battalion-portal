package com.vision.assistant.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.vision.assistant.VisionApp
import com.vision.assistant.ai.AssistantSession
import com.vision.assistant.ai.OrbState

private fun orbCaption(s: OrbState) = when (s) {
    OrbState.IDLE -> "Tap the orb to speak"
    OrbState.LISTENING -> "Listening…"
    OrbState.THINKING -> "Thinking…"
    OrbState.SPEAKING -> "Speaking — tap to stop"
}

/**
 * The AI screen. overlay=true is the compact "call VISION" sheet shown over other apps / the lock screen:
 * it starts listening immediately and closes by tapping outside the card.
 */
@Composable
fun AiScreen(app: VisionApp, overlay: Boolean, autoListen: Boolean, onUnlockRequest: (String) -> Unit = {}, unlockedText: String? = null, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val session = remember { AssistantSession(app, ctx, scope) }
    val messages by app.database.chat().all().collectAsState(initial = emptyList())
    val orb by session.orb.collectAsState()
    val level by session.level.collectAsState()
    val heard by session.heard.collectAsState()
    val reply by session.reply.collectAsState()
    val status by session.status.collectAsState()
    val pending by session.pending.collectAsState()
    val permission by session.permission.collectAsState()
    val unlock by session.unlock.collectAsState()
    val engineLabel by app.engines.label.collectAsState()
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    var hindi by remember { mutableStateOf(false) }
    var speak by remember { mutableStateOf(true) }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) session.listen() else session.status.value = "Microphone permission denied."
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { session.permission.value = null }

    val imageRequest by session.imageRequest.collectAsState()
    var seeQuestion by remember { mutableStateOf("") }
    var camUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val takePic = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) camUri?.let { session.analyze(it, seeQuestion, false) } else session.imageRequest.value = null }
    val pickImg = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) session.analyze(uri, seeQuestion, false) else session.imageRequest.value = null }
    val pickDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) session.analyze(uri, seeQuestion, true) else session.imageRequest.value = null }
    fun camera(q: String) {
        seeQuestion = q
        val f = java.io.File(ctx.cacheDir, "cam-${System.currentTimeMillis()}.jpg")
        val u = androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
        camUri = u; takePic.launch(u)
    }
    fun startMic() {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        else session.listen()
    }
    fun orbTap() = when (orb) { OrbState.LISTENING -> session.stopListening(); OrbState.SPEAKING -> session.stopSpeaking(); OrbState.IDLE -> startMic(); OrbState.THINKING -> Unit }

    LaunchedEffect(Unit) { app.engines.current() }
    LaunchedEffect(autoListen) { if (autoListen) startMic() }
    LaunchedEffect(unlock) { unlock?.let { onUnlockRequest(it) } }
    LaunchedEffect(unlockedText) { unlockedText?.let { session.send(it, forceUnlocked = true) } }
    LaunchedEffect(hindi) { session.hindiMic = hindi }
    LaunchedEffect(speak) { session.speakReplies = speak; if (!speak) session.stopSpeaking() }
    LaunchedEffect(messages.size) { if (messages.isNotEmpty() && !overlay) listState.animateScrollToItem(messages.size - 1) }
    DisposableEffect(Unit) { onDispose { session.shutdown() } }

    if (overlay) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xCC02060C), Color(0xF2040A14)))).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
            Column(
                Modifier.fillMaxWidth().padding(20.dp).statusBarsPadding().navigationBarsPadding().glass(28).clickable(enabled = false) {}.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                JarvisOrb(orb, level, Modifier.size(240.dp).clip(CircleShape).clickable { orbTap() })
                Text(orbCaption(orb), color = VisionColors.Muted, fontSize = 13.sp, letterSpacing = 2.sp)
                if (heard.isNotBlank()) Text("“$heard”", color = VisionColors.Cyan, textAlign = TextAlign.Center, fontSize = 18.sp)
                AnimatedVisibility(reply.isNotBlank(), enter = fadeIn(tween(700)) + slideInVertically(tween(700)) { it / 4 }, exit = fadeOut(tween(400))) {
                    Text(reply, textAlign = TextAlign.Center, fontSize = 17.sp, lineHeight = 24.sp)
                }
                if (status.isNotBlank()) Text(status, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, textAlign = TextAlign.Center)
                ConfirmBar(pending, session)
                SeeCard(imageRequest, onCamera = { camera(it) }, onImage = { seeQuestion = it; pickImg.launch("image/*") }, onDoc = { seeQuestion = it; pickDoc.launch(arrayOf("application/pdf")) }, onCancel = { session.imageRequest.value = null })
                permission?.let { p -> Button(onClick = { permLauncher.launch(p) }) { Text("Grant permission, then ask again") } }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !hindi, onClick = { hindi = false }, label = { Text("English") })
                    FilterChip(selected = hindi, onClick = { hindi = true }, label = { Text("हिंदी") })
                    OutlinedButton(onClick = onClose) { Text("Close") }
                }
            }
        }
        return
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onClose) { Text("‹ Back") }
            Text(engineLabel, color = VisionColors.Muted, fontSize = 11.sp, modifier = Modifier.weight(1f).padding(start = 10.dp), maxLines = 2)
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            JarvisOrb(orb, level, Modifier.size(210.dp).clip(CircleShape).clickable { orbTap() })
        }
        Text(orbCaption(orb), color = VisionColors.Muted, fontSize = 12.sp, letterSpacing = 2.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(messages) { m ->
                val mine = m.role == "user"
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                    Text(
                        m.text,
                        Modifier.clip(RoundedCornerShape(18.dp)).background(if (mine) Color(0x3300E5FF) else Color(0x338E5CFF)).padding(horizontal = 14.dp, vertical = 10.dp).fillMaxWidth(0.82f),
                        color = VisionColors.Text, fontSize = 15.sp, lineHeight = 21.sp,
                    )
                }
            }
        }
        if (status.isNotBlank()) Text(status, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        ConfirmBar(pending, session)
        SeeCard(imageRequest, onCamera = { camera(it) }, onImage = { seeQuestion = it; pickImg.launch("image/*") }, onDoc = { seeQuestion = it; pickDoc.launch(arrayOf("application/pdf")) }, onCancel = { session.imageRequest.value = null })
        permission?.let { p -> Button(onClick = { permLauncher.launch(p) }) { Text("Grant permission, then ask again") } }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = false, onClick = { camera(input); input = "" }, label = { Text("📷 Camera") })
            FilterChip(selected = false, onClick = { seeQuestion = input; input = ""; pickImg.launch("image/*") }, label = { Text("🖼 Image") })
            FilterChip(selected = false, onClick = { seeQuestion = input; input = ""; pickDoc.launch(arrayOf("application/pdf")) }, label = { Text("📄 PDF") })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !hindi, onClick = { hindi = false }, label = { Text("Mic: English") })
            FilterChip(selected = hindi, onClick = { hindi = true }, label = { Text("Mic: हिंदी") })
            FilterChip(selected = speak, onClick = { speak = !speak }, label = { Text("Voice replies") })
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(input, { input = it }, Modifier.weight(1f), placeholder = { Text("English, हिंदी or Hinglish") }, singleLine = true, shape = RoundedCornerShape(24.dp))
            Button(onClick = { session.send(input); input = "" }, shape = RoundedCornerShape(24.dp)) { Text("Send") }
        }
    }
}

@Composable
private fun ConfirmBar(pending: com.vision.assistant.ai.AgentReply?, session: AssistantSession) {
    AnimatedVisibility(pending != null, enter = fadeIn(tween(500)), exit = fadeOut(tween(300))) {
        Column(Modifier.fillMaxWidth().glass(18).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(pending?.text.orEmpty())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { session.confirm(true) }) { Text("Confirm") }
                OutlinedButton(onClick = { session.confirm(false) }) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun SeeCard(request: Pair<String, Boolean>?, onCamera: (String) -> Unit, onImage: (String) -> Unit, onDoc: (String) -> Unit, onCancel: () -> Unit) {
    AnimatedVisibility(request != null, enter = fadeIn(tween(500)), exit = fadeOut(tween(300))) {
        val (q, doc) = request ?: ("" to false)
        Column(Modifier.fillMaxWidth().glass(18).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (doc) "Choose the PDF to read" else "Show me something")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (doc) Button(onClick = { onDoc(q) }) { Text("Choose PDF") }
                else { Button(onClick = { onCamera(q) }) { Text("Take photo") }; OutlinedButton(onClick = { onImage(q) }) { Text("Choose image") } }
                OutlinedButton(onClick = onCancel) { Text("Cancel") }
            }
        }
    }
}
