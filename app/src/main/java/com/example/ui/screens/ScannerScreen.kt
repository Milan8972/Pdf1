package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.data.DocumentEntity
import com.example.data.DocumentSource
import com.example.data.ScannedImageEntity
import com.example.engine.MarginOption
import com.example.engine.PageSizeOption
import com.example.engine.PdfRenderHelper
import com.example.engine.ScanFilter
import com.example.ui.components.CameraCapabilities
import com.example.ui.components.CameraXDocumentPreview
import com.example.ui.components.CapturedScanPage
import com.example.ui.components.DocumentGuideOverlay
import com.example.ui.components.EmptyStateBlock
import com.example.ui.components.FileListRow
import com.example.ui.components.FlashModeOption
import com.example.ui.components.ScanCropStep
import com.example.ui.components.ScanFilterStep
import com.example.ui.components.ScanReviewStep
import com.example.ui.theme.BrandAmber
import com.example.ui.theme.BrandCoral
import com.example.ui.theme.LocalPaperPilotColors
import com.example.ui.theme.TintScan
import com.example.viewmodel.PaperPilotViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
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
                        subtitle = "Capture receipts, whiteboards, or multi-page paper documents and convert them into a single PDF.",
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
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(0.78f)
                                            .clip(RoundedCornerShape(12.dp))
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(img.name, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                                    Text(
                                        img.filterName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = LocalPaperPilotColors.current.mutedText
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                if (scannedPdfs.isEmpty()) {
                    EmptyStateBlock(
                        title = "No scanned PDFs yet",
                        subtitle = "Scan one or more pages with CameraX and combine them into a clean single PDF.",
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
            Icon(
                Icons.Rounded.DocumentScanner,
                contentDescription = "Open camera scanner",
                tint = Color.White,
                modifier = Modifier.size(30.dp)
            )
        }
    }
}

private enum class ScanStep { CAMERA, CROP, FILTER, REVIEW }

@Composable
fun ScannerFlowScreen(
    viewModel: PaperPilotViewModel,
    defaultOcrLang: String,
    onBack: () -> Unit,
    onPdfCreated: (DocumentEntity) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val captureExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose { captureExecutor.shutdown() }
    }

    var step by remember { mutableStateOf(ScanStep.CAMERA) }
    var batchPages by remember { mutableStateOf<List<CapturedScanPage>>(emptyList()) }
    var workingRawFile by remember { mutableStateOf<File?>(null) }

    // CameraX controls & capture state
    var hasCameraPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var cameraAvailable by remember { mutableStateOf(true) }
    var cameraErrorMessage by remember { mutableStateOf<String?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var capabilities by remember { mutableStateOf(CameraCapabilities()) }
    var useFrontCamera by remember { mutableStateOf(false) }
    var flashMode by remember { mutableStateOf(FlashModeOption.AUTO) }
    var torchEnabled by remember { mutableStateOf(false) }
    var zoomRatio by remember { mutableFloatStateOf(1f) }
    var batchCaptureMode by remember { mutableStateOf(true) } // true = rapid multi-page batch capture
    var isCapturing by remember { mutableStateOf(false) }
    var shutterFlash by remember { mutableStateOf(false) }

    // Crop corners (normalized 0..1)
    var tl by remember { mutableStateOf(Offset(0.06f, 0.06f)) }
    var tr by remember { mutableStateOf(Offset(0.94f, 0.06f)) }
    var bl by remember { mutableStateOf(Offset(0.06f, 0.94f)) }
    var br by remember { mutableStateOf(Offset(0.94f, 0.94f)) }
    var cropRotation by remember { mutableFloatStateOf(0f) }

    // Filter state
    var selectedFilter by remember { mutableStateOf(ScanFilter.AUTO_ENHANCE) }
    var brightness by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(1f) }
    var isSavingPage by remember { mutableStateOf(false) }

    // Review & single-PDF export options
    var pdfTitle by remember {
        mutableStateOf("Scan_${SimpleDateFormat("MMMdd_HHmm", Locale.US).format(Date())}")
    }
    var pageSize by remember { mutableStateOf(PageSizeOption.A4) }
    var pageMargin by remember { mutableStateOf(MarginOption.NONE) }
    var enableOcr by remember { mutableStateOf(true) }
    var ocrLang by remember { mutableStateOf(defaultOcrLang) }
    var isCreatingPdf by remember { mutableStateOf(false) }

    LaunchedEffect(shutterFlash) {
        if (shutterFlash) {
            delay(160)
            shutterFlash = false
        }
    }

    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPerm = granted
        if (granted) {
            cameraAvailable = true
            cameraErrorMessage = null
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPerm) permLauncher.launch(Manifest.permission.CAMERA)
    }

    suspend fun autoEnhanceToPage(rawFile: File): CapturedScanPage? = withContext(Dispatchers.IO) {
        val decoded = PdfRenderHelper.decodeSampledBitmap(rawFile, 1600) ?: return@withContext null
        val enhanced = PdfRenderHelper.applyScanTransform(
            src = decoded,
            filter = ScanFilter.AUTO_ENHANCE
        )
        val processedFile = File(viewModel.repository.scansDir, "processed_${System.currentTimeMillis()}_${rawFile.name}")
        FileOutputStream(processedFile).use { enhanced.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        if (enhanced !== decoded) enhanced.recycle()
        decoded.recycle()
        CapturedScanPage(
            rawFile = rawFile,
            processedFile = processedFile,
            filter = ScanFilter.AUTO_ENHANCE
        )
    }

    // Zero-permission Android Photo Picker supporting multi-image selection into one PDF
    val galleryImport = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20)
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            isCapturing = true
            val importedFiles = withContext(Dispatchers.IO) {
                viewModel.repository.scansDir.mkdirs()
                uris.mapIndexedNotNull { idx, uri ->
                    val raw = File(viewModel.repository.scansDir, "import_${System.currentTimeMillis()}_$idx.jpg")
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            raw.outputStream().use { input.copyTo(it) }
                        }
                        if (raw.exists() && raw.length() > 0L) raw else null
                    }.getOrNull()
                }
            }
            if (importedFiles.isEmpty()) {
                isCapturing = false
                snackbarHostState.showSnackbar("Could not read selected image(s).")
                return@launch
            }
            if (importedFiles.size == 1 && !batchCaptureMode) {
                workingRawFile = importedFiles.first()
                tl = Offset(0.06f, 0.06f); tr = Offset(0.94f, 0.06f)
                bl = Offset(0.06f, 0.94f); br = Offset(0.94f, 0.94f)
                cropRotation = 0f
                isCapturing = false
                step = ScanStep.CROP
            } else {
                val newPages = importedFiles.mapNotNull { autoEnhanceToPage(it) }
                batchPages = batchPages + newPages
                isCapturing = false
                if (newPages.isNotEmpty()) {
                    step = ScanStep.REVIEW
                }
            }
        }
    }

    BackHandler {
        when (step) {
            ScanStep.CAMERA -> onBack()
            ScanStep.CROP -> step = ScanStep.CAMERA
            ScanStep.FILTER -> step = ScanStep.CROP
            ScanStep.REVIEW -> if (!isCreatingPdf) step = ScanStep.CAMERA
        }
    }

    when (step) {
        ScanStep.CAMERA -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                if (hasCameraPerm && cameraAvailable) {
                    CameraXDocumentPreview(
                        useFrontCamera = useFrontCamera,
                        flashMode = flashMode,
                        torchEnabled = torchEnabled,
                        zoomRatio = zoomRatio,
                        onImageCaptureReady = { imageCapture = it },
                        onCapabilitiesChanged = { caps ->
                            capabilities = caps
                            if (!caps.hasFlashUnit) torchEnabled = false
                            if (zoomRatio > caps.maxZoomRatio) zoomRatio = 1f
                        },
                        onCameraUnavailable = { reason ->
                            cameraAvailable = false
                            cameraErrorMessage = reason
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    DocumentGuideOverlay(shutterFlashActive = shutterFlash)
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp)
                    ) {
                        Icon(
                            Icons.Rounded.CameraAlt,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.78f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = if (!hasCameraPerm) {
                                "Camera permission required"
                            } else {
                                "Camera unavailable on this device"
                            },
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = cameraErrorMessage
                                ?: "Allow camera access to scan documents live, or import photos from your gallery to combine into a single PDF.",
                            color = Color.White.copy(alpha = 0.72f),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(20.dp))
                        if (!hasCameraPerm) {
                            Button(
                                onClick = { permLauncher.launch(Manifest.permission.CAMERA) },
                                shape = CircleShape,
                                modifier = Modifier.testTag("scanner_grant_permission_button")
                            ) {
                                Text("Grant camera permission")
                            }
                            Spacer(Modifier.height(10.dp))
                        }
                        OutlinedButton(
                            onClick = {
                                galleryImport.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            shape = CircleShape,
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.55f))
                        ) {
                            Icon(Icons.Rounded.PhotoLibrary, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Import photos from gallery", color = Color.White)
                        }
                    }
                }

                // Top CameraX bar
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f))
                            .testTag("scanner_close_button")
                    ) {
                        Icon(Icons.Rounded.Close, "Close scanner", tint = Color.White)
                    }

                    // Single vs Batch capture mode pill
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
                    ) {
                        Row(modifier = Modifier.padding(3.dp)) {
                            listOf(true to "Batch", false to "Single").forEach { (isBatch, label) ->
                                val selected = batchCaptureMode == isBatch
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(if (selected) TintScan else Color.Transparent)
                                        .clickable { batchCaptureMode = isBatch }
                                        .padding(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = label,
                                        color = if (selected) Color(0xFF0A1F1F) else Color.White,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (hasCameraPerm && cameraAvailable && capabilities.hasFlashUnit) {
                            IconButton(
                                onClick = { torchEnabled = !torchEnabled },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.55f))
                            ) {
                                Icon(
                                    imageVector = if (torchEnabled) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                                    contentDescription = if (torchEnabled) "Turn torch off" else "Turn torch on",
                                    tint = if (torchEnabled) BrandAmber else Color.White
                                )
                            }
                        }
                        IconButton(
                            onClick = { flashMode = flashMode.next() },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f))
                                .testTag("scanner_flash_button")
                        ) {
                            Icon(
                                imageVector = flashMode.icon,
                                contentDescription = flashMode.label,
                                tint = if (flashMode == FlashModeOption.ON) BrandAmber else Color.White
                            )
                        }
                        if (hasCameraPerm && cameraAvailable && capabilities.hasBackCamera && capabilities.hasFrontCamera) {
                            IconButton(
                                onClick = {
                                    useFrontCamera = !useFrontCamera
                                    torchEnabled = false
                                    zoomRatio = 1f
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.55f))
                            ) {
                                Icon(Icons.Rounded.Cameraswitch, "Switch camera", tint = Color.White)
                            }
                        }
                    }
                }

                // Bottom overlay: optional zoom chip + hint + Gallery | Shutter | Batch stack
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 22.dp, start = 20.dp, end = 20.dp)
                ) {
                    if (hasCameraPerm && cameraAvailable && capabilities.maxZoomRatio >= 2f) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.55f),
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            Row(modifier = Modifier.padding(3.dp)) {
                                listOf(1f to "1x", 2f to "2x").forEach { (z, lbl) ->
                                    val selected = (zoomRatio - z).let { it > -0.1f && it < 0.1f }
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(if (selected) Color.White.copy(alpha = 0.24f) else Color.Transparent)
                                            .clickable { zoomRatio = z }
                                            .padding(horizontal = 12.dp, vertical = 4.dp)
                                    ) {
                                        Text(lbl, color = Color.White, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }

                    Text(
                        text = if (batchCaptureMode) {
                            "Tap preview to focus • Capture multiple pages, then tap count to create PDF"
                        } else {
                            "Tap preview to focus • Capture a page to crop & enhance"
                        },
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 14.dp)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Gallery multi-photo import
                        IconButton(
                            onClick = {
                                galleryImport.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            enabled = !isCapturing,
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.18f))
                                .testTag("scanner_gallery_button")
                        ) {
                            Icon(Icons.Rounded.PhotoLibrary, "Import from gallery", tint = Color.White)
                        }

                        // CameraX Shutter button
                        val shutterEnabled = hasCameraPerm && cameraAvailable && imageCapture != null && !isCapturing
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .border(4.dp, Color.White.copy(alpha = if (shutterEnabled) 1f else 0.4f), CircleShape)
                                .padding(6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (shutterEnabled) {
                                        Brush.linearGradient(listOf(BrandCoral, BrandAmber))
                                    } else {
                                        Brush.linearGradient(listOf(Color.Gray, Color.DarkGray))
                                    }
                                )
                                .clickable(enabled = shutterEnabled) {
                                    val cap = imageCapture ?: return@clickable
                                    isCapturing = true
                                    shutterFlash = true
                                    viewModel.repository.scansDir.mkdirs()
                                    val outFile = File(viewModel.repository.scansDir, "cam_${System.currentTimeMillis()}.jpg")
                                    val opts = ImageCapture.OutputFileOptions.Builder(outFile).build()
                                    cap.takePicture(
                                        opts,
                                        captureExecutor,
                                        object : ImageCapture.OnImageSavedCallback {
                                            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                                scope.launch {
                                                    if (batchCaptureMode) {
                                                        val page = autoEnhanceToPage(outFile)
                                                        if (page != null) {
                                                            batchPages = batchPages + page
                                                        } else {
                                                            snackbarHostState.showSnackbar("Failed to process captured photo.")
                                                        }
                                                        isCapturing = false
                                                    } else {
                                                        workingRawFile = outFile
                                                        tl = Offset(0.06f, 0.06f); tr = Offset(0.94f, 0.06f)
                                                        bl = Offset(0.06f, 0.94f); br = Offset(0.94f, 0.94f)
                                                        cropRotation = 0f
                                                        isCapturing = false
                                                        step = ScanStep.CROP
                                                    }
                                                }
                                            }

                                            override fun onError(exception: ImageCaptureException) {
                                                scope.launch {
                                                    isCapturing = false
                                                    snackbarHostState.showSnackbar(
                                                        "Capture failed: ${exception.message ?: "Camera error"}"
                                                    )
                                                }
                                            }
                                        }
                                    )
                                }
                                .testTag("scanner_shutter_button")
                        ) {
                            if (isCapturing) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        // Batch page thumbnail stack & review trigger
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(58.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .border(
                                    width = 2.dp,
                                    color = if (batchPages.isNotEmpty()) TintScan else Color.White.copy(alpha = 0.28f),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .background(Color.White.copy(alpha = 0.14f))
                                .clickable(enabled = batchPages.isNotEmpty() && !isCapturing) {
                                    step = ScanStep.REVIEW
                                }
                                .testTag("scanner_batch_review_button")
                        ) {
                            val latest = batchPages.lastOrNull()
                            if (latest != null) {
                                AsyncImage(
                                    model = latest.processedFile,
                                    contentDescription = "Review ${batchPages.size} captured pages",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(BrandCoral)
                                ) {
                                    Text(
                                        text = "${batchPages.size}",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                Text(
                                    text = "0",
                                    color = Color.White.copy(alpha = 0.8f),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    }
                }

                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 128.dp)
                )
            }
        }

        ScanStep.CROP -> {
            ScanCropStep(
                rawFile = workingRawFile,
                cropRotation = cropRotation,
                tl = tl,
                tr = tr,
                bl = bl,
                br = br,
                onUpdateCorners = { nTl, nTr, nBl, nBr ->
                    tl = nTl; tr = nTr; bl = nBl; br = nBr
                },
                onRotate = { cropRotation = (cropRotation + 90f) % 360f },
                onBack = { step = ScanStep.CAMERA },
                onNext = { step = ScanStep.FILTER }
            )
        }

        ScanStep.FILTER -> {
            ScanFilterStep(
                rawFile = workingRawFile,
                cropRotation = cropRotation,
                tl = tl,
                tr = tr,
                bl = bl,
                br = br,
                selectedFilter = selectedFilter,
                brightness = brightness,
                contrast = contrast,
                isSavingPage = isSavingPage,
                onFilterChange = { selectedFilter = it },
                onBrightnessChange = { brightness = it },
                onContrastChange = { contrast = it },
                onBack = { step = ScanStep.CROP },
                onKeepPage = { bmp ->
                    val raw = workingRawFile ?: return@ScanFilterStep
                    isSavingPage = true
                    scope.launch {
                        val out = withContext(Dispatchers.IO) {
                            viewModel.repository.scansDir.mkdirs()
                            val file = File(viewModel.repository.scansDir, "processed_${System.currentTimeMillis()}.jpg")
                            FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                            file
                        }
                        batchPages = batchPages + CapturedScanPage(raw, out, selectedFilter, brightness, contrast, cropRotation)
                        isSavingPage = false
                        step = ScanStep.REVIEW
                    }
                }
            )
        }

        ScanStep.REVIEW -> {
            ScanReviewStep(
                batchPages = batchPages,
                pdfTitle = pdfTitle,
                pageSize = pageSize,
                margin = pageMargin,
                enableOcr = enableOcr,
                ocrLang = ocrLang,
                isCreatingPdf = isCreatingPdf,
                onPdfTitleChange = { pdfTitle = it },
                onPageSizeChange = { pageSize = it },
                onMarginChange = { pageMargin = it },
                onToggleOcr = { enableOcr = it },
                onOcrLangChange = { ocrLang = it },
                onMovePage = { from, to ->
                    if (from in batchPages.indices && to in batchPages.indices) {
                        val mutable = batchPages.toMutableList()
                        val moved = mutable.removeAt(from)
                        mutable.add(to, moved)
                        batchPages = mutable
                    }
                },
                onDeletePage = { idx ->
                    batchPages = batchPages.filterIndexed { i, _ -> i != idx }
                },
                onAddMorePages = { step = ScanStep.CAMERA },
                onCreatePdf = {
                    isCreatingPdf = true
                    viewModel.saveScannedBatchToPdf(
                        pageFiles = batchPages.map { it.processedFile },
                        filterNames = batchPages.map { it.filter.label },
                        enableOcr = enableOcr,
                        ocrLang = ocrLang,
                        pdfTitle = pdfTitle,
                        pageSize = pageSize,
                        margin = pageMargin,
                        onCreated = { createdDoc ->
                            isCreatingPdf = false
                            onPdfCreated(createdDoc)
                        },
                        onError = { msg ->
                            isCreatingPdf = false
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                        }
                    )
                }
            )
        }
    }
}
