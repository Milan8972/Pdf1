package com.example.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.DocumentEntity
import com.example.engine.PdfRenderHelper
import com.example.ui.components.DocumentDetailsDialog
import com.example.ui.theme.BrandAmber
import com.example.ui.theme.LocalPaperPilotColors
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    document: DocumentEntity,
    isWideLayout: Boolean,
    onBack: () -> Unit,
    onOpenEditor: (DocumentEntity) -> Unit,
    onShare: (DocumentEntity) -> Unit,
    onPrint: (DocumentEntity) -> Unit,
    onToggleFavorite: (DocumentEntity) -> Unit
) {
    BackHandler { onBack() }

    val file = remember(document.filePath) { File(document.filePath) }
    val totalPages = remember(document.filePath, document.pageCount) {
        PdfRenderHelper.getPageCount(file).coerceAtLeast(1)
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var zoomScale by remember { mutableFloatStateOf(1f) }
    var nightReading by remember { mutableStateOf(false) }
    var showThumbs by remember { mutableStateOf(true) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }

    val currentPage by remember {
        derivedStateOf { (listState.firstVisibleItemIndex + 1).coerceIn(1, totalPages) }
    }

    val searchMatches = remember(searchQuery, document.extractedText) {
        if (searchQuery.isBlank()) emptyList()
        else document.extractedText.lines().filter { it.contains(searchQuery, ignoreCase = true) }.take(5)
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = document.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("viewer_back_button")) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showSearch = !showSearch; if (!showSearch) searchQuery = "" }) {
                            Icon(Icons.Rounded.Search, contentDescription = "Search inside PDF")
                        }
                        IconButton(
                            onClick = { nightReading = !nightReading },
                            modifier = Modifier.testTag("viewer_night_mode_toggle")
                        ) {
                            Icon(
                                imageVector = if (nightReading) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                                contentDescription = "Toggle night reading mode"
                            )
                        }
                        IconButton(onClick = { showThumbs = !showThumbs }) {
                            Icon(Icons.Rounded.ViewCarousel, contentDescription = "Toggle thumbnails")
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = "More viewer options")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Share") },
                                    leadingIcon = { Icon(Icons.Rounded.Share, null) },
                                    onClick = { menuOpen = false; onShare(document) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Details") },
                                    leadingIcon = { Icon(Icons.Rounded.Info, null) },
                                    onClick = { menuOpen = false; showDetails = true }
                                )
                                DropdownMenuItem(
                                    text = { Text("Print") },
                                    leadingIcon = { Icon(Icons.Rounded.Print, null) },
                                    onClick = { menuOpen = false; onPrint(document) }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (document.isFavorite) "Remove favorite" else "Favorite") },
                                    leadingIcon = {
                                        Icon(
                                            if (document.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                            null,
                                            tint = if (document.isFavorite) BrandAmber else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    onClick = { menuOpen = false; onToggleFavorite(document) }
                                )
                            }
                        }
                    }
                )
                AnimatedVisibility(visible = showSearch) {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search text in document...") },
                            singleLine = true,
                            shape = CircleShape,
                            trailingIcon = {
                                IconButton(onClick = { searchQuery = ""; showSearch = false }) {
                                    Icon(Icons.Rounded.Close, "Close search")
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (searchQuery.isNotBlank()) {
                            Text(
                                text = if (searchMatches.isEmpty()) "No text matches found"
                                else "${searchMatches.size} match(es): \"${searchMatches.first().take(60)}\"",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 6.dp, start = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Row(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Side thumbnail panel on tablets
            if (isWideLayout && showThumbs) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, LocalPaperPilotColors.current.subtleBorder),
                    modifier = Modifier.width(116.dp).fillMaxHeight()
                ) {
                    LazyColumn(
                        contentPadding = PaddingValues(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(totalPages) { pageIdx ->
                            ViewerThumbnailChip(
                                file = file,
                                pageIdx = pageIdx,
                                selected = currentPage == pageIdx + 1,
                                onClick = { scope.launch { listState.animateScrollToItem(pageIdx) } }
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(if (nightReading) Color(0xFF0A090D) else MaterialTheme.colorScheme.background)
            ) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, _, zoom, _ ->
                                zoomScale = (zoomScale * zoom).coerceIn(1f, 3f)
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    zoomScale = if (zoomScale > 1.2f) 1f else 2.1f
                                }
                            )
                        }
                ) {
                    items(totalPages, key = { it }) { pageIdx ->
                        RenderedPdfPageCard(
                            file = file,
                            pageIndex = pageIdx,
                            nightMode = nightReading,
                            zoomScale = zoomScale
                        )
                    }
                    item { Spacer(Modifier.height(92.dp)) }
                }

                // Page indicator pill
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    border = BorderStroke(1.dp, LocalPaperPilotColors.current.subtleBorder),
                    shadowElevation = 3.dp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 10.dp)
                        .testTag("viewer_page_pill")
                ) {
                    Text(
                        text = "$currentPage / $totalPages" + if (zoomScale > 1.05f) " • ${(zoomScale * 100).toInt()}%" else "",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }

                // Floating Edit button
                FloatingActionButton(
                    onClick = { onOpenEditor(document) },
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 18.dp, bottom = if (!isWideLayout && showThumbs) 96.dp else 20.dp)
                        .navigationBarsPadding()
                        .testTag("viewer_edit_fab")
                ) {
                    Icon(Icons.Rounded.Edit, contentDescription = "Open in PDF Editor")
                }

                // Collapsible bottom thumbnail strip on phones
                if (!isWideLayout) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showThumbs,
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                            border = BorderStroke(1.dp, LocalPaperPilotColors.current.subtleBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.navigationBarsPadding()
                            ) {
                                items(totalPages) { pageIdx ->
                                    ViewerThumbnailChip(
                                        file = file,
                                        pageIdx = pageIdx,
                                        selected = currentPage == pageIdx + 1,
                                        onClick = { scope.launch { listState.animateScrollToItem(pageIdx) } }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDetails) {
        DocumentDetailsDialog(doc = document, onDismiss = { showDetails = false })
    }
}

@Composable
private fun RenderedPdfPageCard(
    file: File,
    pageIndex: Int,
    nightMode: Boolean,
    zoomScale: Float
) {
    var bitmap by remember(file.absolutePath, pageIndex, nightMode) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(file.absolutePath, pageIndex, nightMode) {
        bitmap = PdfRenderHelper.renderPage(file, pageIndex, targetWidthPx = 1080, nightMode = nightMode)
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (nightMode) Color(0xFF1A1722) else Color.White,
        shadowElevation = 3.dp,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = zoomScale
                scaleY = zoomScale
            }
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "PDF page ${pageIndex + 1}",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth().aspectRatio(0.71f).background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text("Rendering page ${pageIndex + 1}...", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ViewerThumbnailChip(
    file: File,
    pageIdx: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    var bmp by remember(file.absolutePath, pageIdx) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(file.absolutePath, pageIdx) {
        bmp = PdfRenderHelper.renderPage(file, pageIdx, targetWidthPx = 150)
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(52.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp, 60.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    width = if (selected) 2.5.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else LocalPaperPilotColors.current.subtleBorder,
                    shape = RoundedCornerShape(8.dp)
                )
        ) {
            bmp?.let {
                Image(it.asImageBitmap(), "Page ${pageIdx + 1}", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Text(
            text = "${pageIdx + 1}",
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else LocalPaperPilotColors.current.mutedText
        )
    }
}
