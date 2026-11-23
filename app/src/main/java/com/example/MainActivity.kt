package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ads.AdMobBannerBar
import com.example.ads.AdMobManager
import com.example.ads.findActivity
import com.example.data.DocumentEntity
import com.example.data.DocumentSource
import com.example.model.ToolId
import com.example.ui.components.LogoVariant
import com.example.ui.components.PaperPilotLogo
import com.example.ui.components.PasswordPromptDialog
import com.example.ui.screens.FilesScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.PdfEditorScreen
import com.example.ui.screens.PdfViewerScreen
import com.example.ui.screens.ScannerFlowScreen
import com.example.ui.screens.ScannerTabLanding
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TemplatesPickerScreen
import com.example.ui.screens.ToolFlowScreen
import com.example.ui.screens.ToolsScreen
import com.example.ui.theme.PaperPilotTheme
import com.example.viewmodel.AppDestination
import com.example.viewmodel.MainTab
import com.example.viewmodel.PaperPilotViewModel
import com.google.android.gms.ads.MobileAds

class MainActivity : ComponentActivity() {
    private var wentToBackground = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AdMobManager.configureTestDevices()
        MobileAds.initialize(this) {
            AdMobManager.onMobileAdsInitialized(this)
        }
        enableEdgeToEdge()
        setContent {
            PaperPilotApp()
        }
    }

    override fun onStop() {
        super.onStop()
        wentToBackground = true
    }

    override fun onStart() {
        super.onStart()
        if (wentToBackground) {
            wentToBackground = false
            AdMobManager.showAppOpenIfAvailable(this)
        }
    }
}

@Composable
fun PaperPilotApp(viewModel: PaperPilotViewModel = viewModel()) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val trashedDocuments by viewModel.trashedDocuments.collectAsStateWithLifecycle()
    val signatures by viewModel.signatures.collectAsStateWithLifecycle()
    val scannedImages by viewModel.scannedImages.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val backStack by viewModel.backStack.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoadingInitial.collectAsStateWithLifecycle()
    val passwordPromptDoc by viewModel.passwordPromptDoc.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val pendingImportSource = remember { androidx.compose.runtime.mutableStateOf(DocumentSource.OPENED) }

    LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collect { event ->
            val result = snackbarHostState.showSnackbar(
                message = event.message,
                actionLabel = event.actionLabel,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                event.onAction?.invoke()
            }
        }
    }

    val importDeviceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importUris(uris, pendingImportSource.value)
        }
    }

    fun launchDeviceImport(source: DocumentSource = DocumentSource.OPENED) {
        pendingImportSource.value = source
        importDeviceLauncher.launch(arrayOf("application/pdf", "image/*"))
    }

    fun triggerShare(doc: DocumentEntity) {
        val intent = viewModel.shareDocument(doc)
        if (intent != null) {
            context.startActivity(Intent.createChooser(intent, "Share ${doc.name}"))
        } else {
            viewModel.emitMessage("File not available to share")
        }
    }

    PaperPilotTheme(
        themeMode = settings.themeMode,
        accentPreset = settings.accentPreset
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isWideLayout = maxWidth >= 600.dp
            val toolColumns = when {
                maxWidth >= 840.dp -> 5
                maxWidth >= 600.dp -> 4
                else -> 3
            }

            val currentDestination = backStack.lastOrNull() ?: AppDestination.MainTabs

            // Android Back button support across tabs & full-screen destinations
            BackHandler(enabled = currentDestination != AppDestination.MainTabs || selectedTab != MainTab.HOME) {
                if (currentDestination != AppDestination.MainTabs) {
                    viewModel.navigateBack()
                } else if (selectedTab != MainTab.HOME) {
                    viewModel.selectTab(MainTab.HOME)
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when (val dest = currentDestination) {
                    AppDestination.Onboarding -> {
                        OnboardingScreen(onFinish = { viewModel.completeOnboarding() })
                    }

                    AppDestination.TemplatesPicker -> {
                        TemplatesPickerScreen(
                            onBack = { viewModel.navigateBack() },
                            onSelectTemplate = { tpl -> viewModel.createFromTemplate(tpl) }
                        )
                    }

                    AppDestination.ScannerCamera -> {
                        ScannerFlowScreen(
                            viewModel = viewModel,
                            defaultOcrLang = settings.ocrLanguage,
                            onBack = { viewModel.navigateBack() },
                            onPdfCreated = { created ->
                                AdMobManager.showInterstitialIfReady(context.findActivity()) {
                                    viewModel.popToMainTabs(MainTab.SCAN)
                                    viewModel.openDocument(created, bypassPassword = true)
                                }
                            }
                        )
                    }

                    is AppDestination.ToolFlow -> {
                        ToolFlowScreen(
                            toolId = dest.toolId,
                            preselectedIds = dest.preselectedDocIds,
                            allDocuments = documents,
                            viewModel = viewModel,
                            onBack = { viewModel.navigateBack() },
                            onOpenDocument = { doc ->
                                viewModel.popToMainTabs(MainTab.FILES)
                                viewModel.openDocument(doc, bypassPassword = true)
                            },
                            onOpenEditor = { doc, editorTool ->
                                viewModel.openEditor(doc, editorTool)
                            },
                            onShareDocument = ::triggerShare
                        )
                    }

                    is AppDestination.PdfViewer -> {
                        val doc = documents.firstOrNull { it.id == dest.documentId }
                        if (doc != null) {
                            PdfViewerScreen(
                                document = doc,
                                isWideLayout = isWideLayout,
                                onBack = { viewModel.navigateBack() },
                                onOpenEditor = { viewModel.openEditor(it) },
                                onShare = ::triggerShare,
                                onPrint = { viewModel.printDocument(context, it) },
                                onToggleFavorite = { viewModel.toggleFavorite(it) }
                            )
                        } else {
                            LaunchedEffect(dest.documentId) { viewModel.navigateBack() }
                        }
                    }

                    is AppDestination.PdfEditor -> {
                        val doc = documents.firstOrNull { it.id == dest.documentId }
                        if (doc != null) {
                            PdfEditorScreen(
                                document = doc,
                                initialTool = dest.initialTool,
                                savedSignatures = signatures,
                                viewModel = viewModel,
                                onBack = { viewModel.navigateBack() },
                                onSavedDocument = { saved ->
                                    AdMobManager.showInterstitialIfReady(context.findActivity()) {
                                        viewModel.popToMainTabs(MainTab.FILES)
                                        viewModel.openDocument(saved, bypassPassword = true)
                                    }
                                }
                            )
                        } else {
                            LaunchedEffect(dest.documentId) { viewModel.navigateBack() }
                        }
                    }

                    AppDestination.MainTabs -> {
                        MainTabsScaffold(
                            selectedTab = selectedTab,
                            isWideLayout = isWideLayout,
                            snackbarHostState = snackbarHostState,
                            onSelectTab = { viewModel.selectTab(it) }
                        ) { contentModifier ->
                            when (selectedTab) {
                                MainTab.HOME -> HomeScreen(
                                    documents = documents,
                                    quickTools = viewModel.getQuickTools(),
                                    isLoading = isLoading,
                                    onOpenDocument = { viewModel.openDocument(it) },
                                    onToggleFavorite = { viewModel.toggleFavorite(it) },
                                    onOpenTool = { viewModel.openTool(it) },
                                    onOpenScanner = { viewModel.navigateTo(AppDestination.ScannerCamera) },
                                    onOpenSettings = { viewModel.selectTab(MainTab.SETTINGS) },
                                    onSeeAllTools = { viewModel.selectTab(MainTab.TOOLS) },
                                    onImportFromDevice = {
                                        launchDeviceImport(DocumentSource.OPENED)
                                    },
                                    onCreateFromTemplate = {
                                        viewModel.navigateTo(AppDestination.TemplatesPicker)
                                    },
                                    modifier = contentModifier
                                )

                                MainTab.FILES -> FilesScreen(
                                    documents = documents,
                                    trashedDocuments = trashedDocuments,
                                    isWideLayout = isWideLayout,
                                    onOpenDocument = { viewModel.openDocument(it) },
                                    onRenameDocument = { doc, name -> viewModel.renameDocument(doc, name) },
                                    onShareDocument = ::triggerShare,
                                    onToggleFavorite = { viewModel.toggleFavorite(it) },
                                    onDeleteDocument = { viewModel.deleteDocumentWithUndo(it) },
                                    onDeleteMultiple = { viewModel.deleteDocumentsWithUndo(it) },
                                    onRestoreFromBin = { viewModel.restoreFromBin(it) },
                                    onPermanentlyDelete = { viewModel.permanentlyDeleteDocument(it) },
                                    onEmptyBin = { viewModel.emptyBin() },
                                    onMergeSelected = { docs ->
                                        viewModel.openTool(ToolId.MERGE, docs.map { it.id })
                                    },
                                    onImportFromDevice = { source ->
                                        launchDeviceImport(source)
                                    },
                                    onOpenSettings = { viewModel.selectTab(MainTab.SETTINGS) },
                                    modifier = contentModifier
                                )

                                MainTab.TOOLS -> ToolsScreen(
                                    columnsCount = toolColumns,
                                    onSelectTool = { viewModel.openTool(it) },
                                    modifier = contentModifier
                                )

                                MainTab.SCAN -> ScannerTabLanding(
                                    scannedImages = scannedImages,
                                    allDocuments = documents,
                                    onStartScan = { viewModel.navigateTo(AppDestination.ScannerCamera) },
                                    onOpenDocument = { viewModel.openDocument(it) },
                                    onShareDocument = ::triggerShare,
                                    onToggleFavorite = { viewModel.toggleFavorite(it) },
                                    onDeleteDocument = { viewModel.deleteDocumentWithUndo(it) },
                                    modifier = contentModifier
                                )

                                MainTab.SETTINGS -> SettingsScreen(
                                    settings = settings,
                                    onSetThemeMode = viewModel::setThemeMode,
                                    onSetAccentPreset = viewModel::setAccentPreset,
                                    onSetDefaultSaveFolder = viewModel::setDefaultSaveFolder,
                                    onSetOcrLanguage = viewModel::setOcrLanguage,
                                    onSetDefaultImageQuality = viewModel::setDefaultImageQuality,
                                    onSetKeepOriginalFiles = viewModel::setKeepOriginalFiles,
                                    onReplayOnboarding = { viewModel.navigateTo(AppDestination.Onboarding) },
                                    modifier = contentModifier
                                )
                            }
                        }
                    }
                }
            }

            // Global password prompt dialog when opening a locked PDF
            passwordPromptDoc?.let { lockedDoc ->
                PasswordPromptDialog(
                    doc = lockedDoc,
                    onDismiss = { viewModel.dismissPasswordPrompt() },
                    onVerify = { pwd -> viewModel.verifyAndOpenProtected(lockedDoc, pwd) }
                )
            }
        }
    }
}

@Composable
private fun MainTabsScaffold(
    selectedTab: MainTab,
    isWideLayout: Boolean,
    snackbarHostState: SnackbarHostState,
    onSelectTab: (MainTab) -> Unit,
    content: @Composable (Modifier) -> Unit
) {
    val tabIcons: Map<MainTab, Pair<ImageVector, ImageVector>> = mapOf(
        MainTab.HOME to (Icons.Rounded.Home to Icons.Outlined.Home),
        MainTab.FILES to (Icons.Rounded.Folder to Icons.Outlined.Folder),
        MainTab.TOOLS to (Icons.Rounded.Build to Icons.Outlined.Build),
        MainTab.SCAN to (Icons.Rounded.DocumentScanner to Icons.Outlined.DocumentScanner),
        MainTab.SETTINGS to (Icons.Rounded.Settings to Icons.Outlined.Settings)
    )

    if (isWideLayout) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surface,
                    header = {
                        Box(modifier = Modifier.padding(vertical = 14.dp)) {
                            PaperPilotLogo(size = 34.dp, variant = LogoVariant.BADGE)
                        }
                    }
                ) {
                    MainTab.entries.forEach { tab ->
                        val active = selectedTab == tab
                        val (filled, outlined) = tabIcons.getValue(tab)
                        NavigationRailItem(
                            selected = active,
                            onClick = { onSelectTab(tab) },
                            icon = { Icon(if (active) filled else outlined, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            colors = NavigationRailItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f).fillMaxSize()) {
                    content(Modifier.weight(1f).fillMaxSize())
                    AdMobBannerBar()
                }
            }
        }
    } else {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                Column {
                    AdMobBannerBar()
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp
                    ) {
                        MainTab.entries.forEach { tab ->
                            val active = selectedTab == tab
                            val (filled, outlined) = tabIcons.getValue(tab)
                            NavigationBarItem(
                                selected = active,
                                onClick = { onSelectTab(tab) },
                                icon = { Icon(if (active) filled else outlined, contentDescription = tab.label) },
                                label = { Text(tab.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            content(
                Modifier
                    .fillMaxSize()
                    .padding(bottom = innerPadding.calculateBottomPadding())
                    .windowInsetsPadding(WindowInsets.statusBars)
            )
        }
    }
}
