package com.vision.assistant.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val LocalOpenSettings = androidx.compose.runtime.staticCompositionLocalOf<() -> Unit> { {} }

/** Round settings button shown at the top right of every main screen. */
@Composable
fun GearButton() {
    val open = LocalOpenSettings.current
    Box(Modifier.size(40.dp).glass(20).clickable { open() }, contentAlignment = Alignment.Center) { Text("⚙", fontSize = 18.sp, color = VisionColors.Cyan) }
}

/** Heading that starts a group of related settings. */
@Composable
fun GroupHeader(text: String, sub: String = "") {
    Column(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 2.dp)) {
        Text(text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = VisionColors.Text)
        if (sub.isNotBlank()) Text(sub, fontSize = 12.sp, color = VisionColors.Muted)
        Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(1.dp).background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(VisionColors.Cyan.copy(alpha = 0.45f), Color.Transparent))))
    }
}

/** Standard screen layout: one title, optional subtitle and action, consistent padding. No per-screen Back buttons (the system back and bottom bar handle that). */
@Composable
fun ScreenFrame(title: String, subtitle: String = "", action: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = VisionColors.Text)
                if (subtitle.isNotBlank()) Text(subtitle, fontSize = 13.sp, color = VisionColors.Muted)
            }
            action?.invoke()
            if (title != "Settings") { if (action != null) Spacer(Modifier.padding(start = 8.dp)); GearButton() }
        }
        content()
    }
}

@Composable
fun SectionLabel(text: String) = Text(text.uppercase(), fontSize = 11.sp, letterSpacing = 2.sp, color = VisionColors.Cyan.copy(alpha = 0.85f), modifier = Modifier.padding(top = 6.dp))

@Composable
fun InfoCard(modifier: Modifier = Modifier, accent: Color = VisionColors.Cyan, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val base = modifier.fillMaxWidth().glass(18).let { if (onClick != null) it.clickable { onClick() } else it }
    Column(base.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
}

@Composable
fun Pill(text: String, color: Color = VisionColors.Cyan, selected: Boolean = true, onClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(50)
    val m = Modifier.let { if (onClick != null) it.clickable { onClick() } else it }
    Box(m.border2(color, selected, shape).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(text, fontSize = 12.sp, color = if (selected) color else VisionColors.Muted)
    }
}

private fun Modifier.border2(color: Color, selected: Boolean, shape: RoundedCornerShape): Modifier =
    this.then(Modifier.background(if (selected) color.copy(alpha = 0.16f) else Color.Transparent, shape))
        .then(Modifier.border(BorderStroke(1.dp, color.copy(alpha = if (selected) 0.7f else 0.25f)), shape))

@Composable
fun EmptyState(emoji: String, title: String, hint: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(emoji, fontSize = 44.sp)
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Medium)
        Text(hint, fontSize = 13.sp, color = VisionColors.Muted, modifier = Modifier.padding(horizontal = 24.dp))
    }
}

@Composable
fun ScreenTitle(text: String) = Text(text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = VisionColors.Text)
