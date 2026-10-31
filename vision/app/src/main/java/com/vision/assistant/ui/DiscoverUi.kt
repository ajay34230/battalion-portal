package com.vision.assistant.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vision.assistant.VisionApp
import com.vision.assistant.core.ModelCatalog
import com.vision.assistant.core.ModelDiscovery
import com.vision.assistant.core.ResourceManifest
import kotlinx.coroutines.launch

/** "Find more models online": searches Hugging Face for LiteRT-LM models, shows whether each fits this phone, and adds the chosen file to the list. Nothing downloads until the owner taps Download on the list. */
@Composable
fun DiscoverCard(app: VisionApp, onAdded: () -> Unit) {
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var repos by remember { mutableStateOf<List<ModelCatalog.Repo>>(emptyList()) }
    var shown by remember { mutableStateOf<ModelCatalog.Repo?>(null) }
    var files by remember { mutableStateOf<List<ModelCatalog.RemoteFile>>(emptyList()) }
    val p = app.deviceProfile
    InfoCard {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Find more models online", fontSize = 15.sp, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { open = !open }) { Text(if (open) "Hide" else "Open") }
        }
        if (open) {
            Text("Searches Hugging Face (publisher litert-community, the models this app's engine can run). Only your search words are sent. Nothing is downloaded until you tap Download on a model in the list below.", fontSize = 11.sp, color = VisionColors.Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(query, { query = it }, Modifier.weight(1f), singleLine = true, label = { Text("Name, e.g. qwen, gemma, phi") })
                Button(enabled = !busy, onClick = {
                    busy = true; msg = ""; shown = null; files = emptyList()
                    scope.launch {
                        ModelDiscovery.search(query).onSuccess { repos = it; msg = if (it.isEmpty()) "No models found." else "${it.size} found." }
                            .onFailure { msg = "Could not search: ${it.message ?: "no internet?"}" }
                        busy = false
                    }
                }) { Text(if (busy) "…" else "Search") }
            }
            if (msg.isNotBlank()) Text(msg, fontSize = 12.sp, color = VisionColors.Muted)
            for (r in repos.take(15)) {
                Column(Modifier.fillMaxWidth().padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(r.id.substringAfter('/'), fontSize = 14.sp)
                    Text("${r.downloads} downloads · license ${r.license.ifBlank { "unknown" }} · ${ModelCatalog.categories(r.id, r.tags).joinToString(", ")}", fontSize = 11.sp, color = VisionColors.Muted)
                    OutlinedButton(enabled = !busy, onClick = {
                        busy = true; shown = r; files = emptyList(); msg = ""
                        scope.launch {
                            ModelDiscovery.files(r.id).onSuccess { files = it; if (it.isEmpty()) msg = "No verifiable .litertlm files in this repository." }
                                .onFailure { msg = "Could not list files: ${it.message}" }
                            busy = false
                        }
                    }) { Text("Show files") }
                    if (shown?.id == r.id) for (f in files) {
                        val a = ModelCatalog.assess(f.sizeBytes / 1_048_576, p.totalRamMb, p.freeStorageMb, p.abis.any { it.contains("64") })
                        Column(Modifier.padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("${f.path} · ${f.sizeBytes / 1_048_576} MB · ${a.level.label}", fontSize = 12.sp)
                            Text(a.reasons.joinToString(" "), fontSize = 11.sp, color = VisionColors.Muted)
                            val entry = ModelCatalog.toEntry(r, f, p.totalRamMb)
                            val have = entry != null && ResourceManifest.byId(entry.id) != null
                            Button(enabled = entry != null && !have && a.level != ModelCatalog.Compat.INCOMPATIBLE, onClick = {
                                if (entry != null) { ResourceManifest.addExtra(app, entry); onAdded(); msg = "Added to the list below. Tap Download there when you want it." }
                            }) { Text(if (have) "Already in list" else if (a.level == ModelCatalog.Compat.INCOMPATIBLE) "Won't fit" else "Add to my models") }
                        }
                    }
                }
            }
        }
    }
}
