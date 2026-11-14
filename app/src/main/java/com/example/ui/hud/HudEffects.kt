package com.example.ui.hud

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.ui.theme.JarvisColors
import kotlin.math.roundToInt

fun Modifier.hudGlassPanel(
    strong: Boolean = false,
    cornerRadius: Dp = 14.dp,
    borderColor: Color = JarvisColors.CyanBorder
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    val fill = if (strong) JarvisColors.GlassSurfaceStrong else JarvisColors.GlassSurface
    return this
        .clip(shape)
        .background(
            Brush.verticalGradient(
                listOf(fill.copy(alpha = (fill.alpha + 0.08f).coerceAtMost(0.95f)), fill)
            )
        )
        .border(1.dp, borderColor, shape)
        .hudCornerBrackets(cornerLength = 12.dp)
}

fun Modifier.hudCornerBrackets(cornerLength: Dp = 12.dp): Modifier = this.drawWithContent {
    drawContent()
    val len = cornerLength.toPx()
    val stroke = 2.dp.toPx()

    val topLeft = Path().apply {
        moveTo(0f, len)
        lineTo(0f, 0f)
        lineTo(len, 0f)
    }
    drawPath(topLeft, color = JarvisColors.NeonCyan, style = Stroke(width = stroke, cap = StrokeCap.Square))

    val bottomRight = Path().apply {
        moveTo(size.width - len, size.height)
        lineTo(size.width, size.height)
        lineTo(size.width, size.height - len)
    }
    drawPath(bottomRight, color = JarvisColors.MatrixEmerald, style = Stroke(width = stroke, cap = StrokeCap.Square))
}

@Composable
fun GlitchTitleText(
    text: String,
    style: TextStyle,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier
) {
    val glitchShift = if (reducedMotion) {
        0f
    } else {
        val transition = rememberInfiniteTransition(label = "hud_glitch")
        val phase by transition.animateFloat(
            initialValue = 0f,
            targetValue = 100f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 4800, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "glitch_phase"
        )
        when {
            phase in 92f..93.5f -> -3f
            phase in 93.5f..95f -> 3f
            else -> 0f
        }
    }

    Box(modifier = modifier) {
        if (glitchShift != 0f) {
            Text(
                text = text,
                style = style,
                color = JarvisColors.DangerCrimson.copy(alpha = 0.75f),
                modifier = Modifier.offset { IntOffset(glitchShift.roundToInt(), 1) }
            )
            Text(
                text = text,
                style = style,
                color = JarvisColors.MatrixEmerald.copy(alpha = 0.75f),
                modifier = Modifier.offset { IntOffset(-glitchShift.roundToInt(), -1) }
            )
        }
        Text(
            text = text,
            style = style,
            color = JarvisColors.NeonCyan
        )
    }
}

@Composable
fun ScanlineOverlay(reducedMotion: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val alpha = if (reducedMotion) 0.025f else 0.055f
        val step = 4.dp.toPx()
        var y = 0f
        while (y < size.height) {
            drawLine(
                color = JarvisColors.NeonCyan.copy(alpha = alpha),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx()
            )
            y += step
        }
    }
}

@Composable
fun BootSequenceOverlay(
    lines: List<String>,
    progress: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(JarvisColors.VoidBlack.copy(alpha = 0.82f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .hudGlassPanel(strong = true)
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "SYS.BOOT // NEURAL CORE V4.2",
                style = MaterialTheme.typography.labelSmall,
                color = JarvisColors.MatrixEmerald
            )
            GlitchTitleText(
                text = "MAIM",
                style = MaterialTheme.typography.displaySmall,
                reducedMotion = false
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                lines.forEach { line ->
                    Text(
                        text = "> $line",
                        style = MaterialTheme.typography.bodySmall,
                        color = JarvisColors.FrostWhite
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(99.dp)),
                color = JarvisColors.MatrixEmerald,
                trackColor = JarvisColors.CyanTint
            )
        }
    }
}
