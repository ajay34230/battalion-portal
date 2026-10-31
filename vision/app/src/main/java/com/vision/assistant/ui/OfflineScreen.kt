package com.vision.assistant.ui

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vision.assistant.VisionApp
import com.vision.assistant.core.*
import kotlinx.coroutines.launch

/** Live "online / offline" state of the phone (validated internet). */
@Composable
fun rememberOnline(): State<Boolean> {
    val ctx = LocalContext.current
    val online = remember { mutableStateOf(NetworkMonitor.isOnline(ctx)) }
    DisposableEffect(Unit) {
        val cm = ctx.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { online.value = NetworkMonitor.isOnline(ctx) }
            override fun onLost(network: Network) { online.value = NetworkMonitor.isOnline(ctx) }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { online.value = NetworkMonitor.isOnline(ctx) }
        }
        try { cm.registerDefaultNetworkCallback(cb) } catch (_: Throwable) { }
        onDispose { try { cm.unregisterNetworkCallback(cb) } catch (_: Throwable) { } }
    }
    return online
}

@Composable
fun ConnectivityChip() {
    val on by rememberOnline()
    Text(if (on) "● Online" else "○ Offline", fontSize = 11.sp, color = if (on) VisionColors.Mint else VisionColors.Muted, modifier = Modifier.padding(end = 8.dp))
}

@Composable
fun OfflineScreen(app: VisionApp) {
    val scope = rememberCoroutineScope()
    val on by rememberOnline()
    var running by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(OfflineStore.load(app)) }
    ScreenFrame("Offline check", "Does VISION work without internet?") {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (on) "The phone is ONLINE now. Switch on airplane mode first if you want to prove offline operation." else "The phone has no internet now. Results will count as offline proof.", fontSize = 13.sp, color = if (on) Color(0xFFFFB74D) else VisionColors.Mint)
            Button(enabled = !running, onClick = { running = true; scope.launch { try { OfflineRunner.run(app); saved = OfflineStore.load(app) } finally { running = false } } }) { Text(if (running) "Running… (can take a couple of minutes)" else "Run the checks") }
            val s = saved
            if (s == null) Text("Nothing recorded yet.", fontSize = 12.sp, color = VisionColors.Muted)
            else {
                Text(OfflineSummary.headline(s.first, s.second), fontSize = 13.sp)
                Text("Last run: ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(s.third))}", fontSize = 11.sp, color = VisionColors.Muted)
                for (r in s.first) InfoCard {
                    Text("${when (r.status) { CheckStatus.PASSED -> "✓"; CheckStatus.FAILED -> "✗"; CheckStatus.BLOCKED -> "⛔"; CheckStatus.NOT_SUPPORTED -> "—"; CheckStatus.NOT_TESTED -> "?" }} ${r.title} — ${r.status.label}", fontSize = 14.sp)
                    Text(r.detail + if (r.ms >= 0) " (${r.ms / 1000.0} s)" else "", fontSize = 11.sp, color = VisionColors.Muted)
                }
            }
            Text("Checks that need a microphone, camera, speaker or Termux cannot be run automatically, so they show Not tested or Blocked, never Passed.", fontSize = 11.sp, color = VisionColors.Muted)
        }
    }
}
