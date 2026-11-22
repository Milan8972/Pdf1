package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.DocumentEntity
import com.example.data.DocumentSource
import com.example.engine.DocumentTemplate
import com.example.model.ToolId
import com.example.ui.components.EmptyStateBlock
import com.example.ui.components.FileCardHorizontal
import com.example.ui.components.LogoVariant
import com.example.ui.components.PaperPilotLogo
import com.example.ui.components.ToolTile
import com.example.ui.theme.BrandAmber
import com.example.ui.theme.BrandCoral
import com.example.ui.theme.LocalPaperPilotColors
import com.example.ui.theme.TintScan

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    documents: List<DocumentEntity>,
    quickTools: List<ToolId>,
    isPro: Boolean,
    isLoading: Boolean,
    onOpenDocument: (DocumentEntity) -> Unit,
    onToggleFavorite: (DocumentEntity) -> Unit,
    onOpenTool: (ToolId) -> Unit,
    onOpenScanner: () -> Unit,
    onOpenSettings: () -> Unit,
    onSeeAllTools: () -> Unit,
    onImportFromDevice: () -> Unit,
    onCreateFromTemplate: () -> Unit,
    modifier: Modifier = Modifier
) {
    var recentFilter by remember { mutableStateOf("Opened") }
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showCreateSheet by remember { mutableStateOf(false) }
    val extra = LocalPaperPilotColors.current

    val recentDocs = remember(documents, recentFilter, searchQuery) {
        val base = if (recentFilter == "Created") {
            documents.filter { it.source == DocumentSource.CREATED.name || it.source == DocumentSource.SCANNED.name }
        } else {
            documents
        }
        val filtered = if (searchQuery.isBlank()) base else base.filter {
            it.name.contains(searchQuery, ignoreCase = true)
        }
        filtered.sortedByDescending { it.lastOpenedAt }.take(12)
    }

    val favoriteDocs = remember(documents) {
        documents.filter { it.isFavorite }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Top bar
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    PaperPilotLogo(size = 32.dp, variant = LogoVariant.FULL)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { searchOpen = !searchOpen; if (!searchOpen) searchQuery = "" },
                            modifier = Modifier.testTag("home_search_button")
                        ) {
                            Icon(
                                imageVector = if (searchOpen) Icons.Rounded.Close else Icons.Rounded.Search,
                                contentDescription = "Search recent files"
                            )
                        }
                        Surface(
                            onClick = onOpenSettings,
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(40.dp).testTag("home_profile_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Person,
                                    contentDescription = "Open Settings and Profile",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
                if (searchOpen) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search your PDFs...") },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        singleLine = true,
                        shape = CircleShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 8.dp)
                    )
                }
            }

            // Recent Files Section
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    ) {
                        Text(
                            text = "Recent files",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Opened", "Created").forEach { chip ->
                                FilterChip(
                                    selected = recentFilter == chip,
                                    onClick = { recentFilter = chip },
                                    label = { Text(chip) },
                                    modifier = Modifier.testTag("home_recent_chip_${chip.lowercase()}")
                                )
                            }
                        }
                    }

                    if (isLoading) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(3) {
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.size(152.dp, 224.dp)
                                ) {}
                            }
                        }
                    } else if (recentDocs.isEmpty()) {
                        EmptyStateBlock(
                            title = "No files yet",
                            subtitle = "Import a PDF or image from your device to get started privately.",
                            actionLabel = stringResource(R.string.action_import_file),
                            onAction = onImportFromDevice
                        )
                    } else {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(recentDocs, key = { it.id }) { doc ->
                                FileCardHorizontal(
                                    doc = doc,
                                    onOpen = { onOpenDocument(doc) },
                                    onToggleFavorite = { onToggleFavorite(doc) }
                                )
                            }
                        }
                    }
                }
            }

            // Quick Tools Row
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    ) {
                        Text(
                            text = "Quick tools",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        TextButton(onClick = onSeeAllTools) {
                            Text("All 28 tools")
                        }
                    }
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(quickTools, key = { it.name }) { tool ->
                            ToolTile(
                                tool = tool,
                                isProUnlocked = isPro,
                                onClick = { onOpenTool(tool) },
                                modifier = Modifier.width(108.dp)
                            )
                        }
                    }
                }
            }

            // Scan Paper into PDF Promo Card
            item {
                Surface(
                    onClick = onOpenScanner,
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, extra.subtleBorder),
                    shadowElevation = if (extra.isDark) 0.dp else 3.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .testTag("home_scan_promo_card")
                ) {
                    Column {
                        Box(modifier = Modifier.fillMaxWidth().height(152.dp)) {
                            Image(
                                painter = painterResource(id = R.drawable.img_scan_promo),
                                contentDescription = "Scan paper documents into crisp PDFs",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Surface(
                                shape = CircleShape,
                                color = TintScan,
                                modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
                            ) {
                                Text(
                                    text = "ON-DEVICE SCANNER",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth().padding(16.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Scan paper into PDF",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Auto edge guides, perspective crop, and crisp filters offline.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = extra.mutedText
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Button(
                                onClick = onOpenScanner,
                                shape = CircleShape,
                                modifier = Modifier.height(44.dp)
                            ) {
                                Icon(Icons.Rounded.DocumentScanner, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Scan")
                            }
                        }
                    }
                }
            }

            // Favorites Section
            if (favoriteDocs.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Favorites",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(favoriteDocs, key = { it.id }) { doc ->
                                FileCardHorizontal(
                                    doc = doc,
                                    onOpen = { onOpenDocument(doc) },
                                    onToggleFavorite = { onToggleFavorite(doc) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Floating "+" FAB
        FloatingActionButton(
            onClick = { showCreateSheet = true },
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .size(58.dp)
                .testTag("home_fab_add")
        ) {
            Icon(Icons.Rounded.Add, contentDescription = "Create or import document", modifier = Modifier.size(28.dp))
        }
    }

    if (showCreateSheet) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showCreateSheet = false },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp)
            ) {
                Text(
                    text = "Add to paperpilot",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                SheetActionRow(
                    icon = Icons.Rounded.UploadFile,
                    tint = BrandCoral,
                    title = "Import from device",
                    subtitle = "Pick PDFs or images via Android Document Picker",
                    testTag = "sheet_import_device"
                ) {
                    showCreateSheet = false
                    onImportFromDevice()
                }
                SheetActionRow(
                    icon = Icons.Rounded.CameraAlt,
                    tint = TintScan,
                    title = "Scan with camera",
                    subtitle = "Capture multi-page receipts, notes, or forms",
                    testTag = "sheet_scan_camera"
                ) {
                    showCreateSheet = false
                    onOpenScanner()
                }
                SheetActionRow(
                    icon = Icons.Rounded.Article,
                    tint = BrandAmber,
                    title = "Create from template",
                    subtitle = "Start with Resume, Invoice, Meeting Notes, or Lined Sheet",
                    testTag = "sheet_create_template"
                ) {
                    showCreateSheet = false
                    onCreateFromTemplate()
                }
            }
        }
    }
}

@Composable
private fun SheetActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    title: String,
    subtitle: String,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth().testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(14.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(tint.copy(alpha = 0.16f))
            ) {
                Icon(icon, contentDescription = null, tint = tint)
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = LocalPaperPilotColors.current.mutedText)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatesPickerScreen(
    onBack: () -> Unit,
    onSelectTemplate: (DocumentTemplate) -> Unit
) {
    val extra = LocalPaperPilotColors.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create from template") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            items(DocumentTemplate.entries) { tpl ->
                Surface(
                    onClick = { onSelectTemplate(tpl) },
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, extra.subtleBorder),
                    modifier = Modifier.fillMaxWidth().testTag("template_${tpl.name.lowercase()}")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    Brush.linearGradient(listOf(BrandCoral.copy(0.2f), BrandAmber.copy(0.2f)))
                                )
                        ) {
                            Icon(Icons.Rounded.Article, contentDescription = null, tint = BrandCoral)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(tpl.title, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(2.dp))
                            Text(tpl.subtitle, style = MaterialTheme.typography.bodySmall, color = extra.mutedText)
                        }
                    }
                }
            }
        }
    }
}
