package com.vision.assistant.ui

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vision.assistant.VisionApp
import com.vision.assistant.code.*
import com.vision.assistant.termux.TermuxGateway
import com.vision.assistant.termux.TermuxPrefs
import com.vision.assistant.termux.Validation
import kotlinx.coroutines.launch

private fun statusColor(s: ToolStatus) = when (s) {
    ToolStatus.INSTALLED_AND_READY -> VisionColors.Mint
    ToolStatus.INSTALLED_BUT_OUTDATED, ToolStatus.PARTIALLY_INSTALLED, ToolStatus.INSTALLATION_REQUIRED, ToolStatus.PERMISSION_REQUIRED -> Color(0xFFFFB86C)
    ToolStatus.UNSUPPORTED, ToolStatus.INSTALLED_BUT_INCOMPATIBLE -> Color(0xFFFF6B6B)
    ToolStatus.NOT_INSTALLED -> VisionColors.Muted
}

/** Development environment inventory + guarded installer. Nothing is downloaded or installed without the summary dialog and an explicit Approve. */
@Composable
fun DevToolsPanel(app: VisionApp, studio: CodeStudio, forRequest: String = "", projectFiles: List<String> = emptyList(), modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { ctx.getSharedPreferences("vision_devenv", android.content.Context.MODE_PRIVATE) }
    var states by remember { mutableStateOf(DevInventory.fromJson(prefs.getString("inventory", "") ?: "")) }
    var busy by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var confirmScan by remember { mutableStateOf(false) }
    var installing by remember { mutableStateOf<ToolSpec?>(null) }
    val needed = remember(forRequest, projectFiles) { RequirementDetector.detect(forRequest, projectFiles) }
    val adv = TermuxPrefs.advanced(ctx)
    val p = app.deviceProfile
    val gateway = remember { TermuxGateway(ctx) }

    fun save(m: Map<String, ToolState>) { states = m; prefs.edit().putString("inventory", DevInventory.toJson(m.values)).apply() }
    suspend fun scan() {
        busy = "Checking installed tools…"
        studio.log("Tool inventory scan")
        val r = gateway.execute(Validation.Ok("bash", listOf("-c", DevInventory.probeScript()), sensitive = true, high = true))
        r.onSuccess { out ->
            val probed = DevInventory.parseProbe(out)
            val m = HashMap<String, ToolState>()
            for (spec in ToolCatalogue.all) m[spec.id] = DevInventory.status(spec, probed[spec.id], true, m)
            // second pass so dependency-based statuses see the first-pass results
            for (spec in ToolCatalogue.all) m[spec.id] = DevInventory.status(spec, probed[spec.id], true, m)
            save(m); message = "Checked ${m.size} tools."
        }.onFailure { message = "Could not check: ${it.message}" }
        busy = ""
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        InfoCard {
            SectionLabel("This phone")
            Text("${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${p.sdk})", fontSize = 14.sp)
            Text("${p.cores} cores · ${p.abis.firstOrNull().orEmpty()} · RAM ${String.format("%.1f", p.totalRamMb / 1024.0)} GB · free storage ${p.freeStorageMb / 1024} GB · tier ${p.tier}", fontSize = 12.sp, color = VisionColors.Muted)
            Text("Built-in, always ready: HTML/CSS/JavaScript editor and sandboxed WebView preview, project snapshots, ZIP export.", fontSize = 12.sp, color = VisionColors.Mint)
        }
        if (needed.isNotEmpty()) InfoCard {
            SectionLabel("This project needs")
            for (id in needed) { val spec = ToolCatalogue.byId(id) ?: continue; val st = states[id]
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(spec.name, Modifier.weight(1f), fontSize = 14.sp)
                    Pill((st?.status ?: ToolStatus.INSTALLATION_REQUIRED).name.lowercase().replace('_', ' '), statusColor(st?.status ?: ToolStatus.INSTALLATION_REQUIRED))
                } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Button(enabled = busy.isEmpty(), onClick = { confirmScan = true }) { Text("Check installed tools") }
            if (busy.isNotEmpty()) Text(busy, color = VisionColors.Cyan, fontSize = 12.sp)
        }
        if (message.isNotBlank()) Text(message, fontSize = 12.sp, color = VisionColors.Mint)
        if (!adv) Text("Termux Advanced mode is off: checking and installing need it (More → Termux control).", fontSize = 12.sp, color = Color(0xFFFFB86C))
        for (spec in ToolCatalogue.all) {
            val st = states[spec.id]
            val status = st?.status ?: if (spec.unsupportedNote != null) ToolStatus.UNSUPPORTED else ToolStatus.NOT_INSTALLED
            InfoCard {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(spec.name + if (!st?.version.isNullOrBlank()) "  ${st?.version}" else "", fontSize = 15.sp)
                        Text(spec.purpose, fontSize = 11.sp, color = VisionColors.Muted)
                        Pill(status.name.lowercase().replace('_', ' '), statusColor(status))
                    }
                    if (status == ToolStatus.NOT_INSTALLED || status == ToolStatus.INSTALLATION_REQUIRED || status == ToolStatus.INSTALLED_BUT_OUTDATED || status == ToolStatus.UNSUPPORTED)
                        TextButton(onClick = { installing = spec }) { Text(if (status == ToolStatus.UNSUPPORTED) "Why not?" else if (status == ToolStatus.INSTALLED_BUT_OUTDATED) "Update" else "Install") }
                }
            }
        }
    }

    if (confirmScan) AlertDialog(onDismissRequest = { confirmScan = false }, title = { Text("Check installed tools?") },
        text = { Text("Category A — inspection only. VISION runs each tool's version command inside Termux (python3 --version, git --version …). Nothing is downloaded or changed.\n\nRequires Termux Advanced mode.") },
        confirmButton = { TextButton(enabled = adv, onClick = { confirmScan = false; scope.launch { scan() } }) { Text("Check") } },
        dismissButton = { TextButton(onClick = { confirmScan = false }) { Text("Cancel") } })

    installing?.let { spec ->
        val cmd = InstallSummary.command(spec)
        val reason = if (spec.id in needed) "your current project needs it" else "you asked to install it"
        AlertDialog(onDismissRequest = { installing = null }, title = { Text(if (cmd == null) spec.name else "Approve install?") },
            text = { Text(InstallSummary.build(spec, reason, p.freeStorageMb), fontSize = 13.sp) },
            confirmButton = {
                if (cmd != null) TextButton(enabled = adv && busy.isEmpty(), onClick = {
                    installing = null
                    scope.launch {
                        busy = "Installing ${spec.name}… (can take several minutes)"; studio.log("User approved install: $cmd")
                        val r = gateway.execute(Validation.Ok("bash", listOf("-c", "yes | $cmd 2>&1 | tail -n 5"), sensitive = true, high = true), timeoutSeconds = 1500)
                        message = r.fold({ "Installer said:\n$it" }, { "Install failed: ${it.message}" })
                        studio.log("Install result for ${spec.id}: ${message.lineSequence().firstOrNull()}")
                        scan() // verify the installation and update the inventory
                    }
                }) { Text("Approve & install") }
            },
            dismissButton = { TextButton(onClick = { installing = null }) { Text(if (cmd == null) "OK" else "Reject") } })
    }
}
