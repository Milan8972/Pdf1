package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.CallMerge
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.Task
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
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
import com.example.ui.components.LogoVariant
import com.example.ui.components.PaperPilotLogo
import com.example.ui.components.RenameDocumentDialog
import com.example.ui.components.formatShortDate
import com.example.ui.theme.BrandCoral
import com.example.ui.theme.LocalPaperPilotColors

private enum class StorageFolder(val title: String) {
    INTERNAL_STORAGE("Internal Storage"),
    DOWNLOADS("Downloads"),
    PROCESSED("Processed files"),
    FAVORITES("Favorites"),
    BIN("Bin")
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
    trashedDocuments: List<DocumentEntity>,
    isWideLayout: Boolean,
    onOpenDocument: (DocumentEntity) -> Unit,
    onRenameDocument: (DocumentEntity, String) -> Unit,
    onShareDocument: (DocumentEntity) -> Unit,
    onToggleFavorite: (DocumentEntity) -> Unit,
    onDeleteDocument: (DocumentEntity) -> Unit,
    onDeleteMultiple: (List<DocumentEntity>) -> Unit,
    onRestoreFromBin: (DocumentEntity) -> Unit,
    onPermanentlyDelete: (DocumentEntity) -> Unit,
    onEmptyBin: () -> Unit,
    onMergeSelected: (List<DocumentEntity>) -> Unit,
    onImportFromDevice: (DocumentSource) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var activeFolder by remember { mutableStateOf<StorageFolder?>(null) }
    var hubSearchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var sortBy by remember { mutableStateOf(FileSort.DATE) }
    var isGridView by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val selectionMode = selectedIds.isNotEmpty()
    val selectedDocs = remember(documents, selectedIds) { documents.filter { it.id in selectedIds } }

    var renameTarget by remember { mutableStateOf<DocumentEntity?>(null) }
    var detailsTarget by remember { mutableStateOf<DocumentEntity?>(null) }
    var deleteConfirmSingle by remember { mutableStateOf<DocumentEntity?>(null) }
    var deleteConfirmMulti by remember { mutableStateOf(false) }
    var permanentDeleteTarget by remember { mutableStateOf<DocumentEntity?>(null) }
    var confirmEmptyBin by remember { mutableStateOf(false) }
    var wideFocusedDoc by remember { mutableStateOf<DocumentEntity?>(null) }

    val downloadsDocs = remember(documents) {
        documents.filter { it.source == DocumentSource.DOWNLOADS.name }
    }
    val processedDocs = remember(documents) {
        documents.filter { it.source == DocumentSource.CREATED.name || it.source == DocumentSource.SCANNED.name }
    }
    val favoriteDocs = remember(documents) {
        documents.filter { it.isFavorite }
    }

    // Handle system back when inside a storage folder or hub search
    BackHandler(enabled = activeFolder != null || hubSearchOpen) {
        when {
            selectionMode -> selectedIds = emptySet()
            activeFolder != null -> {
                activeFolder = null
                searchQuery = ""
                wideFocusedDoc = null
            }
            hubSearchOpen -> {
                hubSearchOpen = false
                searchQuery = ""
            }
        }
    }

    val currentFolder = activeFolder
    if (currentFolder == null) {
        // Storage Locations Overview Hub (Local storage + Others, zero cloud options)
        val hubSearchResults = remember(documents, searchQuery) {
            if (searchQuery.isBlank()) emptyList()
            else documents.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }

        Column(modifier = modifier.fillMaxSize()) {
            // Top bar matching paperpilot branding
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                PaperPilotLogo(size = 32.dp, variant = LogoVariant.FULL)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            hubSearchOpen = !hubSearchOpen
                            if (!hubSearchOpen) searchQuery = ""
                        },
                        modifier = Modifier.testTag("files_hub_search_button")
                    ) {
                        Icon(
                            imageVector = if (hubSearchOpen) Icons.Rounded.Close else Icons.Rounded.Search,
                            contentDescription = "Search local files"
                        )
                    }
                    Surface(
                        onClick = onOpenSettings,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(40.dp).testTag("files_hub_settings_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = "Open Settings",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            if (hubSearchOpen) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search files on this device...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        { IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Rounded.Close, "Clear search") } }
                    } else null,
                    singleLine = true,
                    shape = CircleShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 12.dp)
                        .testTag("files_search_input")
                )
            }

            if (hubSearchOpen && searchQuery.isNotBlank()) {
                if (hubSearchResults.isEmpty()) {
                    EmptyStateBlock(
                        title = "No matching files",
                        subtitle = "Try another filename or import a file from device storage.",
                        actionLabel = "Import from storage",
                        onAction = { onImportFromDevice(DocumentSource.OPENED) }
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(hubSearchResults, key = { it.id }) { doc ->
                            FileListRow(
                                doc = doc,
                                selected = false,
                                selectionMode = false,
                                onClick = { onOpenDocument(doc) },
                                onLongClick = {},
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
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // My storage section
                    item {
                        Text(
                            text = "My storage",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(top = 6.dp, bottom = 10.dp)
                        )
                    }

                    item {
                        StorageLocationRow(
                            icon = Icons.Rounded.PhoneAndroid,
                            title = "Internal Storage",
                            countText = documents.size.toString(),
                            testTag = "storage_row_internal",
                            onClick = { activeFolder = StorageFolder.INTERNAL_STORAGE }
                        )
                    }

                    item {
                        StorageLocationRow(
                            icon = Icons.Rounded.Download,
                            title = "Downloads",
                            countText = downloadsDocs.size.toString(),
                            testTag = "storage_row_downloads",
                            onClick = { activeFolder = StorageFolder.DOWNLOADS }
                        )
                    }

                    item {
                        StorageLocationRow(
                            icon = Icons.Rounded.Task,
                            title = "Processed files",
                            countText = processedDocs.size.toString(),
                            testTag = "storage_row_processed",
                            onClick = { activeFolder = StorageFolder.PROCESSED }
                        )
                    }

                    // Device storage import action option
                    item {
                        StorageImportActionRow(
                            icon = Icons.Rounded.UploadFile,
                            title = "Import from storage",
                            subtitle = "Add PDFs or images from your device",
                            actionLabel = "Add files",
                            testTag = "storage_row_import_device",
                            onClick = { onImportFromDevice(DocumentSource.DOWNLOADS) }
                        )
                    }

                    item { Spacer(Modifier.height(18.dp)) }

                    // Others section
                    item {
                        Text(
                            text = "Others",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                    }

                    item {
                        StorageLocationRow(
                            icon = Icons.Rounded.StarOutline,
                            title = "Favorites",
                            countText = favoriteDocs.size.toString(),
                            testTag = "storage_row_favorites",
                            onClick = { activeFolder = StorageFolder.FAVORITES }
                        )
                    }

                    item {
                        StorageLocationRow(
                            icon = Icons.Rounded.DeleteOutline,
                            title = "Bin",
                            countText = trashedDocuments.size.toString(),
                            testTag = "storage_row_bin",
                            onClick = { activeFolder = StorageFolder.BIN }
                        )
                    }

                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    } else if (currentFolder == StorageFolder.BIN) {
        // Bin folder view with Restore and Permanent Delete
        Column(modifier = modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                IconButton(
                    onClick = { activeFolder = null },
                    modifier = Modifier.testTag("files_folder_back")
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to My storage")
                }
                Text(
                    text = "Bin",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                if (trashedDocuments.isNotEmpty()) {
                    TextButton(
                        onClick = { confirmEmptyBin = true },
                        modifier = Modifier.testTag("files_empty_bin_button")
                    ) {
                        Text("Empty Bin", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            if (trashedDocuments.isEmpty()) {
                EmptyStateBlock(
                    title = "Bin is empty",
                    subtitle = "Deleted PDFs and images appear here so you can restore them anytime."
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(trashedDocuments, key = { it.id }) { doc ->
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, LocalPaperPilotColors.current.subtleBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(12.dp)
                            ) {
                                DocumentThumbnail(doc, Modifier.size(48.dp, 62.dp))
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = doc.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${PdfRenderHelper.formatFileSize(doc.sizeBytes)} • ${formatShortDate(doc.lastOpenedAt)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = LocalPaperPilotColors.current.mutedText
                                    )
                                }
                                IconButton(
                                    onClick = { onRestoreFromBin(doc) },
                                    modifier = Modifier.testTag("bin_restore_${doc.id}")
                                ) {
                                    Icon(
                                        Icons.Rounded.RestoreFromTrash,
                                        contentDescription = "Restore ${doc.name}",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(
                                    onClick = { permanentDeleteTarget = doc },
                                    modifier = Modifier.testTag("bin_delete_forever_${doc.id}")
                                ) {
                                    Icon(
                                        Icons.Rounded.DeleteForever,
                                        contentDescription = "Delete ${doc.name} permanently",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        // Active storage folder browser (Internal Storage, Downloads, Processed files, Favorites)
        val folderImportSource = if (currentFolder == StorageFolder.DOWNLOADS) {
            DocumentSource.DOWNLOADS
        } else {
            DocumentSource.OPENED
        }

        val filtered = remember(documents, currentFolder, searchQuery, sortBy) {
            val base = when (currentFolder) {
                StorageFolder.INTERNAL_STORAGE -> documents
                StorageFolder.DOWNLOADS -> documents.filter { it.source == DocumentSource.DOWNLOADS.name }
                StorageFolder.PROCESSED -> documents.filter {
                    it.source == DocumentSource.CREATED.name || it.source == DocumentSource.SCANNED.name
                }
                StorageFolder.FAVORITES -> documents.filter { it.isFavorite }
                StorageFolder.BIN -> emptyList()
            }
            val queryFiltered = if (searchQuery.isBlank()) base
            else base.filter { it.name.contains(searchQuery, ignoreCase = true) }

            when (sortBy) {
                FileSort.DATE -> queryFiltered.sortedByDescending { it.lastOpenedAt }
                FileSort.NAME -> queryFiltered.sortedBy { it.name.lowercase() }
                FileSort.SIZE -> queryFiltered.sortedByDescending { it.sizeBytes }
            }
        }

        Column(modifier = modifier.fillMaxSize()) {
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
                            Icon(Icons.Rounded.Delete, contentDescription = "Move selected to Bin", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    IconButton(
                        onClick = {
                            activeFolder = null
                            searchQuery = ""
                            wideFocusedDoc = null
                        },
                        modifier = Modifier.testTag("files_folder_back")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to My storage")
                    }
                    Text(
                        text = currentFolder.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (currentFolder == StorageFolder.INTERNAL_STORAGE || currentFolder == StorageFolder.DOWNLOADS) {
                        IconButton(
                            onClick = { onImportFromDevice(folderImportSource) },
                            modifier = Modifier.testTag("files_folder_import_button")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = "Import from device storage")
                        }
                    }
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

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Filter ${currentFolder.title.lowercase()}...") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = if (searchQuery.isNotEmpty()) {
                    { IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Rounded.Close, "Clear search") } }
                } else null,
                singleLine = true,
                shape = CircleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .testTag("files_search_input")
            )

            Spacer(Modifier.height(12.dp))

            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    if (filtered.isEmpty()) {
                        EmptyStateBlock(
                            title = "No files in ${currentFolder.title}",
                            subtitle = if (searchQuery.isNotBlank()) "Try a different search keyword."
                            else "Import PDFs or images from your device storage.",
                            actionLabel = "Import from storage",
                            onAction = { onImportFromDevice(folderImportSource) }
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
            title = "Move to Bin?",
            message = "Move \"${doc.name}\" to Bin? You can restore it anytime from Others → Bin.",
            onDismiss = { deleteConfirmSingle = null },
            onConfirm = {
                onDeleteDocument(doc)
                deleteConfirmSingle = null
            }
        )
    }
    if (deleteConfirmMulti) {
        ConfirmDeleteDialog(
            title = "Move ${selectedDocs.size} files to Bin?",
            message = "Selected documents will be moved to Bin where you can restore or delete them permanently.",
            onDismiss = { deleteConfirmMulti = false },
            onConfirm = {
                onDeleteMultiple(selectedDocs)
                selectedIds = emptySet()
                deleteConfirmMulti = false
            }
        )
    }
    permanentDeleteTarget?.let { doc ->
        ConfirmDeleteDialog(
            title = "Delete permanently?",
            message = "Permanently remove \"${doc.name}\" from this device? This cannot be undone.",
            onDismiss = { permanentDeleteTarget = null },
            onConfirm = {
                onPermanentlyDelete(doc)
                permanentDeleteTarget = null
            }
        )
    }
    if (confirmEmptyBin) {
        ConfirmDeleteDialog(
            title = "Empty Bin?",
            message = "Permanently delete all ${trashedDocuments.size} files in Bin? This cannot be undone.",
            onDismiss = { confirmEmptyBin = false },
            onConfirm = {
                onEmptyBin()
                confirmEmptyBin = false
            }
        )
    }
}

@Composable
private fun StorageLocationRow(
    icon: ImageVector,
    title: String,
    countText: String,
    testTag: String,
    onClick: () -> Unit
) {
    val extra = LocalPaperPilotColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 2.dp)
            .testTag(testTag)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (extra.isDark) 0.7f else 0.85f))
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = countText,
            style = MaterialTheme.typography.bodyLarge,
            color = extra.mutedText,
            modifier = Modifier.padding(end = 4.dp)
        )
    }
}

@Composable
private fun StorageImportActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String,
    testTag: String,
    onClick: () -> Unit
) {
    val extra = LocalPaperPilotColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 2.dp)
            .testTag(testTag)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(BrandCoral.copy(alpha = 0.14f))
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = BrandCoral,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = extra.mutedText
            )
        }
        Text(
            text = actionLabel,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, end = 4.dp)
        )
    }
}
