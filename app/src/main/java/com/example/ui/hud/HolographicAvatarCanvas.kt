package com.example.ui.hud

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.ui.theme.JarvisColors
import com.example.ui.theme.JetBrainsMonoFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 4D Full-Body Realistic Anime Girl Avatar Layer.
 * Renders the full-body head-to-toe semi-realistic anime avatar with an automatic
 * edge-connected transparent cutout so she stands directly inside the AR CameraX
 * SurfaceView or Matrix void on volumetric holographic floor rings, complete with
 * 4D perspective tracking and MAIM's fingertip void-tap launcher for MAIM OS.
 */
@Composable
fun HolographicAvatarCanvas(
    isSpeaking: Boolean,
    isListening: Boolean,
    speechEnergy: Float,
    reducedMotion: Boolean,
    desktopOpen: Boolean = false,
    onVoidFingerTap: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
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

    var cutoutBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(Unit) {
        cutoutBitmap = withContext(Dispatchers.Default) {
            loadFullBodyTransparentAvatar(context)
        }
    }

    var pointerX by remember { mutableFloatStateOf(0f) }
    var pointerY by remember { mutableFloatStateOf(0f) }
    var smoothLookX by remember { mutableFloatStateOf(0f) }
    var smoothLookY by remember { mutableFloatStateOf(0f) }
    var elapsedSec by remember { mutableFloatStateOf(0f) }
    var smoothSpeech by remember { mutableFloatStateOf(0f) }

    // Void-tap fingertip animation state
    var tapTarget by remember { mutableStateOf<Offset?>(null) }
    var tapAnimProgress by remember { mutableFloatStateOf(0f) }
    var tapTriggeredCallback by remember { mutableStateOf(false) }

    val textMeasurer = rememberTextMeasurer()
    val hintStyle = remember {
        TextStyle(
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 9.sp,
            color = JarvisColors.NeonCyan.copy(alpha = 0.88f)
        )
    }

    LaunchedEffect(isResumed, reducedMotion, isSpeaking, speechEnergy, tapTarget) {
        if (!isResumed) return@LaunchedEffect
        var lastNanos = 0L
        while (true) {
            withFrameNanos { nanos ->
                val dt = if (lastNanos == 0L) 0.016f else ((nanos - lastNanos) / 1_000_000_000f).coerceAtMost(0.05f)
                lastNanos = nanos
                if (!reducedMotion) {
                    elapsedSec += dt
                }
                smoothLookX += (pointerX - smoothLookX) * (dt * 5.5f).coerceAtMost(1f)
                smoothLookY += (pointerY - smoothLookY) * (dt * 5.5f).coerceAtMost(1f)

                val targetSpeech = if (isSpeaking) {
                    val wave = abs(sin(elapsedSec * 10.5f) * cos(elapsedSec * 4.8f))
                    (0.25f + 0.75f * maxOf(wave, speechEnergy)).coerceIn(0f, 1f)
                } else if (isListening) {
                    (speechEnergy * 0.45f).coerceIn(0f, 0.45f)
                } else {
                    0f
                }
                smoothSpeech += (targetSpeech - smoothSpeech) * (dt * 14f).coerceAtMost(1f)

                if (tapTarget != null) {
                    tapAnimProgress += dt * 2.8f
                    if (tapAnimProgress >= 0.55f && !tapTriggeredCallback) {
                        tapTriggeredCallback = true
                        onVoidFingerTap()
                    }
                    if (tapAnimProgress >= 1f) {
                        tapTarget = null
                        tapAnimProgress = 0f
                        tapTriggeredCallback = false
                    }
                }
            }
        }
    }

    val motionScale = if (reducedMotion) 0.12f else 1f
    val breath = sin(elapsedSec * 1.8f) * motionScale
    val headSway = if (isSpeaking) sin(elapsedSec * 2.4f) * 3.5f * motionScale else sin(elapsedSec * 0.9f) * 2.0f * motionScale
    val headNod = if (isSpeaking) sin(elapsedSec * 3.8f) * 2.2f * motionScale else breath * 1.0f
    val rotY = (smoothLookX * 14f + headSway).coerceIn(-18f, 18f)
    val rotX = (smoothLookY * 7f + headNod).coerceIn(-10f, 10f)
    val avatarScale = 1f + breath * 0.012f + smoothSpeech * 0.022f
    val density = LocalDensity.current.density

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    pointerX = ((offset.x / size.width) * 2f - 1f).coerceIn(-1f, 1f)
                    pointerY = (-((offset.y / size.height) * 2f - 1f)).coerceIn(-1f, 1f)
                    tapTarget = offset
                    tapAnimProgress = 0f
                    tapTriggeredCallback = false
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    pointerX = ((change.position.x / size.width) * 2f - 1f).coerceIn(-1f, 1f)
                    pointerY = (-((change.position.y / size.height) * 2f - 1f)).coerceIn(-1f, 1f)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val fullHeightDp = maxHeight * 0.72f
        val fullWidthDp = minOf(maxWidth * 0.88f, fullHeightDp * 0.62f)
        val auraColor = if (isSpeaking) JarvisColors.MatrixEmerald else JarvisColors.NeonCyan

        // Back layer: Volumetric floor projection rings right under her boots + silhouette aura
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width * 0.5f + smoothLookX * 10f
            val bodyCenterY = size.height * 0.45f - breath * 5f
            val scale = minOf(size.width, size.height) * 0.42f
            val floorY = size.height * 0.79f

            val ringCenter = Offset(size.width * 0.5f, floorY)
            val glowRadius = scale * (0.82f + smoothSpeech * 0.16f)
            drawOval(
                brush = Brush.radialGradient(
                    listOf(
                        auraColor.copy(alpha = 0.34f + smoothSpeech * 0.22f),
                        Color.Transparent
                    ),
                    center = ringCenter,
                    radius = glowRadius
                ),
                topLeft = Offset(ringCenter.x - glowRadius, ringCenter.y - scale * 0.16f),
                size = Size(glowRadius * 2f, scale * 0.32f)
            )
            drawOval(
                color = JarvisColors.NeonCyan.copy(alpha = 0.72f),
                topLeft = Offset(ringCenter.x - scale * 0.60f, ringCenter.y - scale * 0.11f),
                size = Size(scale * 1.20f, scale * 0.22f),
                style = Stroke(width = 2.4f)
            )
            drawOval(
                color = JarvisColors.MatrixEmerald.copy(alpha = 0.54f + smoothSpeech * 0.3f),
                topLeft = Offset(ringCenter.x - scale * 0.42f, ringCenter.y - scale * 0.07f),
                size = Size(scale * 0.84f, scale * 0.14f),
                style = Stroke(width = 1.8f)
            )

            // Soft full-body holographic aura behind the anime character
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(
                        auraColor.copy(alpha = 0.20f + smoothSpeech * 0.18f),
                        Color.Transparent
                    ),
                    center = Offset(cx, bodyCenterY),
                    radius = scale * 1.1f
                ),
                topLeft = Offset(cx - scale * 0.78f, bodyCenterY - scale * 1.25f),
                size = Size(scale * 1.56f, scale * 2.45f)
            )
        }

        // Full-body head-to-toe high-quality realistic anime girl cutout in 4D perspective
        val imgModifier = Modifier
            .offset {
                IntOffset(
                    x = (smoothLookX * 10f).roundToInt(),
                    y = (-24f - breath * 8f).roundToInt()
                )
            }
            .width(fullWidthDp)
            .height(fullHeightDp)
            .graphicsLayer {
                cameraDistance = 16f * density
                rotationY = rotY
                rotationX = rotX
                scaleX = avatarScale
                scaleY = avatarScale
            }

        val readyCutout = cutoutBitmap
        if (readyCutout != null) {
            Image(
                bitmap = readyCutout,
                contentDescription = "MAIM Full-Body Realistic Anime Avatar",
                contentScale = ContentScale.Fit,
                modifier = imgModifier
            )
        } else {
            Image(
                painter = painterResource(id = R.drawable.img_maim_anime_full),
                contentDescription = "MAIM Full-Body Realistic Anime Avatar",
                contentScale = ContentScale.Fit,
                modifier = imgModifier
            )
        }

        // Foreground layer: voice resonance wave + MAIM's holographic fingertip portal
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width * 0.5f + smoothLookX * 10f
            val cy = size.height * 0.43f - breath * 5f
            val scale = minOf(size.width, size.height) * 0.42f

            // Voice-reactive resonance bars near her lower hologram ring
            if (isSpeaking || isListening) {
                val waveY = size.height * 0.75f
                val bars = 15
                val spacing = scale * 0.052f
                val startX = cx - (bars - 1) * spacing * 0.5f
                for (i in 0 until bars) {
                    val distFromCenter = 1f - (abs(i - bars / 2f) / (bars / 2f))
                    val amp = (0.18f + smoothSpeech * 0.82f) *
                        distFromCenter *
                        (0.55f + 0.45f * abs(sin(elapsedSec * 12f + i * 0.7f)))
                    val barH = scale * 0.08f * amp
                    val x = startX + i * spacing
                    drawLine(
                        color = if (isSpeaking) JarvisColors.MatrixEmerald else JarvisColors.NeonCyan,
                        start = Offset(x, waveY - barH),
                        end = Offset(x, waveY + barH),
                        strokeWidth = 3f
                    )
                }
            }

            // MAIM's glowing fingertip emitter & void-tap projection
            val shoulderAnchor = Offset(cx + scale * 0.25f, cy - scale * 0.08f)
            val restFingerTip = Offset(
                cx + scale * 0.56f,
                cy - scale * 0.14f + sin(elapsedSec * 2.1f) * scale * 0.03f
            )
            val reachT = if (tapTarget != null) {
                if (tapAnimProgress < 0.5f) tapAnimProgress * 2f else (1f - tapAnimProgress) * 2f
            } else {
                0f
            }
            val target = tapTarget ?: restFingerTip
            val activeFingerTip = Offset(
                restFingerTip.x + (target.x - restFingerTip.x) * reachT,
                restFingerTip.y + (target.y - restFingerTip.y) * reachT
            )
            val wristPos = Offset(
                (shoulderAnchor.x + activeFingerTip.x) * 0.52f,
                (shoulderAnchor.y + activeFingerTip.y) * 0.56f + scale * 0.03f
            )

            drawLine(
                color = JarvisColors.NeonCyan.copy(alpha = 0.36f),
                start = shoulderAnchor,
                end = wristPos,
                strokeWidth = scale * 0.035f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = JarvisColors.MatrixEmerald.copy(alpha = 0.72f),
                start = wristPos,
                end = activeFingerTip,
                strokeWidth = scale * 0.020f,
                cap = StrokeCap.Round
            )
            drawCircle(
                color = JarvisColors.NeonCyan.copy(alpha = 0.75f),
                radius = scale * 0.028f,
                center = wristPos,
                style = Stroke(width = 1.8f)
            )

            val fingerPulse = 1f + 0.18f * sin(elapsedSec * 4.5f)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(JarvisColors.MatrixEmerald.copy(alpha = 0.80f), Color.Transparent),
                    center = activeFingerTip,
                    radius = scale * 0.092f * fingerPulse
                ),
                radius = scale * 0.092f * fingerPulse,
                center = activeFingerTip
            )
            drawCircle(
                color = JarvisColors.NeonCyan,
                radius = scale * 0.018f * fingerPulse,
                center = activeFingerTip
            )

            if (!desktopOpen && tapTarget == null) {
                drawText(
                    textMeasurer = textMeasurer,
                    text = "TAP VOID → MAIM OS",
                    topLeft = Offset(
                        (restFingerTip.x - scale * 0.24f).coerceIn(12f, size.width - 140f),
                        restFingerTip.y + scale * 0.07f
                    ),
                    style = hintStyle
                )
            }

            if (tapTarget != null) {
                val rippleAlpha = (1f - tapAnimProgress).coerceIn(0f, 1f)
                val rippleRadius = scale * (0.08f + tapAnimProgress * 0.65f)
                drawCircle(
                    color = JarvisColors.NeonCyan.copy(alpha = rippleAlpha * 0.85f),
                    radius = rippleRadius,
                    center = activeFingerTip,
                    style = Stroke(width = 2.6f)
                )
                drawCircle(
                    color = JarvisColors.MatrixEmerald.copy(alpha = rippleAlpha * 0.65f),
                    radius = rippleRadius * 0.58f,
                    center = activeFingerTip,
                    style = Stroke(width = 2f)
                )
            }
        }
    }
}

private fun loadFullBodyTransparentAvatar(context: Context): ImageBitmap? {
    return runCatching {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(context.resources, R.drawable.img_maim_anime_full, boundsOpts)
        var sample = 1
        while (boundsOpts.outHeight / sample > 1100 || boundsOpts.outWidth / sample > 900) {
            sample *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val src = BitmapFactory.decodeResource(context.resources, R.drawable.img_maim_anime_full, decodeOpts)
            ?: return null

        val w = src.width
        val h = src.height
        val total = w * h
        val pixels = IntArray(total)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        if (src != null && !src.isRecycled) src.recycle()

        val visited = BooleanArray(total)
        val queue = IntArray(total)
        var head = 0
        var tail = 0

        fun isDarkBg(px: Int): Boolean {
            val r = (px ushr 16) and 0xFF
            val g = (px ushr 8) and 0xFF
            val b = px and 0xFF
            return maxOf(r, g, b) <= 28
        }

        fun enqueueIfBg(x: Int, y: Int) {
            val idx = y * w + x
            if (!visited[idx] && isDarkBg(pixels[idx])) {
                visited[idx] = true
                queue[tail++] = idx
            }
        }

        for (x in 0 until w) {
            enqueueIfBg(x, 0)
            enqueueIfBg(x, h - 1)
        }
        for (y in 0 until h) {
            enqueueIfBg(0, y)
            enqueueIfBg(w - 1, y)
        }

        while (head < tail) {
            val idx = queue[head++]
            pixels[idx] = 0x00000000
            val x = idx % w
            val y = idx / w
            if (x > 0) enqueueIfBg(x - 1, y)
            if (x + 1 < w) enqueueIfBg(x + 1, y)
            if (y > 0) enqueueIfBg(x, y - 1)
            if (y + 1 < h) enqueueIfBg(x, y + 1)
        }

        // 1px soft anti-aliased feather along the cutout silhouette border
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val idx = y * w + x
                if (visited[idx]) continue
                val touchesBg = visited[idx - 1] || visited[idx + 1] || visited[idx - w] || visited[idx + w]
                if (touchesBg) {
                    val px = pixels[idx]
                    val r = (px ushr 16) and 0xFF
                    val g = (px ushr 8) and 0xFF
                    val b = px and 0xFF
                    val maxRgb = maxOf(r, g, b)
                    if (maxRgb < 72) {
                        val alpha = ((maxRgb / 72f) * 255f).toInt().coerceIn(40, 255)
                        pixels[idx] = (alpha shl 24) or (px and 0x00FFFFFF)
                    }
                }
            }
        }

        Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
    }.getOrNull()
}
