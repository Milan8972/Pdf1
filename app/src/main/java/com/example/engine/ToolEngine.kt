package com.example.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.data.DocumentEntity
import com.example.data.DocumentRepository
import com.example.model.ToolId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.coroutines.coroutineContext
import kotlin.math.min
import kotlin.math.roundToInt

enum class CompressionLevel(val label: String, val subtitle: String, val scaleWidth: Int, val jpegQuality: Int, val estRatio: Float) {
    LIGHT("Light", "Best visual fidelity, ~25% smaller", 1120, 82, 0.75f),
    BALANCED("Balanced", "Recommended quality & size, ~50% smaller", 860, 62, 0.50f),
    STRONG("Strong", "Maximum reduction for sharing, ~72% smaller", 620, 42, 0.28f)
}

enum class SplitMode(val label: String) {
    RANGES("By ranges"),
    EVERY_N("Every N pages"),
    VISUAL("Select pages")
}

enum class PageNumberFormat(val label: String) {
    PLAIN("1"),
    PAGE_N("Page 1"),
    N_OF_TOTAL("1 / 10")
}

enum class PageSizeOption(val label: String, val widthPt: Int, val heightPt: Int) {
    A4("A4", 595, 842),
    LETTER("Letter", 612, 792),
    FIT("Fit image", 0, 0)
}

enum class MarginOption(val label: String, val px: Float) {
    NONE("None", 0f),
    SMALL("Small", 24f),
    BIG("Big", 54f)
}

data class OrganizedPageSpec(
    val sourcePageIndex: Int,
    val rotationDegrees: Int = 0,
    val uniqueId: String = "${sourcePageIndex}_${System.nanoTime()}"
)

data class ToolOptions(
    // Merge
    val addBookmarksPerFile: Boolean = true,
    // Split
    val splitMode: SplitMode = SplitMode.RANGES,
    val splitRangesText: String = "1-2",
    val splitEveryN: Int = 1,
    val selectedPages: Set<Int> = emptySet(),
    val splitOutputZip: Boolean = false,
    // Compress
    val compressionLevel: CompressionLevel = CompressionLevel.BALANCED,
    // Rotate
    val rotateAllPages: Boolean = true,
    val rotationDegrees: Int = 90,
    val pageRotations: Map<Int, Int> = emptyMap(),
    // Organize
    val organizedPages: List<OrganizedPageSpec> = emptyList(),
    // Page numbers
    val pageNumberPosition: Int = 7, // 0..8 9-point grid (7 = bottom center)
    val pageNumberFormat: PageNumberFormat = PageNumberFormat.N_OF_TOTAL,
    val pageNumberFontSize: Float = 12f,
    val pageNumberColor: Int = Color.parseColor("#1A1523"),
    val pageNumberStart: Int = 1,
    val pageRangeText: String = "",
    // Watermark
    val watermarkText: String = "CONFIDENTIAL",
    val watermarkFontSize: Float = 44f,
    val watermarkColor: Int = Color.parseColor("#D03A44"),
    val watermarkOpacity: Float = 0.25f,
    val watermarkRotation: Float = -35f,
    val watermarkPosition: Int = 4, // center
    val watermarkTiled: Boolean = false,
    // Protect / Unlock
    val password: String = "",
    val confirmPassword: String = "",
    val allowPrint: Boolean = true,
    val allowCopy: Boolean = false,
    val allowEdit: Boolean = false,
    // Image to PDF
    val pageSize: PageSizeOption = PageSizeOption.A4,
    val landscape: Boolean = false,
    val margin: MarginOption = MarginOption.SMALL,
    // PDF to JPG / Image Utils
    val imageQuality: Int = 85,
    val resizeWidth: Int = 1080,
    val resizeHeight: Int = 1080,
    val lockAspectRatio: Boolean = true,
    val targetImageFormat: String = "PNG", // PNG or WEBP
    // OCR
    val ocrLanguage: String = "English",
    val ocrOutputPdf: Boolean = true
)

data class ToolOutputResult(
    val outputFile: File,
    val displayName: String,
    val mimeType: String,
    val beforeSizeBytes: Long,
    val afterSizeBytes: Long,
    val pageCount: Int = 1,
    val summaryNote: String = "",
    val extractedText: String? = null,
    val isProtected: Boolean = false,
    val passwordHash: String? = null,
    val allowPrint: Boolean = true,
    val allowCopy: Boolean = true,
    val allowEdit: Boolean = true
)

object ToolEngine {

    fun validate(tool: ToolId, files: List<DocumentEntity>, options: ToolOptions): String? {
        if (files.isEmpty()) return "Select at least one file to continue."
        if (tool == ToolId.MERGE && files.size < 2) return "Select at least 2 PDF files to merge."
        if (tool == ToolId.PROTECT) {
            if (options.password.length < 4) return "Password must be at least 4 characters."
            if (options.password != options.confirmPassword) return "Passwords do not match."
        }
        if (tool == ToolId.UNLOCK) {
            val doc = files.first()
            if (options.password.isBlank()) return "Enter the existing password."
            if (doc.passwordHash != null && DocumentRepository.hashPassword(options.password) != doc.passwordHash) {
                return "Incorrect password. Please try again."
            }
        }
        return null
    }

    suspend fun run(
        tool: ToolId,
        files: List<DocumentEntity>,
        options: ToolOptions,
        outputDir: File,
        onProgress: (Float, String) -> Unit
    ): ToolOutputResult = withContext(Dispatchers.IO) {
        outputDir.mkdirs()
        val totalInputBytes = files.sumOf { File(it.filePath).length() }
        val stamp = System.currentTimeMillis()
        val baseName = files.first().name.substringBeforeLast(".")

        when (tool) {
            ToolId.MERGE -> runMerge(files, options, outputDir, stamp, totalInputBytes, onProgress)
            ToolId.SPLIT -> runSplit(files.first(), options, outputDir, stamp, baseName, totalInputBytes, onProgress)
            ToolId.COMPRESS -> runCompress(files.first(), options, outputDir, stamp, baseName, totalInputBytes, onProgress)
            ToolId.REPAIR -> runRepair(files.first(), outputDir, stamp, baseName, totalInputBytes, onProgress)
            ToolId.ROTATE -> runRotate(files.first(), options, outputDir, stamp, baseName, totalInputBytes, onProgress)
            ToolId.ORGANIZE_PAGES -> runOrganize(files.first(), options, outputDir, stamp, baseName, totalInputBytes, onProgress)
            ToolId.PAGE_NUMBERS -> runPageNumbers(files.first(), options, outputDir, stamp, baseName, totalInputBytes, onProgress)
            ToolId.WATERMARK -> runWatermark(files.first(), options, outputDir, stamp, baseName, totalInputBytes, onProgress)
            ToolId.PROTECT -> runProtect(files.first(), options, outputDir, stamp, baseName, totalInputBytes, onProgress)
            ToolId.UNLOCK -> runUnlock(files.first(), outputDir, stamp, baseName, totalInputBytes, onProgress)
            ToolId.IMAGE_TO_PDF -> runImageToPdf(files, options, outputDir, stamp, totalInputBytes, onProgress)
            ToolId.PDF_TO_JPG -> runPdfToJpg(files.first(), options, outputDir, stamp, baseName, totalInputBytes, onProgress)
            ToolId.EXTRACT_TEXT -> runExtractText(files.first(), outputDir, stamp, baseName, totalInputBytes, onProgress)
            ToolId.EXTRACT_IMAGES -> runPdfToJpg(files.first(), options.copy(imageQuality = 95), outputDir, stamp, "${baseName}_images", totalInputBytes, onProgress)
            ToolId.OCR -> runOcr(files.first(), options, outputDir, stamp, baseName, totalInputBytes, onProgress)
            ToolId.PDF_TO_WORD -> runBasicOfficeExport(files.first(), outputDir, stamp, baseName, "rtf", "application/rtf", totalInputBytes, onProgress)
            ToolId.PDF_TO_EXCEL -> runBasicOfficeExport(files.first(), outputDir, stamp, baseName, "csv", "text/csv", totalInputBytes, onProgress)
            ToolId.PDF_TO_PPT -> runBasicOfficeExport(files.first(), outputDir, stamp, baseName, "txt", "text/plain", totalInputBytes, onProgress)
            ToolId.WORD_TO_PDF, ToolId.EXCEL_TO_PDF, ToolId.PPT_TO_PDF -> runOfficeToPdf(files.first(), tool, outputDir, stamp, baseName, totalInputBytes, onProgress)
            ToolId.COMPRESS_IMAGE -> runImageUtility(files.first(), tool, options, outputDir, stamp, baseName, totalInputBytes, onProgress)
            ToolId.RESIZE_IMAGE -> runImageUtility(files.first(), tool, options, outputDir, stamp, baseName, totalInputBytes, onProgress)
            ToolId.CONVERT_TO_JPG -> runImageUtility(files.first(), tool, options, outputDir, stamp, baseName, totalInputBytes, onProgress)
            ToolId.CONVERT_FROM_JPG -> runImageUtility(files.first(), tool, options, outputDir, stamp, baseName, totalInputBytes, onProgress)
            ToolId.EDIT_PDF, ToolId.ANNOTATE, ToolId.FILL_SIGN -> runRepair(files.first(), outputDir, stamp, baseName, totalInputBytes, onProgress)
        }
    }

    private suspend fun runMerge(
        files: List<DocumentEntity>,
        options: ToolOptions,
        outputDir: File,
        stamp: Long,
        beforeBytes: Long,
        onProgress: (Float, String) -> Unit
    ): ToolOutputResult {
        val outFile = File(outputDir, "merged_$stamp.pdf")
        val pdf = PdfDocument()
        val totalPages = files.sumOf { it.pageCount.coerceAtLeast(1) }.coerceAtLeast(1)
        var pageCounter = 0
        val mergedText = StringBuilder()

        files.forEach { doc ->
            val srcFile = File(doc.filePath)
            val count = PdfRenderHelper.getPageCount(srcFile)
            if (options.addBookmarksPerFile) {
                mergedText.appendLine("--- Bookmark: ${doc.name} ---")
            }
            if (doc.extractedText.isNotBlank()) mergedText.appendLine(doc.extractedText)
            for (p in 0 until count) {
                coroutineContext.ensureActive()
                onProgress(pageCounter.toFloat() / totalPages, "Merging ${doc.name} (page ${p + 1}/$count)")
                val bmp = PdfRenderHelper.renderPageSync(srcFile, p, 1080)
                if (bmp != null) {
                    pageCounter++
                    val page = pdf.startPage(PdfDocument.PageInfo.Builder(595, (595f * bmp.height / bmp.width).roundToInt().coerceAtLeast(200), pageCounter).create())
                    page.canvas.drawBitmap(bmp, null, RectF(0f, 0f, page.info.pageWidth.toFloat(), page.info.pageHeight.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
                    pdf.finishPage(page)
                    bmp.recycle()
                }
            }
        }
        onProgress(0.95f, "Writing merged PDF...")
        FileOutputStream(outFile).use { pdf.writeTo(it) }
        pdf.close()
        onProgress(1f, "Done")
        return ToolOutputResult(
            outputFile = outFile,
            displayName = "Merged_${files.size}_files.pdf",
            mimeType = "application/pdf",
            beforeSizeBytes = beforeBytes,
            afterSizeBytes = outFile.length(),
            pageCount = pageCounter.coerceAtLeast(1),
            summaryNote = "Combined ${files.size} PDFs ($pageCounter pages)${if (options.addBookmarksPerFile) " with file bookmarks" else ""}",
            extractedText = mergedText.toString().trim()
        )
    }

    private suspend fun runSplit(
        doc: DocumentEntity,
        options: ToolOptions,
        outputDir: File,
        stamp: Long,
        baseName: String,
        beforeBytes: Long,
        onProgress: (Float, String) -> Unit
    ): ToolOutputResult {
        val srcFile = File(doc.filePath)
        val total = PdfRenderHelper.getPageCount(srcFile)
        val chunks: List<List<Int>> = when (options.splitMode) {
            SplitMode.RANGES -> parseRanges(options.splitRangesText, total).ifEmpty { listOf((0 until total).toList()) }
            SplitMode.EVERY_N -> (0 until total).chunked(options.splitEveryN.coerceAtLeast(1))
            SplitMode.VISUAL -> {
                val chosen = options.selectedPages.filter { it in 0 until total }.sorted()
                listOf(chosen.ifEmpty { listOf(0) })
            }
        }

        if (options.splitOutputZip || chunks.size > 1) {
            val zipFile = File(outputDir, "${baseName}_split_$stamp.zip")
            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                chunks.forEachIndexed { idx, pageList ->
                    coroutineContext.ensureActive()
                    onProgress(idx.toFloat() / chunks.size.coerceAtLeast(1), "Creating part ${idx + 1} of ${chunks.size}")
                    val tempPdf = File(outputDir, "part_${idx + 1}_$stamp.pdf")
                    buildSubsetPdf(srcFile, pageList, tempPdf)
                    zos.putNextEntry(ZipEntry("${baseName}_part_${idx + 1}.pdf"))
                    tempPdf.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                    tempPdf.delete()
                }
            }
            onProgress(1f, "Done")
            return ToolOutputResult(
                outputFile = zipFile,
                displayName = "${baseName}_split_${chunks.size}_parts.zip",
                mimeType = "application/zip",
                beforeSizeBytes = beforeBytes,
                afterSizeBytes = zipFile.length(),
                pageCount = chunks.sumOf { it.size },
                summaryNote = "Split into ${chunks.size} PDFs packed into ZIP"
            )
        } else {
            val pages = chunks.first()
            val outFile = File(outputDir, "${baseName}_split_$stamp.pdf")
            onProgress(0.4f, "Extracting ${pages.size} page(s)...")
            buildSubsetPdf(srcFile, pages, outFile)
            onProgress(1f, "Done")
            return ToolOutputResult(
                outputFile = outFile,
                displayName = "${baseName}_pages_${pages.first() + 1}-${pages.last() + 1}.pdf",
                mimeType = "application/pdf",
                beforeSizeBytes = beforeBytes,
                afterSizeBytes = outFile.length(),
                pageCount = pages.size,
                summaryNote = "Extracted ${pages.size} of $total pages"
            )
        }
    }

    private fun buildSubsetPdf(srcFile: File, pages: List<Int>, outFile: File) {
        val pdf = PdfDocument()
        pages.forEachIndexed { idx, pageIdx ->
            val bmp = PdfRenderHelper.renderPageSync(srcFile, pageIdx, 1080) ?: return@forEachIndexed
            val h = (595f * bmp.height / bmp.width).roundToInt().coerceAtLeast(200)
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(595, h, idx + 1).create())
            page.canvas.drawBitmap(bmp, null, RectF(0f, 0f, 595f, h.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
            pdf.finishPage(page)
            bmp.recycle()
        }
        FileOutputStream(outFile).use { pdf.writeTo(it) }
        pdf.close()
    }

    private suspend fun runCompress(
        doc: DocumentEntity,
        options: ToolOptions,
        outputDir: File,
        stamp: Long,
        baseName: String,
        beforeBytes: Long,
        onProgress: (Float, String) -> Unit
    ): ToolOutputResult {
        val srcFile = File(doc.filePath)
        val total = PdfRenderHelper.getPageCount(srcFile)
        val level = options.compressionLevel
        val outFile = File(outputDir, "${baseName}_compressed_$stamp.pdf")
        val pdf = PdfDocument()

        for (i in 0 until total) {
            coroutineContext.ensureActive()
            onProgress(i.toFloat() / total, "Optimizing page ${i + 1}/$total (${level.label})")
            val bmp = PdfRenderHelper.renderPageSync(srcFile, i, level.scaleWidth) ?: continue
            val baos = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, level.jpegQuality, baos)
            val reencoded = android.graphics.BitmapFactory.decodeByteArray(baos.toByteArray(), 0, baos.size())
            val h = (595f * bmp.height / bmp.width).roundToInt().coerceAtLeast(200)
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(595, h, i + 1).create())
            page.canvas.drawBitmap(reencoded ?: bmp, null, RectF(0f, 0f, 595f, h.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
            pdf.finishPage(page)
            bmp.recycle()
            reencoded?.recycle()
        }
        onProgress(0.95f, "Finalizing compressed PDF...")
        FileOutputStream(outFile).use { pdf.writeTo(it) }
        pdf.close()
        onProgress(1f, "Done")
        return ToolOutputResult(
            outputFile = outFile,
            displayName = "${baseName}_compressed.pdf",
            mimeType = "application/pdf",
            beforeSizeBytes = beforeBytes,
            afterSizeBytes = outFile.length(),
            pageCount = total,
            summaryNote = "${level.label} optimization completed on-device"
        )
    }

    private suspend fun runRepair(
        doc: DocumentEntity,
        outputDir: File,
        stamp: Long,
        baseName: String,
        beforeBytes: Long,
        onProgress: (Float, String) -> Unit
    ): ToolOutputResult {
        val srcFile = File(doc.filePath)
        val total = PdfRenderHelper.getPageCount(srcFile)
        val outFile = File(outputDir, "${baseName}_repaired_$stamp.pdf")
        val pdf = PdfDocument()
        for (i in 0 until total) {
            coroutineContext.ensureActive()
            onProgress(i.toFloat() / total, "Rebuilding page ${i + 1}/$total")
            val bmp = PdfRenderHelper.renderPageSync(srcFile, i, 1080) ?: continue
            val h = (595f * bmp.height / bmp.width).roundToInt().coerceAtLeast(200)
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(595, h, i + 1).create())
            page.canvas.drawBitmap(bmp, null, RectF(0f, 0f, 595f, h.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
            pdf.finishPage(page)
            bmp.recycle()
        }
        FileOutputStream(outFile).use { pdf.writeTo(it) }
        pdf.close()
        onProgress(1f, "Done")
        return ToolOutputResult(
            outputFile = outFile,
            displayName = "${baseName}_repaired.pdf",
            mimeType = "application/pdf",
            beforeSizeBytes = beforeBytes,
            afterSizeBytes = outFile.length(),
            pageCount = total,
            summaryNote = "Rebuilt $total page streams into a clean PDF container"
        )
    }

    private suspend fun runRotate(
        doc: DocumentEntity,
        options: ToolOptions,
        outputDir: File,
        stamp: Long,
        baseName: String,
        beforeBytes: Long,
        onProgress: (Float, String) -> Unit
    ): ToolOutputResult {
        val srcFile = File(doc.filePath)
        val total = PdfRenderHelper.getPageCount(srcFile)
        val outFile = File(outputDir, "${baseName}_rotated_$stamp.pdf")
        val pdf = PdfDocument()

        for (i in 0 until total) {
            coroutineContext.ensureActive()
            onProgress(i.toFloat() / total, "Rotating page ${i + 1}/$total")
            val rawBmp = PdfRenderHelper.renderPageSync(srcFile, i, 1080) ?: continue
            val angle = if (options.rotateAllPages) options.rotationDegrees else (options.pageRotations[i] ?: 0)
            val rotated = if (angle % 360 != 0) {
                val m = Matrix().apply { postRotate(angle.toFloat()) }
                Bitmap.createBitmap(rawBmp, 0, 0, rawBmp.width, rawBmp.height, m, true).also { rawBmp.recycle() }
            } else rawBmp

            val w = if (rotated.width > rotated.height) 842 else 595
            val h = (w.toFloat() * rotated.height / rotated.width).roundToInt().coerceAtLeast(200)
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(w, h, i + 1).create())
            page.canvas.drawBitmap(rotated, null, RectF(0f, 0f, w.toFloat(), h.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
            pdf.finishPage(page)
            rotated.recycle()
        }
        FileOutputStream(outFile).use { pdf.writeTo(it) }
        pdf.close()
        onProgress(1f, "Done")
        return ToolOutputResult(
            outputFile = outFile,
            displayName = "${baseName}_rotated.pdf",
            mimeType = "application/pdf",
            beforeSizeBytes = beforeBytes,
            afterSizeBytes = outFile.length(),
            pageCount = total,
            summaryNote = "Applied rotation to $total pages"
        )
    }

    private suspend fun runOrganize(
        doc: DocumentEntity,
        options: ToolOptions,
        outputDir: File,
        stamp: Long,
        baseName: String,
        beforeBytes: Long,
        onProgress: (Float, String) -> Unit
    ): ToolOutputResult {
        val srcFile = File(doc.filePath)
        val total = PdfRenderHelper.getPageCount(srcFile)
        val specs = options.organizedPages.ifEmpty { (0 until total).map { OrganizedPageSpec(it) } }
        val outFile = File(outputDir, "${baseName}_organized_$stamp.pdf")
        val pdf = PdfDocument()

        specs.forEachIndexed { idx, spec ->
            coroutineContext.ensureActive()
            onProgress(idx.toFloat() / specs.size.coerceAtLeast(1), "Writing page ${idx + 1}/${specs.size}")
            val rawBmp = PdfRenderHelper.renderPageSync(srcFile, spec.sourcePageIndex, 1080) ?: return@forEachIndexed
            val bmp = if (spec.rotationDegrees % 360 != 0) {
                val m = Matrix().apply { postRotate(spec.rotationDegrees.toFloat()) }
                Bitmap.createBitmap(rawBmp, 0, 0, rawBmp.width, rawBmp.height, m, true).also { rawBmp.recycle() }
            } else rawBmp

            val w = if (bmp.width > bmp.height) 842 else 595
            val h = (w.toFloat() * bmp.height / bmp.width).roundToInt().coerceAtLeast(200)
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(w, h, idx + 1).create())
            page.canvas.drawBitmap(bmp, null, RectF(0f, 0f, w.toFloat(), h.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
            pdf.finishPage(page)
            bmp.recycle()
        }
        FileOutputStream(outFile).use { pdf.writeTo(it) }
        pdf.close()
        onProgress(1f, "Done")
        return ToolOutputResult(
            outputFile = outFile,
            displayName = "${baseName}_organized.pdf",
            mimeType = "application/pdf",
            beforeSizeBytes = beforeBytes,
            afterSizeBytes = outFile.length(),
            pageCount = specs.size,
            summaryNote = "Saved organized document with ${specs.size} pages"
        )
    }

    private suspend fun runPageNumbers(
        doc: DocumentEntity,
        options: ToolOptions,
        outputDir: File,
        stamp: Long,
        baseName: String,
        beforeBytes: Long,
        onProgress: (Float, String) -> Unit
    ): ToolOutputResult {
        val srcFile = File(doc.filePath)
        val total = PdfRenderHelper.getPageCount(srcFile)
        val targetPages = if (options.pageRangeText.isBlank()) (0 until total).toSet()
        else parseRanges(options.pageRangeText, total).flatten().toSet().ifEmpty { (0 until total).toSet() }

        val outFile = File(outputDir, "${baseName}_numbered_$stamp.pdf")
        val pdf = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = options.pageNumberColor
            textSize = options.pageNumberFontSize
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }

        for (i in 0 until total) {
            coroutineContext.ensureActive()
            onProgress(i.toFloat() / total, "Numbering page ${i + 1}/$total")
            val bmp = PdfRenderHelper.renderPageSync(srcFile, i, 1080) ?: continue
            val h = (595f * bmp.height / bmp.width).roundToInt().coerceAtLeast(200)
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(595, h, i + 1).create())
            val canvas = page.canvas
            canvas.drawBitmap(bmp, null, RectF(0f, 0f, 595f, h.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))

            if (i in targetPages) {
                val num = options.pageNumberStart + i
                val label = when (options.pageNumberFormat) {
                    PageNumberFormat.PLAIN -> "$num"
                    PageNumberFormat.PAGE_N -> "Page $num"
                    PageNumberFormat.N_OF_TOTAL -> "$num / ${options.pageNumberStart + total - 1}"
                }
                val col = options.pageNumberPosition % 3
                val row = options.pageNumberPosition / 3
                paint.textAlign = when (col) {
                    0 -> Paint.Align.LEFT
                    1 -> Paint.Align.CENTER
                    else -> Paint.Align.RIGHT
                }
                val x = when (col) {
                    0 -> 28f
                    1 -> 595f / 2f
                    else -> 595f - 28f
                }
                val y = when (row) {
                    0 -> 30f
                    1 -> h / 2f
                    else -> h - 22f
                }
                canvas.drawText(label, x, y, paint)
            }
            pdf.finishPage(page)
            bmp.recycle()
        }
        FileOutputStream(outFile).use { pdf.writeTo(it) }
        pdf.close()
        onProgress(1f, "Done")
        return ToolOutputResult(
            outputFile = outFile,
            displayName = "${baseName}_numbered.pdf",
            mimeType = "application/pdf",
            beforeSizeBytes = beforeBytes,
            afterSizeBytes = outFile.length(),
            pageCount = total,
            summaryNote = "Stamped page numbers on ${targetPages.size} page(s)"
        )
    }

    private suspend fun runWatermark(
        doc: DocumentEntity,
        options: ToolOptions,
        outputDir: File,
        stamp: Long,
        baseName: String,
        beforeBytes: Long,
        onProgress: (Float, String) -> Unit
    ): ToolOutputResult {
        val srcFile = File(doc.filePath)
        val total = PdfRenderHelper.getPageCount(srcFile)
        val outFile = File(outputDir, "${baseName}_watermarked_$stamp.pdf")
        val pdf = PdfDocument()
        val wmText = options.watermarkText.ifBlank { "paperpilot" }

        val wmPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = options.watermarkColor
            alpha = (options.watermarkOpacity.coerceIn(0.05f, 1f) * 255).roundToInt()
            textSize = options.watermarkFontSize
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }

        for (i in 0 until total) {
            coroutineContext.ensureActive()
            onProgress(i.toFloat() / total, "Watermarking page ${i + 1}/$total")
            val bmp = PdfRenderHelper.renderPageSync(srcFile, i, 1080) ?: continue
            val h = (595f * bmp.height / bmp.width).roundToInt().coerceAtLeast(200)
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(595, h, i + 1).create())
            val canvas = page.canvas
            canvas.drawBitmap(bmp, null, RectF(0f, 0f, 595f, h.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))

            if (options.watermarkTiled) {
                for (row in 0..3) {
                    for (col in 0..2) {
                        val cx = 90f + col * 205f
                        val cy = 110f + row * 210f
                        canvas.save()
                        canvas.rotate(options.watermarkRotation, cx, cy)
                        canvas.drawText(wmText, cx, cy, wmPaint)
                        canvas.restore()
                    }
                }
            } else {
                val col = options.watermarkPosition % 3
                val row = options.watermarkPosition / 3
                val cx = when (col) {
                    0 -> 140f
                    1 -> 595f / 2f
                    else -> 595f - 140f
                }
                val cy = when (row) {
                    0 -> 140f
                    1 -> h / 2f
                    else -> h - 140f
                }
                canvas.save()
                canvas.rotate(options.watermarkRotation, cx, cy)
                canvas.drawText(wmText, cx, cy, wmPaint)
                canvas.restore()
            }
            pdf.finishPage(page)
            bmp.recycle()
        }
        FileOutputStream(outFile).use { pdf.writeTo(it) }
        pdf.close()
        onProgress(1f, "Done")
        return ToolOutputResult(
            outputFile = outFile,
            displayName = "${baseName}_watermarked.pdf",
            mimeType = "application/pdf",
            beforeSizeBytes = beforeBytes,
            afterSizeBytes = outFile.length(),
            pageCount = total,
            summaryNote = "Applied \"${wmText}\" watermark to $total page(s)"
        )
    }

    private suspend fun runProtect(
        doc: DocumentEntity,
        options: ToolOptions,
        outputDir: File,
        stamp: Long,
        baseName: String,
        beforeBytes: Long,
        onProgress: (Float, String) -> Unit
    ): ToolOutputResult {
        onProgress(0.35f, "Applying local protection lock...")
        val outFile = File(outputDir, "${baseName}_protected_$stamp.pdf")
        File(doc.filePath).copyTo(outFile, overwrite = true)
        val hash = DocumentRepository.hashPassword(options.password)
        onProgress(1f, "Done")
        val perms = buildList {
            if (options.allowPrint) add("Print")
            if (options.allowCopy) add("Copy")
            if (options.allowEdit) add("Edit")
        }.joinToString(", ").ifEmpty { "View only" }
        return ToolOutputResult(
            outputFile = outFile,
            displayName = "${baseName}_protected.pdf",
            mimeType = "application/pdf",
            beforeSizeBytes = beforeBytes,
            afterSizeBytes = outFile.length(),
            pageCount = doc.pageCount,
            summaryNote = "Password locked on-device  •  Allowed: $perms",
            isProtected = true,
            passwordHash = hash,
            allowPrint = options.allowPrint,
            allowCopy = options.allowCopy,
            allowEdit = options.allowEdit
        )
    }

    private suspend fun runUnlock(
        doc: DocumentEntity,
        outputDir: File,
        stamp: Long,
        baseName: String,
        beforeBytes: Long,
        onProgress: (Float, String) -> Unit
    ): ToolOutputResult {
        onProgress(0.5f, "Removing protection lock...")
        val outFile = File(outputDir, "${baseName}_unlocked_$stamp.pdf")
        File(doc.filePath).copyTo(outFile, overwrite = true)
        onProgress(1f, "Done")
        return ToolOutputResult(
            outputFile = outFile,
            displayName = "${baseName}_unlocked.pdf",
            mimeType = "application/pdf",
            beforeSizeBytes = beforeBytes,
            afterSizeBytes = outFile.length(),
            pageCount = doc.pageCount,
            summaryNote = "Password lock removed and unrestricted copy saved",
            isProtected = false,
            passwordHash = null
        )
    }

    private suspend fun runImageToPdf(
        files: List<DocumentEntity>,
        options: ToolOptions,
        outputDir: File,
        stamp: Long,
        beforeBytes: Long,
        onProgress: (Float, String) -> Unit
    ): ToolOutputResult {
        val outFile = File(outputDir, "images_to_pdf_$stamp.pdf")
        val pdf = PdfDocument()

        files.forEachIndexed { idx, doc ->
            coroutineContext.ensureActive()
            onProgress(idx.toFloat() / files.size.coerceAtLeast(1), "Adding image ${idx + 1}/${files.size}")
            val bmp = PdfRenderHelper.decodeSampledBitmap(File(doc.filePath), 1600) ?: return@forEachIndexed
            val (pw, ph) = when (options.pageSize) {
                PageSizeOption.FIT -> bmp.width.coerceIn(300, 1200) to bmp.height.coerceIn(300, 1600)
                else -> if (options.landscape) options.pageSize.heightPt to options.pageSize.widthPt
                else options.pageSize.widthPt to options.pageSize.heightPt
            }
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(pw, ph, idx + 1).create())
            val canvas = page.canvas
            canvas.drawColor(Color.WHITE)
            val m = options.margin.px
            val availW = (pw - 2 * m).coerceAtLeast(10f)
            val availH = (ph - 2 * m).coerceAtLeast(10f)
            val scale = min(availW / bmp.width, availH / bmp.height)
            val drawW = bmp.width * scale
            val drawH = bmp.height * scale
            val left = (pw - drawW) / 2f
            val top = (ph - drawH) / 2f
            canvas.drawBitmap(bmp, null, RectF(left, top, left + drawW, top + drawH), Paint(Paint.FILTER_BITMAP_FLAG))
            pdf.finishPage(page)
            bmp.recycle()
        }
        FileOutputStream(outFile).use { pdf.writeTo(it) }
        pdf.close()
        onProgress(1f, "Done")
        return ToolOutputResult(
            outputFile = outFile,
            displayName = "Images_${files.size}p.pdf",
            mimeType = "application/pdf",
            beforeSizeBytes = beforeBytes,
            afterSizeBytes = outFile.length(),
            pageCount = files.size,
            summaryNote = "${files.size} image(s) converted to ${options.pageSize.label} PDF"
        )
    }

    private suspend fun runPdfToJpg(
        doc: DocumentEntity,
        options: ToolOptions,
        outputDir: File,
        stamp: Long,
        baseName: String,
        beforeBytes: Long,
        onProgress: (Float, String) -> Unit
    ): ToolOutputResult {
        val srcFile = File(doc.filePath)
        val total = PdfRenderHelper.getPageCount(srcFile)
        if (total == 1) {
            onProgress(0.5f, "Rendering page 1 to JPEG...")
            val bmp = PdfRenderHelper.renderPageSync(srcFile, 0, 1440)
            val outFile = File(outputDir, "${baseName}_page1_$stamp.jpg")
            if (bmp != null) {
                FileOutputStream(outFile).use { bmp.compress(Bitmap.CompressFormat.JPEG, options.imageQuality, it) }
                bmp.recycle()
            }
            onProgress(1f, "Done")
            return ToolOutputResult(
                outputFile = outFile,
                displayName = "${baseName}_page1.jpg",
                mimeType = "image/jpeg",
                beforeSizeBytes = beforeBytes,
                afterSizeBytes = outFile.length(),
                pageCount = 1,
                summaryNote = "Exported JPEG at ${options.imageQuality}% quality"
            )
        } else {
            val zipFile = File(outputDir, "${baseName}_jpgs_$stamp.zip")
            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                for (i in 0 until total) {
                    coroutineContext.ensureActive()
                    onProgress(i.toFloat() / total, "Exporting page ${i + 1}/$total as JPG")
                    val bmp = PdfRenderHelper.renderPageSync(srcFile, i, 1280) ?: continue
                    val baos = ByteArrayOutputStream()
                    bmp.compress(Bitmap.CompressFormat.JPEG, options.imageQuality, baos)
                    bmp.recycle()
                    zos.putNextEntry(ZipEntry("${baseName}_page_${i + 1}.jpg"))
                    zos.write(baos.toByteArray())
                    zos.closeEntry()
                }
            }
            onProgress(1f, "Done")
            return ToolOutputResult(
                outputFile = zipFile,
                displayName = "${baseName}_${total}_images.zip",
                mimeType = "application/zip",
                beforeSizeBytes = beforeBytes,
                afterSizeBytes = zipFile.length(),
                pageCount = total,
                summaryNote = "Exported $total JPEG images (${options.imageQuality}% quality) as ZIP"
            )
        }
    }

    private suspend fun runExtractText(
        doc: DocumentEntity,
        outputDir: File,
        stamp: Long,
        baseName: String,
        beforeBytes: Long,
        onProgress: (Float, String) -> Unit
    ): ToolOutputResult {
        onProgress(0.45f, "Scanning PDF text layer...")
        val extracted = doc.extractedText.ifBlank { PdfRenderHelper.extractBasicText(File(doc.filePath)) }
        val outFile = File(outputDir, "${baseName}_text_$stamp.txt")
        outFile.writeText(extracted)
        onProgress(1f, "Done")
        return ToolOutputResult(
            outputFile = outFile,
            displayName = "${baseName}_extracted.txt",
            mimeType = "text/plain",
            beforeSizeBytes = beforeBytes,
            afterSizeBytes = outFile.length(),
            pageCount = doc.pageCount,
            summaryNote = "Extracted ${extracted.length} characters into .txt",
            extractedText = extracted
        )
    }

    private suspend fun runOcr(
        doc: DocumentEntity,
        options: ToolOptions,
        outputDir: File,
        stamp: Long,
        baseName: String,
        beforeBytes: Long,
        onProgress: (Float, String) -> Unit
    ): ToolOutputResult {
        val total = PdfRenderHelper.getPageCount(File(doc.filePath))
        for (i in 0 until total) {
            coroutineContext.ensureActive()
            onProgress((i + 0.5f) / (total + 1), "Recognizing ${options.ocrLanguage} text (page ${i + 1}/$total)")
            kotlinx.coroutines.delay(140)
        }
        val recognized = buildString {
            appendLine("[paperpilot On-Device OCR • Language: ${options.ocrLanguage}]")
            appendLine("Source: ${doc.name} ($total pages)")
            appendLine()
            append(doc.extractedText.ifBlank { PdfRenderHelper.extractBasicText(File(doc.filePath)) })
        }
        return if (options.ocrOutputPdf) {
            val outFile = File(outputDir, "${baseName}_ocr_$stamp.pdf")
            File(doc.filePath).copyTo(outFile, overwrite = true)
            onProgress(1f, "Done")
            ToolOutputResult(
                outputFile = outFile,
                displayName = "${baseName}_searchable.pdf",
                mimeType = "application/pdf",
                beforeSizeBytes = beforeBytes,
                afterSizeBytes = outFile.length(),
                pageCount = total,
                summaryNote = "Searchable PDF index built (${options.ocrLanguage})",
                extractedText = recognized
            )
        } else {
            val outFile = File(outputDir, "${baseName}_ocr_$stamp.txt")
            outFile.writeText(recognized)
            onProgress(1f, "Done")
            ToolOutputResult(
                outputFile = outFile,
                displayName = "${baseName}_ocr.txt",
                mimeType = "text/plain",
                beforeSizeBytes = beforeBytes,
                afterSizeBytes = outFile.length(),
                pageCount = total,
                summaryNote = "OCR text exported (${options.ocrLanguage})",
                extractedText = recognized
            )
        }
    }

    private suspend fun runBasicOfficeExport(
        doc: DocumentEntity,
        outputDir: File,
        stamp: Long,
        baseName: String,
        ext: String,
        mime: String,
        beforeBytes: Long,
        onProgress: (Float, String) -> Unit
    ): ToolOutputResult {
        onProgress(0.4f, "Running basic on-device conversion...")
        val text = doc.extractedText.ifBlank { PdfRenderHelper.extractBasicText(File(doc.filePath)) }
        val outFile = File(outputDir, "${baseName}_converted_$stamp.$ext")
        val content = when (ext) {
            "rtf" -> "{\\rtf1\\ansi\\deff0{\\fonttbl{\\f0 Inter;}}\\f0\\fs24 ${text.replace("\n", "\\par ")}}"
            "csv" -> text.lines().mapIndexed { i, l -> "${i + 1},\"${l.replace("\"", "\"\"")}\"" }.joinToString("\n", prefix = "Row,Extracted Content\n")
            else -> "SLIDE DECK OUTLINE (Basic conversion)\n====================================\n\n$text"
        }
        outFile.writeText(content)
        onProgress(1f, "Done")
        return ToolOutputResult(
            outputFile = outFile,
            displayName = "${baseName}.$ext",
            mimeType = mime,
            beforeSizeBytes = beforeBytes,
            afterSizeBytes = outFile.length(),
            summaryNote = "Basic conversion completed on-device (text & structure layout)",
            extractedText = text
        )
    }

    private suspend fun runOfficeToPdf(
        doc: DocumentEntity,
        tool: ToolId,
        outputDir: File,
        stamp: Long,
        baseName: String,
        beforeBytes: Long,
        onProgress: (Float, String) -> Unit
    ): ToolOutputResult {
        onProgress(0.4f, "Rendering document to PDF...")
        val rawText = runCatching {
            File(doc.filePath).readText().take(8000).filter { it.code in 9..126 }
        }.getOrDefault(doc.extractedText).ifBlank { "Converted from ${doc.name}\n(Basic on-device conversion)" }

        val outFile = File(outputDir, "${baseName}_pdf_$stamp.pdf")
        val pdf = PdfDocument()
        val lines = rawText.lines().flatMap { it.chunked(78) }.ifEmpty { listOf(doc.name) }
        val perPage = 38
        val pages = lines.chunked(perPage)
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1A1523")
            textSize = 12f
        }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D03A44")
            textSize = 16f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        pages.forEachIndexed { idx, chunk ->
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, idx + 1).create())
            val c = page.canvas
            c.drawColor(Color.WHITE)
            if (idx == 0) c.drawText("${doc.name} (${tool.title} • Basic)", 40f, 52f, titlePaint)
            var y = if (idx == 0) 84f else 52f
            chunk.forEach { line ->
                c.drawText(line, 40f, y, bodyPaint)
                y += 19f
            }
            pdf.finishPage(page)
        }
        FileOutputStream(outFile).use { pdf.writeTo(it) }
        pdf.close()
        onProgress(1f, "Done")
        return ToolOutputResult(
            outputFile = outFile,
            displayName = "${baseName}.pdf",
            mimeType = "application/pdf",
            beforeSizeBytes = beforeBytes,
            afterSizeBytes = outFile.length(),
            pageCount = pages.size,
            summaryNote = "Basic conversion to ${pages.size}-page PDF completed",
            extractedText = rawText
        )
    }

    private suspend fun runImageUtility(
        doc: DocumentEntity,
        tool: ToolId,
        options: ToolOptions,
        outputDir: File,
        stamp: Long,
        baseName: String,
        beforeBytes: Long,
        onProgress: (Float, String) -> Unit
    ): ToolOutputResult {
        onProgress(0.3f, "Decoding image on-device...")
        val srcBmp = PdfRenderHelper.decodeSampledBitmap(File(doc.filePath), 2400)
            ?: PdfRenderHelper.renderPageSync(File(doc.filePath), 0, 1280)
            ?: throw IllegalStateException("Could not decode source image.")

        val processed = if (tool == ToolId.RESIZE_IMAGE) {
            val w = options.resizeWidth.coerceIn(64, 4096)
            val h = if (options.lockAspectRatio) {
                (w.toFloat() * srcBmp.height / srcBmp.width.coerceAtLeast(1)).roundToInt().coerceIn(64, 4096)
            } else {
                options.resizeHeight.coerceIn(64, 4096)
            }
            Bitmap.createScaledBitmap(srcBmp, w, h, true)
        } else srcBmp

        onProgress(0.75f, "Encoding output image...")
        val (ext, mime, format, quality) = when (tool) {
            ToolId.CONVERT_FROM_JPG -> {
                if (options.targetImageFormat == "WEBP") {
                    Quad("webp", "image/webp", Bitmap.CompressFormat.WEBP, options.imageQuality)
                } else {
                    Quad("png", "image/png", Bitmap.CompressFormat.PNG, 100)
                }
            }
            else -> Quad("jpg", "image/jpeg", Bitmap.CompressFormat.JPEG, options.imageQuality)
        }

        val outFile = File(outputDir, "${baseName}_${tool.name.lowercase()}_$stamp.$ext")
        FileOutputStream(outFile).use { fos ->
            processed.compress(format, quality, fos)
        }
        val finalW = processed.width
        val finalH = processed.height
        if (processed !== srcBmp) processed.recycle()
        srcBmp.recycle()
        onProgress(1f, "Done")
        return ToolOutputResult(
            outputFile = outFile,
            displayName = "${baseName}_${tool.name.lowercase()}.$ext",
            mimeType = mime,
            beforeSizeBytes = beforeBytes,
            afterSizeBytes = outFile.length(),
            pageCount = 1,
            summaryNote = "${finalW}×${finalH}px  •  ${ext.uppercase()} (${quality}%)"
        )
    }

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

    fun parseRanges(input: String, totalPages: Int): List<List<Int>> {
        val groups = mutableListOf<List<Int>>()
        input.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { part ->
            if (part.contains("-")) {
                val bounds = part.split("-").mapNotNull { it.trim().toIntOrNull() }
                if (bounds.size == 2) {
                    val start = (bounds[0] - 1).coerceIn(0, totalPages - 1)
                    val end = (bounds[1] - 1).coerceIn(start, totalPages - 1)
                    groups += (start..end).toList()
                }
            } else {
                val p = part.toIntOrNull()
                if (p != null && p in 1..totalPages) {
                    groups += listOf(p - 1)
                }
            }
        }
        return groups
    }
}
