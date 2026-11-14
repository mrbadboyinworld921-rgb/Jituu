package com.example.ui.hud

import android.graphics.Color
import android.util.Log
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Renders the live CameraX feed inside a SurfaceView-backed PreviewView
 * (ImplementationMode.PERFORMANCE) behind the 4D holographic avatar layer
 * for low-latency Augmented Reality HUD compositing.
 */
@Composable
fun CameraHudBackground(
    useFrontCamera: Boolean = false,
    modifier: Modifier = Modifier,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
            // PERFORMANCE mode uses an android.view.SurfaceView dedicated hardware surface
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
            setBackgroundColor(Color.TRANSPARENT)
        }
    }

    DisposableEffect(lifecycleOwner, useFrontCamera) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val executor = ContextCompat.getMainExecutor(context)
        var boundProvider: ProcessCameraProvider? = null
        var isDisposed = false

        providerFuture.addListener(
            {
                if (isDisposed) return@addListener
                runCatching {
                    val provider = providerFuture.get()
                    boundProvider = provider
                    val preferredSelector = if (useFrontCamera) {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    } else {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    }
                    val selector = when {
                        provider.hasCamera(preferredSelector) -> preferredSelector
                        provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                        provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                        else -> throw IllegalStateException("No camera hardware available on this device.")
                    }
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        selector,
                        preview
                    )
                }.onFailure { err ->
                    Log.e("CameraHudBackground", "Failed to start AR SurfaceView camera", err)
                    onError(err.localizedMessage ?: "Camera unavailable on this device.")
                }
            },
            executor
        )

        onDispose {
            isDisposed = true
            runCatching { boundProvider?.unbindAll() }
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier
            .fillMaxSize()
            .testTag("ar_camera_surface_view")
    )
}
