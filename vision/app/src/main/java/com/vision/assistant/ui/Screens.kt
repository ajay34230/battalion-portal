package com.vision.assistant.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.vision.assistant.core.DeviceProfile

@Composable
fun HomeScreen(profile: DeviceProfile, go: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("VISION", style = MaterialTheme.typography.headlineLarge)
        Text("Personal AI agent — foundation build. Device tier: ${profile.tier}")
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("AI: runs a downloaded Qwen3 model on this phone (install it under Models & downloads)")
                Text("Voice: push-to-talk in Chat; Speak notification in Settings")
            }
        }
        Button(onClick = { go(Routes.CHAT) }, modifier = Modifier.fillMaxWidth()) { Text("Talk to VISION") }
        Button(onClick = { go(Routes.TASKS) }, modifier = Modifier.fillMaxWidth()) { Text("Tasks & reminders") }
        Button(onClick = { go(Routes.MEMORY) }, modifier = Modifier.fillMaxWidth()) { Text("Memory") }
        Button(onClick = { go(Routes.MODELS) }, modifier = Modifier.fillMaxWidth()) { Text("Models & downloads") }
        Button(onClick = { go(Routes.DEVICE) }, modifier = Modifier.fillMaxWidth()) { Text("This device") }
        Button(onClick = { go(Routes.TERMUX) }, modifier = Modifier.fillMaxWidth()) { Text("Termux") }
        Button(onClick = { go(Routes.PRIVACY) }, modifier = Modifier.fillMaxWidth()) { Text("Privacy centre") }
        Button(onClick = { go(Routes.SETTINGS) }, modifier = Modifier.fillMaxWidth()) { Text("Settings") }
        Button(onClick = { go(Routes.PERMISSIONS) }, modifier = Modifier.fillMaxWidth()) { Text("Permissions") }
    }
}

@Composable
fun DeviceScreen(p: DeviceProfile, back: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("This device", style = MaterialTheme.typography.headlineSmall)
        Text("Tier: ${p.tier}")
        Text("RAM: ${p.totalRamMb} MB")
        Text("Free storage: ${p.freeStorageMb} MB")
        Text("CPU ABIs: ${p.abis.joinToString()}")
        Text("64-bit: ${p.is64Bit}  Cores: ${p.cores}")
        Text("Android SDK: ${p.sdk}")
        OutlinedButton(onClick = back) { Text("Back") }
    }
}

@Composable
fun PermissionsScreen(back: () -> Unit) {
    val ctx = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    fun granted(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    val items = buildList {
        add("Microphone (voice input)" to Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) add("Notifications" to Manifest.permission.POST_NOTIFICATIONS)
    }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Permissions", style = MaterialTheme.typography.headlineSmall)
        key(refresh) {
            items.forEach { (label, perm) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("$label: ${if (granted(perm)) "granted" else "not granted"}")
                    if (!granted(perm)) Button(onClick = { launcher.launch(perm) }) { Text("Grant") }
                }
            }
        }
        OutlinedButton(onClick = {
            ctx.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }) { Text("Open app settings (revoke permissions)") }
        OutlinedButton(onClick = back) { Text("Back") }
    }
}
