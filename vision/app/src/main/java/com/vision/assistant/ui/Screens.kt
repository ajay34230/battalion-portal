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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.vision.assistant.ai.OrbState
import com.vision.assistant.VisionApp

private data class Tile(val icon: String, val title: String, val sub: String, val route: String)

@Composable
fun HomeScreen(app: VisionApp, go: (String) -> Unit) {
    val profile = app.deviceProfile
    val engineLabel by app.engines.label.collectAsState()
    androidx.compose.runtime.LaunchedEffect(Unit) { app.engines.current() }
    val tiles = listOf(
        Tile("💬", "Talk", "Voice & chat", Routes.CHAT), Tile("⏰", "Tasks", "Reminders", Routes.TASKS),
        Tile("🧠", "Memory", "What I remember", Routes.MEMORY), Tile("⬇", "Models", "AI & voice downloads", Routes.MODELS),
        Tile("⌨", "Termux", "Safe commands", Routes.TERMUX), Tile("🛡", "Privacy", "Data & action log", Routes.PRIVACY),
        Tile("⚙", "Settings", "Lock screen & more", Routes.SETTINGS), Tile("📱", "This phone", "Tier ${profile.tier}", Routes.DEVICE),
        Tile("🔑", "Permissions", "Control access", Routes.PERMISSIONS),
    )
    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
        columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(8.dp))
                Text("V I S I O N", fontSize = 30.sp, fontWeight = FontWeight.Light, color = VisionColors.Cyan,
                    style = MaterialTheme.typography.headlineLarge.copy(shadow = Shadow(VisionColors.Cyan, blurRadius = 28f)))
                Text(engineLabel, color = VisionColors.Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                JarvisOrb(OrbState.IDLE, 0f, Modifier.size(270.dp).clip(CircleShape).clickable { go(Routes.CHAT) })
                Text("Tap the orb to talk", color = VisionColors.Muted, fontSize = 12.sp, letterSpacing = 3.sp)
                Spacer(Modifier.height(8.dp))
            }
        }
        itemsIndexed(tiles) { index, t ->
            var shown by remember { mutableStateOf(false) }
            androidx.compose.runtime.LaunchedEffect(Unit) { kotlinx.coroutines.delay(150L + index * 110L); shown = true }
            AnimatedVisibility(shown, enter = fadeIn(tween(900)) + slideInVertically(tween(900)) { it / 4 }) {
                Column(Modifier.fillMaxWidth().glass(22).clickable { go(t.route) }.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(t.icon, fontSize = 26.sp)
                    Text(t.title, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                    Text(t.sub, fontSize = 12.sp, color = VisionColors.Muted)
                }
            }
        }
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
