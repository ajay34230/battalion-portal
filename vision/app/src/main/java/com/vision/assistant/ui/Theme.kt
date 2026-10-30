package com.vision.assistant.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object VisionColors {
    val Bg = Color(0xFF040A14)
    val Cyan = Color(0xFF00E5FF)
    val Blue = Color(0xFF2979FF)
    val Violet = Color(0xFF8E5CFF)
    val Mint = Color(0xFF3DFFC0)
    val Text = Color(0xFFE6F3FF)
    val Muted = Color(0xFF8FA8C4)

    val scheme = darkColorScheme(
        primary = Cyan, onPrimary = Color(0xFF00222B), secondary = Violet, tertiary = Mint,
        background = Bg, onBackground = Text, surface = Color(0xFF0A1424), onSurface = Text,
        surfaceVariant = Color(0xFF0F1D33), onSurfaceVariant = Muted, error = Color(0xFFFF6B6B),
        outline = Color(0x5500E5FF),
    )
}

@Composable
fun VisionTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = VisionColors.scheme) {
        Surface(color = Color.Transparent, contentColor = VisionColors.Text, modifier = Modifier.fillMaxSize()) { content() }
    }
}

/** Slowly drifting cyan/violet light behind every screen. */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val t = rememberInfiniteTransition(label = "aurora")
    val a by t.animateFloat(0f, 1f, infiniteRepeatable(tween(18000, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    Box(
        modifier.fillMaxSize().background(VisionColors.Bg).drawBehind {
            val r = size.width * 0.95f
            val c1 = Offset(size.width * (0.15f + 0.6f * a), size.height * 0.12f)
            drawCircle(Brush.radialGradient(listOf(VisionColors.Cyan.copy(alpha = 0.18f), Color.Transparent), center = c1, radius = r), radius = r, center = c1)
            val c2 = Offset(size.width * (0.9f - 0.6f * a), size.height * 0.88f)
            drawCircle(Brush.radialGradient(listOf(VisionColors.Violet.copy(alpha = 0.20f), Color.Transparent), center = c2, radius = r), radius = r, center = c2)
        },
        content = content,
    )
}

fun Modifier.glass(radius: Int = 22): Modifier {
    val shape = RoundedCornerShape(radius.dp)
    return this.clip(shape)
        .background(Brush.verticalGradient(listOf(Color(0x2600E5FF), Color(0x0F2979FF))))
        .border(1.dp, Color(0x4400E5FF), shape)
}
