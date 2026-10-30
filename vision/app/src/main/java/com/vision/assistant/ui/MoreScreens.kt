package com.vision.assistant.ui

import android.content.Intent
import android.provider.Settings
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.vision.assistant.VisionApp
import com.vision.assistant.research.Prefs
import com.vision.assistant.privacy.LockPrefs
import com.vision.assistant.privacy.LockSettings
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import com.vision.assistant.service.VoiceService
import com.vision.assistant.termux.CommandPolicy
import com.vision.assistant.termux.TermuxGateway
import com.vision.assistant.termux.TermuxResults
import com.vision.assistant.termux.Validation
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
private fun ToggleRow(title: String, sub: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().glass(16).padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title)
            if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = VisionColors.Muted)
        }
        androidx.compose.material3.Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
fun SettingsScreen(app: VisionApp, back: () -> Unit) {
    val ctx = LocalContext.current
    var searx by remember { mutableStateOf(Prefs.searxngUrl(ctx)) }
    var msg by remember { mutableStateOf("") }
    var lock by remember { mutableStateOf(LockPrefs.load(ctx)) }
    fun upd(n: LockSettings) { lock = n; LockPrefs.save(ctx, n) }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        Text("Calling VISION", style = MaterialTheme.typography.titleMedium, color = VisionColors.Cyan)
        Text("VISION opens as a glowing sheet over any screen. Ways to call it: the Quick Settings tile “Ask VISION”, the Speak button in the voice-session notification, or set VISION as your Digital assistant app (Android Settings → Apps → Default apps) so a power-button or home-gesture long press opens it. A hands-free “Hey Vision” wake word is not built yet.", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { ctx.startActivity(Intent(ctx, com.vision.assistant.AssistActivity::class.java)) }) { Text("Try it now") }
            OutlinedButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Text("Default assistant") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) msg = "Grant microphone permission first (Permissions screen)."
                else { ContextCompat.startForegroundService(ctx, Intent(ctx, VoiceService::class.java)); msg = "Voice-session notification is on." }
            }) { Text("Show notification") }
            OutlinedButton(onClick = { ctx.startService(Intent(ctx, VoiceService::class.java).setAction(VoiceService.ACTION_STOP)); msg = "Notification removed." }) { Text("Remove") }
        }

        Text("Lock screen", style = MaterialTheme.typography.titleMedium, color = VisionColors.Cyan)
        Text("No limits are on by default. VISION never bypasses your PIN, pattern or fingerprint: apps and calls make Android ask you to unlock first.", style = MaterialTheme.typography.bodySmall)
        ToggleRow("Show VISION over the lock screen", "Turns the screen on and shows the assistant even when locked", lock.overLockScreen) { upd(lock.copy(overLockScreen = it)) }
        ToggleRow("Limit what VISION does when locked", "Off = no limits. Turn on to pick what is allowed.", lock.limitsEnabled) { upd(lock.copy(limitsEnabled = it)) }
        if (lock.limitsEnabled) {
            ToggleRow("Open apps", "", lock.allowApps) { upd(lock.copy(allowApps = it)) }
            ToggleRow("Calls", "", lock.allowCalls) { upd(lock.copy(allowCalls = it)) }
            ToggleRow("Messages (WhatsApp drafts)", "", lock.allowMessages) { upd(lock.copy(allowMessages = it)) }
            ToggleRow("Read notifications & email", "", lock.allowNotifications) { upd(lock.copy(allowNotifications = it)) }
            ToggleRow("Tasks & memory", "", lock.allowPersonalData) { upd(lock.copy(allowPersonalData = it)) }
        }
        ToggleRow("Read OTP / bank messages aloud", "Off by default so codes are not spoken in public. Applies to newly received notifications.", lock.readSensitiveAloud) { upd(lock.copy(readSensitiveAloud = it)) }

        Text("Search", style = MaterialTheme.typography.titleMedium, color = VisionColors.Cyan)
        Text("Optional SearXNG server (must have JSON output enabled). Empty = Wikipedia.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(searx, { searx = it }, Modifier.fillMaxWidth(), label = { Text("SearXNG URL") }, singleLine = true)
        Button(onClick = { Prefs.setSearxngUrl(ctx, searx); msg = "Saved." }) { Text("Save") }
        if (msg.isNotBlank()) Text(msg)
        OutlinedButton(onClick = back) { Text("Back") }
    }
}

@Composable
fun PrivacyScreen(app: VisionApp, back: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val audit by app.database.audit().recent().collectAsState(initial = emptyList())
    var msg by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Privacy centre", style = MaterialTheme.typography.headlineSmall)
        Text("Everything is stored only on this phone. Nothing is uploaded except the searches and downloads you ask for.", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { scope.launch { app.database.chat().clear(); msg = "Chat erased." } }) { Text("Erase chat") }
            OutlinedButton(onClick = { scope.launch { app.database.notifications().clear(); msg = "Notification history erased." } }) { Text("Erase notifications") }
        }
        OutlinedButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Text("Notification access settings") }
        if (msg.isNotBlank()) Text(msg)
        Text("Action log (latest first)", style = MaterialTheme.typography.titleMedium)
        LazyColumn(Modifier.weight(1f)) {
            items(audit) { a -> Text("${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(a.createdAt))} · ${a.tool} · ${a.outcome} · ${a.summary}", style = MaterialTheme.typography.bodySmall) }
        }
        OutlinedButton(onClick = back) { Text("Back") }
    }
}

@Composable
fun TermuxScreen(app: VisionApp, back: () -> Unit) {
    val ctx = LocalContext.current
    val gateway = remember { TermuxGateway(ctx) }
    val out by TermuxResults.last.collectAsState()
    var script by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<Validation.Ok?>(null) }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> msg = if (ok) "Permission granted. Tap the command again." else "Permission denied." }

    fun go(cmd: String, args: List<String>) {
        if (ContextCompat.checkSelfPermission(ctx, "com.termux.permission.RUN_COMMAND") != android.content.pm.PackageManager.PERMISSION_GRANTED) { perm.launch("com.termux.permission.RUN_COMMAND"); return }
        when (val v = CommandPolicy.validate(cmd, args)) {
            is Validation.Rejected -> msg = "Rejected: ${v.reason}"
            is Validation.Ok -> if (v.sensitive) pending = v else msg = gateway.run(v) ?: "Sent to Termux."
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Termux", style = MaterialTheme.typography.headlineSmall)
        Text(if (gateway.isInstalled()) "Termux is installed." else "Termux is not installed. Install it from F-Droid or the official GitHub releases (termux/termux-app), not Google Play. VISION works without it.", style = MaterialTheme.typography.bodySmall)
        Text("In Termux run once: mkdir -p ~/.termux ~/vision-scripts && echo 'allow-external-apps=true' >> ~/.termux/termux.properties, then restart Termux.", style = MaterialTheme.typography.bodySmall)
        Text("Only allow-listed commands run, each with a 30 s limit:", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { go("uname", listOf("-a")) }) { Text("uname -a") }
            OutlinedButton(onClick = { go("date", emptyList()) }) { Text("date") }
            OutlinedButton(onClick = { go("ls", listOf("${CommandPolicy.HOME}/vision-scripts")) }) { Text("list scripts") }
        }
        OutlinedTextField(script, { script = it }, Modifier.fillMaxWidth(), label = { Text("Script in ~/vision-scripts (name.py)") }, singleLine = true)
        Button(onClick = { go("python3", listOf(script.trim())) }) { Text("Run script (asks to confirm)") }
        if (msg.isNotBlank()) Text(msg)
        Text("Last result:", style = MaterialTheme.typography.titleMedium)
        Text(out.ifBlank { "(none yet)" }, style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = back) { Text("Back") }
    }
    pending?.let { v ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Run this script in Termux?") },
            text = { Text("${v.binary} ${v.args.joinToString(" ")}") },
            confirmButton = { Button(onClick = { msg = gateway.run(v) ?: "Sent to Termux."; pending = null }) { Text("Run") } },
            dismissButton = { OutlinedButton(onClick = { pending = null }) { Text("Cancel") } },
        )
    }
}
