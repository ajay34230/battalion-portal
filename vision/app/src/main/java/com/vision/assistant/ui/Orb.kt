package com.vision.assistant.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.vision.assistant.ai.OrbState

/** Glowing Jarvis-style orb: rotating rings, breathing core, expanding waves while speaking, reacts to the microphone level. */
@Composable
fun JarvisOrb(state: OrbState, level: Float, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "orb")
    val slow by t.animateFloat(0f, 360f, infiniteRepeatable(tween(26000, easing = LinearEasing)), label = "slow")
    val mid by t.animateFloat(360f, 0f, infiniteRepeatable(tween(16000, easing = LinearEasing)), label = "mid")
    val fast by t.animateFloat(0f, 360f, infiniteRepeatable(tween(if (state == OrbState.THINKING) 3000 else 10000, easing = LinearEasing)), label = "fast")
    val pulse by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    val wave by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "wave")
    val main by animateColorAsState(
        when (state) { OrbState.IDLE -> VisionColors.Cyan; OrbState.LISTENING -> VisionColors.Mint; OrbState.THINKING -> VisionColors.Violet; OrbState.SPEAKING -> VisionColors.Blue },
        tween(900), label = "color",
    )
    val lv by animateFloatAsState(level.coerceIn(0f, 1f), tween(220), label = "level")

    Canvas(modifier.aspectRatio(1f)) {
        val c = center
        val r = size.minDimension / 2f
        val breathe = 1f + 0.05f * pulse + 0.2f * lv
        val sw = r * 0.018f

        // soft outer glow
        drawCircle(Brush.radialGradient(listOf(main.copy(alpha = 0.38f), main.copy(alpha = 0.10f), Color.Transparent), center = c, radius = r), radius = r, center = c)

        // fine tick ring
        rotate(mid, c) {
            for (i in 0 until 72) {
                val a = Math.toRadians(i * 5.0)
                val long = i % 6 == 0
                val r1 = r * 0.96f; val r2 = r * (if (long) 0.90f else 0.93f)
                drawLine(main.copy(alpha = if (long) 0.75f else 0.35f), Offset(c.x + (r1 * Math.cos(a)).toFloat(), c.y + (r1 * Math.sin(a)).toFloat()), Offset(c.x + (r2 * Math.cos(a)).toFloat(), c.y + (r2 * Math.sin(a)).toFloat()), strokeWidth = sw * 0.7f)
            }
        }
        // dashed ring
        rotate(-slow, c) {
            drawCircle(main.copy(alpha = 0.5f), radius = r * 0.80f, center = c, style = Stroke(sw, pathEffect = PathEffect.dashPathEffect(floatArrayOf(r * 0.04f, r * 0.06f))))
        }
        // bold arcs
        rotate(slow, c) {
            for (k in 0 until 3) drawArc(main.copy(alpha = 0.95f), startAngle = k * 120f, sweepAngle = 70f, useCenter = false,
                topLeft = Offset(c.x - r * 0.70f, c.y - r * 0.70f), size = Size(r * 1.40f, r * 1.40f), style = Stroke(sw * 2.2f, cap = StrokeCap.Round))
        }
        rotate(fast, c) {
            for (k in 0 until 4) drawArc(VisionColors.Text.copy(alpha = 0.7f), startAngle = k * 90f + 10f, sweepAngle = 28f, useCenter = false,
                topLeft = Offset(c.x - r * 0.58f, c.y - r * 0.58f), size = Size(r * 1.16f, r * 1.16f), style = Stroke(sw * 1.4f, cap = StrokeCap.Round))
        }
        // speaking / listening waves
        if (state == OrbState.SPEAKING || state == OrbState.LISTENING) {
            for (i in 0 until 3) {
                val ph = (wave + i / 3f) % 1f
                drawCircle(main.copy(alpha = (1f - ph) * 0.55f), radius = r * (0.30f + 0.5f * ph) * (1f + 0.15f * lv), center = c, style = Stroke(sw))
            }
        }
        // glowing core
        val core = r * 0.30f * breathe
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.95f), main.copy(alpha = 0.85f), main.copy(alpha = 0.0f)), center = c, radius = core * 1.9f), radius = core * 1.9f, center = c)
        drawCircle(Color.White.copy(alpha = 0.9f), radius = core * 0.45f, center = c)
    }
}
