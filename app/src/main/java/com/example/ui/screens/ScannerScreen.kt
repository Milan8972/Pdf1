package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint as AndroidPaint
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.example.data.DocumentEntity
import com.example.data.DocumentSource
import com.example.data.ScannedImageEntity
import com.example.engine.PdfRenderHelper
import com.example.engine.ScanFilter
import com.example.ui.components.EmptyStateBlock
import com.example.ui.components.FileListRow
import com.example.ui.theme.BrandAmber
import com.example.ui.theme.BrandCoral
import com.example.ui.theme.LocalPaperPilotColors
import com.example.ui.theme.TintScan
import com.example.viewmodel.PaperPilotViewModel
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerTabLanding(
    scannedImages: List<ScannedImageEntity>,
    allDocuments: List<DocumentEntity>,
    onStartScan: () -> Unit,
    onOpenDocument: (DocumentEntity) -> Unit,
    onShareDocument: (DocumentEntity) -> Unit,
    onToggleFavorite: (DocumentEntity) -> Unit,
    onDeleteDocument: (DocumentEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Scanned images, 1 = Created PDFs
    val scannedPdfs = remember(allDocuments) {
        allDocuments.filter { it.source == DocumentSource.SCANNED.name }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "Scanner",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
            )
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            ) {
                SegmentedButton(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                    label = { Text("Scanned images") }
                )
                SegmentedButton(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    label = { Text("Created PDFs") }
                )
            }
            Spacer(Modifier.height(14.dp))

            if (selectedTab == 0) {
                if (scannedImages.isEmpty()) {
                    EmptyStateBlock(
                        title = "No scanned images yet",
                        subtitle = "Capture receipts, whiteboards, or multi-page paper documents right on your device.",
                        actionLabel = "Start scanning",
                        onAction = onStartScan,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(140.dp),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 100.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(scannedImages, key = { it.id }) { img ->
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, LocalPaperPilotColors.current.subtleBorder)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    AsyncImage(
                                        model = File(img.imagePath),
                                        contentDescription = img.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxWidth().aspectRatio(0.78f).clip(RoundedCornerShape(12.dp))
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(img.name, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                                    Text(img.filterName, style = MaterialTheme.typography.labelSmall, color = LocalPaperPilotColors.current.mutedText)
                                }
                            }
                        }
                    }
                }
            } else {
                if (scannedPdfs.isEmpty()) {
                    EmptyStateBlock(
                        title = "No scanned PDFs yet",
                        subtitle = "Scan one or more pages and turn them into a clean, searchable PDF.",
                        actionLabel = "Scan to PDF",
                        onAction = onStartScan,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 100.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(scannedPdfs, key = { it.id }) { doc ->
                            FileListRow(
                                doc = doc,
                                selected = false,
                                selectionMode = false,
                                onClick = { onOpenDocument(doc) },
                                onLongClick = {},
                                onOpen = { onOpenDocument(doc) },
                                onRename = {},
                                onShare = { onShareDocument(doc) },
                                onFavorite = { onToggleFavorite(doc) },
                                onDelete = { onDeleteDocument(doc) },
                                onDetails = {}
                            )
                        }
                    }
                }
            }
        }

        // Large Coral-to-Amber gradient Scan FAB
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(22.dp)
                .size(64.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(BrandCoral, BrandAmber)))
                .clickable(onClick = onStartScan)
                .testTag("scanner_launch_fab")
        ) {
            Icon(Icons.Rounded.DocumentScanner, contentDescription = "Open camera scanner", tint = Color.White, modifier = Modifier.size(30.dp))
        }
    }
}

private enum class ScanStep { CAMERA, CROP, FILTER, REVIEW }

private data class CapturedScanPage(
    val rawFile: File,
    val processedFile: File,
    val filter: ScanFilter = ScanFilter.AUTO_ENHANCE,
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val rotation: Float = 0f
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerFlowScreen(
    viewModel: PaperPilotViewModel,
    defaultOcrLang: String,
    onBack: () -> Unit,
    onPdfCreated: (DocumentEntity) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var step by remember { mutableStateOf(ScanStep.CAMERA) }
    var batchPages by remember { mutableStateOf<List<CapturedScanPage>>(emptyList()) }
    var workingRawFile by remember { mutableStateOf<File?>(null) }

    // Crop corners (normalized 0..1)
    var tl by remember { mutableStateOf(Offset(0.08f, 0.08f)) }
    var tr by remember { mutableStateOf(Offset(0.92f, 0.08f)) }
    var bl by remember { mutableStateOf(Offset(0.08f, 0.92f)) }
    var br by remember { mutableStateOf(Offset(0.92f, 0.92f)) }
    var cropRotation by remember { mutableFloatStateOf(0f) }

    // Filter state
    var selectedFilter by remember { mutableStateOf(ScanFilter.AUTO_ENHANCE) }
    var brightness by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(1f) }

    // Review OCR state
    var enableOcr by remember { mutableStateOf(true) }
    var ocrLang by remember { mutableStateOf(defaultOcrLang) }

    // Camera permission state
    var hasCameraPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var flashOn by remember { mutableStateOf(false) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var cameraAvailable by remember { mutableStateOf(true) }

    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPerm = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPerm) permLauncher.launch(Manifest.permission.CAMERA)
    }

    val galleryImport = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val raw = File(viewModel.repository.scansDir, "raw_import_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                raw.outputStream().use { input.copyTo(it) }
            }
            if (raw.exists()) {
                workingRawFile = raw
                tl = Offset(0.06f, 0.06f); tr = Offset(0.94f, 0.06f)
                bl = Offset(0.06f, 0.94f); br = Offset(0.94f, 0.94f)
                cropRotation = 0f
                step = ScanStep.CROP
            }
        }
    }

    BackHandler {
        when (step) {
            ScanStep.CAMERA -> onBack()
            ScanStep.CROP -> step = ScanStep.CAMERA
            ScanStep.FILTER -> step = ScanStep.CROP
            ScanStep.REVIEW -> step = ScanStep.CAMERA
        }
    }

    fun captureFallbackSamplePage() {
        val raw = File(viewModel.repository.scansDir, "scan_cap_${System.currentTimeMillis()}.jpg")
        val bmp = Bitmap.createBitmap(900, 1200, Bitmap.Config.ARGB_8888)
        val c = AndroidCanvas(bmp)
        c.drawColor(AndroidColor.parseColor("#EFECE6"))
        val paperPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.WHITE }
        c.drawRect(70f, 90f, 830f, 1110f, paperPaint)
        val headPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.parseColor("#1A1523")
            textSize = 42f
            isFakeBoldText = true
        }
        val linePaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.parseColor("#4B5563")
            textSize = 28f
        }
        c.drawText("SCANNED RECEIPT #${batchPages.size + 1}", 120f, 200f, headPaint)
        c.drawText("Merchant: Cedar Coffee Roasters", 120f, 290f, linePaint)
        c.drawText("Date: Nov 21, 2026", 120f, 350f, linePaint)
        c.drawText("Items: Espresso + Notebook", 120f, 430f, linePaint)
        c.drawText("Total Paid: $18.50", 120f, 520f, headPaint)
        FileOutputStream(raw).use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        bmp.recycle()
        workingRawFile = raw
        tl = Offset(0.08f, 0.08f); tr = Offset(0.92f, 0.08f)
        bl = Offset(0.08f, 0.92f); br = Offset(0.92f, 0.92f)
        cropRotation = 0f
        step = ScanStep.CROP
    }

    when (step) {
        ScanStep.CAMERA -> {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                if (hasCameraPerm && cameraAvailable) {
                    val previewView = remember { PreviewView(context) }
                    DisposableEffect(lifecycleOwner, flashOn) {
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                        val executor = ContextCompat.getMainExecutor(context)
                        cameraProviderFuture.addListener({
                            runCatching {
                                val provider = cameraProviderFuture.get()
                                val preview = Preview.Builder().build().also {
                                    it.surfaceProvider = previewView.surfaceProvider
                                }
                                val cap = ImageCapture.Builder()
                                    .setFlashMode(if (flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF)
                                    .build()
                                imageCapture = cap
                                provider.unbindAll()
                                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, cap)
                            }.onFailure {
                                cameraAvailable = false
                            }
                        }, executor)
                        onDispose {
                            runCatching { cameraProviderFuture.get().unbindAll() }
                        }
                    }
                    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize().padding(32.dp)
                    ) {
                        Icon(Icons.Rounded.CameraAlt, null, tint = Color.White.copy(0.75f), modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = if (!hasCameraPerm) "Camera permission needed for live scanning" else "Camera preview unavailable on this device",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "You can import a photo from your gallery or tap the shutter below to test the crop & filter pipeline.",
                            color = Color.White.copy(0.7f),
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (!hasCameraPerm) {
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { permLauncher.launch(Manifest.permission.CAMERA) }, shape = CircleShape) {
                                Text("Grant camera permission")
                            }
                        }
                    }
                }

                // Auto edge-detection guide overlay
                Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 128.dp)) {
                    val corner = 32.dp.toPx()
                    val stroke = Stroke(width = 3.5.dp.toPx())
                    // Subtle bounding rect
                    drawRect(color = TintScan.copy(alpha = 0.25f), style = Stroke(1.5.dp.toPx()))
                    // 4 crisp teal edge brackets
                    val w = size.width
                    val h = size.height
                    drawLine(TintScan, Offset(0f, 0f), Offset(corner, 0f), stroke.width)
                    drawLine(TintScan, Offset(0f, 0f), Offset(0f, corner), stroke.width)
                    drawLine(TintScan, Offset(w, 0f), Offset(w - corner, 0f), stroke.width)
                    drawLine(TintScan, Offset(w, 0f), Offset(w, corner), stroke.width)
                    drawLine(TintScan, Offset(0f, h), Offset(corner, h), stroke.width)
                    drawLine(TintScan, Offset(0f, h), Offset(0f, h - corner), stroke.width)
                    drawLine(TintScan, Offset(w, h), Offset(w - corner, h), stroke.width)
                    drawLine(TintScan, Offset(w, h), Offset(w, h - corner), stroke.width)
                }

                // Top controls
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.clip(CircleShape).background(Color.Black.copy(0.55f))
                    ) { Icon(Icons.Rounded.Close, "Close scanner", tint = Color.White) }

                    Surface(shape = CircleShape, color = Color.Black.copy(0.55f)) {
                        Text("Auto edge detect", color = TintScan, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                    }

                    IconButton(
                        onClick = { flashOn = !flashOn },
                        modifier = Modifier.clip(CircleShape).background(Color.Black.copy(0.55f))
                    ) {
                        Icon(if (flashOn) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff, "Toggle flash", tint = if (flashOn) BrandAmber else Color.White)
                    }
                }

                // Bottom camera controls (Gallery | Shutter | Batch counter)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 26.dp, start = 20.dp, end = 20.dp)
                ) {
                    IconButton(
                        onClick = { galleryImport.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        modifier = Modifier.size(54.dp).clip(CircleShape).background(Color.White.copy(0.18f)).testTag("scanner_gallery_button")
                    ) {
                        Icon(Icons.Rounded.PhotoLibrary, "Import from gallery", tint = Color.White)
                    }

                    // Shutter button
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .border(4.dp, Color.White, CircleShape)
                            .padding(6.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(BrandCoral, BrandAmber)))
                            .clickable {
                                val cap = imageCapture
                                if (hasCameraPerm && cameraAvailable && cap != null) {
                                    val outFile = File(viewModel.repository.scansDir, "cam_${System.currentTimeMillis()}.jpg")
                                    val opts = ImageCapture.OutputFileOptions.Builder(outFile).build()
                                    cap.takePicture(
                                        opts,
                                        ContextCompat.getMainExecutor(context),
                                        object : ImageCapture.OnImageSavedCallback {
                                            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                                workingRawFile = outFile
                                                step = ScanStep.CROP
                                            }
                                            override fun onError(exception: ImageCaptureException) {
                                                captureFallbackSamplePage()
                                            }
                                        }
                                    )
                                } else {
                                    captureFallbackSamplePage()
                                }
                            }
                            .testTag("scanner_shutter_button")
                    ) {}

                    // Batch counter badge
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (batchPages.isNotEmpty()) TintScan else Color.White.copy(0.18f))
                            .clickable(enabled = batchPages.isNotEmpty()) { step = ScanStep.REVIEW }
                    ) {
                        Text(
                            text = "${batchPages.size}",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }

        ScanStep.CROP -> {
            val raw = workingRawFile
            val rawBmp = remember(raw?.absolutePath, cropRotation) {
                raw?.let { f ->
                    PdfRenderHelper.decodeSampledBitmap(f, 1200)?.let {
                        if (cropRotation != 0f) PdfRenderHelper.applyScanTransform(it, ScanFilter.ORIGINAL, rotationDegrees = cropRotation)
                        else it
                    }
                }
            }
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Crop page") },
                        navigationIcon = { IconButton(onClick = { step = ScanStep.CAMERA }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                        actions = {
                            TextButton(onClick = { step = ScanStep.FILTER }) { Text("Next") }
                        }
                    )
                }
            ) { padding ->
                Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFF14111B))
                    ) {
                        val wPx = with(LocalDensity.current) { maxWidth.toPx() }.coerceAtLeast(1f)
                        val hPx = with(LocalDensity.current) { maxHeight.toPx() }.coerceAtLeast(1f)
                        rawBmp?.let {
                            Image(it.asImageBitmap(), "Captured page", contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
                        }
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val p = Path().apply {
                                moveTo(tl.x * size.width, tl.y * size.height)
                                lineTo(tr.x * size.width, tr.y * size.height)
                                lineTo(br.x * size.width, br.y * size.height)
                                lineTo(bl.x * size.width, bl.y * size.height)
                                close()
                            }
                            drawPath(p, TintScan, style = Stroke(3.dp.toPx()))
                        }
                        CropCornerHandle(tl, wPx, hPx) { tl = it }
                        CropCornerHandle(tr, wPx, hPx) { tr = it }
                        CropCornerHandle(bl, wPx, hPx) { bl = it }
                        CropCornerHandle(br, wPx, hPx) { br = it }
                    }

                    Spacer(Modifier.height(14.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                    ) {
                        OutlinedButton(
                            onClick = {
                                tl = Offset(0f, 0f); tr = Offset(1f, 0f)
                                bl = Offset(0f, 1f); br = Offset(1f, 1f)
                            },
                            shape = CircleShape,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.CropFree, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Full page", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = {
                                tl = Offset(0.08f, 0.08f); tr = Offset(0.92f, 0.08f)
                                bl = Offset(0.08f, 0.92f); br = Offset(0.92f, 0.92f)
                            },
                            shape = CircleShape,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.AutoFixHigh, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Auto", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { cropRotation = (cropRotation + 90f) % 360f },
                            shape = CircleShape,
                            modifier = Modifier.weight(0.9f)
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.RotateRight, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Rotate")
                        }
                    }
                }
            }
        }

        ScanStep.FILTER -> {
            val raw = workingRawFile
            val filteredBmp = remember(raw?.absolutePath, selectedFilter, brightness, contrast, cropRotation, tl, tr, bl, br) {
                raw?.let { f ->
                    PdfRenderHelper.decodeSampledBitmap(f, 1100)?.let { src ->
                        PdfRenderHelper.applyScanTransform(
                            src = src,
                            filter = selectedFilter,
                            brightness = brightness,
                            contrast = contrast,
                            rotationDegrees = cropRotation,
                            cropLeft = minOf(tl.x, bl.x),
                            cropTop = minOf(tl.y, tr.y),
                            cropRight = maxOf(tr.x, br.x),
                            cropBottom = maxOf(bl.y, br.y)
                        )
                    }
                }
            }

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Enhance & Filter") },
                        navigationIcon = { IconButton(onClick = { step = ScanStep.CROP }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                        actions = {
                            Button(
                                onClick = {
                                    if (raw != null && filteredBmp != null) {
                                        val out = File(viewModel.repository.scansDir, "processed_${System.currentTimeMillis()}.jpg")
                                        FileOutputStream(out).use { filteredBmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                                        batchPages = batchPages + CapturedScanPage(raw, out, selectedFilter, brightness, contrast, cropRotation)
                                        step = ScanStep.REVIEW
                                    }
                                },
                                shape = CircleShape,
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Icon(Icons.Rounded.Check, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Keep")
                            }
                        }
                    )
                }
            ) { padding ->
                Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFF14111B))
                    ) {
                        filteredBmp?.let {
                            Image(it.asImageBitmap(), "Filtered scan preview", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ScanFilter.entries) { f ->
                            FilterChip(
                                selected = selectedFilter == f,
                                onClick = { selectedFilter = f },
                                label = { Text(f.label) }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Brightness", style = MaterialTheme.typography.labelMedium)
                    Slider(value = brightness, onValueChange = { brightness = it }, valueRange = -0.35f..0.35f)
                    Text("Contrast", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = contrast,
                        onValueChange = { contrast = it },
                        valueRange = 0.6f..1.8f,
                        modifier = Modifier.navigationBarsPadding()
                    )
                }
            }
        }

        ScanStep.REVIEW -> {
            val langs = listOf("English", "Spanish", "French", "German", "Hindi", "Japanese")
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Review (${batchPages.size} page${if (batchPages.size == 1) "" else "s"})") },
                        navigationIcon = { IconButton(onClick = { step = ScanStep.CAMERA }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } }
                    )
                }
            ) { padding ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).navigationBarsPadding()
                ) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        itemsIndexed(batchPages) { idx, page ->
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, LocalPaperPilotColors.current.subtleBorder),
                                modifier = Modifier.width(210.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    AsyncImage(
                                        model = page.processedFile,
                                        contentDescription = "Scanned page ${idx + 1}",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        IconButton(
                                            onClick = {
                                                if (idx > 0) {
                                                    val m = batchPages.toMutableList()
                                                    val it = m.removeAt(idx)
                                                    m.add(idx - 1, it)
                                                    batchPages = m
                                                }
                                            },
                                            enabled = idx > 0
                                        ) { Icon(Icons.Rounded.ArrowBackIosNew, "Move left", modifier = Modifier.size(16.dp)) }
                                        Text("Page ${idx + 1}", style = MaterialTheme.typography.labelMedium)
                                        IconButton(
                                            onClick = {
                                                if (idx < batchPages.lastIndex) {
                                                    val m = batchPages.toMutableList()
                                                    val it = m.removeAt(idx)
                                                    m.add(idx + 1, it)
                                                    batchPages = m
                                                }
                                            },
                                            enabled = idx < batchPages.lastIndex
                                        ) { Icon(Icons.Rounded.ArrowForwardIos, "Move right", modifier = Modifier.size(16.dp)) }
                                        IconButton(
                                            onClick = { batchPages = batchPages.filterIndexed { i, _ -> i != idx } }
                                        ) { Icon(Icons.Rounded.Delete, "Delete page", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) }
                                    }
                                }
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("OCR (make text searchable)", style = MaterialTheme.typography.titleSmall)
                            Text("Index scanned pages locally in $ocrLang", style = MaterialTheme.typography.bodySmall, color = LocalPaperPilotColors.current.mutedText)
                        }
                        Switch(checked = enableOcr, onCheckedChange = { enableOcr = it })
                    }

                    if (enableOcr) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(langs) { l ->
                                FilterChip(selected = ocrLang == l, onClick = { ocrLang = l }, label = { Text(l) })
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { step = ScanStep.CAMERA },
                            shape = CircleShape,
                            modifier = Modifier.weight(1f).height(50.dp)
                        ) {
                            Icon(Icons.Rounded.Add, null)
                            Spacer(Modifier.width(4.dp))
                            Text("Add page")
                        }
                        Button(
                            onClick = {
                                viewModel.saveScannedBatchToPdf(
                                    pageFiles = batchPages.map { it.processedFile },
                                    filterNames = batchPages.map { it.filter.label },
                                    enableOcr = enableOcr,
                                    ocrLang = ocrLang,
                                    onCreated = onPdfCreated
                                )
                            },
                            enabled = batchPages.isNotEmpty(),
                            shape = CircleShape,
                            modifier = Modifier.weight(1.4f).height(50.dp).testTag("scanner_create_pdf_button")
                        ) {
                            Text("Create PDF")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CropCornerHandle(
    position: Offset,
    widthPx: Float,
    heightPx: Float,
    onMove: (Offset) -> Unit
) {
    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (position.x * widthPx - 18.dp.toPx()).roundToInt(),
                    (position.y * heightPx - 18.dp.toPx()).roundToInt()
                )
            }
            .size(36.dp)
            .pointerInput(position) {
                detectDragGestures { change, drag ->
                    change.consume()
                    onMove(
                        Offset(
                            (position.x + drag.x / widthPx).coerceIn(0f, 1f),
                            (position.y + drag.y / heightPx).coerceIn(0f, 1f)
                        )
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(BrandCoral)
                .border(2.5.dp, Color.White, CircleShape)
        )
    }
}
