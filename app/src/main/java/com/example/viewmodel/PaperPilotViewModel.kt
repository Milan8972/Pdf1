package com.example.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.DocumentEntity
import com.example.data.DocumentRepository
import com.example.data.DocumentSource
import com.example.data.PaperPilotDatabase
import com.example.data.ScannedImageEntity
import com.example.data.SettingsStore
import com.example.data.SignatureEntity
import com.example.data.UserSettings
import com.example.engine.DocumentTemplate
import com.example.engine.EditableTextBlock
import com.example.engine.EditorTool
import com.example.engine.PageAnnotation
import com.example.engine.PdfFlattener
import com.example.engine.PdfRenderHelper
import com.example.engine.SamplePdfGenerator
import com.example.engine.ToolEngine
import com.example.engine.ToolOptions
import com.example.engine.ToolOutputResult
import com.example.model.ToolId
import com.example.ui.theme.AccentPreset
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class MainTab(val label: String) {
    HOME("Home"),
    FILES("Files"),
    TOOLS("Tools"),
    SCAN("Scan"),
    SETTINGS("Settings")
}

sealed class AppDestination {
    data object Onboarding : AppDestination()
    data object MainTabs : AppDestination()
    data class ToolFlow(val toolId: ToolId, val preselectedDocIds: List<Long> = emptyList()) : AppDestination()
    data class PdfViewer(val documentId: Long) : AppDestination()
    data class PdfEditor(val documentId: Long, val initialTool: EditorTool = EditorTool.EDIT_CONTENT) : AppDestination()
    data object ScannerCamera : AppDestination()
    data object TemplatesPicker : AppDestination()
}

data class SnackbarEvent(
    val message: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null
)

class PaperPilotViewModel(application: Application) : AndroidViewModel(application) {

    private val db = PaperPilotDatabase.getInstance(application)
    val repository = DocumentRepository(application, db)
    val settingsStore = SettingsStore(application)

    val documents: StateFlow<List<DocumentEntity>> = repository.allDocuments.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val signatures: StateFlow<List<SignatureEntity>> = repository.allSignatures.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val scannedImages: StateFlow<List<ScannedImageEntity>> = repository.allScannedImages.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val settings: StateFlow<UserSettings> = settingsStore.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UserSettings()
    )

    private val _selectedTab = MutableStateFlow(MainTab.HOME)
    val selectedTab: StateFlow<MainTab> = _selectedTab.asStateFlow()

    private val _backStack = MutableStateFlow<List<AppDestination>>(listOf(AppDestination.MainTabs))
    val backStack: StateFlow<List<AppDestination>> = _backStack.asStateFlow()

    private val _isLoadingInitial = MutableStateFlow(true)
    val isLoadingInitial: StateFlow<Boolean> = _isLoadingInitial.asStateFlow()

    private val _snackbarEvents = MutableSharedFlow<SnackbarEvent>(extraBufferCapacity = 4)
    val snackbarEvents = _snackbarEvents.asSharedFlow()

    // Pending password prompt when opening a protected document
    private val _passwordPromptDoc = MutableStateFlow<DocumentEntity?>(null)
    val passwordPromptDoc: StateFlow<DocumentEntity?> = _passwordPromptDoc.asStateFlow()

    private var activeToolJob: Job? = null

    init {
        viewModelScope.launch {
            repository.ensureSampleDocuments()
            _isLoadingInitial.value = false
        }
        viewModelScope.launch {
            settingsStore.settingsFlow.collect { s ->
                if (!s.onboardingComplete && _backStack.value == listOf(AppDestination.MainTabs)) {
                    _backStack.value = listOf(AppDestination.MainTabs, AppDestination.Onboarding)
                }
            }
        }
    }

    fun selectTab(tab: MainTab) {
        _selectedTab.value = tab
    }

    fun navigateTo(destination: AppDestination) {
        _backStack.value = _backStack.value + destination
    }

    fun navigateBack(): Boolean {
        val current = _backStack.value
        return if (current.size > 1) {
            _backStack.value = current.dropLast(1)
            true
        } else {
            false
        }
    }

    fun popToMainTabs(tab: MainTab? = null) {
        if (tab != null) _selectedTab.value = tab
        _backStack.value = listOf(AppDestination.MainTabs)
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            settingsStore.setOnboardingComplete(true)
            popToMainTabs(MainTab.HOME)
        }
    }

    fun openDocument(doc: DocumentEntity, bypassPassword: Boolean = false) {
        if (doc.isProtected && !bypassPassword) {
            _passwordPromptDoc.value = doc
            return
        }
        viewModelScope.launch {
            repository.touchOpened(doc.id)
        }
        navigateTo(AppDestination.PdfViewer(doc.id))
    }

    fun dismissPasswordPrompt() {
        _passwordPromptDoc.value = null
    }

    fun verifyAndOpenProtected(doc: DocumentEntity, password: String): Boolean {
        val ok = doc.passwordHash == null || DocumentRepository.hashPassword(password) == doc.passwordHash
        if (ok) {
            _passwordPromptDoc.value = null
            openDocument(doc, bypassPassword = true)
        }
        return ok
    }

    fun openEditor(doc: DocumentEntity, initialTool: EditorTool = EditorTool.EDIT_CONTENT) {
        navigateTo(AppDestination.PdfEditor(doc.id, initialTool))
    }

    fun openTool(toolId: ToolId, preselectedDocIds: List<Long> = emptyList()) {
        val s = settings.value
        if (toolId.isProTool && !s.isPro) {
            val today = todayDateKey()
            val countToday = if (s.lastUsageDate == today) s.dailyHeavyToolCount else 0
            if (countToday >= 2) {
                emitMessage("Daily free limit reached for ${toolId.title}. Unlock Pro in Settings.")
                popToMainTabs(MainTab.SETTINGS)
                return
            }
        }
        navigateTo(AppDestination.ToolFlow(toolId, preselectedDocIds))
    }

    fun importUris(uris: List<Uri>, source: DocumentSource = DocumentSource.OPENED, onImported: (List<DocumentEntity>) -> Unit = {}) {
        viewModelScope.launch {
            val imported = uris.mapNotNull { repository.importFromUri(it, source) }
            if (imported.isNotEmpty()) {
                emitMessage(
                    if (imported.size == 1) "Imported ${imported.first().name}"
                    else "Imported ${imported.size} files"
                )
            }
            onImported(imported)
        }
    }

    fun createFromTemplate(template: DocumentTemplate) {
        viewModelScope.launch {
            val (file, text) = SamplePdfGenerator.generateFromTemplate(template, repository.documentsDir)
            val doc = repository.registerOutputFile(
                file = file,
                displayName = template.defaultFileName,
                mimeType = "application/pdf",
                source = DocumentSource.CREATED,
                extractedText = text
            )
            emitMessage("Created ${doc.name}")
            popToMainTabs(MainTab.HOME)
            openEditor(doc, EditorTool.EDIT_CONTENT)
        }
    }

    fun toggleFavorite(doc: DocumentEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(doc.id, !doc.isFavorite)
            emitMessage(if (!doc.isFavorite) "Added to Favorites" else "Removed from Favorites")
        }
    }

    fun renameDocument(doc: DocumentEntity, newName: String) {
        viewModelScope.launch {
            repository.renameDocument(doc.id, newName)
            emitMessage("Renamed to $newName")
        }
    }

    fun deleteDocumentWithUndo(doc: DocumentEntity) {
        viewModelScope.launch {
            repository.deleteDocument(doc)
            _snackbarEvents.tryEmit(
                SnackbarEvent(
                    message = "Deleted ${doc.name}",
                    actionLabel = "Undo",
                    onAction = {
                        viewModelScope.launch { repository.restoreDocument(doc) }
                    }
                )
            )
        }
    }

    fun deleteDocumentsWithUndo(docs: List<DocumentEntity>) {
        if (docs.isEmpty()) return
        viewModelScope.launch {
            repository.deleteDocuments(docs)
            _snackbarEvents.tryEmit(
                SnackbarEvent(
                    message = "Deleted ${docs.size} files",
                    actionLabel = "Undo",
                    onAction = {
                        viewModelScope.launch {
                            docs.forEach { repository.restoreDocument(it) }
                        }
                    }
                )
            )
        }
    }

    fun runTool(
        tool: ToolId,
        selectedFiles: List<DocumentEntity>,
        options: ToolOptions,
        onProgress: (Float, String) -> Unit,
        onSuccess: (ToolOutputResult, DocumentEntity) -> Unit,
        onError: (String) -> Unit
    ) {
        activeToolJob?.cancel()
        activeToolJob = viewModelScope.launch {
            try {
                val result = ToolEngine.run(
                    tool = tool,
                    files = selectedFiles,
                    options = options,
                    outputDir = repository.documentsDir,
                    onProgress = onProgress
                )
                val savedDoc = repository.registerOutputFile(
                    file = result.outputFile,
                    displayName = result.displayName,
                    mimeType = result.mimeType,
                    source = DocumentSource.CREATED,
                    isProtected = result.isProtected,
                    passwordHash = result.passwordHash,
                    allowPrint = result.allowPrint,
                    allowCopy = result.allowCopy,
                    allowEdit = result.allowEdit,
                    extractedText = result.extractedText ?: ""
                )
                settingsStore.recordToolUsage(
                    toolKey = tool.name,
                    isHeavy = tool.isProTool || tool == ToolId.COMPRESS || tool == ToolId.MERGE,
                    todayKey = todayDateKey()
                )
                onSuccess(result, savedDoc)
            } catch (_: CancellationException) {
                onError("Cancelled")
            } catch (e: Exception) {
                onError(e.message ?: "Processing failed. Please try again.")
            }
        }
    }

    fun cancelActiveTool() {
        activeToolJob?.cancel()
    }

    fun saveEditedPdf(
        originalDoc: DocumentEntity,
        annotations: List<PageAnnotation>,
        editedContentBlocks: List<EditableTextBlock> = emptyList(),
        overwriteOriginal: Boolean,
        onSaved: (DocumentEntity) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val baseName = originalDoc.name.substringBeforeLast(".")
                val outFile = if (overwriteOriginal) {
                    File(originalDoc.filePath)
                } else {
                    File(repository.documentsDir, "${baseName}_edited_${System.currentTimeMillis()}.pdf")
                }
                val tempTarget = File(repository.documentsDir, "tmp_flat_${System.currentTimeMillis()}.pdf")
                PdfFlattener.flattenAnnotationsToPdf(
                    sourcePdf = File(originalDoc.filePath),
                    outputPdf = tempTarget,
                    annotations = annotations,
                    editedContentBlocks = editedContentBlocks
                )
                tempTarget.copyTo(outFile, overwrite = true)
                tempTarget.delete()

                val finalDoc = if (overwriteOriginal) {
                    val thumb = PdfRenderHelper.generateThumbnail(outFile, repository.thumbnailsDir)
                    val updated = originalDoc.copy(
                        sizeBytes = outFile.length(),
                        thumbnailPath = thumb ?: originalDoc.thumbnailPath,
                        lastOpenedAt = System.currentTimeMillis()
                    )
                    repository.updateDocument(updated)
                    updated
                } else {
                    repository.registerOutputFile(
                        file = outFile,
                        displayName = "${baseName}_edited.pdf",
                        mimeType = "application/pdf",
                        source = DocumentSource.CREATED,
                        extractedText = originalDoc.extractedText
                    )
                }
                emitMessage(if (overwriteOriginal) "Updated ${finalDoc.name}" else "Saved copy: ${finalDoc.name}")
                onSaved(finalDoc)
            } catch (e: Exception) {
                emitMessage("Save failed: ${e.message}")
            }
        }
    }

    fun saveTypedSignature(text: String, colorArgb: Int, onSaved: (SignatureEntity) -> Unit) {
        viewModelScope.launch {
            val outFile = File(repository.signaturesDir, "sig_typed_${System.currentTimeMillis()}.png")
            PdfFlattener.renderTypedSignatureBitmap(text, colorArgb, outFile)
            val entity = repository.saveSignature(text.ifBlank { "Typed Signature" }, "TYPED", outFile)
            onSaved(entity)
        }
    }

    fun saveDrawnSignature(strokes: List<List<Pair<Float, Float>>>, colorArgb: Int, onSaved: (SignatureEntity) -> Unit) {
        viewModelScope.launch {
            val outFile = File(repository.signaturesDir, "sig_drawn_${System.currentTimeMillis()}.png")
            PdfFlattener.renderDrawnSignatureBitmap(strokes, colorArgb, outFile)
            val entity = repository.saveSignature("Handwritten Signature", "DRAWN", outFile)
            onSaved(entity)
        }
    }

    fun saveImageSignature(uri: Uri, onSaved: (SignatureEntity) -> Unit) {
        viewModelScope.launch {
            val outFile = File(repository.signaturesDir, "sig_img_${System.currentTimeMillis()}.png")
            getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                outFile.outputStream().use { input.copyTo(it) }
            }
            if (outFile.exists()) {
                val entity = repository.saveSignature("Uploaded Signature", "IMAGE", outFile)
                onSaved(entity)
            }
        }
    }

    fun deleteSignature(id: Long) {
        viewModelScope.launch { repository.deleteSignature(id) }
    }

    fun saveScannedBatchToPdf(
        pageFiles: List<File>,
        filterNames: List<String>,
        enableOcr: Boolean,
        ocrLang: String,
        onCreated: (DocumentEntity) -> Unit
    ) {
        viewModelScope.launch {
            val stamp = System.currentTimeMillis()
            pageFiles.forEachIndexed { idx, file ->
                repository.addScannedImage(
                    name = "Scan_${idx + 1}.jpg",
                    imageFile = file,
                    filterName = filterNames.getOrElse(idx) { "Auto-enhance" }
                )
            }
            val tempDocs = pageFiles.mapIndexed { idx, f ->
                DocumentEntity(
                    name = "Page_${idx + 1}.jpg",
                    filePath = f.absolutePath,
                    mimeType = "image/jpeg",
                    sizeBytes = f.length()
                )
            }
            val result = ToolEngine.run(
                tool = ToolId.IMAGE_TO_PDF,
                files = tempDocs,
                options = ToolOptions(),
                outputDir = repository.documentsDir,
                onProgress = { _, _ -> }
            )
            val ocrNote = if (enableOcr) {
                "[Scanned with paperpilot OCR • $ocrLang • ${pageFiles.size} page(s)]"
            } else ""
            val saved = repository.registerOutputFile(
                file = result.outputFile,
                displayName = "Scan_${SimpleDateFormat("MMMdd_HHmm", Locale.US).format(Date(stamp))}.pdf",
                mimeType = "application/pdf",
                source = DocumentSource.SCANNED,
                extractedText = ocrNote
            )
            emitMessage("Created scanned PDF (${pageFiles.size} pages)")
            onCreated(saved)
        }
    }

    fun shareDocument(doc: DocumentEntity): Intent? = repository.createShareIntent(doc)

    fun printDocument(activityContext: android.content.Context, doc: DocumentEntity) {
        val file = File(doc.filePath)
        if (!file.exists()) {
            emitMessage("File unavailable to print")
            return
        }
        val printManager = activityContext.getSystemService(android.content.Context.PRINT_SERVICE) as? PrintManager
        if (printManager == null) {
            emitMessage("Printing is unavailable on this device")
            return
        }
        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes?,
                cancellationSignal: android.os.CancellationSignal?,
                callback: LayoutResultCallback?,
                extras: android.os.Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onLayoutCancelled()
                    return
                }
                val info = android.print.PrintDocumentInfo.Builder(doc.name)
                    .setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(doc.pageCount)
                    .build()
                callback?.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out android.print.PageRange>?,
                destination: android.os.ParcelFileDescriptor?,
                cancellationSignal: android.os.CancellationSignal?,
                callback: WriteResultCallback?
            ) {
                try {
                    file.inputStream().use { input ->
                        java.io.FileOutputStream(destination?.fileDescriptor).use { output ->
                            input.copyTo(output)
                        }
                    }
                    callback?.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback?.onWriteFailed(e.message)
                }
            }
        }
        printManager.print(doc.name, adapter, PrintAttributes.Builder().build())
    }

    // Settings mutators
    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settingsStore.setThemeMode(mode) }
    fun setAccentPreset(preset: AccentPreset) = viewModelScope.launch { settingsStore.setAccentPreset(preset) }
    fun setAskMobileData(v: Boolean) = viewModelScope.launch { settingsStore.setAskMobileData(v) }
    fun setDefaultSaveFolder(f: String) = viewModelScope.launch { settingsStore.setDefaultSaveFolder(f) }
    fun setOcrLanguage(l: String) = viewModelScope.launch { settingsStore.setOcrLanguage(l) }
    fun setDefaultImageQuality(q: Int) = viewModelScope.launch { settingsStore.setDefaultImageQuality(q) }
    fun setKeepOriginalFiles(k: Boolean) = viewModelScope.launch { settingsStore.setKeepOriginalFiles(k) }
    fun setLocalProfile(name: String, email: String) = viewModelScope.launch {
        settingsStore.setLocalProfile(name, email)
        emitMessage("Local profile saved on this device")
    }
    fun setProEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsStore.setProEnabled(enabled)
        emitMessage(if (enabled) "Pro tools unlocked (demo mode)" else "Switched to Free plan")
    }

    fun emitMessage(msg: String) {
        _snackbarEvents.tryEmit(SnackbarEvent(msg))
    }

    fun getQuickTools(): List<ToolId> {
        val counts = settings.value.usageMap()
        if (counts.isEmpty()) return ToolId.defaultQuickTools
        val sortedUsed = counts.entries
            .sortedByDescending { it.value }
            .mapNotNull { entry -> ToolId.entries.firstOrNull { it.name == entry.key } }
        return (sortedUsed + ToolId.defaultQuickTools).distinct().take(6)
    }

    private fun todayDateKey(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
}
