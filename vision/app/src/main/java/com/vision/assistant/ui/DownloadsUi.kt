package com.vision.assistant.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vision.assistant.core.DlPhase
import com.vision.assistant.core.DlState
import com.vision.assistant.core.Downloads
import com.vision.assistant.core.InstallState
import com.vision.assistant.core.ResourceEntry
import com.vision.assistant.core.ResourceManifest

private fun mb(b: Long) = "${b / 1_048_576} MB"
private fun speed(b: Long) = "%.1f MB/s".format(b / 1_048_576.0)
private fun eta(s: Long?): String = when {
    s == null -> ""
    s < 60 -> "${s}s left"
    s < 3600 -> "${s / 60}m ${s % 60}s left"
    else -> "${s / 3600}h ${(s % 3600) / 60}m left"
}

/** Circular progress with a glowing, rotating highlight while data is flowing. */
@Composable
fun DownloadRing(state: DlState, size: Dp = 64.dp) {
    val anim by animateFloatAsState(state.fraction, tween(700), label = "p")
    val inf = rememberInfiniteTransition(label = "ring")
    val spin by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "spin")
    val pulse by inf.animateFloat(0.4f, 1f, infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "pulse")
    val busy = state.phase == DlPhase.VERIFYING || state.phase == DlPhase.UNPACKING || (state.phase == DlPhase.QUEUED && state.done == 0L)
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val stroke = s * 0.10f
            val inset = stroke / 2 + s * 0.05f
            val tl = Offset(inset, inset)
            val arc = Size(s - 2 * inset, s - 2 * inset)
            drawCircle(VisionColors.Cyan.copy(alpha = 0.07f * pulse), radius = s / 2)
            drawArc(Color(0x2600E5FF), 0f, 360f, false, tl, arc, style = Stroke(stroke))
            if (busy) {
                rotate(spin) { drawArc(VisionColors.Violet.copy(alpha = pulse), -90f, 110f, false, tl, arc, style = Stroke(stroke, cap = StrokeCap.Round)) }
            } else {
                rotate(-90f) {
                    drawArc(Brush.sweepGradient(listOf(VisionColors.Blue, VisionColors.Cyan, VisionColors.Mint, VisionColors.Blue)), 0f, 360f * anim, false, tl, arc, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                if (state.phase == DlPhase.DOWNLOADING && anim < 1f) {
                    rotate(spin) { drawArc(Color.White.copy(alpha = 0.22f * pulse), -90f, 26f, false, tl, arc, style = Stroke(stroke * 0.6f, cap = StrokeCap.Round)) }
                }
            }
        }
        val label = when (state.phase) {
            DlPhase.VERIFYING -> "✓?"; DlPhase.UNPACKING -> "…"; DlPhase.PAUSED -> "❚❚"; DlPhase.FAILED -> "!"; DlPhase.DONE -> "✓"
            else -> if (busy) "…" else "${(anim * 100).toInt()}%"
        }
        Text(label, fontSize = (size.value * 0.24f).sp, fontWeight = FontWeight.SemiBold, color = if (state.phase == DlPhase.FAILED) Color(0xFFFF6B6B) else VisionColors.Text)
    }
}

/** Thin progress bar with a moving shine. */
@Composable
fun ShimmerBar(fraction: Float, active: Boolean, modifier: Modifier = Modifier) {
    val anim by animateFloatAsState(fraction, tween(700), label = "bar")
    val inf = rememberInfiniteTransition(label = "shine")
    val x by inf.animateFloat(-0.3f, 1.3f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "x")
    Canvas(modifier.fillMaxWidth().height(8.dp)) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(Color(0x2200E5FF), cornerRadius = r)
        val w = size.width * anim
        if (w > 0f) {
            drawRoundRect(Brush.horizontalGradient(listOf(VisionColors.Blue, VisionColors.Cyan, VisionColors.Mint)), size = Size(w, size.height), cornerRadius = r)
            if (active) {
                val cx = w * x
                drawRoundRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.55f), Color.Transparent), startX = cx - 40f, endX = cx + 40f), size = Size(w, size.height), cornerRadius = r)
            }
        }
    }
}

@Composable
fun DownloadCard(
    e: ResourceEntry, st: DlState?, install: InstallState, fits: Boolean,
    onDownload: () -> Unit, onPause: () -> Unit, onResume: () -> Unit, onCancel: () -> Unit, onDelete: () -> Unit,
) {
    val showRing = st != null && st.phase != DlPhase.DONE
    Column(Modifier.fillMaxWidth().glass(22).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            if (showRing && st != null) DownloadRing(st, 60.dp)
            Column(Modifier.weight(1f)) {
                Text(e.title, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                Text("${e.sizeMb} MB · ${e.license} · ${e.languages}", color = VisionColors.Muted, fontSize = 12.sp)
                if (e.note.isNotBlank()) Text(e.note, color = VisionColors.Muted, fontSize = 12.sp)
            }
        }
        if (st != null && st.phase != DlPhase.DONE) {
            val line = when (st.phase) {
                DlPhase.QUEUED -> "Waiting to start…" + if (st.done > 0) " (resuming from ${mb(st.done)})" else ""
                DlPhase.DOWNLOADING -> "${mb(st.done)} / ${mb(st.total)}" + (if (st.bytesPerSec > 0) " · ${speed(st.bytesPerSec)}" else "") + (st.etaSeconds?.let { " · ${eta(it)}" } ?: "")
                DlPhase.VERIFYING -> "Download finished. Checking the checksum — this can take a minute…"
                DlPhase.UNPACKING -> "Unpacking files…"
                DlPhase.PAUSED -> "Paused at ${mb(st.done)} / ${mb(st.total)} — your progress is saved"
                DlPhase.FAILED -> st.message.ifBlank { "Download failed" }
                DlPhase.DONE -> ""
            }
            Text(line, fontSize = 12.sp, color = if (st.phase == DlPhase.FAILED) Color(0xFFFF6B6B) else VisionColors.Muted)
            if (st.phase != DlPhase.FAILED) ShimmerBar(st.fraction, st.phase == DlPhase.DOWNLOADING)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            when {
                st != null && (st.phase == DlPhase.QUEUED || st.phase == DlPhase.DOWNLOADING) -> {
                    Button(onClick = onPause) { Text("Pause") }
                    OutlinedButton(onClick = onCancel) { Text("Cancel") }
                }
                st != null && (st.phase == DlPhase.VERIFYING || st.phase == DlPhase.UNPACKING) -> Text("Almost done — please wait", fontSize = 12.sp, color = VisionColors.Mint)
                st != null && st.phase == DlPhase.PAUSED -> {
                    Button(onClick = onResume) { Text("Resume") }
                    OutlinedButton(onClick = onCancel) { Text("Cancel & delete") }
                }
                st != null && st.phase == DlPhase.FAILED -> {
                    Button(onClick = onResume) { Text("Retry") }
                    OutlinedButton(onClick = onCancel) { Text("Discard") }
                }
                install == InstallState.INSTALLED -> {
                    Text("✓ Installed & verified", color = VisionColors.Mint, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = onDelete) { Text("Delete") }
                }
                else -> {
                    Button(enabled = fits, onClick = onDownload) { Text("Download") }
                    if (!fits) Text("Does not fit this phone", color = VisionColors.Muted, fontSize = 12.sp)
                    if (install == InstallState.DOWNLOADED_UNVERIFIED) OutlinedButton(onClick = onDelete) { Text("Delete") }
                }
            }
        }
    }
}

/** Slim banner shown over every screen while something downloads. Tap to open the downloads screen. */
@Composable
fun DownloadBanner(onClick: () -> Unit) {
    val states by Downloads.states.collectAsState()
    val active = states.entries.firstOrNull { it.value.active }
    AnimatedVisibility(active != null, enter = fadeIn(tween(500)) + expandVertically(tween(500)), exit = fadeOut(tween(400)) + shrinkVertically(tween(400))) {
        val (id, st) = active?.toPair() ?: return@AnimatedVisibility
        val title = ResourceManifest.byId(id)?.title ?: id
        Column(
            Modifier.statusBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth().glass(16).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("⬇ $title", fontSize = 12.sp, maxLines = 1, modifier = Modifier.weight(1f))
                Text(
                    when (st.phase) { DlPhase.VERIFYING -> "verifying…"; DlPhase.UNPACKING -> "unpacking…"; DlPhase.QUEUED -> "starting…"; else -> "${(st.fraction * 100).toInt()}%" },
                    fontSize = 12.sp, color = VisionColors.Cyan,
                )
            }
            ShimmerBar(st.fraction, st.phase == DlPhase.DOWNLOADING)
        }
    }
}
