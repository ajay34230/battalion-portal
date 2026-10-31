package com.vision.assistant.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.vision.assistant.ai.OrbState
import kotlin.math.sin

/**
 * VISION's face: the Jarvis orb with expressive eyes and a mouth.
 * Idle: slow blinks and a wandering gaze. Listening: wide eyes that follow the voice level.
 * Thinking: eyes drift upward in a squint. Speaking: the mouth moves with a talking rhythm.
 */
@Composable
fun VisionAvatar(state: OrbState, level: Float, modifier: Modifier = Modifier) {
    Box(modifier.aspectRatio(1f)) {
        JarvisOrb(state, level, Modifier.fillMaxSize())
        val t = rememberInfiniteTransition(label = "face")
        // 0 = open, 1 = shut. Mostly open, one quick blink per ~4.2 s.
        val blink by t.animateFloat(0f, 0f, infiniteRepeatable(keyframes {
            durationMillis = 4200; 0f at 0; 0f at 3900; 1f at 4000; 0f at 4120
        }), label = "blink")
        val gazeX by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(5200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "gx")
        val talk by t.animateFloat(0f, 1f, infiniteRepeatable(tween(330, easing = LinearEasing), RepeatMode.Reverse), label = "talk")
        val lv by animateFloatAsState(level.coerceIn(0f, 1f), tween(160), label = "lv")
        val eyeColor = when (state) { OrbState.IDLE -> VisionColors.Cyan; OrbState.LISTENING -> VisionColors.Mint; OrbState.THINKING -> VisionColors.Violet; OrbState.SPEAKING -> VisionColors.Blue }

        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val r = size.minDimension / 2f
            val faceR = r * 0.44f
            // dark glass disc so the face reads clearly over the glowing core
            drawCircle(Brush.radialGradient(listOf(Color(0xE60A1628), Color(0xCC050C18)), center = c, radius = faceR), radius = faceR, center = c)
            drawCircle(eyeColor.copy(alpha = 0.55f), radius = faceR, center = c, style = Stroke(r * 0.012f))

            val openness = when (state) {
                OrbState.LISTENING -> 1.15f + 0.25f * lv
                OrbState.THINKING -> 0.55f
                else -> 1f
            } * (1f - blink * 0.92f)
            val gx = when (state) { OrbState.THINKING -> 0.5f * gazeX; OrbState.LISTENING -> 0f; else -> 0.6f * gazeX } * faceR * 0.12f
            val gy = if (state == OrbState.THINKING) -faceR * 0.12f else 0f
            val eyeW = faceR * 0.26f
            val eyeH = faceR * 0.34f * openness.coerceAtLeast(0.06f)
            val eyeY = c.y - faceR * 0.18f + gy
            for (side in listOf(-1f, 1f)) {
                val ex = c.x + side * faceR * 0.42f + gx
                drawRoundRect(Brush.radialGradient(listOf(Color.White, eyeColor), center = Offset(ex, eyeY), radius = eyeW),
                    topLeft = Offset(ex - eyeW / 2f, eyeY - eyeH / 2f), size = Size(eyeW, eyeH), cornerRadius = CornerRadius(eyeW / 2f, eyeW / 2f))
                drawRoundRect(eyeColor.copy(alpha = 0.25f), topLeft = Offset(ex - eyeW * 0.8f, eyeY - eyeH * 0.8f), size = Size(eyeW * 1.6f, eyeH * 1.6f), cornerRadius = CornerRadius(eyeW, eyeW))
            }
            // mouth
            val my = c.y + faceR * 0.42f
            val mw = faceR * 0.62f
            val stroke = Stroke(r * 0.02f, cap = StrokeCap.Round)
            when (state) {
                OrbState.SPEAKING -> {
                    val open = faceR * (0.06f + 0.22f * (0.5f + 0.5f * sin(talk * Math.PI.toFloat())) * (0.6f + 0.4f * lv))
                    val p = Path().apply { moveTo(c.x - mw / 2, my); quadraticBezierTo(c.x, my + open * 2f, c.x + mw / 2, my); quadraticBezierTo(c.x, my - open * 0.4f, c.x - mw / 2, my); close() }
                    drawPath(p, eyeColor.copy(alpha = 0.9f))
                }
                OrbState.LISTENING -> drawOval(eyeColor.copy(alpha = 0.9f), Offset(c.x - mw * 0.14f, my - faceR * 0.04f), Size(mw * 0.28f, faceR * 0.12f + faceR * 0.14f * lv))
                OrbState.THINKING -> drawLine(eyeColor.copy(alpha = 0.8f), Offset(c.x - mw * 0.25f, my), Offset(c.x + mw * 0.25f, my), strokeWidth = r * 0.02f, cap = StrokeCap.Round)
                OrbState.IDLE -> {
                    val p = Path().apply { moveTo(c.x - mw / 2, my - faceR * 0.04f); quadraticBezierTo(c.x, my + faceR * 0.16f, c.x + mw / 2, my - faceR * 0.04f) }
                    drawPath(p, eyeColor.copy(alpha = 0.9f), style = stroke)
                }
            }
        }
    }
}

