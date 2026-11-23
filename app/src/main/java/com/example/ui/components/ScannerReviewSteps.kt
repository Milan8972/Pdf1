package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.engine.MarginOption
import com.example.engine.PageSizeOption
import com.example.engine.PdfRenderHelper
import com.example.engine.ScanFilter
import com.example.ui.theme.LocalPaperPilotColors
import com.example.ui.theme.TintScan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class CapturedScanPage(
    val rawFile: File,
    val processedFile: File,
    val filter: ScanFilter = ScanFilter.AUTO_ENHANCE,
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val rotation: Float = 0f
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanCropStep(
    rawFile: File?,
    cropRotation: Float,
    tl: Offset,
    tr: Offset,
    bl: Offset,
    br: Offset,
    onUpdateCorners: (Offset, Offset, Offset, Offset) -> Unit,
    onRotate: () -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    val rawBmp by produceState<Bitmap?>(initialValue = null, rawFile?.absolutePath, cropRotation) {
        value = withContext(Dispatchers.Default) {
            rawFile?.let { f ->
                PdfRenderHelper.decodeSampledBitmap(f, 1200)?.let { decoded ->
                    if (cropRotation != 0f) {
                        PdfRenderHelper.applyScanTransform(decoded, ScanFilter.ORIGINAL, rotationDegrees = cropRotation)
                    } else decoded
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Crop page") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back to camera")
                    }
                },
                actions = {
                    TextButton(
                        onClick = onNext,
                        enabled = rawBmp != null,
                        modifier = Modifier.testTag("scanner_crop_next_button")
                    ) { Text("Next") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF14111B))
                    .padding(10.dp)
            ) {
                val bmp = rawBmp
                if (bmp == null) {
                    CircularProgressIndicator(color = TintScan)
                } else {
                    val imgAspect = (bmp.width.toFloat() / bmp.height.coerceAtLeast(1)).coerceIn(0.4f, 2.4f)
                    BoxWithConstraints(
                        modifier = Modifier
                            .aspectRatio(imgAspect)
                            .clip(RoundedCornerShape(10.dp))
                    ) {
                        val wPx = with(LocalDensity.current) { maxWidth.toPx() }.coerceAtLeast(1f)
                        val hPx = with(LocalDensity.current) { maxHeight.toPx() }.coerceAtLeast(1f)
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Captured page",
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize()
                        )
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val p = Path().apply {
                                moveTo(tl.x * size.width, tl.y * size.height)
                                lineTo(tr.x * size.width, tr.y * size.height)
                                lineTo(br.x * size.width, br.y * size.height)
                                lineTo(bl.x * size.width, bl.y * size.height)
                                close()
                            }
                            drawPath(p, TintScan.copy(alpha = 0.12f))
                            drawPath(p, TintScan, style = Stroke(3.dp.toPx()))
                        }
                        CropCornerHandle(tl, wPx, hPx) { onUpdateCorners(it, tr, bl, br) }
                        CropCornerHandle(tr, wPx, hPx) { onUpdateCorners(tl, it, bl, br) }
                        CropCornerHandle(bl, wPx, hPx) { onUpdateCorners(tl, tr, it, br) }
                        CropCornerHandle(br, wPx, hPx) { onUpdateCorners(tl, tr, bl, it) }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                OutlinedButton(
                    onClick = {
                        onUpdateCorners(Offset(0f, 0f), Offset(1f, 0f), Offset(0f, 1f), Offset(1f, 1f))
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
                        onUpdateCorners(Offset(0.06f, 0.06f), Offset(0.94f, 0.06f), Offset(0.06f, 0.94f), Offset(0.94f, 0.94f))
                    },
                    shape = CircleShape,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.AutoFixHigh, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Auto", maxLines = 1)
                }
                OutlinedButton(
                    onClick = onRotate,
                    shape = CircleShape,
                    modifier = Modifier.weight(0.9f)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.RotateRight, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Rotate", maxLines = 1)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanFilterStep(
    rawFile: File?,
    cropRotation: Float,
    tl: Offset,
    tr: Offset,
    bl: Offset,
    br: Offset,
    selectedFilter: ScanFilter,
    brightness: Float,
    contrast: Float,
    isSavingPage: Boolean,
    onFilterChange: (ScanFilter) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onContrastChange: (Float) -> Unit,
    onBack: () -> Unit,
    onKeepPage: (Bitmap) -> Unit
) {
    val filteredBmp by produceState<Bitmap?>(
        initialValue = null,
        rawFile?.absolutePath,
        selectedFilter,
        brightness,
        contrast,
        cropRotation,
        tl,
        tr,
        bl,
        br
    ) {
        value = withContext(Dispatchers.Default) {
            rawFile?.let { f ->
                PdfRenderHelper.decodeSampledBitmap(f, 1200)?.let { src ->
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
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Enhance & Filter") },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !isSavingPage) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back to crop")
                    }
                },
                actions = {
                    Button(
                        onClick = { filteredBmp?.let(onKeepPage) },
                        enabled = filteredBmp != null && !isSavingPage,
                        shape = CircleShape,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("scanner_keep_page_button")
                    ) {
                        if (isSavingPage) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Icon(Icons.Rounded.Check, null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(4.dp))
                        Text(if (isSavingPage) "Saving" else "Keep")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF14111B))
            ) {
                val bmp = filteredBmp
                if (bmp == null) {
                    CircularProgressIndicator(color = TintScan)
                } else {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Filtered scan preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ScanFilter.entries) { f ->
                    FilterChip(
                        selected = selectedFilter == f,
                        onClick = { onFilterChange(f) },
                        label = { Text(f.label) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Brightness", style = MaterialTheme.typography.labelMedium)
            Slider(value = brightness, onValueChange = onBrightnessChange, valueRange = -0.35f..0.35f)
            Text("Contrast", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = contrast,
                onValueChange = onContrastChange,
                valueRange = 0.6f..1.8f,
                modifier = Modifier.navigationBarsPadding()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanReviewStep(
    batchPages: List<CapturedScanPage>,
    pdfTitle: String,
    pageSize: PageSizeOption,
    margin: MarginOption,
    enableOcr: Boolean,
    ocrLang: String,
    isCreatingPdf: Boolean,
    onPdfTitleChange: (String) -> Unit,
    onPageSizeChange: (PageSizeOption) -> Unit,
    onMarginChange: (MarginOption) -> Unit,
    onToggleOcr: (Boolean) -> Unit,
    onOcrLangChange: (String) -> Unit,
    onMovePage: (Int, Int) -> Unit,
    onDeletePage: (Int) -> Unit,
    onAddMorePages: () -> Unit,
    onCreatePdf: () -> Unit
) {
    val langs = listOf("English", "Spanish", "French", "German", "Hindi", "Japanese")
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Review (${batchPages.size} page${if (batchPages.size == 1) "" else "s"})")
                },
                navigationIcon = {
                    IconButton(onClick = onAddMorePages, enabled = !isCreatingPdf) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back to camera")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .navigationBarsPadding()
        ) {
            if (batchPages.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Text(
                        text = "No pages in batch yet. Tap Add page to scan or import images.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalPaperPilotColors.current.mutedText
                    )
                }
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                ) {
                    itemsIndexed(batchPages) { idx, page ->
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, LocalPaperPilotColors.current.subtleBorder),
                            modifier = Modifier.width(196.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                AsyncImage(
                                    model = page.processedFile,
                                    contentDescription = "Scanned page ${idx + 1}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                )
                                Spacer(Modifier.height(6.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    IconButton(
                                        onClick = { onMovePage(idx, idx - 1) },
                                        enabled = idx > 0 && !isCreatingPdf
                                    ) {
                                        Icon(Icons.Rounded.ArrowBackIosNew, "Move left", modifier = Modifier.size(16.dp))
                                    }
                                    Text("Page ${idx + 1}", style = MaterialTheme.typography.labelMedium)
                                    IconButton(
                                        onClick = { onMovePage(idx, idx + 1) },
                                        enabled = idx < batchPages.lastIndex && !isCreatingPdf
                                    ) {
                                        Icon(Icons.Rounded.ArrowForwardIos, "Move right", modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(
                                        onClick = { onDeletePage(idx) },
                                        enabled = !isCreatingPdf
                                    ) {
                                        Icon(
                                            Icons.Rounded.Delete,
                                            "Delete page",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = pdfTitle,
                    onValueChange = onPdfTitleChange,
                    label = { Text("PDF file name") },
                    suffix = { Text(".pdf") },
                    singleLine = true,
                    enabled = !isCreatingPdf,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("scanner_pdf_name_input")
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Page size:", style = MaterialTheme.typography.labelLarge)
                    PageSizeOption.entries.forEach { sizeOpt ->
                        FilterChip(
                            selected = pageSize == sizeOpt,
                            onClick = { onPageSizeChange(sizeOpt) },
                            enabled = !isCreatingPdf,
                            label = { Text(sizeOpt.label) }
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Margins:", style = MaterialTheme.typography.labelLarge)
                    listOf(MarginOption.NONE, MarginOption.SMALL).forEach { marginOpt ->
                        FilterChip(
                            selected = margin == marginOpt,
                            onClick = { onMarginChange(marginOpt) },
                            enabled = !isCreatingPdf,
                            label = { Text(marginOpt.label) }
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("OCR (make text searchable)", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Index scanned pages locally in $ocrLang",
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalPaperPilotColors.current.mutedText
                        )
                    }
                    Switch(
                        checked = enableOcr,
                        onCheckedChange = onToggleOcr,
                        enabled = !isCreatingPdf
                    )
                }

                if (enableOcr) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(langs) { l ->
                            FilterChip(
                                selected = ocrLang == l,
                                onClick = { onOcrLangChange(l) },
                                enabled = !isCreatingPdf,
                                label = { Text(l) }
                            )
                        }
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = onAddMorePages,
                    enabled = !isCreatingPdf,
                    shape = CircleShape,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                ) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Add page")
                }
                Button(
                    onClick = onCreatePdf,
                    enabled = batchPages.isNotEmpty() && !isCreatingPdf,
                    shape = CircleShape,
                    modifier = Modifier
                        .weight(1.45f)
                        .height(50.dp)
                        .testTag("scanner_create_pdf_button")
                ) {
                    if (isCreatingPdf) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Creating PDF...")
                    } else {
                        Icon(Icons.Rounded.PictureAsPdf, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Create single PDF")
                    }
                }
            }
        }
    }
}
