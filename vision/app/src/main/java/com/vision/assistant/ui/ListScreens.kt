package com.vision.assistant.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vision.assistant.VisionApp
import com.vision.assistant.core.DownloadResult
import com.vision.assistant.core.InstallState
import com.vision.assistant.core.ModelManager
import com.vision.assistant.core.ResourceManifest
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun TasksScreen(app: VisionApp, back: () -> Unit) {
    val tasks by app.database.tasks().all().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Tasks & reminders", style = MaterialTheme.typography.headlineSmall)
        LazyColumn(Modifier.weight(1f)) {
            items(tasks) { t ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(t.title)
                        t.dueAt?.let { Text(DateFormat.getDateTimeInstance().format(Date(it)), style = MaterialTheme.typography.bodySmall) }
                        t.repeat?.let { Text("repeats $it", style = MaterialTheme.typography.bodySmall) }
                    }
                    Checkbox(checked = t.done, onCheckedChange = { c -> scope.launch { app.database.tasks().update(t.copy(done = c)) } })
                }
            }
        }
        OutlinedButton(onClick = back) { Text("Back") }
    }
}

@Composable
fun MemoryScreen(app: VisionApp, back: () -> Unit) {
    val items by app.database.memory().all().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Memory", style = MaterialTheme.typography.headlineSmall)
        Text("Say \"remember that …\" / \"yaad rakho ki …\" to add. Stored only on this phone.", style = MaterialTheme.typography.bodySmall)
        LazyColumn(Modifier.weight(1f)) {
            items(items) { m ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("[${m.category}] ${m.content}", Modifier.weight(1f))
                    OutlinedButton(onClick = { scope.launch { app.database.memory().delete(m) } }) { Text("Delete") }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { scope.launch { app.database.memory().clear() } }) { Text("Erase all memory") }
            OutlinedButton(onClick = back) { Text("Back") }
        }
    }
}

@Composable
fun ModelsScreen(app: VisionApp, back: () -> Unit) {
    val ctx = LocalContext.current
    val mgr = remember { ModelManager(java.io.File(ctx.filesDir, "models"), app.deviceProfile) }
    val scope = rememberCoroutineScope()
    val status = remember { mutableStateMapOf<String, String>() }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Models & resources", style = MaterialTheme.typography.headlineSmall)
        Text("Tier ${app.deviceProfile.tier}: ${app.deviceProfile.totalRamMb} MB RAM, ${app.deviceProfile.freeStorageMb} MB free. A resource counts as installed only after its checksum matches.", style = MaterialTheme.typography.bodySmall)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(ResourceManifest.entries) { e ->
                val st = mgr.state(e)
                Column {
                    Text("${e.title} (~${e.sizeMb} MB, ${e.license})")
                    Text("State: $st · ${if (mgr.compatible(e)) "compatible" else "not compatible"}" + (status[e.id]?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodySmall)
                    if (e.sha256.isBlank()) Text("No official hash recorded yet: downloads stay UNVERIFIED and unusable.", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = mgr.compatible(e), onClick = {
                            scope.launch {
                                status[e.id] = "downloading…"
                                val r = mgr.download(e) { d, t -> status[e.id] = if (t > 0) "${d * 100 / t}%" else "${d / 1_000_000} MB" }
                                status[e.id] = when (r) {
                                    is DownloadResult.Installed -> "installed & verified"
                                    is DownloadResult.Unverified -> "downloaded, UNVERIFIED (sha256 ${r.computedSha256.take(12)}…)"
                                    is DownloadResult.Failed -> "failed: ${r.reason}"
                                }
                            }
                        }) { Text("Download (Wi-Fi recommended)") }
                        if (st != InstallState.NOT_INSTALLED) OutlinedButton(onClick = { mgr.delete(e); status[e.id] = "deleted" }) { Text("Delete") }
                    }
                }
            }
        }
        OutlinedButton(onClick = back) { Text("Back") }
    }
}
