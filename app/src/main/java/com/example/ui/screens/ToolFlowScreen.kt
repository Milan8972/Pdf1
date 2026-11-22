package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.DocumentEntity
import com.example.data.DocumentSource
import com.example.engine.CompressionLevel
import com.example.engine.EditorTool
import com.example.engine.MarginOption
import com.example.engine.OrganizedPageSpec
import com.example.engine.PageNumberFormat
import com.example.engine.PageSizeOption
import com.example.engine.PdfRenderHelper
import com.example.engine.SplitMode
import com.example.engine.ToolEngine
import com.example.engine.ToolOptions
import com.example.engine.ToolOutputResult
import com.example.model.FileAcceptType
import com.example.model.ToolId
import com.example.ui.components.DocumentThumbnail
import com.example.ui.components.ProgressRing
import com.example.ui.theme.BrandAmber
import com.example.ui.theme.BrandCoral
import com.example.ui.theme.LocalPaperPilotColors
import com.example.ui.theme.TintOptimize
import com.example.ui.theme.TintScan
import com.example.viewmodel.PaperPilotViewModel
import java.io.File
import kotlin.math.roundToInt

private enum class ToolStep { PICK, OPTIONS, PROCESSING, RESULT, ERROR }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolFlowScreen(
    toolId: ToolId,
    preselectedIds: List<Long>,
    allDocuments: List<DocumentEntity>,
    viewModel: PaperPilotViewModel,
    onBack: () -> Unit,
    onOpenDocument: (DocumentEntity) -> Unit,
    onOpenEditor: (DocumentEntity, EditorTool) -> Unit,
    onShareDocument: (DocumentEntity) -> Unit
) {
    val context = LocalContext.current
    val extra = LocalPaperPilotColors.current

    var step by remember { mutableStateOf(ToolStep.PICK) }
    var selectedFiles by remember(preselectedIds) {
        mutableStateOf(
            if (preselectedIds.isNotEmpty()) allDocuments.filter { it.id in preselectedIds }
            else emptyList()
        )
    }
    var options by remember { mutableStateOf(ToolOptions()) }
    var progress by remember { mutableFloatStateOf(0f) }
    var progressLabel by remember { mutableStateOf("Preparing on-device engine...") }
    var outputResult by remember { mutableStateOf<ToolOutputResult?>(null) }
    var outputDoc by remember { mutableStateOf<DocumentEntity?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // SAF device pickers (zero storage permission!)
    val openPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importUris(uris, DocumentSource.OPENED) { imported ->
                selectedFiles = if (toolId.acceptType == FileAcceptType.PDF_MULTI) {
                    (selectedFiles + imported).distinctBy { it.id }
                } else {
                    imported.take(1)
                }
            }
        }
    }

    val pickImagesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(20)
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importUris(uris, DocumentSource.OPENED) { imported ->
                selectedFiles = if (toolId.acceptType == FileAcceptType.IMAGE_MULTI) {
                    (selectedFiles + imported).distinctBy { it.id }
                } else {
                    imported.take(1)
                }
            }
        }
    }

    val saveToDeviceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(outputDoc?.mimeType ?: "application/pdf")
    ) { uri ->
        val doc = outputDoc
        if (uri != null && doc != null) {
            val ok = viewModel.repository.exportToUri(doc, uri)
            viewModel.emitMessage(if (ok) "Saved ${doc.name} to device" else "Could not save file")
        }
    }

    BackHandler {
        when (step) {
            ToolStep.PICK -> onBack()
            ToolStep.OPTIONS -> step = ToolStep.PICK
            ToolStep.PROCESSING -> {
                viewModel.cancelActiveTool()
                step = ToolStep.OPTIONS
            }
            ToolStep.RESULT, ToolStep.ERROR -> onBack()
        }
    }

    val stepTitle = when (step) {
        ToolStep.PICK -> "1. Select files"
        ToolStep.OPTIONS -> "2. Configure options"
        ToolStep.PROCESSING -> "3. Processing"
        ToolStep.RESULT -> "4. Done"
        ToolStep.ERROR -> "Processing error"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(toolId.title, style = MaterialTheme.typography.titleLarge)
                        Text(stepTitle, style = MaterialTheme.typography.bodySmall, color = extra.mutedText)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (step == ToolStep.OPTIONS) step = ToolStep.PICK
                        else if (step == ToolStep.PROCESSING) {
                            viewModel.cancelActiveTool()
                            step = ToolStep.OPTIONS
                        } else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        when (step) {
            ToolStep.PICK -> {
                val eligibleDocs = remember(allDocuments, toolId) {
                    when (toolId.acceptType) {
                        FileAcceptType.IMAGE_SINGLE, FileAcceptType.IMAGE_MULTI ->
                            allDocuments.filter { it.mimeType.startsWith("image/") || it.mimeType.contains("pdf") }
                        else -> allDocuments
                    }
                }
                val isValidPick = when (toolId) {
                    ToolId.MERGE -> selectedFiles.size >= 2
                    else -> selectedFiles.isNotEmpty()
                }
                val totalSelectedBytes = selectedFiles.sumOf { it.sizeBytes }

                Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (toolId.isBasicConversion) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = BrandAmber.copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, BrandAmber.copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
                                        Icon(Icons.Rounded.WarningAmber, null, tint = BrandAmber, modifier = Modifier.size(20.dp))
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            "Basic conversion: runs 100% locally on-device using text & layout extraction.",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }

                        // Large-file >50MB safety banner
                        if (totalSelectedBytes > 50L * 1024L * 1024L) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
                                        Icon(Icons.Rounded.WarningAmber, null, tint = MaterialTheme.colorScheme.error)
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            "Large selection (>50 MB). paperpilot processes pages in chunks so your UI stays responsive.",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }

                        // Device picker zone
                        item {
                            Surface(
                                onClick = {
                                    when (toolId.acceptType) {
                                        FileAcceptType.IMAGE_SINGLE, FileAcceptType.IMAGE_MULTI ->
                                            pickImagesLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                        FileAcceptType.OFFICE_DOC ->
                                            openPdfLauncher.launch(arrayOf("text/*", "application/pdf", "application/msword", "application/vnd.openxmlformats-officedocument.*"))
                                        else ->
                                            openPdfLauncher.launch(arrayOf("application/pdf"))
                                    }
                                },
                                shape = RoundedCornerShape(22.dp),
                                color = toolId.category.tint.copy(alpha = 0.09f),
                                border = BorderStroke(1.5.dp, toolId.category.tint.copy(alpha = 0.45f)),
                                modifier = Modifier.fillMaxWidth().testTag("tool_pick_device_button")
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(22.dp)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(CircleShape)
                                            .background(toolId.category.tint.copy(alpha = 0.16f))
                                    ) {
                                        Icon(Icons.Rounded.UploadFile, null, tint = toolId.category.tint, modifier = Modifier.size(28.dp))
                                    }
                                    Text(
                                        text = if (toolId.acceptType == FileAcceptType.IMAGE_MULTI || toolId.acceptType == FileAcceptType.IMAGE_SINGLE)
                                            "Select images from device" else "Select files from device",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = toolId.shortDescription,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = extra.mutedText,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        item {
                            Text(
                                text = "Or pick from your paperpilot library",
                                style = MaterialTheme.typography.titleSmall,
                                color = extra.mutedText
                            )
                        }

                        items(eligibleDocs, key = { it.id }) { doc ->
                            val isChosen = selectedFiles.any { it.id == doc.id }
                            Surface(
                                onClick = {
                                    selectedFiles = if (toolId.acceptType == FileAcceptType.PDF_MULTI || toolId.acceptType == FileAcceptType.IMAGE_MULTI) {
                                        if (isChosen) selectedFiles.filterNot { it.id == doc.id }
                                        else selectedFiles + doc
                                    } else {
                                        if (isChosen) emptyList() else listOf(doc)
                                    }
                                },
                                shape = RoundedCornerShape(18.dp),
                                color = if (isChosen) MaterialTheme.colorScheme.primaryContainer.copy(0.45f) else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, if (isChosen) MaterialTheme.colorScheme.primary else extra.subtleBorder),
                                modifier = Modifier.fillMaxWidth().testTag("tool_library_item_${doc.id}")
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
                                    DocumentThumbnail(doc, Modifier.size(44.dp, 54.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(doc.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            "${PdfRenderHelper.formatFileSize(doc.sizeBytes)} • ${doc.pageCount}p",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = extra.mutedText
                                        )
                                    }
                                    if (isChosen) {
                                        Icon(Icons.Rounded.CheckCircle, "Selected", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }

                    // Bottom selected-files tray + Continue button
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, extra.subtleBorder),
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.navigationBarsPadding().padding(16.dp)
                        ) {
                            AnimatedVisibility(visible = selectedFiles.isNotEmpty()) {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    itemsIndexed(selectedFiles, key = { _, d -> d.id }) { idx, doc ->
                                        Surface(
                                            shape = RoundedCornerShape(14.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            modifier = Modifier.width(if (toolId.supportsReorder) 196.dp else 156.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                                                DocumentThumbnail(doc, Modifier.size(30.dp, 38.dp))
                                                Spacer(Modifier.width(8.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(doc.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    Text(PdfRenderHelper.formatFileSize(doc.sizeBytes), style = MaterialTheme.typography.labelSmall, color = extra.mutedText)
                                                }
                                                if (toolId.supportsReorder && selectedFiles.size > 1) {
                                                    IconButton(
                                                        onClick = {
                                                            if (idx > 0) {
                                                                val m = selectedFiles.toMutableList()
                                                                val item = m.removeAt(idx)
                                                                m.add(idx - 1, item)
                                                                selectedFiles = m
                                                            }
                                                        },
                                                        modifier = Modifier.size(26.dp)
                                                    ) { Icon(Icons.Rounded.ArrowUpward, "Move earlier", modifier = Modifier.size(15.dp)) }
                                                }
                                                IconButton(
                                                    onClick = { selectedFiles = selectedFiles.filterNot { it.id == doc.id } },
                                                    modifier = Modifier.size(26.dp)
                                                ) { Icon(Icons.Rounded.Close, "Remove ${doc.name}", modifier = Modifier.size(16.dp)) }
                                            }
                                        }
                                    }
                                }
                            }
                            Button(
                                onClick = {
                                    if (toolId.opensEditorDirectly && selectedFiles.isNotEmpty()) {
                                        val initialEditorTool = when (toolId) {
                                            ToolId.ANNOTATE -> EditorTool.HIGHLIGHT
                                            ToolId.FILL_SIGN -> EditorTool.SIGNATURE
                                            else -> EditorTool.EDIT_CONTENT
                                        }
                                        onOpenEditor(selectedFiles.first(), initialEditorTool)
                                    } else {
                                        // Pre-populate organized pages if needed
                                        if (toolId == ToolId.ORGANIZE_PAGES && selectedFiles.isNotEmpty()) {
                                            val count = selectedFiles.first().pageCount.coerceAtLeast(1)
                                            options = options.copy(organizedPages = (0 until count).map { OrganizedPageSpec(it) })
                                        }
                                        step = ToolStep.OPTIONS
                                    }
                                },
                                enabled = isValidPick,
                                shape = CircleShape,
                                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("tool_continue_button")
                            ) {
                                Text(
                                    if (toolId == ToolId.MERGE && selectedFiles.size < 2) "Select at least 2 PDFs"
                                    else if (toolId.opensEditorDirectly) "Open in Editor"
                                    else stringResource(R.string.action_continue)
                                )
                            }
                        }
                    }
                }
            }

            ToolStep.OPTIONS -> {
                val validationError = ToolEngine.validate(toolId, selectedFiles, options)
                Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                    LazyColumn(
                        contentPadding = PaddingValues(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        item {
                            ToolOptionsPanel(
                                toolId = toolId,
                                selectedFiles = selectedFiles,
                                options = options,
                                onOptionsChange = { options = it },
                                onReorderFiles = { selectedFiles = it }
                            )
                        }
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, extra.subtleBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.navigationBarsPadding().padding(16.dp)) {
                            if (validationError != null) {
                                Text(
                                    text = validationError,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                            Button(
                                onClick = {
                                    step = ToolStep.PROCESSING
                                    progress = 0.05f
                                    progressLabel = "Starting ${toolId.title}..."
                                    viewModel.runTool(
                                        tool = toolId,
                                        selectedFiles = selectedFiles,
                                        options = options,
                                        onProgress = { p, msg ->
                                            progress = p
                                            progressLabel = msg
                                        },
                                        onSuccess = { res, saved ->
                                            outputResult = res
                                            outputDoc = saved
                                            step = ToolStep.RESULT
                                        },
                                        onError = { err ->
                                            if (err == "Cancelled") step = ToolStep.OPTIONS
                                            else {
                                                errorMessage = err
                                                step = ToolStep.ERROR
                                            }
                                        }
                                    )
                                },
                                enabled = validationError == null,
                                shape = CircleShape,
                                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("tool_run_button")
                            ) {
                                Text("Run ${toolId.title}")
                            }
                        }
                    }
                }
            }

            ToolStep.PROCESSING -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize().padding(padding).padding(28.dp)
                ) {
                    ProgressRing(progress = progress, size = 156.dp)
                    Spacer(Modifier.height(24.dp))
                    Text(progressLabel, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Running locally on your Android device...",
                        style = MaterialTheme.typography.bodySmall,
                        color = extra.mutedText
                    )
                    Spacer(Modifier.height(28.dp))
                    OutlinedButton(
                        onClick = {
                            viewModel.cancelActiveTool()
                            step = ToolStep.OPTIONS
                        },
                        shape = CircleShape,
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            }

            ToolStep.RESULT -> {
                val res = outputResult
                val doc = outputDoc
                LazyColumn(
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxSize().padding(padding)
                ) {
                    item {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .background(TintOptimize.copy(alpha = 0.16f))
                        ) {
                            Icon(Icons.Rounded.CheckCircle, null, tint = TintOptimize, modifier = Modifier.size(46.dp))
                        }
                    }
                    item {
                        Text("Ready!", style = MaterialTheme.typography.headlineLarge)
                        Spacer(Modifier.height(6.dp))
                        Surface(
                            shape = CircleShape,
                            color = TintScan.copy(alpha = 0.14f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Rounded.Shield, null, tint = TintScan, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    stringResource(R.string.processed_on_device),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TintScan
                                )
                            }
                        }
                    }

                    if (doc != null && res != null) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, extra.subtleBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(14.dp)) {
                                    DocumentThumbnail(doc, Modifier.size(56.dp, 70.dp))
                                    Spacer(Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(doc.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            "${PdfRenderHelper.formatFileSize(res.afterSizeBytes)} • ${res.pageCount}p",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = extra.mutedText
                                        )
                                        if (res.summaryNote.isNotBlank()) {
                                            Spacer(Modifier.height(4.dp))
                                            Text(res.summaryNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                            }
                        }

                        // Before/After size card for Compress tools
                        if (toolId == ToolId.COMPRESS || toolId == ToolId.COMPRESS_IMAGE) {
                            val savedPct = if (res.beforeSizeBytes > 0) {
                                (((res.beforeSizeBytes - res.afterSizeBytes).toFloat() / res.beforeSizeBytes) * 100).roundToInt()
                            } else 0
                            item {
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = TintOptimize.copy(alpha = 0.12f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(14.dp)
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("Before", style = MaterialTheme.typography.labelSmall, color = extra.mutedText)
                                            Text(PdfRenderHelper.formatFileSize(res.beforeSizeBytes), style = MaterialTheme.typography.titleMedium)
                                        }
                                        Text("→", style = MaterialTheme.typography.headlineSmall, color = TintOptimize)
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("After", style = MaterialTheme.typography.labelSmall, color = extra.mutedText)
                                            Text(PdfRenderHelper.formatFileSize(res.afterSizeBytes), style = MaterialTheme.typography.titleMedium, color = TintOptimize)
                                        }
                                        Surface(shape = CircleShape, color = TintOptimize) {
                                            Text(
                                                text = if (savedPct > 0) "-$savedPct%" else "Optimized",
                                                color = Color.White,
                                                style = MaterialTheme.typography.labelMedium,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Extracted text preview + copy to clipboard
                        if (!res.extractedText.isNullOrBlank() && (toolId == ToolId.EXTRACT_TEXT || toolId == ToolId.OCR)) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Extracted text preview", style = MaterialTheme.typography.titleSmall)
                                            OutlinedButton(
                                                onClick = {
                                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                    cm.setPrimaryClip(ClipData.newPlainText("paperpilot_text", res.extractedText))
                                                    viewModel.emitMessage("Copied text to clipboard")
                                                },
                                                shape = CircleShape
                                            ) {
                                                Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text("Copy")
                                            }
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            text = res.extractedText.take(600),
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 8,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        // Action buttons
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                if (doc.mimeType.contains("pdf")) {
                                    Button(
                                        onClick = { onOpenDocument(doc) },
                                        shape = CircleShape,
                                        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("result_open_button")
                                    ) {
                                        Icon(Icons.Rounded.OpenInNew, null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(stringResource(R.string.action_open))
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                    OutlinedButton(
                                        onClick = { saveToDeviceLauncher.launch(doc.name) },
                                        shape = CircleShape,
                                        modifier = Modifier.weight(1f).height(48.dp)
                                    ) {
                                        Icon(Icons.Rounded.Download, null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Save")
                                    }
                                    OutlinedButton(
                                        onClick = { onShareDocument(doc) },
                                        shape = CircleShape,
                                        modifier = Modifier.weight(1f).height(48.dp)
                                    ) {
                                        Icon(Icons.Rounded.Share, null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(stringResource(R.string.action_share))
                                    }
                                }
                                OutlinedButton(
                                    onClick = {
                                        selectedFiles = emptyList()
                                        outputResult = null
                                        outputDoc = null
                                        step = ToolStep.PICK
                                    },
                                    shape = CircleShape,
                                    modifier = Modifier.fillMaxWidth().height(48.dp)
                                ) {
                                    Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.action_process_another))
                                }
                            }
                        }
                    }
                }
            }

            ToolStep.ERROR -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize().padding(padding).padding(28.dp)
                ) {
                    Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("Something went wrong", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(6.dp))
                    Text(errorMessage ?: "Unable to complete operation.", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = extra.mutedText)
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = { step = ToolStep.OPTIONS }, shape = CircleShape, modifier = Modifier.height(48.dp)) {
                        Text("Retry")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolOptionsPanel(
    toolId: ToolId,
    selectedFiles: List<DocumentEntity>,
    options: ToolOptions,
    onOptionsChange: (ToolOptions) -> Unit,
    onReorderFiles: (List<DocumentEntity>) -> Unit
) {
    val extra = LocalPaperPilotColors.current
    val firstDoc = selectedFiles.firstOrNull()

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        when (toolId) {
            ToolId.MERGE -> {
                Text("Merge order", style = MaterialTheme.typography.titleMedium)
                selectedFiles.forEachIndexed { idx, doc ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, extra.subtleBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
                            Text("${idx + 1}.", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Text(doc.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            IconButton(
                                onClick = {
                                    if (idx > 0) {
                                        val m = selectedFiles.toMutableList()
                                        val it = m.removeAt(idx)
                                        m.add(idx - 1, it)
                                        onReorderFiles(m)
                                    }
                                },
                                enabled = idx > 0
                            ) { Icon(Icons.Rounded.ArrowUpward, "Move up") }
                            IconButton(
                                onClick = {
                                    if (idx < selectedFiles.lastIndex) {
                                        val m = selectedFiles.toMutableList()
                                        val it = m.removeAt(idx)
                                        m.add(idx + 1, it)
                                        onReorderFiles(m)
                                    }
                                },
                                enabled = idx < selectedFiles.lastIndex
                            ) { Icon(Icons.Rounded.ArrowDownward, "Move down") }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Add bookmarks per file", style = MaterialTheme.typography.titleSmall)
                        Text("Record each source document boundary in output metadata", style = MaterialTheme.typography.bodySmall, color = extra.mutedText)
                    }
                    Switch(checked = options.addBookmarksPerFile, onCheckedChange = { onOptionsChange(options.copy(addBookmarksPerFile = it)) })
                }
            }

            ToolId.SPLIT -> {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SplitMode.entries.forEachIndexed { idx, mode ->
                        SegmentedButton(
                            selected = options.splitMode == mode,
                            onClick = { onOptionsChange(options.copy(splitMode = mode)) },
                            shape = SegmentedButtonDefaults.itemShape(idx, SplitMode.entries.size),
                            label = { Text(mode.label, maxLines = 1) }
                        )
                    }
                }
                when (options.splitMode) {
                    SplitMode.RANGES -> {
                        OutlinedTextField(
                            value = options.splitRangesText,
                            onValueChange = { onOptionsChange(options.copy(splitRangesText = it)) },
                            label = { Text("Page ranges (e.g. 1-2, 3, 4-5)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    SplitMode.EVERY_N -> {
                        Text("Split every ${options.splitEveryN} page(s)", style = MaterialTheme.typography.titleSmall)
                        Slider(
                            value = options.splitEveryN.toFloat(),
                            onValueChange = { onOptionsChange(options.copy(splitEveryN = it.roundToInt().coerceAtLeast(1))) },
                            valueRange = 1f..(firstDoc?.pageCount?.coerceAtLeast(2)?.toFloat() ?: 5f)
                        )
                    }
                    SplitMode.VISUAL -> {
                        val total = firstDoc?.pageCount?.coerceAtLeast(1) ?: 1
                        Text("Tap pages to extract (${options.selectedPages.size} selected)", style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            (0 until total.coerceAtMost(12)).forEach { p ->
                                val picked = p in options.selectedPages
                                FilterChip(
                                    selected = picked,
                                    onClick = {
                                        val next = if (picked) options.selectedPages - p else options.selectedPages + p
                                        onOptionsChange(options.copy(selectedPages = next))
                                    },
                                    label = { Text("P${p + 1}") }
                                )
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Package outputs as ZIP archive", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = options.splitOutputZip, onCheckedChange = { onOptionsChange(options.copy(splitOutputZip = it)) })
                }
            }

            ToolId.COMPRESS -> {
                val origBytes = firstDoc?.sizeBytes ?: 100_000L
                Text("Compression level", style = MaterialTheme.typography.titleMedium)
                CompressionLevel.entries.forEach { lvl ->
                    val chosen = options.compressionLevel == lvl
                    Surface(
                        onClick = { onOptionsChange(options.copy(compressionLevel = lvl)) },
                        shape = RoundedCornerShape(18.dp),
                        color = if (chosen) MaterialTheme.colorScheme.primaryContainer.copy(0.45f) else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.5.dp, if (chosen) MaterialTheme.colorScheme.primary else extra.subtleBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(14.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(lvl.label, style = MaterialTheme.typography.titleMedium)
                                Text(lvl.subtitle, style = MaterialTheme.typography.bodySmall, color = extra.mutedText)
                            }
                            Text(
                                "~${PdfRenderHelper.formatFileSize((origBytes * lvl.estRatio).toLong())}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            ToolId.ROTATE -> {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Rotate all pages uniformly", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Switch(checked = options.rotateAllPages, onCheckedChange = { onOptionsChange(options.copy(rotateAllPages = it)) })
                }
                if (options.rotateAllPages) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf(90, 180, 270).forEach { deg ->
                            FilterChip(
                                selected = options.rotationDegrees == deg,
                                onClick = { onOptionsChange(options.copy(rotationDegrees = deg)) },
                                label = { Text("$deg°") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                } else {
                    val count = firstDoc?.pageCount?.coerceAtLeast(1) ?: 1
                    (0 until count).forEach { idx ->
                        val deg = options.pageRotations[idx] ?: 0
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("Page ${idx + 1}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            OutlinedButton(onClick = {
                                val next = (deg + 90) % 360
                                onOptionsChange(options.copy(pageRotations = options.pageRotations + (idx to next)))
                            }) {
                                Icon(Icons.AutoMirrored.Rounded.RotateRight, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("$deg°")
                            }
                        }
                    }
                }
            }

            ToolId.ORGANIZE_PAGES -> {
                val srcFile = firstDoc?.let { File(it.filePath) }
                Text("Reorder, rotate, duplicate, or delete pages", style = MaterialTheme.typography.titleSmall)
                options.organizedPages.forEachIndexed { idx, spec ->
                    var thumb by remember(spec.sourcePageIndex) { mutableStateOf<Bitmap?>(null) }
                    LaunchedEffect(srcFile, spec.sourcePageIndex) {
                        if (srcFile != null) thumb = PdfRenderHelper.renderPage(srcFile, spec.sourcePageIndex, 200)
                    }
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, extra.subtleBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(10.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp, 56.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                thumb?.let {
                                    Image(it.asImageBitmap(), "Page ${spec.sourcePageIndex + 1}", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Position ${idx + 1} (Source P${spec.sourcePageIndex + 1})", style = MaterialTheme.typography.titleSmall)
                                Text("Rotation: ${spec.rotationDegrees}°", style = MaterialTheme.typography.bodySmall, color = extra.mutedText)
                            }
                            IconButton(
                                onClick = {
                                    if (idx > 0) {
                                        val m = options.organizedPages.toMutableList()
                                        val item = m.removeAt(idx)
                                        m.add(idx - 1, item)
                                        onOptionsChange(options.copy(organizedPages = m))
                                    }
                                },
                                enabled = idx > 0
                            ) { Icon(Icons.Rounded.ArrowUpward, "Move up") }
                            IconButton(
                                onClick = {
                                    val m = options.organizedPages.toMutableList()
                                    m[idx] = spec.copy(rotationDegrees = (spec.rotationDegrees + 90) % 360)
                                    onOptionsChange(options.copy(organizedPages = m))
                                }
                            ) { Icon(Icons.AutoMirrored.Rounded.RotateRight, "Rotate") }
                            IconButton(
                                onClick = {
                                    val m = options.organizedPages.toMutableList()
                                    m.add(idx + 1, spec.copy(uniqueId = "${spec.sourcePageIndex}_${System.nanoTime()}"))
                                    onOptionsChange(options.copy(organizedPages = m))
                                }
                            ) { Icon(Icons.Rounded.ContentCopy, "Duplicate") }
                            IconButton(
                                onClick = {
                                    if (options.organizedPages.size > 1) {
                                        onOptionsChange(options.copy(organizedPages = options.organizedPages.filterIndexed { i, _ -> i != idx }))
                                    }
                                },
                                enabled = options.organizedPages.size > 1
                            ) { Icon(Icons.Rounded.Delete, "Remove", tint = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }

            ToolId.PAGE_NUMBERS -> {
                Text("Position (9-point grid)", style = MaterialTheme.typography.titleSmall)
                NinePointPositionGrid(
                    selected = options.pageNumberPosition,
                    onSelect = { onOptionsChange(options.copy(pageNumberPosition = it)) }
                )
                Text("Format", style = MaterialTheme.typography.titleSmall)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    PageNumberFormat.entries.forEachIndexed { idx, fmt ->
                        SegmentedButton(
                            selected = options.pageNumberFormat == fmt,
                            onClick = { onOptionsChange(options.copy(pageNumberFormat = fmt)) },
                            shape = SegmentedButtonDefaults.itemShape(idx, PageNumberFormat.entries.size),
                            label = { Text(fmt.label) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = options.pageNumberStart.toString(),
                        onValueChange = { onOptionsChange(options.copy(pageNumberStart = it.toIntOrNull() ?: 1)) },
                        label = { Text("Start #") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = options.pageRangeText,
                        onValueChange = { onOptionsChange(options.copy(pageRangeText = it)) },
                        label = { Text("Range (blank=all)") },
                        singleLine = true,
                        modifier = Modifier.weight(1.6f)
                    )
                }
                Text("Font size: ${options.pageNumberFontSize.toInt()}pt", style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = options.pageNumberFontSize,
                    onValueChange = { onOptionsChange(options.copy(pageNumberFontSize = it)) },
                    valueRange = 9f..24f
                )
            }

            ToolId.WATERMARK -> {
                OutlinedTextField(
                    value = options.watermarkText,
                    onValueChange = { onOptionsChange(options.copy(watermarkText = it)) },
                    label = { Text("Watermark text") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                ColorSwatchesRow(
                    selectedArgb = options.watermarkColor,
                    onSelect = { onOptionsChange(options.copy(watermarkColor = it)) }
                )
                Text("Opacity: ${(options.watermarkOpacity * 100).roundToInt()}%", style = MaterialTheme.typography.titleSmall)
                Slider(value = options.watermarkOpacity, onValueChange = { onOptionsChange(options.copy(watermarkOpacity = it)) }, valueRange = 0.08f..0.85f)
                Text("Rotation: ${options.watermarkRotation.roundToInt()}°", style = MaterialTheme.typography.titleSmall)
                Slider(value = options.watermarkRotation, onValueChange = { onOptionsChange(options.copy(watermarkRotation = it)) }, valueRange = -90f..90f)
                Text("Size: ${options.watermarkFontSize.roundToInt()}pt", style = MaterialTheme.typography.titleSmall)
                Slider(value = options.watermarkFontSize, onValueChange = { onOptionsChange(options.copy(watermarkFontSize = it)) }, valueRange = 18f..72f)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Tile watermark across entire page", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = options.watermarkTiled, onCheckedChange = { onOptionsChange(options.copy(watermarkTiled = it)) })
                }
                if (!options.watermarkTiled) {
                    Text("Position", style = MaterialTheme.typography.titleSmall)
                    NinePointPositionGrid(options.watermarkPosition) { onOptionsChange(options.copy(watermarkPosition = it)) }
                }
            }

            ToolId.PROTECT -> {
                var showPwd by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = options.password,
                    onValueChange = { onOptionsChange(options.copy(password = it)) },
                    label = { Text("New password (min 4 chars)") },
                    singleLine = true,
                    visualTransformation = if (showPwd) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPwd = !showPwd }) {
                            Icon(if (showPwd) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "Toggle visibility")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = options.confirmPassword,
                    onValueChange = { onOptionsChange(options.copy(confirmPassword = it)) },
                    label = { Text("Confirm password") },
                    singleLine = true,
                    visualTransformation = if (showPwd) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Permissions", style = MaterialTheme.typography.titleSmall)
                PermissionCheckRow("Allow printing", options.allowPrint) { onOptionsChange(options.copy(allowPrint = it)) }
                PermissionCheckRow("Allow copying text", options.allowCopy) { onOptionsChange(options.copy(allowCopy = it)) }
                PermissionCheckRow("Allow editing", options.allowEdit) { onOptionsChange(options.copy(allowEdit = it)) }
            }

            ToolId.UNLOCK -> {
                var showPwd by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = options.password,
                    onValueChange = { onOptionsChange(options.copy(password = it)) },
                    label = { Text("Enter existing password") },
                    singleLine = true,
                    visualTransformation = if (showPwd) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPwd = !showPwd }) {
                            Icon(if (showPwd) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "Toggle visibility")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            ToolId.IMAGE_TO_PDF -> {
                Text("Page size", style = MaterialTheme.typography.titleSmall)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    PageSizeOption.entries.forEachIndexed { idx, ps ->
                        SegmentedButton(
                            selected = options.pageSize == ps,
                            onClick = { onOptionsChange(options.copy(pageSize = ps)) },
                            shape = SegmentedButtonDefaults.itemShape(idx, PageSizeOption.entries.size),
                            label = { Text(ps.label) }
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Landscape orientation", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = options.landscape, onCheckedChange = { onOptionsChange(options.copy(landscape = it)) })
                }
                Text("Margin", style = MaterialTheme.typography.titleSmall)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    MarginOption.entries.forEachIndexed { idx, m ->
                        SegmentedButton(
                            selected = options.margin == m,
                            onClick = { onOptionsChange(options.copy(margin = m)) },
                            shape = SegmentedButtonDefaults.itemShape(idx, MarginOption.entries.size),
                            label = { Text(m.label) }
                        )
                    }
                }
            }

            ToolId.PDF_TO_JPG, ToolId.EXTRACT_IMAGES, ToolId.COMPRESS_IMAGE, ToolId.CONVERT_TO_JPG -> {
                Text("Output image quality: ${options.imageQuality}%", style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = options.imageQuality.toFloat(),
                    onValueChange = { onOptionsChange(options.copy(imageQuality = it.roundToInt())) },
                    valueRange = 40f..100f
                )
            }

            ToolId.RESIZE_IMAGE -> {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = options.resizeWidth.toString(),
                        onValueChange = { onOptionsChange(options.copy(resizeWidth = it.toIntOrNull() ?: 1080)) },
                        label = { Text("Width (px)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = options.resizeHeight.toString(),
                        onValueChange = { onOptionsChange(options.copy(resizeHeight = it.toIntOrNull() ?: 1080)) },
                        label = { Text("Height (px)") },
                        enabled = !options.lockAspectRatio,
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Lock aspect ratio", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = options.lockAspectRatio, onCheckedChange = { onOptionsChange(options.copy(lockAspectRatio = it)) })
                }
            }

            ToolId.CONVERT_FROM_JPG -> {
                Text("Target format", style = MaterialTheme.typography.titleSmall)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    listOf("PNG", "WEBP").forEachIndexed { idx, fmt ->
                        SegmentedButton(
                            selected = options.targetImageFormat == fmt,
                            onClick = { onOptionsChange(options.copy(targetImageFormat = fmt)) },
                            shape = SegmentedButtonDefaults.itemShape(idx, 2),
                            label = { Text(fmt) }
                        )
                    }
                }
            }

            ToolId.OCR -> {
                val langs = listOf("English", "Spanish", "French", "German", "Hindi", "Japanese")
                Text("Recognition language", style = MaterialTheme.typography.titleSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(langs) { lang ->
                        FilterChip(
                            selected = options.ocrLanguage == lang,
                            onClick = { onOptionsChange(options.copy(ocrLanguage = lang)) },
                            label = { Text(lang) }
                        )
                    }
                }
                Text("Output format", style = MaterialTheme.typography.titleSmall)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = options.ocrOutputPdf,
                        onClick = { onOptionsChange(options.copy(ocrOutputPdf = true)) },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                        label = { Text("Searchable PDF") }
                    )
                    SegmentedButton(
                        selected = !options.ocrOutputPdf,
                        onClick = { onOptionsChange(options.copy(ocrOutputPdf = false)) },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                        label = { Text("Plain TXT") }
                    )
                }
            }

            else -> {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(16.dp)) {
                        Text("Ready to process", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "paperpilot will process ${selectedFiles.firstOrNull()?.name ?: "your file"} completely on your device without uploading anything.",
                            style = MaterialTheme.typography.bodySmall,
                            color = extra.mutedText
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NinePointPositionGrid(selected: Int, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.width(168.dp)) {
        for (row in 0..2) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                for (col in 0..2) {
                    val idx = row * 3 + col
                    val isSel = selected == idx
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onSelect(idx) }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isSel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorSwatchesRow(selectedArgb: Int, onSelect: (Int) -> Unit) {
    val swatches = listOf(
        AndroidColor.parseColor("#D03A44"),
        AndroidColor.parseColor("#1A1523"),
        AndroidColor.parseColor("#14B8A6"),
        AndroidColor.parseColor("#8B5CF6"),
        AndroidColor.parseColor("#F59E0B"),
        AndroidColor.parseColor("#64748B")
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        swatches.forEach { argb ->
            val sel = selectedArgb == argb
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(argb))
                    .then(if (sel) Modifier.border(3.dp, MaterialTheme.colorScheme.onBackground, CircleShape) else Modifier)
                    .clickable { onSelect(argb) }
            )
        }
    }
}

@Composable
private fun PermissionCheckRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable { onToggle(!checked) }
    ) {
        Checkbox(checked = checked, onCheckedChange = onToggle)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
