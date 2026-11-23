package com.example.ui.components

import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FlashAuto
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.BrandCoral
import com.example.ui.theme.TintScan
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

enum class FlashModeOption(
    val label: String,
    val cameraXMode: Int,
    val icon: ImageVector
) {
    AUTO("Flash Auto", ImageCapture.FLASH_MODE_AUTO, Icons.Rounded.FlashAuto),
    ON("Flash On", ImageCapture.FLASH_MODE_ON, Icons.Rounded.FlashOn),
    OFF("Flash Off", ImageCapture.FLASH_MODE_OFF, Icons.Rounded.FlashOff);

    fun next(): FlashModeOption = when (this) {
        AUTO -> ON
        ON -> OFF
        OFF -> AUTO
    }
}

data class CameraCapabilities(
    val hasBackCamera: Boolean = true,
    val hasFrontCamera: Boolean = false,
    val hasFlashUnit: Boolean = false,
    val maxZoomRatio: Float = 1f
)

@Composable
fun CameraXDocumentPreview(
    useFrontCamera: Boolean,
    flashMode: FlashModeOption,
    torchEnabled: Boolean,
    zoomRatio: Float,
    onImageCaptureReady: (ImageCapture?) -> Unit,
    onCapabilitiesChanged: (CameraCapabilities) -> Unit,
    onCameraUnavailable: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var boundImageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var focusTapOffset by remember { mutableStateOf<Offset?>(null) }

    // Clear focus ring after 1.1 seconds
    LaunchedEffect(focusTapOffset) {
        if (focusTapOffset != null) {
            delay(1100)
            focusTapOffset = null
        }
    }

    // Bind CameraX lifecycle only when lifecycleOwner or selected camera lens changes (no flicker on flash/zoom!)
    DisposableEffect(lifecycleOwner, useFrontCamera) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)
        var activeProvider: ProcessCameraProvider? = null

        cameraProviderFuture.addListener({
            runCatching {
                val provider = cameraProviderFuture.get()
                activeProvider = provider

                val hasBack = runCatching { provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) }.getOrDefault(false)
                val hasFront = runCatching { provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) }.getOrDefault(false)

                val selector = when {
                    useFrontCamera && hasFront -> CameraSelector.DEFAULT_FRONT_CAMERA
                    hasBack -> CameraSelector.DEFAULT_BACK_CAMERA
                    hasFront -> CameraSelector.DEFAULT_FRONT_CAMERA
                    else -> null
                }

                if (selector == null) {
                    boundCamera = null
                    boundImageCapture = null
                    onImageCaptureReady(null)
                    onCameraUnavailable("No camera hardware detected on this device.")
                    return@addListener
                }

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val captureBuilder = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setFlashMode(flashMode.cameraXMode)
                previewView.display?.rotation?.let { captureBuilder.setTargetRotation(it) }
                val capture = captureBuilder.build()

                provider.unbindAll()
                val camera = provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)

                boundCamera = camera
                boundImageCapture = capture
                onImageCaptureReady(capture)

                val maxZoom = camera.cameraInfo.zoomState.value?.maxZoomRatio ?: 1f
                onCapabilitiesChanged(
                    CameraCapabilities(
                        hasBackCamera = hasBack,
                        hasFrontCamera = hasFront,
                        hasFlashUnit = camera.cameraInfo.hasFlashUnit(),
                        maxZoomRatio = maxZoom
                    )
                )
            }.onFailure { err ->
                boundCamera = null
                boundImageCapture = null
                onImageCaptureReady(null)
                onCameraUnavailable(err.message ?: "Unable to start CameraX preview.")
            }
        }, mainExecutor)

        onDispose {
            boundCamera = null
            boundImageCapture = null
            onImageCaptureReady(null)
            runCatching { activeProvider?.unbindAll() }
        }
    }

    // Update flash mode, torch, and zoom smoothly without rebinding CameraX
    LaunchedEffect(flashMode, boundImageCapture) {
        boundImageCapture?.flashMode = flashMode.cameraXMode
    }
    LaunchedEffect(torchEnabled, boundCamera) {
        val cam = boundCamera ?: return@LaunchedEffect
        if (cam.cameraInfo.hasFlashUnit()) {
            runCatching { cam.cameraControl.enableTorch(torchEnabled) }
        }
    }
    LaunchedEffect(zoomRatio, boundCamera) {
        val cam = boundCamera ?: return@LaunchedEffect
        val zs = cam.cameraInfo.zoomState.value
        val clamped = if (zs != null) zoomRatio.coerceIn(zs.minZoomRatio, zs.maxZoomRatio) else zoomRatio
        runCatching { cam.cameraControl.setZoomRatio(clamped) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(boundCamera) {
                detectTapGestures { offset ->
                    val cam = boundCamera ?: return@detectTapGestures
                    focusTapOffset = offset
                    runCatching {
                        val point = previewView.meteringPointFactory.createPoint(offset.x, offset.y)
                        val action = FocusMeteringAction.Builder(
                            point,
                            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
                        )
                            .setAutoCancelDuration(3, TimeUnit.SECONDS)
                            .build()
                        cam.cameraControl.startFocusAndMetering(action)
                    }
                }
            }
    ) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // Tap-to-focus indicator ring
        AnimatedVisibility(
            visible = focusTapOffset != null,
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(300))
        ) {
            focusTapOffset?.let { pt ->
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (pt.x - 32.dp.toPx()).roundToInt(),
                                (pt.y - 32.dp.toPx()).roundToInt()
                            )
                        }
                        .size(64.dp)
                        .border(2.dp, TintScan, CircleShape)
                        .padding(18.dp)
                        .clip(CircleShape)
                        .background(TintScan.copy(alpha = 0.2f))
                )
            }
        }
    }
}

@Composable
fun DocumentGuideOverlay(
    shutterFlashActive: Boolean,
    modifier: Modifier = Modifier
) {
    val flashAlpha by animateFloatAsState(
        targetValue = if (shutterFlashActive) 0.55f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "shutter_flash"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val hInset = 28.dp.toPx()
        val topInset = 112.dp.toPx()
        val bottomInset = 164.dp.toPx()
        val frameW = (size.width - hInset * 2f).coerceAtLeast(120f)
        val frameH = (size.height - topInset - bottomInset).coerceAtLeast(160f)
        val left = hInset
        val top = topInset
        val right = left + frameW
        val bottom = top + frameH
        val radius = 16.dp.toPx()

        // Dimmed vignette outside document frame
        val cutout = Path().apply {
            addRoundRect(
                androidx.compose.ui.geometry.RoundRect(
                    rect = Rect(left, top, right, bottom),
                    cornerRadius = CornerRadius(radius, radius)
                )
            )
        }
        clipPath(cutout, clipOp = ClipOp.Difference) {
            drawRect(Color.Black.copy(alpha = 0.36f))
        }

        // Subtle inner guide outline
        drawRoundRect(
            color = TintScan.copy(alpha = 0.28f),
            topLeft = Offset(left, top),
            size = Size(frameW, frameH),
            cornerRadius = CornerRadius(radius, radius),
            style = Stroke(width = 1.25.dp.toPx())
        )

        // Crisp L-shaped scanner brackets on all 4 corners
        val corner = 30.dp.toPx()
        val strokeW = 3.5.dp.toPx()
        // Top-left
        drawLine(TintScan, Offset(left, top), Offset(left + corner, top), strokeW)
        drawLine(TintScan, Offset(left, top), Offset(left, top + corner), strokeW)
        // Top-right
        drawLine(TintScan, Offset(right, top), Offset(right - corner, top), strokeW)
        drawLine(TintScan, Offset(right, top), Offset(right, top + corner), strokeW)
        // Bottom-left
        drawLine(TintScan, Offset(left, bottom), Offset(left + corner, bottom), strokeW)
        drawLine(TintScan, Offset(left, bottom), Offset(left, bottom - corner), strokeW)
        // Bottom-right
        drawLine(TintScan, Offset(right, bottom), Offset(right - corner, bottom), strokeW)
        drawLine(TintScan, Offset(right, bottom), Offset(right, bottom - corner), strokeW)

        // Brief shutter capture flash
        if (flashAlpha > 0.01f) {
            drawRect(Color.White.copy(alpha = flashAlpha))
        }
    }
}

@Composable
fun CropCornerHandle(
    position: Offset,
    widthPx: Float,
    heightPx: Float,
    onMove: (Offset) -> Unit
) {
    val currentPos by rememberUpdatedState(position)
    val currentOnMove by rememberUpdatedState(onMove)

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (position.x * widthPx - 24.dp.toPx()).roundToInt(),
                    (position.y * heightPx - 24.dp.toPx()).roundToInt()
                )
            }
            .size(48.dp)
            .pointerInput(widthPx, heightPx) {
                detectDragGestures { change, drag ->
                    change.consume()
                    currentOnMove(
                        Offset(
                            (currentPos.x + drag.x / widthPx).coerceIn(0f, 1f),
                            (currentPos.y + drag.y / heightPx).coerceIn(0f, 1f)
                        )
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(BrandCoral)
                .border(2.5.dp, Color.White, CircleShape)
        )
    }
}
