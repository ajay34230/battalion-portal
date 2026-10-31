package com.vision.assistant.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vision.assistant.VisionApp
import com.vision.assistant.health.CrashLog
import com.vision.assistant.health.Health
import com.vision.assistant.health.Status

@Composable
fun HealthScreen(app: VisionApp) {
    val ctx = LocalContext.current
    var tick by remember { mutableStateOf(0) }
    val items = remember(tick) { Health.check(app) }
    val todo = items.count { it.status == Status.ACTION }
    ScreenFrame("Setup & health", if (todo == 0) "Everything important looks good" else "$todo thing(s) need your attention", action = { OutlinedButton(onClick = { tick++ }) { Text("Re-check") } }) {
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items) { h ->
                val c = when (h.status) { Status.OK -> VisionColors.Mint; Status.ACTION -> Color(0xFFFFB74D); Status.INFO -> VisionColors.Muted }
                InfoCard {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(when (h.status) { Status.OK -> "✓"; Status.ACTION -> "!"; Status.INFO -> "i" }, color = c, fontSize = 20.sp, modifier = Modifier.padding(end = 10.dp))
                        Text(h.title, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    }
                    Text(h.detail, fontSize = 12.sp, color = VisionColors.Muted)
                    h.fix?.let { f -> if (h.status != Status.OK) OutlinedButton(onClick = { try { ctx.startActivity(f) } catch (_: Exception) {} }) { Text(h.fixLabel) } }
                    if (h.title == "Last crash" && h.status == Status.ACTION) OutlinedButton(onClick = { CrashLog.clear(ctx); tick++ }) { Text("Clear") }
                }
            }
        }
    }
}
