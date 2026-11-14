package com.example.ui.hud

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.JarvisColors
import com.example.ui.theme.JetBrainsMonoFontFamily
import kotlin.math.pow
import kotlin.random.Random

@Composable
fun MatrixGridBackground(
    cameraActive: Boolean,
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

    val textMeasurer = rememberTextMeasurer()
    val glyphs = remember { "01MAIMHEX9Fアイウエオカキクケコ".toCharArray() }
    val columns = 24
    val drops = remember { FloatArray(columns) { Random.nextFloat() * -30f } }
    val dropGlyphs = remember { CharArray(columns) { glyphs[Random.nextInt(glyphs.size)] } }
    var gridOffset by remember { mutableFloatStateOf(0f) }
    var tickCounter by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isResumed, reducedMotion) {
        if (!isResumed || reducedMotion) return@LaunchedEffect
        var lastNanos = 0L
        while (true) {
            withFrameNanos { nanos ->
                val dt = if (lastNanos == 0L) 0.016f else ((nanos - lastNanos) / 1_000_000_000f).coerceAtMost(0.05f)
                lastNanos = nanos
                gridOffset = (gridOffset + dt * 1.4f) % 10f
                for (i in 0 until columns) {
                    drops[i] += dt * (11f + (i % 4) * 2.2f)
                    if (drops[i] > 42f && Random.nextFloat() > 0.92f) {
                        drops[i] = Random.nextFloat() * -8f
                        dropGlyphs[i] = glyphs[Random.nextInt(glyphs.size)]
                    } else if (Random.nextFloat() > 0.93f) {
                        dropGlyphs[i] = glyphs[Random.nextInt(glyphs.size)]
                    }
                }
                tickCounter += dt
            }
        }
    }

    val glyphStyle = remember {
        TextStyle(
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 12.sp
        )
    }

    val arTagStyle = remember {
        TextStyle(
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 10.sp,
            color = JarvisColors.MatrixEmerald.copy(alpha = 0.78f)
        )
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        // Trigger redraw each frame
        @Suppress("UNUSED_EXPRESSION")
        tickCounter

        if (!cameraActive) {
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(
                        JarvisColors.VoidBlack,
                        JarvisColors.DeepHudSurface,
                        JarvisColors.VoidBlack
                    )
                )
            )
        } else {
            // Translucent AR HUD contrast wash over live CameraX SurfaceView
            drawRect(
                brush = Brush.radialGradient(
                    listOf(
                        Color(0x14020B12),
                        Color(0x4602080E)
                    )
                )
            )

            // AR Spatial HUD perimeter brackets & depth horizon ticks
            val pad = size.minDimension * 0.05f
            val arm = size.minDimension * 0.065f
            val bracketColor = JarvisColors.NeonCyan.copy(alpha = 0.65f)
            val strokeW = 2f

            // Top-left bracket
            drawLine(bracketColor, Offset(pad, pad), Offset(pad + arm, pad), strokeW)
            drawLine(bracketColor, Offset(pad, pad), Offset(pad, pad + arm), strokeW)
            // Top-right bracket
            drawLine(bracketColor, Offset(size.width - pad, pad), Offset(size.width - pad - arm, pad), strokeW)
            drawLine(bracketColor, Offset(size.width - pad, pad), Offset(size.width - pad, pad + arm), strokeW)
            // Bottom-left bracket
            drawLine(bracketColor, Offset(pad, size.height - pad), Offset(pad + arm, size.height - pad), strokeW)
            drawLine(bracketColor, Offset(pad, size.height - pad), Offset(pad, size.height - pad - arm), strokeW)
            // Bottom-right bracket
            drawLine(bracketColor, Offset(size.width - pad, size.height - pad), Offset(size.width - pad - arm, size.height - pad), strokeW)
            drawLine(bracketColor, Offset(size.width - pad, size.height - pad), Offset(size.width - pad, size.height - pad - arm), strokeW)

            // Subtle AR spatial lock ring behind avatar center
            drawCircle(
                color = JarvisColors.NeonCyan.copy(alpha = 0.16f),
                radius = size.minDimension * 0.34f,
                center = Offset(size.width * 0.5f, size.height * 0.42f),
                style = Stroke(width = 1.2f)
            )
            drawText(
                textMeasurer = textMeasurer,
                text = "AR SURFACE // LIVE",
                topLeft = Offset(pad + 6f, pad + 6f),
                style = arTagStyle
            )
        }

        // Perspective grid floor
        val horizonY = size.height * 0.62f
        val vanishX = size.width * 0.5f
        val gridAlpha = if (cameraActive) 0.18f else 0.14f
        val rays = 12
        for (i in -rays..rays) {
            drawLine(
                color = JarvisColors.NeonCyan.copy(alpha = gridAlpha),
                start = Offset(vanishX, horizonY),
                end = Offset(vanishX + (i.toFloat() / rays) * size.width * 1.45f, size.height),
                strokeWidth = 1f
            )
        }
        val rows = 10
        for (j in 0 until rows) {
            val t = ((j + gridOffset) % rows) / rows.toFloat()
            val y = horizonY + t.pow(2.2f) * (size.height - horizonY)
            drawLine(
                color = JarvisColors.NeonCyan.copy(alpha = gridAlpha),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f
            )
        }

        if (!reducedMotion) {
            val colWidth = size.width / columns
            val rowHeight = size.height / 36f
            for (i in 0 until columns) {
                if (cameraActive && i % 3 != 0) continue
                val x = i * colWidth + colWidth * 0.25f
                val headRow = drops[i]
                for (trail in 0..4) {
                    val y = (headRow - trail) * rowHeight
                    if (y < -rowHeight || y > size.height) continue
                    val alpha = when {
                        trail == 0 -> if (cameraActive) 0.42f else 0.72f
                        else -> (0.42f - trail * 0.08f).coerceAtLeast(0.06f)
                    }
                    val color = if (trail == 0 && i % 3 == 0) {
                        JarvisColors.MatrixEmerald.copy(alpha = alpha)
                    } else {
                        JarvisColors.NeonCyan.copy(alpha = alpha)
                    }
                    val ch = glyphs[(dropGlyphs[i].code + trail) % glyphs.size].toString()
                    drawText(
                        textMeasurer = textMeasurer,
                        text = ch,
                        topLeft = Offset(x, y),
                        style = glyphStyle.copy(color = color)
                    )
                }
            }
        }
    }
}
