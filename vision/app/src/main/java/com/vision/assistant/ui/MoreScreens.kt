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
import androidx.compose.ui.unit.sp
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
    var brave by remember { mutableStateOf(Prefs.braveKey(ctx)) }
    var msg by remember { mutableStateOf("") }
    var lock by remember { mutableStateOf(LockPrefs.load(ctx)) }
    fun upd(n: LockSettings) { lock = n; LockPrefs.save(ctx, n) }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ScreenTitle("Settings")
        Text("Everything about how VISION listens, speaks, searches and behaves on a locked phone.", fontSize = 13.sp, color = VisionColors.Muted)

        GroupHeader("Language model", "Which installed AI brain VISION thinks with")
        run {
            val mgr = remember { com.vision.assistant.core.Downloads.manager(app) }
            val installedLlms = remember { com.vision.assistant.core.ResourceManifest.entries.filter { it.kind == com.vision.assistant.core.ResourceKind.LLM && mgr.state(it) == com.vision.assistant.core.InstallState.INSTALLED } }
            var chosen by remember { mutableStateOf(app.engines.preferred()) }
            val label by app.engines.label.collectAsState()
            Text("Now running: $label", fontSize = 12.sp, color = VisionColors.Muted)
            if (installedLlms.isEmpty()) Text("No model installed yet. Open More → Models & downloads.", fontSize = 12.sp)
            else Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                fun pick(id: String) { chosen = id; app.engines.setPreferred(id); app.appScope.launch { try { app.engines.current() } catch (_: Throwable) {} } }
                androidx.compose.material3.FilterChip(selected = chosen == "auto", onClick = { pick("auto") }, label = { Text("Automatic (strongest that fits)") })
                for (m in installedLlms) androidx.compose.material3.FilterChip(selected = chosen == m.id, onClick = { pick(m.id) }, label = { Text(m.title + (if (m.experimental) "  (experimental)" else "") + "  ·  " + app.engines.status(m).label) })
                Text("Switching reloads the model; the first answer after a switch is slower. Smaller models answer faster.", fontSize = 11.sp, color = VisionColors.Muted)
            }
        }
        GroupHeader("Calling VISION")
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

        GroupHeader("Lock screen")
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

        GroupHeader("Search")
        Text("Optional SearXNG server (must have JSON output enabled). Empty = Wikipedia.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(searx, { searx = it }, Modifier.fillMaxWidth(), label = { Text("SearXNG URL") }, singleLine = true)
        var wake by remember { mutableStateOf(Prefs.wakeWord(ctx)) }
        val micOk = ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val voskOk = remember { com.vision.assistant.core.Downloads.manager(app).voskDir("vosk-small-en-us") != null }
        ToggleRow("Hands-free “Hey Vision” (experimental)", "Keeps the microphone open (visible notification) so a small offline model can hear “Hey Vision”. Nothing is recorded or sent. Uses battery. " +
            if (!micOk) "Needs the microphone permission first." else if (!voskOk) "Needs the offline English voice model (More → Models & downloads)." else "Say “Hey Vision”, wait for “Yes?”, then speak.", wake, enabled = micOk && voskOk) {
            wake = it; Prefs.setWakeWord(ctx, it); if (it) com.vision.assistant.service.WakeWordService.start(ctx) else com.vision.assistant.service.WakeWordService.stop(ctx)
        }
        var gpu by remember { mutableStateOf(Prefs.useGpu(ctx)) }
        ToggleRow("Use the phone's GPU for the language model", "Much faster for big models like Gemma 4. If the GPU fails or crashes VISION falls back to the CPU by itself. Takes effect the next time the model loads (restart the app). The Home screen shows GPU or CPU next to the model name.", gpu) { gpu = it; Prefs.setUseGpu(ctx, it) }
        var phoneVoice by remember { mutableStateOf(Prefs.phoneVoice(ctx)) }
        ToggleRow("Use my phone's built-in voice", "Talk using the speech recogniser and voice already on your phone (Google/vivo). Turn off to use VISION's downloaded offline model instead.", phoneVoice) { phoneVoice = it; Prefs.setPhoneVoice(ctx, it) }
        Text("Optional Brave Search API key (free at brave.com/search/api) for better web results. Without any key VISION uses DuckDuckGo + Wikipedia.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(brave, { brave = it }, Modifier.fillMaxWidth(), label = { Text("Brave API key") }, singleLine = true)
        Button(onClick = { Prefs.setSearxngUrl(ctx, searx); Prefs.setBraveKey(ctx, brave); msg = "Saved." }) { Text("Save") }
        if (msg.isNotBlank()) Text(msg)
        androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 48.dp))
    }
}

@Composable
fun PrivacyScreen(app: VisionApp, back: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val audit by app.database.audit().recent().collectAsState(initial = emptyList())
    var msg by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ScreenTitle("Privacy centre")
        Text("Everything is stored only on this phone. Nothing is uploaded except the searches and downloads you ask for.", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { scope.launch { app.database.chat().clear(); msg = "Chat erased." } }) { Text("Erase chat") }
            OutlinedButton(onClick = { scope.launch { app.database.notifications().clear(); msg = "Notification history erased." } }) { Text("Erase notifications") }
        }
        OutlinedButton(onClick = {
            scope.launch {
                try {
                    val json = com.vision.assistant.backup.Backup.toJson(app.database.memory().list(), app.database.tasks().pending() + emptyList())
                    val f = com.vision.assistant.core.SharedFiles.file(ctx, "vision-backup.json").also { it.writeText(json) }
                    val uri = androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Export my VISION data").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    msg = "Backup ready: your memory and open reminders as a JSON file (no passwords or keys)."
                } catch (t: Throwable) { msg = "Export failed: ${t.message}" }
            }
        }) { Text("Export my data (JSON)") }
        val picker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) scope.launch {
                msg = try { val text = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().take(2_000_000).toByteArray().toString(Charsets.UTF_8) }.orEmpty(); com.vision.assistant.backup.restoreBackup(app, text) } catch (t: Throwable) { "Import failed: ${t.message}" }
            }
        }
        OutlinedButton(onClick = { picker.launch(arrayOf("application/json", "text/plain", "*/*")) }) { Text("Import a VISION export") }
        OutlinedButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Text("Notification access settings") }
        if (msg.isNotBlank()) Text(msg)
        Text("Action log (latest first)", style = MaterialTheme.typography.titleMedium)
        LazyColumn(Modifier.weight(1f)) {
            items(audit) { a -> Text("${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(a.createdAt))} · ${a.tool} · ${a.outcome} · ${a.summary}", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun TermuxScreen(app: VisionApp, back: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val gateway = remember { TermuxGateway(ctx) }
    val out by TermuxResults.last.collectAsState()
    var line by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var advanced by remember { mutableStateOf(com.vision.assistant.termux.TermuxPrefs.advanced(ctx)) }
    var pending by remember { mutableStateOf<Validation.Ok?>(null) }
    var retry by remember { mutableStateOf<String?>(null) }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        msg = if (ok) "Permission granted." else "Permission denied."
        if (ok) retry?.let { l -> retry = null; line = l }
    }
    val home = CommandPolicy.HOME

    fun exec(v: Validation.Ok) { msg = "Running…"; scope.launch { val r = gateway.execute(v); msg = r.fold({ "Done." }, { it.message ?: "Failed." }) } }
    fun go(l: String) {
        if (ContextCompat.checkSelfPermission(ctx, "com.termux.permission.RUN_COMMAND") != android.content.pm.PackageManager.PERMISSION_GRANTED) { retry = l; msg = "Termux control is not allowed yet. If no pop-up appears, open More → Permissions → Termux control and follow the 3 steps there (Termux installed from F-Droid/GitHub, permission in app settings, allow-external-apps in Termux)."; perm.launch("com.termux.permission.RUN_COMMAND"); return }
        when (val v = CommandPolicy.validateLine(l, advanced)) {
            is Validation.Rejected -> msg = "Rejected: ${v.reason}"
            is Validation.Ok -> if (v.sensitive) pending = v else exec(v)
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ScreenTitle("Termux")
        Text(
            (if (gateway.isInstalled()) "Termux: installed. " else "Termux: NOT installed (get it from F-Droid or github.com/termux/termux-app, not Google Play). ") +
                (if (gateway.apiInstalled()) "Termux:API: installed." else "Termux:API app: not installed (needed for torch, camera, location, SMS, volume…; install it from the same source as Termux)."),
            style = MaterialTheme.typography.bodySmall,
        )
        Text("One-time setup: open Termux, paste this, then close and reopen Termux:", style = MaterialTheme.typography.titleSmall, color = VisionColors.Cyan)
        androidx.compose.foundation.text.selection.SelectionContainer { Text(TermuxGateway.SETUP, style = MaterialTheme.typography.bodySmall) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                (ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("termux-setup", TermuxGateway.SETUP)); msg = "Setup command copied."
            }) { Text("Copy setup") }
            OutlinedButton(onClick = { ctx.packageManager.getLaunchIntentForPackage("com.termux")?.let { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } ?: run { msg = "Termux is not installed." } }) { Text("Open Termux") }
        }

        Text("Quick actions", style = MaterialTheme.typography.titleSmall, color = VisionColors.Cyan)
        val quick = listOf(
            "Torch on" to "termux-torch on", "Torch off" to "termux-torch off", "Battery" to "termux-battery-status", "Wi-Fi" to "termux-wifi-connectioninfo",
            "Location" to "termux-location", "Photo" to "termux-camera-photo -c 0 $home/vision-output/photo.jpg", "Clipboard" to "termux-clipboard-get",
            "Volume 8" to "termux-volume music 8", "Vibrate" to "termux-vibrate -d 300", "Storage" to "df -h", "RAM" to "free -m", "Uptime" to "uptime",
            "Scripts" to "ls $home/vision-scripts", "Contacts" to "termux-contact-list", "SMS list" to "termux-sms-list -l 5", "Date" to "date",
        )
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            quick.forEach { (label, cmd) -> OutlinedButton(onClick = { go(cmd) }) { Text(label) } }
        }

        Text("Any command", style = MaterialTheme.typography.titleSmall, color = VisionColors.Cyan)
        OutlinedTextField(line, { line = it }, Modifier.fillMaxWidth(), label = { Text("e.g. termux-tts-speak \"hello\"") }, singleLine = true)
        Button(onClick = { go(line) }) { Text("Run") }
        Row(Modifier.fillMaxWidth().glass(16).padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text("Advanced: allow any shell command")
                Text("Off = only the built-in command list. On = anything can be typed or requested by voice, but every non-listed command asks you to confirm the exact text first. Only enable if you understand shell commands.", style = MaterialTheme.typography.bodySmall, color = VisionColors.Muted)
            }
            androidx.compose.material3.Switch(checked = advanced, onCheckedChange = { advanced = it; com.vision.assistant.termux.TermuxPrefs.setAdvanced(ctx, it) })
        }
        if (msg.isNotBlank()) Text(msg)
        Text("Last result:", style = MaterialTheme.typography.titleMedium)
        androidx.compose.foundation.text.selection.SelectionContainer { Text(out.ifBlank { "(none yet)" }, style = MaterialTheme.typography.bodySmall) }
    }
    pending?.let { v ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(if (v.high) "⚠ Run this in Termux?" else "Run this in Termux?") },
            text = { Text((listOf(v.binary) + v.args).joinToString(" ")) },
            confirmButton = { Button(onClick = { exec(v); pending = null }) { Text("Run") } },
            dismissButton = { OutlinedButton(onClick = { pending = null }) { Text("Cancel") } },
        )
    }
}
