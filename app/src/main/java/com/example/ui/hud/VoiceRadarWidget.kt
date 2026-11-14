package com.example.ui.hud

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.model.VoiceState
import com.example.ui.theme.JarvisColors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun VoiceRadarWidget(
    voiceState: VoiceState,
    rmsEnergy: Float,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var isResumed by remember { mutableStateOf(true) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> isResumed = true
                Lifecycle.Event.ON_PAUSE -> isResumed = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var angle by remember { mutableFloatStateOf(0f) }
    var timeSec by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isResumed, reducedMotion, voiceState) {
        if (!isResumed || reducedMotion) return@LaunchedEffect
        var lastNanos = 0L
        while (true) {
            withFrameNanos { nanos ->
                val dt = if (lastNanos == 0L) 0.016f else ((nanos - lastNanos) / 1_000_000_000f).coerceAtMost(0.05f)
                lastNanos = nanos
                timeSec += dt
                val speed = if (voiceState == VoiceState.THINKING) 4.2f else 1.8f
                angle = (angle + dt * speed) % (2f * PI.toFloat())
            }
        }
    }

    Row(
        modifier = modifier
            .hudGlassPanel(cornerRadius = 12.dp)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Canvas(modifier = Modifier.size(56.dp)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val rMax = size.minDimension * 0.44f

            listOf(0.38f, 0.7f, 1f).forEach { scale ->
                drawCircle(
                    color = JarvisColors.NeonCyan.copy(alpha = 0.25f),
                    radius = rMax * scale,
                    center = Offset(cx, cy),
                    style = Stroke(width = 1.2f)
                )
            }

            val sweepEnd = Offset(cx + cos(angle) * rMax, cy + sin(angle) * rMax)
            val sweepColor = if (voiceState == VoiceState.LISTENING) JarvisColors.MatrixEmerald else JarvisColors.NeonCyan
            drawLine(
                brush = Brush.linearGradient(
                    listOf(Color.Transparent, sweepColor.copy(alpha = 0.85f)),
                    start = Offset(cx, cy),
                    end = sweepEnd
                ),
                start = Offset(cx, cy),
                end = sweepEnd,
                strokeWidth = 2.5f
            )

            val activeEnergy = when (voiceState) {
                VoiceState.LISTENING -> maxOf(rmsEnergy, 0.22f + 0.15f * abs(sin(timeSec * 6f)))
                VoiceState.SPEAKING -> maxOf(rmsEnergy, 0.35f + 0.45f * abs(sin(timeSec * 12f) * cos(timeSec * 7f)))
                VoiceState.THINKING -> 0.25f + 0.2f * abs(sin(timeSec * 9f))
                else -> 0.06f
            }

            val bars = 24
            for (i in 0 until bars) {
                val a = (i.toFloat() / bars) * (2f * PI.toFloat())
                val wave = 0.25f + 0.75f * abs(sin(timeSec * 10f + i * 0.65f))
                val r0 = rMax * 0.36f
                val r1 = r0 + activeEnergy * wave * rMax * 0.56f
                drawLine(
                    color = sweepColor,
                    start = Offset(cx + cos(a) * r0, cy + sin(a) * r0),
                    end = Offset(cx + cos(a) * r1, cy + sin(a) * r1),
                    strokeWidth = 2f
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "SIMULATED",
                style = MaterialTheme.typography.labelSmall,
                color = JarvisColors.MatrixEmerald,
                modifier = Modifier
                    .border(1.dp, JarvisColors.MatrixEmerald.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .background(JarvisColors.EmeraldTint, RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            )
            Text(
                text = voiceState.name,
                style = MaterialTheme.typography.labelLarge,
                color = JarvisColors.NeonCyan
            )
            Text(
                text = "HOLO RIG 3D",
                style = MaterialTheme.typography.bodySmall,
                color = JarvisColors.MutedCyan
            )
        }
    }
}
