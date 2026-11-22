package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.FormatAlignLeft
import androidx.compose.material.icons.automirrored.rounded.FormatAlignRight
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.BorderColor
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.FormatAlignCenter
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.FormatItalic
import androidx.compose.material.icons.rounded.FormatStrikethrough
import androidx.compose.material.icons.rounded.FormatUnderlined
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Input
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.StickyNote2
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.DocumentEntity
import com.example.data.SignatureEntity
import com.example.engine.EditableTextBlock
import com.example.engine.EditorTool
import com.example.engine.PageAnnotation
import com.example.engine.PdfContentExtractor
import com.example.engine.PdfRenderHelper
import com.example.engine.ShapeKind
import com.example.engine.TextAlignOption
import com.example.ui.components.ContentBlocksOverlay
import com.example.ui.components.ContentTextFormattingToolbar
import com.example.ui.theme.BrandAmber
import com.example.ui.theme.BrandCoral
import com.example.ui.theme.LocalPaperPilotColors
import com.example.viewmodel.PaperPilotViewModel
import java.io.File
import java.util.UUID
import kotlin.math.hypot
import kotlin.math.roundToInt

private data class EditorSnapshot(
    val annotations: List<PageAnnotation>,
    val contentBlocks: List<EditableTextBlock>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfEditorScreen(
    document: DocumentEntity,
    initialTool: EditorTool,
    savedSignatures: List<SignatureEntity>,
    viewModel: PaperPilotViewModel,
    onBack: () -> Unit,
    onSavedDocument: (DocumentEntity) -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val file = remember(document.filePath) { File(document.filePath) }
    val totalPages = remember(document.filePath) { PdfRenderHelper.getPageCount(file).coerceAtLeast(1) }

    var currentPageIdx by remember { mutableIntStateOf(0) }
    var pageBitmap by remember(file.absolutePath, currentPageIdx) { mutableStateOf<Bitmap?>(null) }
    var extractedPages by remember(file.absolutePath) { mutableStateOf<Set<Int>>(emptySet()) }
    var contentBlocks by remember(file.absolutePath) { mutableStateOf<List<EditableTextBlock>>(emptyList()) }
    var selectedBlockId by remember { mutableStateOf<String?>(null) }
    var isDetectingBlocks by remember { mutableStateOf(false) }

    LaunchedEffect(file.absolutePath, currentPageIdx) {
        val bmp = PdfRenderHelper.renderPage(file, currentPageIdx, 1180)
        pageBitmap = bmp
        if (currentPageIdx !in extractedPages) {
            isDetectingBlocks = true
            val detected = PdfContentExtractor.extractEditableBlocks(file, currentPageIdx, bmp)
            contentBlocks = contentBlocks + detected
            extractedPages = extractedPages + currentPageIdx
            isDetectingBlocks = false
        }
    }

    var activeTool by remember { mutableStateOf(initialTool) }
    var annotations by remember { mutableStateOf<List<PageAnnotation>>(emptyList()) }
    var undoStack by remember { mutableStateOf<List<EditorSnapshot>>(emptyList()) }
    var redoStack by remember { mutableStateOf<List<EditorSnapshot>>(emptyList()) }

    fun pushSnapshot(
        nextAnnotations: List<PageAnnotation> = annotations,
        nextBlocks: List<EditableTextBlock> = contentBlocks
    ) {
        undoStack = (undoStack + EditorSnapshot(annotations, contentBlocks)).takeLast(40)
        redoStack = emptyList()
        annotations = nextAnnotations
        contentBlocks = nextBlocks
    }

    // Active styling state for non-content tools
    var penColor by remember { mutableIntStateOf(AndroidColor.parseColor("#D03A44")) }
    var penThickness by remember { mutableFloatStateOf(4f) }
    var activeShapeKind by remember { mutableStateOf(ShapeKind.RECTANGLE) }
    var selectedAnnotationId by remember { mutableStateOf<String?>(null) }

    // Add Text / Note / Form dialog state
    var pendingTextPos by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var textDraft by remember { mutableStateOf("") }
    var textFontSize by remember { mutableFloatStateOf(16f) }
    var textBold by remember { mutableStateOf(false) }
    var textItalic by remember { mutableStateOf(false) }
    var textFontFamily by remember { mutableStateOf("Sans") }
    var textAlign by remember { mutableStateOf(TextAlignOption.LEFT) }
    var textLineSpacing by remember { mutableFloatStateOf(1.2f) }

    var pendingNotePos by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var noteDraft by remember { mutableStateOf("") }

    var showSignatureSheet by remember { mutableStateOf(initialTool == EditorTool.SIGNATURE) }
    var showSaveSheet by remember { mutableStateOf(false) }
    var showOverwriteConfirm by remember { mutableStateOf(false) }

    // Live drawing stroke buffer
    var livePoints by remember { mutableStateOf<List<Pair<Float, Float>>>(emptyList()) }
    var liveShapeStart by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var liveShapeEnd by remember { mutableStateOf<Pair<Float, Float>?>(null) }

    val pickStampImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val dest = File(viewModel.repository.signaturesDir, "img_stamp_${System.currentTimeMillis()}.png")
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { input.copyTo(it) }
            }
            if (dest.exists()) {
                val item = PageAnnotation.ImageOverlay(
                    id = UUID.randomUUID().toString(),
                    pageIndex = currentPageIdx,
                    imagePath = dest.absolutePath,
                    normX = 0.5f,
                    normY = 0.5f,
                    normWidth = 0.36f,
                    normHeight = 0.24f,
                    isSignature = false
                )
                pushSnapshot(nextAnnotations = annotations + item)
                selectedAnnotationId = item.id
            }
        }
    }

    val pickSignatureImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.saveImageSignature(uri) { sig ->
                val overlay = PageAnnotation.ImageOverlay(
                    id = UUID.randomUUID().toString(),
                    pageIndex = currentPageIdx,
                    imagePath = sig.imagePath,
                    normX = 0.5f,
                    normY = 0.72f,
                    normWidth = 0.42f,
                    normHeight = 0.14f,
                    isSignature = true
                )
                pushSnapshot(nextAnnotations = annotations + overlay)
                selectedAnnotationId = overlay.id
                showSignatureSheet = false
            }
        }
    }

    val pageAnnotations = remember(annotations, currentPageIdx) {
        annotations.filter { it.pageIndex == currentPageIdx }
    }
    val pageBlocks = remember(contentBlocks, currentPageIdx) {
        contentBlocks.filter { it.pageIndex == currentPageIdx }
    }
    val selectedContentBlock = remember(pageBlocks, selectedBlockId) {
        pageBlocks.firstOrNull { it.id == selectedBlockId && !it.isDeleted }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = {
                                if (currentPageIdx > 0) {
                                    currentPageIdx--
                                    selectedBlockId = null
                                }
                            },
                            enabled = currentPageIdx > 0,
                            modifier = Modifier.size(34.dp)
                        ) { Icon(Icons.Rounded.ChevronLeft, "Previous page") }
                        Text("${currentPageIdx + 1}/$totalPages", style = MaterialTheme.typography.labelLarge)
                        IconButton(
                            onClick = {
                                if (currentPageIdx < totalPages - 1) {
                                    currentPageIdx++
                                    selectedBlockId = null
                                }
                            },
                            enabled = currentPageIdx < totalPages - 1,
                            modifier = Modifier.size(34.dp)
                        ) { Icon(Icons.Rounded.ChevronRight, "Next page") }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("editor_back_button")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (undoStack.isNotEmpty()) {
                                redoStack = redoStack + EditorSnapshot(annotations, contentBlocks)
                                val prev = undoStack.last()
                                annotations = prev.annotations
                                contentBlocks = prev.contentBlocks
                                undoStack = undoStack.dropLast(1)
                            }
                        },
                        enabled = undoStack.isNotEmpty(),
                        modifier = Modifier.testTag("editor_undo_button")
                    ) { Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = "Undo") }
                    IconButton(
                        onClick = {
                            if (redoStack.isNotEmpty()) {
                                undoStack = undoStack + EditorSnapshot(annotations, contentBlocks)
                                val next = redoStack.last()
                                annotations = next.annotations
                                contentBlocks = next.contentBlocks
                                redoStack = redoStack.dropLast(1)
                            }
                        },
                        enabled = redoStack.isNotEmpty(),
                        modifier = Modifier.testTag("editor_redo_button")
                    ) { Icon(Icons.AutoMirrored.Rounded.Redo, contentDescription = "Redo") }
                    Button(
                        onClick = { showSaveSheet = true },
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        modifier = Modifier.padding(end = 8.dp).testTag("editor_save_button")
                    ) {
                        Text("Save")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Interactive PDF Page Canvas
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(
                            pageBitmap?.let { it.width.toFloat() / it.height.coerceAtLeast(1) } ?: 0.71f
                        )
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .border(1.dp, LocalPaperPilotColors.current.subtleBorder, RoundedCornerShape(12.dp))
                ) {
                    val widthPx = with(LocalDensity.current) { maxWidth.toPx() }.coerceAtLeast(1f)
                    val heightPx = with(LocalDensity.current) { maxHeight.toPx() }.coerceAtLeast(1f)

                    // Base rendered PDF page
                    pageBitmap?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Editable PDF page ${currentPageIdx + 1}",
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Background tap catcher to deselect content text block or drop annotations
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(activeTool, penColor, penThickness, activeShapeKind, currentPageIdx, annotations, contentBlocks) {
                                detectTapGestures { offset ->
                                    val nx = (offset.x / widthPx).coerceIn(0.05f, 0.92f)
                                    val ny = (offset.y / heightPx).coerceIn(0.05f, 0.94f)
                                    when (activeTool) {
                                        EditorTool.EDIT_CONTENT -> {
                                            selectedBlockId = null
                                        }
                                        EditorTool.TEXT, EditorTool.FORM -> {
                                            pendingTextPos = nx to ny
                                            textDraft = ""
                                        }
                                        EditorTool.NOTE -> {
                                            pendingNotePos = nx to ny
                                            noteDraft = ""
                                        }
                                        EditorTool.ERASER -> {
                                            val hit = pageAnnotations.lastOrNull { ann ->
                                                when (ann) {
                                                    is PageAnnotation.TextBox -> hypot(ann.normX - nx, ann.normY - ny) < 0.12f
                                                    is PageAnnotation.StickyNote -> hypot(ann.normX - nx, ann.normY - ny) < 0.12f
                                                    is PageAnnotation.ImageOverlay -> hypot(ann.normX - nx, ann.normY - ny) < 0.18f
                                                    is PageAnnotation.FreehandStroke -> ann.points.any { hypot(it.first - nx, it.second - ny) < 0.08f }
                                                    is PageAnnotation.ShapeItem -> hypot((ann.startX + ann.endX) / 2 - nx, (ann.startY + ann.endY) / 2 - ny) < 0.14f
                                                }
                                            }
                                            if (hit != null) pushSnapshot(nextAnnotations = annotations.filterNot { it.id == hit.id })
                                        }
                                        else -> selectedAnnotationId = null
                                    }
                                }
                            }
                            .pointerInput(activeTool, penColor, penThickness, activeShapeKind, currentPageIdx, annotations) {
                                if (activeTool in setOf(EditorTool.DRAW, EditorTool.HIGHLIGHT, EditorTool.UNDERLINE, EditorTool.STRIKETHROUGH, EditorTool.SHAPES)) {
                                    detectDragGestures(
                                        onDragStart = { pos ->
                                            val pt = (pos.x / widthPx).coerceIn(0f, 1f) to (pos.y / heightPx).coerceIn(0f, 1f)
                                            if (activeTool == EditorTool.SHAPES) {
                                                liveShapeStart = pt
                                                liveShapeEnd = pt
                                            } else {
                                                livePoints = listOf(pt)
                                            }
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            val pt = (change.position.x / widthPx).coerceIn(0f, 1f) to (change.position.y / heightPx).coerceIn(0f, 1f)
                                            if (activeTool == EditorTool.SHAPES) {
                                                liveShapeEnd = pt
                                            } else {
                                                livePoints = livePoints + pt
                                            }
                                        },
                                        onDragEnd = {
                                            if (activeTool == EditorTool.SHAPES) {
                                                val s = liveShapeStart
                                                val e = liveShapeEnd
                                                if (s != null && e != null) {
                                                    pushSnapshot(
                                                        nextAnnotations = annotations + PageAnnotation.ShapeItem(
                                                            id = UUID.randomUUID().toString(),
                                                            pageIndex = currentPageIdx,
                                                            kind = activeShapeKind,
                                                            startX = s.first,
                                                            startY = s.second,
                                                            endX = e.first,
                                                            endY = e.second,
                                                            colorArgb = penColor,
                                                            strokeWidthDp = penThickness
                                                        )
                                                    )
                                                }
                                                liveShapeStart = null
                                                liveShapeEnd = null
                                            } else if (livePoints.size >= 2) {
                                                val strokeColor = when (activeTool) {
                                                    EditorTool.HIGHLIGHT -> AndroidColor.parseColor("#FFB020")
                                                    else -> penColor
                                                }
                                                val strokeAlpha = if (activeTool == EditorTool.HIGHLIGHT) 0.36f else 1f
                                                val width = if (activeTool == EditorTool.HIGHLIGHT) 16f else penThickness
                                                val straight = activeTool == EditorTool.UNDERLINE || activeTool == EditorTool.STRIKETHROUGH
                                                pushSnapshot(
                                                    nextAnnotations = annotations + PageAnnotation.FreehandStroke(
                                                        id = UUID.randomUUID().toString(),
                                                        pageIndex = currentPageIdx,
                                                        points = livePoints,
                                                        colorArgb = strokeColor,
                                                        strokeWidthDp = width,
                                                        alpha = strokeAlpha,
                                                        isStraightLine = straight
                                                    )
                                                )
                                                livePoints = emptyList()
                                            }
                                        }
                                    )
                                }
                            }
                    ) {
                        val w = size.width
                        val h = size.height

                        pageAnnotations.forEach { ann ->
                            when (ann) {
                                is PageAnnotation.FreehandStroke -> {
                                    if (ann.points.size >= 2) {
                                        val path = Path().apply {
                                            moveTo(ann.points.first().first * w, ann.points.first().second * h)
                                            if (ann.isStraightLine) {
                                                lineTo(ann.points.last().first * w, ann.points.last().second * h)
                                            } else {
                                                ann.points.drop(1).forEach { (nx, ny) -> lineTo(nx * w, ny * h) }
                                            }
                                        }
                                        drawPath(
                                            path = path,
                                            color = Color(ann.colorArgb).copy(alpha = ann.alpha),
                                            style = Stroke(width = ann.strokeWidthDp.dp.toPx(), cap = StrokeCap.Round)
                                        )
                                    }
                                }
                                is PageAnnotation.ShapeItem -> {
                                    val left = minOf(ann.startX, ann.endX) * w
                                    val top = minOf(ann.startY, ann.endY) * h
                                    val rw = (maxOf(ann.startX, ann.endX) - minOf(ann.startX, ann.endX)) * w
                                    val rh = (maxOf(ann.startY, ann.endY) - minOf(ann.startY, ann.endY)) * h
                                    when (ann.kind) {
                                        ShapeKind.RECTANGLE -> drawRect(
                                            color = Color(ann.colorArgb),
                                            topLeft = Offset(left, top),
                                            size = Size(rw, rh),
                                            style = Stroke(ann.strokeWidthDp.dp.toPx())
                                        )
                                        ShapeKind.CIRCLE -> drawOval(
                                            color = Color(ann.colorArgb),
                                            topLeft = Offset(left, top),
                                            size = Size(rw, rh),
                                            style = Stroke(ann.strokeWidthDp.dp.toPx())
                                        )
                                        ShapeKind.ARROW -> drawLine(
                                            color = Color(ann.colorArgb),
                                            start = Offset(ann.startX * w, ann.startY * h),
                                            end = Offset(ann.endX * w, ann.endY * h),
                                            strokeWidth = ann.strokeWidthDp.dp.toPx(),
                                            cap = StrokeCap.Round
                                        )
                                    }
                                }
                                else -> Unit
                            }
                        }

                        if (livePoints.size >= 2) {
                            val path = Path().apply {
                                moveTo(livePoints.first().first * w, livePoints.first().second * h)
                                livePoints.drop(1).forEach { (nx, ny) -> lineTo(nx * w, ny * h) }
                            }
                            val col = if (activeTool == EditorTool.HIGHLIGHT) BrandAmber.copy(0.4f) else Color(penColor)
                            drawPath(path, col, style = Stroke(width = (if (activeTool == EditorTool.HIGHLIGHT) 16f else penThickness).dp.toPx(), cap = StrokeCap.Round))
                        }
                        if (liveShapeStart != null && liveShapeEnd != null) {
                            val s = liveShapeStart!!
                            val e = liveShapeEnd!!
                            drawRect(
                                color = Color(penColor),
                                topLeft = Offset(minOf(s.first, e.first) * w, minOf(s.second, e.second) * h),
                                size = Size(kotlin.math.abs(e.first - s.first) * w, kotlin.math.abs(e.second - s.second) * h),
                                style = Stroke(penThickness.dp.toPx())
                            )
                        }
                    }

                    // Direct In-Place Content Text Editor Overlay (dashed blue boxes, solid blue handles, live inline BasicTextField)
                    ContentBlocksOverlay(
                        blocks = pageBlocks,
                        selectedBlockId = selectedBlockId,
                        showGuides = activeTool == EditorTool.EDIT_CONTENT,
                        containerWidthDp = maxWidth,
                        containerHeightDp = maxHeight,
                        onSelectBlock = { id ->
                            selectedBlockId = id
                            if (id != null) {
                                // Snapshot state before typing starts so Undo reverts cleanly
                                undoStack = (undoStack + EditorSnapshot(annotations, contentBlocks)).takeLast(40)
                                redoStack = emptyList()
                            }
                        },
                        onUpdateBlock = { updated, recordUndo ->
                            val nextBlocks = contentBlocks.map { if (it.id == updated.id) updated else it }
                            if (recordUndo) pushSnapshot(nextBlocks = nextBlocks)
                            else contentBlocks = nextBlocks
                        },
                        onDeleteBlock = { target ->
                            val nextBlocks = contentBlocks.map {
                                if (it.id == target.id) it.copy(isDeleted = true, isModified = true) else it
                            }
                            pushSnapshot(nextBlocks = nextBlocks)
                            selectedBlockId = null
                        },
                        onResetBlock = { target ->
                            val nextBlocks = contentBlocks.map {
                                if (it.id == target.id) it.copy(
                                    text = it.originalText,
                                    normX = it.origNormX,
                                    normY = it.origNormY,
                                    normWidth = it.origNormWidth,
                                    normHeight = it.origNormHeight,
                                    isModified = false,
                                    isDeleted = false
                                ) else it
                            }
                            pushSnapshot(nextBlocks = nextBlocks)
                        }
                    )

                    // AcroForm interactive fields helper overlay when Fill Form tool is active
                    if (activeTool == EditorTool.FORM) {
                        val formSlots = listOf(
                            Triple("Employee Name", 0.12f, 0.23f),
                            Triple("Department", 0.12f, 0.33f),
                            Triple("Destination City", 0.12f, 0.42f),
                            Triple("Estimated Budget", 0.12f, 0.52f)
                        )
                        formSlots.forEach { (fieldLabel, nx, ny) ->
                            Surface(
                                onClick = {
                                    pendingTextPos = nx to ny
                                    textDraft = ""
                                },
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0x3314B8A6),
                                border = BorderStroke(1.dp, Color(0xFF14B8A6)),
                                modifier = Modifier
                                    .offset { IntOffset((nx * widthPx).roundToInt(), (ny * heightPx).roundToInt()) }
                                    .width(maxWidth * 0.74f)
                                    .height(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.padding(horizontal = 8.dp)) {
                                    Text("Tap to fill: $fieldLabel", fontSize = 10.sp, color = Color(0xFF0F766E))
                                }
                            }
                        }
                    }

                    // Draggable TextBox, StickyNote, and Signature/Image overlays
                    pageAnnotations.forEach { ann ->
                        when (ann) {
                            is PageAnnotation.TextBox -> {
                                val isSel = selectedAnnotationId == ann.id
                                Box(
                                    modifier = Modifier
                                        .offset { IntOffset((ann.normX * widthPx).roundToInt(), (ann.normY * heightPx).roundToInt()) }
                                        .then(if (isSel) Modifier.border(1.5.dp, BrandCoral, RoundedCornerShape(6.dp)) else Modifier)
                                        .pointerInput(ann.id) {
                                            detectDragGestures { change, drag ->
                                                change.consume()
                                                annotations = annotations.map {
                                                    if (it.id == ann.id && it is PageAnnotation.TextBox) {
                                                        it.copy(
                                                            normX = (it.normX + drag.x / widthPx).coerceIn(0.02f, 0.9f),
                                                            normY = (it.normY + drag.y / heightPx).coerceIn(0.02f, 0.94f)
                                                        )
                                                    } else it
                                                }
                                            }
                                        }
                                        .clickable { selectedAnnotationId = ann.id }
                                        .padding(4.dp)
                                ) {
                                    Text(
                                        text = ann.text,
                                        color = Color(ann.colorArgb),
                                        fontSize = (ann.fontSizeSp * 0.85f).sp,
                                        fontWeight = if (ann.bold) FontWeight.Bold else FontWeight.Normal,
                                        fontStyle = if (ann.italic) FontStyle.Italic else FontStyle.Normal,
                                        fontFamily = when (ann.fontFamilyName) {
                                            "Serif" -> FontFamily.Serif
                                            "Mono" -> FontFamily.Monospace
                                            else -> FontFamily.SansSerif
                                        }
                                    )
                                }
                            }

                            is PageAnnotation.StickyNote -> {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(ann.colorArgb),
                                    shadowElevation = 3.dp,
                                    modifier = Modifier
                                        .offset { IntOffset((ann.normX * widthPx).roundToInt(), (ann.normY * heightPx).roundToInt()) }
                                        .pointerInput(ann.id) {
                                            detectDragGestures { change, drag ->
                                                change.consume()
                                                annotations = annotations.map {
                                                    if (it.id == ann.id && it is PageAnnotation.StickyNote) {
                                                        it.copy(
                                                            normX = (it.normX + drag.x / widthPx).coerceIn(0.02f, 0.78f),
                                                            normY = (it.normY + drag.y / heightPx).coerceIn(0.02f, 0.88f)
                                                        )
                                                    } else it
                                                }
                                            }
                                        }
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                                        Icon(Icons.Rounded.StickyNote2, null, tint = Color(0xFF1A1523), modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(ann.message, color = Color(0xFF1A1523), fontSize = 11.sp, maxLines = 2)
                                    }
                                }
                            }

                            is PageAnnotation.ImageOverlay -> {
                                val isSel = selectedAnnotationId == ann.id
                                val boxW = (ann.normWidth * maxWidth.value).dp
                                val boxH = (ann.normHeight * maxHeight.value).dp
                                Box(
                                    modifier = Modifier
                                        .offset {
                                            IntOffset(
                                                ((ann.normX - ann.normWidth / 2f) * widthPx).roundToInt(),
                                                ((ann.normY - ann.normHeight / 2f) * heightPx).roundToInt()
                                            )
                                        }
                                        .size(boxW, boxH)
                                        .graphicsLayer { rotationZ = ann.rotationDegrees }
                                        .then(if (isSel) Modifier.border(1.5.dp, BrandCoral, RoundedCornerShape(6.dp)) else Modifier)
                                        .pointerInput(ann.id) {
                                            detectDragGestures { change, drag ->
                                                change.consume()
                                                annotations = annotations.map {
                                                    if (it.id == ann.id && it is PageAnnotation.ImageOverlay) {
                                                        it.copy(
                                                            normX = (it.normX + drag.x / widthPx).coerceIn(0.1f, 0.9f),
                                                            normY = (it.normY + drag.y / heightPx).coerceIn(0.1f, 0.9f)
                                                        )
                                                    } else it
                                                }
                                            }
                                        }
                                        .clickable { selectedAnnotationId = ann.id }
                                ) {
                                    AsyncImage(
                                        model = File(ann.imagePath),
                                        contentDescription = if (ann.isSignature) "Placed signature" else "Placed image",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }

                            else -> Unit
                        }
                    }

                    if (isDetectingBlocks) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                            shadowElevation = 4.dp,
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp)
                        ) {
                            Text(
                                "Detecting text blocks...",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }

            // Contextual Bar (Content Text Editor Formatting Bar OR Annotation Tool Controls)
            if (activeTool == EditorTool.EDIT_CONTENT) {
                ContentTextFormattingToolbar(
                    selectedBlock = selectedContentBlock,
                    blockCountOnPage = pageBlocks.count { !it.isDeleted },
                    onUpdateSelected = { updated ->
                        val next = contentBlocks.map { if (it.id == updated.id) updated else it }
                        pushSnapshot(nextBlocks = next)
                    },
                    onAddNewTextBlock = {
                        val newBlock = EditableTextBlock(
                            pageIndex = currentPageIdx,
                            origNormX = 0.14f,
                            origNormY = 0.45f,
                            origNormWidth = 0.62f,
                            origNormHeight = 0.055f,
                            originalText = "",
                            text = "New text",
                            fontSizePt = 16f,
                            textColorArgb = AndroidColor.parseColor("#1A1523"),
                            bgColorArgb = PdfContentExtractor.sampleBackgroundColor(pageBitmap, 0.14f, 0.45f, 0.62f, 0.055f),
                            isModified = true,
                            isUserAdded = true
                        )
                        pushSnapshot(nextBlocks = contentBlocks + newBlock)
                        selectedBlockId = newBlock.id
                    }
                )
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, LocalPaperPilotColors.current.subtleBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val selectedImg = pageAnnotations.filterIsInstance<PageAnnotation.ImageOverlay>().firstOrNull { it.id == selectedAnnotationId }
                    if (selectedImg != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Selected", style = MaterialTheme.typography.labelMedium)
                            OutlinedButton(onClick = {
                                annotations = annotations.map {
                                    if (it.id == selectedImg.id && it is PageAnnotation.ImageOverlay) {
                                        it.copy(normWidth = (it.normWidth * 1.15f).coerceAtMost(0.8f), normHeight = (it.normHeight * 1.15f).coerceAtMost(0.5f))
                                    } else it
                                }
                            }) { Text("+Size") }
                            OutlinedButton(onClick = {
                                annotations = annotations.map {
                                    if (it.id == selectedImg.id && it is PageAnnotation.ImageOverlay) {
                                        it.copy(normWidth = (it.normWidth * 0.85f).coerceAtLeast(0.14f), normHeight = (it.normHeight * 0.85f).coerceAtLeast(0.06f))
                                    } else it
                                }
                            }) { Text("-Size") }
                            IconButton(onClick = {
                                annotations = annotations.map {
                                    if (it.id == selectedImg.id && it is PageAnnotation.ImageOverlay) {
                                        it.copy(rotationDegrees = (it.rotationDegrees + 15f) % 360f)
                                    } else it
                                }
                            }) { Icon(Icons.Rounded.RotateRight, "Rotate stamp") }
                            IconButton(onClick = {
                                pushSnapshot(nextAnnotations = annotations.filterNot { it.id == selectedImg.id })
                                selectedAnnotationId = null
                            }) { Icon(Icons.Rounded.Delete, "Delete stamp", tint = MaterialTheme.colorScheme.error) }
                        }
                    } else if (activeTool == EditorTool.TEXT) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            item { FilterChip(selected = textFontFamily == "Sans", onClick = { textFontFamily = "Sans" }, label = { Text("Sans") }) }
                            item { FilterChip(selected = textFontFamily == "Serif", onClick = { textFontFamily = "Serif" }, label = { Text("Serif") }) }
                            item { FilterChip(selected = textFontFamily == "Mono", onClick = { textFontFamily = "Mono" }, label = { Text("Mono") }) }
                            item {
                                IconButton(onClick = { textBold = !textBold }) {
                                    Icon(Icons.Rounded.FormatBold, "Bold", tint = if (textBold) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                }
                            }
                            item {
                                IconButton(onClick = { textItalic = !textItalic }) {
                                    Icon(Icons.Rounded.FormatItalic, "Italic", tint = if (textItalic) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                }
                            }
                            item {
                                IconButton(onClick = {
                                    textAlign = when (textAlign) {
                                        TextAlignOption.LEFT -> TextAlignOption.CENTER
                                        TextAlignOption.CENTER -> TextAlignOption.RIGHT
                                        TextAlignOption.RIGHT, TextAlignOption.JUSTIFY -> TextAlignOption.LEFT
                                    }
                                }) {
                                    Icon(
                                        when (textAlign) {
                                            TextAlignOption.LEFT, TextAlignOption.JUSTIFY -> Icons.AutoMirrored.Rounded.FormatAlignLeft
                                            TextAlignOption.CENTER -> Icons.Rounded.FormatAlignCenter
                                            TextAlignOption.RIGHT -> Icons.AutoMirrored.Rounded.FormatAlignRight
                                        },
                                        "Text alignment"
                                    )
                                }
                            }
                            item {
                                TextButton(onClick = { textFontSize = if (textFontSize >= 26f) 12f else textFontSize + 2f }) {
                                    Text("${textFontSize.toInt()}pt")
                                }
                            }
                            item { EditorColorDots(selected = penColor, onSelect = { penColor = it }) }
                        }
                    } else if (activeTool == EditorTool.DRAW || activeTool == EditorTool.SHAPES || activeTool == EditorTool.UNDERLINE || activeTool == EditorTool.STRIKETHROUGH) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            EditorColorDots(selected = penColor, onSelect = { penColor = it })
                            if (activeTool == EditorTool.SHAPES) {
                                ShapeKind.entries.forEach { k ->
                                    FilterChip(
                                        selected = activeShapeKind == k,
                                        onClick = { activeShapeKind = k },
                                        label = { Text(k.name.take(4)) }
                                    )
                                }
                            } else {
                                Slider(
                                    value = penThickness,
                                    onValueChange = { penThickness = it },
                                    valueRange = 2f..12f,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    } else {
                        Text(
                            text = when (activeTool) {
                                EditorTool.HIGHLIGHT -> "Drag across text to highlight in warm amber"
                                EditorTool.NOTE -> "Tap anywhere on the page to drop a sticky note"
                                EditorTool.SIGNATURE -> "Tap Signature below to draw, type, or pick a saved signature"
                                EditorTool.FORM -> "Tap any highlighted teal form box to enter text"
                                EditorTool.ERASER -> "Tap any annotation on the page to erase it"
                                EditorTool.IMAGE -> "Tap Image below to insert a photo from your device"
                                else -> "Tap anywhere on the page to add an element"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalPaperPilotColors.current.mutedText,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                }
            }

            // Hide main mode bar while actively typing inline so keyboard + formatting pill stay clean (just like screenshots!)
            if (selectedContentBlock == null) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.navigationBarsPadding()
                    ) {
                        val toolItems: List<Pair<EditorTool, ImageVector>> = listOf(
                            EditorTool.EDIT_CONTENT to Icons.Rounded.EditNote,
                            EditorTool.TEXT to Icons.Rounded.TextFields,
                            EditorTool.HIGHLIGHT to Icons.Rounded.BorderColor,
                            EditorTool.UNDERLINE to Icons.Rounded.FormatUnderlined,
                            EditorTool.STRIKETHROUGH to Icons.Rounded.FormatStrikethrough,
                            EditorTool.DRAW to Icons.Rounded.Gesture,
                            EditorTool.SHAPES to Icons.Rounded.CropSquare,
                            EditorTool.NOTE to Icons.Rounded.StickyNote2,
                            EditorTool.IMAGE to Icons.Rounded.Image,
                            EditorTool.SIGNATURE to Icons.Rounded.Draw,
                            EditorTool.FORM to Icons.Rounded.Input,
                            EditorTool.ERASER to Icons.Rounded.AutoFixHigh
                        )
                        items(toolItems) { (tool, icon) ->
                            val active = activeTool == tool
                            Surface(
                                onClick = {
                                    activeTool = tool
                                    if (tool != EditorTool.EDIT_CONTENT) selectedBlockId = null
                                    if (tool == EditorTool.SIGNATURE) showSignatureSheet = true
                                    if (tool == EditorTool.IMAGE) {
                                        pickStampImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                color = if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                modifier = Modifier.testTag("editor_tool_${tool.name.lowercase()}")
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = tool.label,
                                        tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Text(
                                        text = tool.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Place TextBox dialog
    pendingTextPos?.let { (nx, ny) ->
        AlertDialog(
            onDismissRequest = { pendingTextPos = null },
            title = { Text(if (activeTool == EditorTool.FORM) "Fill form field" else "Add text box") },
            text = {
                OutlinedTextField(
                    value = textDraft,
                    onValueChange = { textDraft = it },
                    label = { Text("Enter text") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (textDraft.isNotBlank()) {
                            val item = PageAnnotation.TextBox(
                                id = UUID.randomUUID().toString(),
                                pageIndex = currentPageIdx,
                                normX = nx,
                                normY = ny,
                                text = textDraft,
                                fontSizeSp = textFontSize,
                                colorArgb = penColor,
                                bold = textBold,
                                italic = textItalic,
                                fontFamilyName = textFontFamily,
                                align = textAlign,
                                lineSpacing = textLineSpacing
                            )
                            pushSnapshot(nextAnnotations = annotations + item)
                            selectedAnnotationId = item.id
                        }
                        pendingTextPos = null
                    }
                ) { Text("Place") }
            },
            dismissButton = { TextButton(onClick = { pendingTextPos = null }) { Text("Cancel") } }
        )
    }

    // Sticky note dialog
    pendingNotePos?.let { (nx, ny) ->
        AlertDialog(
            onDismissRequest = { pendingNotePos = null },
            title = { Text("Add note / comment") },
            text = {
                OutlinedTextField(
                    value = noteDraft,
                    onValueChange = { noteDraft = it },
                    label = { Text("Comment") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (noteDraft.isNotBlank()) {
                        pushSnapshot(
                            nextAnnotations = annotations + PageAnnotation.StickyNote(
                                id = UUID.randomUUID().toString(),
                                pageIndex = currentPageIdx,
                                normX = nx,
                                normY = ny,
                                message = noteDraft
                            )
                        )
                    }
                    pendingNotePos = null
                }) { Text("Add note") }
            },
            dismissButton = { TextButton(onClick = { pendingNotePos = null }) { Text("Cancel") } }
        )
    }

    if (showSignatureSheet) {
        SignatureBottomSheet(
            savedSignatures = savedSignatures,
            onDismiss = { showSignatureSheet = false },
            onPlaceSignature = { sig ->
                val item = PageAnnotation.ImageOverlay(
                    id = UUID.randomUUID().toString(),
                    pageIndex = currentPageIdx,
                    imagePath = sig.imagePath,
                    normX = 0.5f,
                    normY = 0.72f,
                    normWidth = 0.44f,
                    normHeight = 0.14f,
                    isSignature = true
                )
                pushSnapshot(nextAnnotations = annotations + item)
                selectedAnnotationId = item.id
                showSignatureSheet = false
            },
            onCreateDrawn = { strokes, colorArgb ->
                viewModel.saveDrawnSignature(strokes, colorArgb) { sig ->
                    val item = PageAnnotation.ImageOverlay(
                        id = UUID.randomUUID().toString(),
                        pageIndex = currentPageIdx,
                        imagePath = sig.imagePath,
                        normX = 0.5f,
                        normY = 0.72f,
                        normWidth = 0.44f,
                        normHeight = 0.14f,
                        isSignature = true
                    )
                    pushSnapshot(nextAnnotations = annotations + item)
                    selectedAnnotationId = item.id
                    showSignatureSheet = false
                }
            },
            onCreateTyped = { typed, colorArgb ->
                viewModel.saveTypedSignature(typed, colorArgb) { sig ->
                    val item = PageAnnotation.ImageOverlay(
                        id = UUID.randomUUID().toString(),
                        pageIndex = currentPageIdx,
                        imagePath = sig.imagePath,
                        normX = 0.5f,
                        normY = 0.72f,
                        normWidth = 0.44f,
                        normHeight = 0.14f,
                        isSignature = true
                    )
                    pushSnapshot(nextAnnotations = annotations + item)
                    selectedAnnotationId = item.id
                    showSignatureSheet = false
                }
            },
            onUploadImage = {
                pickSignatureImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onDeleteSignature = { viewModel.deleteSignature(it.id) }
        )
    }

    // Save sheet (Default = Save as new copy, Overwrite requires explicit confirm)
    if (showSaveSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSaveSheet = false },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)
            ) {
                Text("Save edited PDF", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "By default, paperpilot saves a new copy with all text edits and annotations flattened cleanly.",
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalPaperPilotColors.current.mutedText
                )
                Button(
                    onClick = {
                        showSaveSheet = false
                        viewModel.saveEditedPdf(
                            originalDoc = document,
                            annotations = annotations,
                            editedContentBlocks = contentBlocks,
                            overwriteOriginal = false
                        ) { saved ->
                            onSavedDocument(saved)
                        }
                    },
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("editor_save_copy_button")
                ) { Text("Save as new copy (Recommended)") }
                OutlinedButton(
                    onClick = {
                        showSaveSheet = false
                        showOverwriteConfirm = true
                    },
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) { Text("Overwrite original file") }
            }
        }
    }

    if (showOverwriteConfirm) {
        AlertDialog(
            onDismissRequest = { showOverwriteConfirm = false },
            title = { Text("Overwrite original?") },
            text = { Text("This will replace \"${document.name}\" with your edited text and annotations. Continue?") },
            confirmButton = {
                Button(onClick = {
                    showOverwriteConfirm = false
                    viewModel.saveEditedPdf(
                        originalDoc = document,
                        annotations = annotations,
                        editedContentBlocks = contentBlocks,
                        overwriteOriginal = true
                    ) { saved ->
                        onSavedDocument(saved)
                    }
                }) { Text("Overwrite") }
            },
            dismissButton = { TextButton(onClick = { showOverwriteConfirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun EditorColorDots(selected: Int, onSelect: (Int) -> Unit) {
    val palette = listOf(
        AndroidColor.parseColor("#1A1523"),
        AndroidColor.parseColor("#D03A44"),
        AndroidColor.parseColor("#1D4ED8"),
        AndroidColor.parseColor("#10B981"),
        AndroidColor.parseColor("#8B5CF6")
    )
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        palette.forEach { argb ->
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color(argb))
                    .then(if (selected == argb) Modifier.border(2.5.dp, MaterialTheme.colorScheme.onBackground, CircleShape) else Modifier)
                    .clickable { onSelect(argb) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SignatureBottomSheet(
    savedSignatures: List<SignatureEntity>,
    onDismiss: () -> Unit,
    onPlaceSignature: (SignatureEntity) -> Unit,
    onCreateDrawn: (List<List<Pair<Float, Float>>>, Int) -> Unit,
    onCreateTyped: (String, Int) -> Unit,
    onUploadImage: () -> Unit,
    onDeleteSignature: (SignatureEntity) -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }
    var inkColor by remember { mutableIntStateOf(AndroidColor.parseColor("#1A1523")) }
    var strokes by remember { mutableStateOf<List<List<Pair<Float, Float>>>>(emptyList()) }
    var currentStroke by remember { mutableStateOf<List<Pair<Float, Float>>>(emptyList()) }
    var typedName by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Signature", style = MaterialTheme.typography.headlineSmall)
                EditorColorDots(selected = inkColor, onSelect = { inkColor = it })
            }

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf("Draw", "Type", "Upload").forEachIndexed { idx, label ->
                    SegmentedButton(
                        selected = tab == idx,
                        onClick = { tab = idx },
                        shape = SegmentedButtonDefaults.itemShape(idx, 3),
                        label = { Text(label) }
                    )
                }
            }

            when (tab) {
                0 -> {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.White)
                            .border(1.dp, LocalPaperPilotColors.current.subtleBorder, RoundedCornerShape(18.dp))
                    ) {
                        val wPx = with(LocalDensity.current) { maxWidth.toPx() }.coerceAtLeast(1f)
                        val hPx = with(LocalDensity.current) { maxHeight.toPx() }.coerceAtLeast(1f)
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectDragGestures(
                                        onDragStart = { pos -> currentStroke = listOf(pos.x / wPx to pos.y / hPx) },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            currentStroke = currentStroke + ((change.position.x / wPx).coerceIn(0f, 1f) to (change.position.y / hPx).coerceIn(0f, 1f))
                                        },
                                        onDragEnd = {
                                            if (currentStroke.size >= 2) strokes = strokes + listOf(currentStroke)
                                            currentStroke = emptyList()
                                        }
                                    )
                                }
                        ) {
                            (strokes + listOf(currentStroke)).forEach { pts ->
                                if (pts.size >= 2) {
                                    val path = Path().apply {
                                        moveTo(pts.first().first * size.width, pts.first().second * size.height)
                                        pts.drop(1).forEach { lineTo(it.first * size.width, it.second * size.height) }
                                    }
                                    drawPath(path, Color(inkColor), style = Stroke(3.5.dp.toPx(), cap = StrokeCap.Round))
                                }
                            }
                        }
                        if (strokes.isEmpty() && currentStroke.isEmpty()) {
                            Text(
                                "Sign here with your finger",
                                color = Color(0xFF9CA3AF),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { strokes = emptyList() }, shape = CircleShape, modifier = Modifier.weight(1f)) {
                            Text("Clear")
                        }
                        Button(
                            onClick = { onCreateDrawn(strokes, inkColor) },
                            enabled = strokes.isNotEmpty(),
                            shape = CircleShape,
                            modifier = Modifier.weight(1.6f)
                        ) {
                            Icon(Icons.Rounded.Check, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Save & Place")
                        }
                    }
                }

                1 -> {
                    OutlinedTextField(
                        value = typedName,
                        onValueChange = { typedName = it },
                        label = { Text("Type your full name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, LocalPaperPilotColors.current.subtleBorder),
                        modifier = Modifier.fillMaxWidth().height(84.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = typedName.ifBlank { "Your Signature" },
                                color = Color(inkColor),
                                fontFamily = FontFamily.Serif,
                                fontStyle = FontStyle.Italic,
                                fontSize = 30.sp
                            )
                        }
                    }
                    Button(
                        onClick = { onCreateTyped(typedName, inkColor) },
                        enabled = typedName.isNotBlank(),
                        shape = CircleShape,
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) { Text("Save & Place Signature") }
                }

                2 -> {
                    OutlinedButton(
                        onClick = onUploadImage,
                        shape = CircleShape,
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Icon(Icons.Rounded.Image, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Pick signature image from device")
                    }
                }
            }

            if (savedSignatures.isNotEmpty()) {
                Text("Saved signatures", style = MaterialTheme.typography.titleSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(savedSignatures, key = { it.id }) { sig ->
                        Surface(
                            onClick = { onPlaceSignature(sig) },
                            shape = RoundedCornerShape(14.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, LocalPaperPilotColors.current.subtleBorder),
                            modifier = Modifier.size(136.dp, 68.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxSize().padding(6.dp)) {
                                AsyncImage(
                                    model = File(sig.imagePath),
                                    contentDescription = sig.label,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                                IconButton(
                                    onClick = { onDeleteSignature(sig) },
                                    modifier = Modifier.align(Alignment.TopEnd).size(22.dp)
                                ) {
                                    Icon(Icons.Rounded.Delete, "Delete signature", tint = BrandCoral, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
