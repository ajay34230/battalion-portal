package com.vision.assistant.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The VISION logo: a glowing ring, a violet arc, a V and an eye. Same artwork as the launcher icon. */
@Composable
fun VisionLogo(size: Dp = 64.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val u = this.size.minDimension / 108f
        val c = Offset(54f * u, 54f * u)
        drawCircle(Color(0x3300E5FF), radius = 27f * u, center = c, style = Stroke(6f * u))
        drawCircle(VisionColors.Cyan, radius = 26f * u, center = c, style = Stroke(2.4f * u))
        drawArc(VisionColors.Violet, 180f, 180f, false, topLeft = Offset(34f * u, 34f * u), size = Size(40f * u, 40f * u), style = Stroke(1.6f * u, cap = StrokeCap.Round))
        val v = Path().apply { moveTo(41f * u, 43f * u); lineTo(54f * u, 68f * u); lineTo(67f * u, 43f * u) }
        drawPath(v, Color(0xFFE8FBFF), style = Stroke(5f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(VisionColors.Cyan, radius = 3f * u, center = Offset(54f * u, 40f * u))
    }
}
