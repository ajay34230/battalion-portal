package com.vision.assistant.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vision.assistant.VisionApp
import com.vision.assistant.ai.Learning
import com.vision.assistant.ai.OrbState
import com.vision.assistant.tasks.EventKind
import java.util.Calendar

fun greeting(hour: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)): String = when (hour) {
    in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; in 17..21 -> "Good evening"; else -> "Hello"
}

/** The user's most repeated requests (asked at least twice), shown as one-tap suggestions. */
fun frequentRequests(userMessages: List<String>, max: Int = 4): List<String> =
    userMessages.map { it.trim() }.filter { it.length in 4..60 }
        .groupingBy { it.lowercase() }.eachCount().filter { it.value >= 2 }
        .entries.sortedByDescending { it.value }.take(max).map { e -> userMessages.first { it.trim().lowercase() == e.key }.trim() }

@Composable
fun HomeScreen(app: VisionApp, go: (String) -> Unit, say: (String) -> Unit) {
    val engineLabel by app.engines.label.collectAsState()
    val upcoming by app.database.tasks().upcoming(3).collectAsState(initial = emptyList())
    val chat by app.database.chat().all().collectAsState(initial = emptyList())
    val suggestions = remember(chat.size) { frequentRequests(chat.filter { it.role == "user" }.map { it.text }) }
    LaunchedEffect(Unit) { app.engines.current() }
    val name = Learning.userName
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            VisionLogo(36.dp)
            Column(Modifier.padding(start = 10.dp)) {
                Text("V I S I O N", fontSize = 16.sp, fontWeight = FontWeight.Light, color = VisionColors.Cyan, style = androidx.compose.material3.MaterialTheme.typography.titleMedium.copy(shadow = Shadow(VisionColors.Cyan, blurRadius = 18f)))
                Text(engineLabel, fontSize = 11.sp, color = VisionColors.Muted, maxLines = 1)
            }
        }
        Spacer(Modifier.height(14.dp))
        VisionAvatar(OrbState.IDLE, 0f, Modifier.size(250.dp).clip(CircleShape).clickable { go(Routes.CHAT) })
        Text(if (name.isNotBlank()) "${greeting()}, $name" else greeting(), fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Text("Tap me to talk · Hindi, English or Hinglish", fontSize = 12.sp, color = VisionColors.Muted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(18.dp))

        if (upcoming.isNotEmpty()) {
            Row(Modifier.fillMaxWidth()) { SectionLabel("Coming up") }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (t in upcoming) {
                    val k = EventKind.of(t.kind)
                    InfoCard(onClick = { go(Routes.REMINDERS) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(k.emoji, fontSize = 22.sp)
                            Column(Modifier.weight(1f).padding(start = 10.dp)) { Text(t.title, fontSize = 15.sp, fontWeight = FontWeight.Medium); Text(k.label, fontSize = 12.sp, color = VisionColors.Muted) }
                            Text(t.dueAt?.let { relativeDay(it) }.orEmpty(), fontSize = 13.sp, color = VisionColors.Cyan)
                        }
                    }
                }
            }
        }
        val tips = suggestions.ifEmpty { listOf("What's the time?", "Read my notifications", "Remind me tomorrow at 6 to call mom") }
        Row(Modifier.fillMaxWidth()) { SectionLabel(if (suggestions.isNotEmpty()) "Suggested for you" else "Try saying") }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (s in tips) InfoCard(onClick = { say(s) }) { Text("“$s”", fontSize = 14.sp) }
        }
        Spacer(Modifier.height(10.dp))
        Text("Built with gratitude by ${com.vision.assistant.ai.Identity.CREATOR}", color = VisionColors.Cyan.copy(alpha = 0.8f), fontSize = 11.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
    }
}

private data class Entry(val icon: String, val title: String, val sub: String, val route: String)

@Composable
fun MoreScreen(app: VisionApp, go: (String) -> Unit) {
    val entries = listOf(
        Entry("💻", "Coding Studio", "Build, run and fix programs", Routes.CODING),
        Entry("⬇", "Models & downloads", "AI brains and voices", Routes.MODELS),
        Entry("⌨", "Termux control", "Run safe phone commands", Routes.TERMUX),
        Entry("🛡", "Privacy & action log", "See everything I did", Routes.PRIVACY),
        Entry("⚙", "Settings", "Lock screen, voice, search", Routes.SETTINGS),
        Entry("📱", "This phone", "Tier ${app.deviceProfile.tier} · what it can run", Routes.DEVICE),
        Entry("🔑", "Permissions", "Control what I can access", Routes.PERMISSIONS),
    )
    ScreenFrame("More", "Tools and settings") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (e in entries) InfoCard(onClick = { go(e.route) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(e.icon, fontSize = 24.sp)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) { Text(e.title, fontSize = 16.sp, fontWeight = FontWeight.Medium); Text(e.sub, fontSize = 12.sp, color = VisionColors.Muted) }
                    Text("›", fontSize = 22.sp, color = VisionColors.Cyan)
                }
            }
        }
    }
}
