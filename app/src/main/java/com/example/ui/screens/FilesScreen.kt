package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMerge
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.DocumentEntity
import com.example.data.DocumentSource
import com.example.engine.PdfRenderHelper
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.DocumentDetailsDialog
import com.example.ui.components.DocumentThumbnail
import com.example.ui.components.EmptyStateBlock
import com.example.ui.components.FileListRow
import com.example.ui.components.RenameDocumentDialog
import com.example.ui.components.formatShortDate
import com.example.ui.theme.LocalPaperPilotColors

private enum class FilesSegment(val label: String) {
    ALL("All"),
    DOWNLOADS("Downloads"),
    CREATED("Created"),
    FAVORITES("Favorites")
}

private enum class FileSort(val label: String) {
    DATE("Date"),
    NAME("Name"),
    SIZE("Size")
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FilesScreen(
    documents: List<DocumentEntity>,
    isWideLayout: Boolean,
    onOpenDocument: (DocumentEntity) -> Unit,
    onRenameDocument: (DocumentEntity, String) -> Unit,
    onShareDocument: (DocumentEntity) -> Unit,
    onToggleFavorite: (DocumentEntity) -> Unit,
    onDeleteDocument: (DocumentEntity) -> Unit,
    onDeleteMultiple: (List<DocumentEntity>) -> Unit,
    onMergeSelected: (List<DocumentEntity>) -> Unit,
    onImportFromDevice: () -> Unit,
    modifier: Modifier = Modifier
) {
    var segment by remember { mutableStateOf(FilesSegment.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var sortBy by remember { mutableStateOf(FileSort.DATE) }
    var isGridView by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val selectionMode = selectedIds.isNotEmpty()

    var renameTarget by remember { mutableStateOf<DocumentEntity?>(null) }
    var detailsTarget by remember { mutableStateOf<DocumentEntity?>(null) }
    var deleteConfirmSingle by remember { mutableStateOf<DocumentEntity?>(null) }
    var deleteConfirmMulti by remember { mutableStateOf(false) }
    var wideFocusedDoc by remember { mutableStateOf<DocumentEntity?>(null) }

    val filtered = remember(documents, segment, searchQuery, sortBy) {
        val segFiltered = when (segment) {
            FilesSegment.ALL -> documents
            FilesSegment.DOWNLOADS -> documents.filter { it.source == DocumentSource.DOWNLOADS.name || it.source == DocumentSource.OPENED.name }
            FilesSegment.CREATED -> documents.filter { it.source == DocumentSource.CREATED.name || it.source == DocumentSource.SCANNED.name }
            FilesSegment.FAVORITES -> documents.filter { it.isFavorite }
        }
        val queryFiltered = if (searchQuery.isBlank()) segFiltered
        else segFiltered.filter { it.name.contains(searchQuery, ignoreCase = true) }

        when (sortBy) {
            FileSort.DATE -> queryFiltered.sortedByDescending { it.lastOpenedAt }
            FileSort.NAME -> queryFiltered.sortedBy { it.name.lowercase() }
            FileSort.SIZE -> queryFiltered.sortedByDescending { it.sizeBytes }
        }
    }

    val selectedDocs = remember(documents, selectedIds) { documents.filter { it.id in selectedIds } }

    Column(modifier = modifier.fillMaxSize()) {
        // Multi-select contextual top bar or standard header
        if (selectionMode) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    IconButton(onClick = { selectedIds = emptySet() }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Clear selection")
                    }
                    Text(
                        text = "${selectedIds.size} selected",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            onMergeSelected(selectedDocs)
                            selectedIds = emptySet()
                        },
                        enabled = selectedDocs.size >= 2
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.CallMerge, contentDescription = "Merge selected")
                    }
                    IconButton(onClick = {
                        selectedDocs.firstOrNull()?.let(onShareDocument)
                        selectedIds = emptySet()
                    }) {
                        Icon(Icons.Rounded.Share, contentDescription = "Share selected")
                    }
                    IconButton(onClick = {
                        selectedDocs.forEach(onToggleFavorite)
                        selectedIds = emptySet()
                    }) {
                        Icon(Icons.Rounded.Star, contentDescription = "Favorite selected")
                    }
                    IconButton(onClick = { deleteConfirmMulti = true }) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Delete selected", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Text(
                    text = "Files",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Row {
                    Box {
                        IconButton(onClick = { sortMenuExpanded = true }) {
                            Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = "Sort files")
                        }
                        DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                            FileSort.entries.forEach { s ->
                                DropdownMenuItem(
                                    text = { Text("Sort by ${s.label}${if (sortBy == s) " ✓" else ""}") },
                                    onClick = { sortBy = s; sortMenuExpanded = false }
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = { isGridView = !isGridView },
                        modifier = Modifier.testTag("files_layout_toggle")
                    ) {
                        Icon(
                            imageVector = if (isGridView) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView,
                            contentDescription = if (isGridView) "Switch to list view" else "Switch to grid view"
                        )
                    }
                }
            }
        }

        // Segmented tabs
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            FilesSegment.entries.forEachIndexed { idx, seg ->
                SegmentedButton(
                    selected = segment == seg,
                    onClick = { segment = seg },
                    shape = SegmentedButtonDefaults.itemShape(idx, FilesSegment.entries.size),
                    label = { Text(seg.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.testTag("files_tab_${seg.name.lowercase()}")
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Search input
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Filter by name...") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = if (searchQuery.isNotEmpty()) {
                { IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Rounded.Close, "Clear search") } }
            } else null,
            singleLine = true,
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).testTag("files_search_input")
        )

        Spacer(Modifier.height(12.dp))

        // Master-detail on tablet, single list/grid on phone
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                if (filtered.isEmpty()) {
                    EmptyStateBlock(
                        title = "No matching files",
                        subtitle = if (searchQuery.isNotBlank()) "Try a different search keyword." else "Import PDFs or create one using Tools.",
                        actionLabel = "Import a file",
                        onAction = onImportFromDevice
                    )
                } else if (isGridView) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(148.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filtered, key = { it.id }) { doc ->
                            val isSel = doc.id in selectedIds
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (isSel) MaterialTheme.colorScheme.primaryContainer.copy(0.4f) else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, if (isSel) MaterialTheme.colorScheme.primary else LocalPaperPilotColors.current.subtleBorder),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .combinedClickable(
                                        onClick = {
                                            if (selectionMode) {
                                                selectedIds = if (isSel) selectedIds - doc.id else selectedIds + doc.id
                                            } else if (isWideLayout) {
                                                wideFocusedDoc = doc
                                            } else {
                                                onOpenDocument(doc)
                                            }
                                        },
                                        onLongClick = { selectedIds = selectedIds + doc.id }
                                    )
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Box(modifier = Modifier.fillMaxWidth().aspectRatio(0.82f)) {
                                        DocumentThumbnail(doc, Modifier.fillMaxSize())
                                        if (isSel) {
                                            Icon(
                                                Icons.Rounded.CheckCircle,
                                                contentDescription = "Selected",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Text(doc.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "${PdfRenderHelper.formatFileSize(doc.sizeBytes)} • ${formatShortDate(doc.lastOpenedAt)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = LocalPaperPilotColors.current.mutedText
                                    )
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filtered, key = { it.id }) { doc ->
                            val isSel = doc.id in selectedIds
                            FileListRow(
                                doc = doc,
                                selected = isSel,
                                selectionMode = selectionMode,
                                onClick = {
                                    if (selectionMode) {
                                        selectedIds = if (isSel) selectedIds - doc.id else selectedIds + doc.id
                                    } else if (isWideLayout) {
                                        wideFocusedDoc = doc
                                    } else {
                                        onOpenDocument(doc)
                                    }
                                },
                                onLongClick = { selectedIds = selectedIds + doc.id },
                                onOpen = { onOpenDocument(doc) },
                                onRename = { renameTarget = doc },
                                onShare = { onShareDocument(doc) },
                                onFavorite = { onToggleFavorite(doc) },
                                onDelete = { deleteConfirmSingle = doc },
                                onDetails = { detailsTarget = doc }
                            )
                        }
                    }
                }
            }

            if (isWideLayout) {
                val previewDoc = wideFocusedDoc ?: filtered.firstOrNull()
                if (previewDoc != null) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, LocalPaperPilotColors.current.subtleBorder),
                        modifier = Modifier
                            .width(300.dp)
                            .fillMaxHeight()
                            .padding(end = 20.dp, top = 8.dp, bottom = 16.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(16.dp)
                        ) {
                            DocumentThumbnail(previewDoc, modifier = Modifier.fillMaxWidth().aspectRatio(0.78f))
                            Text(previewDoc.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${PdfRenderHelper.formatFileSize(previewDoc.sizeBytes)} • ${previewDoc.pageCount} pages",
                                style = MaterialTheme.typography.bodySmall,
                                color = LocalPaperPilotColors.current.mutedText
                            )
                            Button(
                                onClick = { onOpenDocument(previewDoc) },
                                shape = CircleShape,
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Icon(Icons.Rounded.OpenInNew, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Open Document")
                            }
                            OutlinedButton(
                                onClick = { onShareDocument(previewDoc) },
                                shape = CircleShape,
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Icon(Icons.Rounded.Share, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Share")
                            }
                        }
                    }
                }
            }
        }
    }

    renameTarget?.let { doc ->
        RenameDocumentDialog(
            doc = doc,
            onDismiss = { renameTarget = null },
            onConfirm = { newName ->
                onRenameDocument(doc, newName)
                renameTarget = null
            }
        )
    }
    detailsTarget?.let { doc ->
        DocumentDetailsDialog(doc = doc, onDismiss = { detailsTarget = null })
    }
    deleteConfirmSingle?.let { doc ->
        ConfirmDeleteDialog(
            title = "Delete file?",
            message = "Remove \"${doc.name}\" from your paperpilot library? You can undo right after.",
            onDismiss = { deleteConfirmSingle = null },
            onConfirm = {
                onDeleteDocument(doc)
                deleteConfirmSingle = null
            }
        )
    }
    if (deleteConfirmMulti) {
        ConfirmDeleteDialog(
            title = "Delete ${selectedDocs.size} files?",
            message = "Remove the selected documents from your library?",
            onDismiss = { deleteConfirmMulti = false },
            onConfirm = {
                onDeleteMultiple(selectedDocs)
                selectedIds = emptySet()
                deleteConfirmMulti = false
            }
        )
    }
}
