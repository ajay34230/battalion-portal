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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vision.assistant.VisionApp
import com.vision.assistant.core.InstallState
import com.vision.assistant.core.ResourceManifest
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun ModelsScreen(app: VisionApp, back: () -> Unit) {
    val ctx = LocalContext.current
    val mgr = remember { com.vision.assistant.core.Downloads.manager(app) }
    val states by com.vision.assistant.core.Downloads.states.collectAsState()
    var confirm by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<com.vision.assistant.core.ResourceEntry?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) { com.vision.assistant.core.Downloads.refreshFromDisk(app) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ScreenTitle("Models & downloads")
        Text("This phone: tier ${app.deviceProfile.tier}, ${app.deviceProfile.totalRamMb} MB RAM, ${app.deviceProfile.freeStorageMb} MB free. A model counts as installed only after its SHA-256 matches the official value. Downloads continue with the app closed (see the notification) and can be paused, resumed or cancelled here.", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Text("Allow unrestricted background use") }
        val sorted = ResourceManifest.entries.sortedByDescending { states[it.id]?.active == true }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(sorted, key = { it.id }) { e ->
                val st = states[e.id]
                val install by androidx.compose.runtime.produceState(InstallState.NOT_INSTALLED, st?.phase) {
                    value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { mgr.state(e) }
                }
                DownloadCard(
                    e, st, install, mgr.compatible(e),
                    onDownload = { confirm = e },
                    onPause = { com.vision.assistant.core.Downloads.pause(app, e.id) },
                    onResume = { com.vision.assistant.core.Downloads.start(app, e) },
                    onCancel = { com.vision.assistant.core.Downloads.cancel(app, e.id) },
                    onDelete = { mgr.delete(e); com.vision.assistant.core.Downloads.cancel(app, e.id); app.engines.invalidate() },
                )
            }
        }
    }
    confirm?.let { e ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Download ${e.title}?") },
            text = { Text("Size: ${e.sizeMb} MB. This uses your internet data (Wi-Fi recommended) and storage. Source: ${java.net.URL(e.url).host}. You can pause or cancel any time.") },
            confirmButton = { Button(onClick = { com.vision.assistant.core.Downloads.start(app, e); confirm = null }) { Text("Download") } },
            dismissButton = { OutlinedButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}
