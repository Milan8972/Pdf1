package com.example.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.example.engine.PdfRenderHelper
import com.example.engine.SamplePdfGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

class DocumentRepository(
    private val context: Context,
    private val db: PaperPilotDatabase
) {
    private val docDao = db.documentDao()
    private val sigDao = db.signatureDao()
    private val scanDao = db.scannedImageDao()

    val documentsDir: File
        get() = File(context.filesDir, "paperpilot_docs").apply { mkdirs() }

    val thumbnailsDir: File
        get() = File(context.filesDir, "paperpilot_thumbs").apply { mkdirs() }

    val signaturesDir: File
        get() = File(context.filesDir, "paperpilot_sigs").apply { mkdirs() }

    val scansDir: File
        get() = File(context.filesDir, "paperpilot_scans").apply { mkdirs() }

    val allDocuments: Flow<List<DocumentEntity>> = docDao.getAllDocuments()
    val allSignatures: Flow<List<SignatureEntity>> = sigDao.getAllSignatures()
    val allScannedImages: Flow<List<ScannedImageEntity>> = scanDao.getAllScannedImages()

    suspend fun ensureSampleDocuments() = withContext(Dispatchers.IO) {
        if (docDao.count() > 0) {
            SamplePdfGenerator.refreshSamplePdfFilesIfNeeded(documentsDir)
            return@withContext
        }
        val samples = SamplePdfGenerator.generateDefaultSamples(documentsDir, thumbnailsDir)
        samples.forEach { docDao.insert(it) }
    }

    suspend fun getDocumentById(id: Long): DocumentEntity? = docDao.getById(id)

    suspend fun insertDocument(doc: DocumentEntity): Long = docDao.insert(doc)

    suspend fun updateDocument(doc: DocumentEntity) = docDao.update(doc)

    suspend fun toggleFavorite(id: Long, favorite: Boolean) = docDao.setFavorite(id, favorite)

    suspend fun renameDocument(id: Long, newName: String) {
        val clean = newName.trim().ifBlank { return }
        docDao.rename(id, clean)
    }

    suspend fun touchOpened(id: Long) = docDao.touchOpened(id)

    suspend fun deleteDocument(doc: DocumentEntity) = withContext(Dispatchers.IO) {
        docDao.delete(doc)
        // Keep physical file until undo window passes if needed, or remove thumbnail cleanly
    }

    suspend fun restoreDocument(doc: DocumentEntity): Long = docDao.insert(doc)

    suspend fun deleteDocuments(docs: List<DocumentEntity>) = withContext(Dispatchers.IO) {
        docDao.deleteByIds(docs.map { it.id })
    }

    suspend fun importFromUri(uri: Uri, source: DocumentSource = DocumentSource.OPENED): DocumentEntity? =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val mime = resolver.getType(uri) ?: "application/pdf"
            var displayName = "Imported_${System.currentTimeMillis()}"
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    cursor.getString(nameIndex)?.let { displayName = it }
                }
            }
            val safeName = displayName.replace(Regex("[^a-zA-Z0-9._ -]"), "_")
            val destFile = File(documentsDir, "${System.currentTimeMillis()}_$safeName")
            resolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext null

            val isPdf = mime.contains("pdf", ignoreCase = true) || safeName.endsWith(".pdf", true)
            val pageCount = if (isPdf) PdfRenderHelper.getPageCount(destFile) else 1
            val thumbPath = if (isPdf) {
                PdfRenderHelper.generateThumbnail(destFile, thumbnailsDir)
            } else if (mime.startsWith("image/")) {
                destFile.absolutePath
            } else null

            val entity = DocumentEntity(
                name = displayName,
                filePath = destFile.absolutePath,
                mimeType = mime,
                sizeBytes = destFile.length(),
                pageCount = pageCount,
                source = source.name,
                thumbnailPath = thumbPath,
                extractedText = if (isPdf) PdfRenderHelper.extractBasicText(destFile) else ""
            )
            val id = docDao.insert(entity)
            entity.copy(id = id)
        }

    suspend fun registerOutputFile(
        file: File,
        displayName: String,
        mimeType: String,
        source: DocumentSource = DocumentSource.CREATED,
        isProtected: Boolean = false,
        passwordHash: String? = null,
        allowPrint: Boolean = true,
        allowCopy: Boolean = true,
        allowEdit: Boolean = true,
        extractedText: String = ""
    ): DocumentEntity = withContext(Dispatchers.IO) {
        val isPdf = mimeType.contains("pdf", ignoreCase = true) || file.name.endsWith(".pdf", true)
        val pageCount = if (isPdf) PdfRenderHelper.getPageCount(file) else 1
        val thumbPath = when {
            isPdf -> PdfRenderHelper.generateThumbnail(file, thumbnailsDir)
            mimeType.startsWith("image/") -> file.absolutePath
            else -> null
        }
        val text = extractedText.ifBlank {
            if (isPdf) PdfRenderHelper.extractBasicText(file) else ""
        }
        val entity = DocumentEntity(
            name = displayName,
            filePath = file.absolutePath,
            mimeType = mimeType,
            sizeBytes = file.length(),
            pageCount = pageCount,
            source = source.name,
            isProtected = isProtected,
            passwordHash = passwordHash,
            allowPrint = allowPrint,
            allowCopy = allowCopy,
            allowEdit = allowEdit,
            thumbnailPath = thumbPath,
            extractedText = text
        )
        val id = docDao.insert(entity)
        entity.copy(id = id)
    }

    suspend fun saveSignature(label: String, type: String, imageFile: File): SignatureEntity {
        val entity = SignatureEntity(
            label = label,
            type = type,
            imagePath = imageFile.absolutePath
        )
        val id = sigDao.insert(entity)
        return entity.copy(id = id)
    }

    suspend fun deleteSignature(id: Long) = sigDao.deleteById(id)

    suspend fun addScannedImage(name: String, imageFile: File, filterName: String): ScannedImageEntity {
        val entity = ScannedImageEntity(
            name = name,
            imagePath = imageFile.absolutePath,
            filterName = filterName
        )
        val id = scanDao.insert(entity)
        return entity.copy(id = id)
    }

    suspend fun deleteScannedImage(id: Long) = scanDao.deleteById(id)

    fun createShareIntent(doc: DocumentEntity): Intent? {
        val file = File(doc.filePath)
        if (!file.exists()) return null
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = doc.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, doc.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun exportToUri(doc: DocumentEntity, destUri: Uri): Boolean {
        val file = File(doc.filePath)
        if (!file.exists()) return false
        return runCatching {
            context.contentResolver.openOutputStream(destUri)?.use { out ->
                file.inputStream().use { it.copyTo(out) }
            }
            true
        }.getOrDefault(false)
    }

    companion object {
        fun hashPassword(password: String): String {
            val bytes = MessageDigest.getInstance("SHA-256").digest(password.toByteArray())
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
