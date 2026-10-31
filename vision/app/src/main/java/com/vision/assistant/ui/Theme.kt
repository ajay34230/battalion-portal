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
import androidx.compose.ui.unit.sp

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

val VisionShapes = androidx.compose.material3.Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp), extraLarge = RoundedCornerShape(28.dp),
)

private val base = androidx.compose.material3.Typography()
val VisionType = androidx.compose.material3.Typography(
    titleLarge = base.titleLarge.copy(fontSize = 24.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleMedium = base.titleMedium.copy(fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, letterSpacing = 0.1.sp),
    bodyLarge = base.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = base.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = base.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = base.labelLarge.copy(fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, letterSpacing = 0.2.sp),
)

@Composable
fun VisionTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = VisionColors.scheme, shapes = VisionShapes, typography = VisionType) {
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
        .background(Color(0xB30A1422))
        .background(Brush.verticalGradient(listOf(Color(0x1A00E5FF), Color(0x042979FF))))
        .border(1.dp, Brush.linearGradient(listOf(Color(0x5500E5FF), Color(0x1A8E5CFF), Color(0x3300E5FF))), shape)
}
