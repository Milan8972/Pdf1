package com.example.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class ScanFilter(val label: String) {
    ORIGINAL("Original"),
    AUTO_ENHANCE("Auto-enhance"),
    GRAYSCALE("Grayscale"),
    BLACK_WHITE("Black & White"),
    MAGIC_COLOR("Magic color")
}

object PdfRenderHelper {
    private val bitmapCache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun getPageCount(file: File): Int {
        if (!file.exists() || !file.name.endsWith(".pdf", ignoreCase = true)) return 1
        return runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer -> renderer.pageCount.coerceAtLeast(1) }
            }
        }.getOrDefault(1)
    }

    suspend fun renderPage(
        file: File,
        pageIndex: Int,
        targetWidthPx: Int = 960,
        nightMode: Boolean = false
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext null
        val cacheKey = "${file.absolutePath}_${file.lastModified()}_${pageIndex}_${targetWidthPx}_$nightMode"
        bitmapCache.get(cacheKey)?.let { if (!it.isRecycled) return@withContext it }

        val bmp = runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val safeIdx = pageIndex.coerceIn(0, (renderer.pageCount - 1).coerceAtLeast(0))
                    renderer.openPage(safeIdx).use { page ->
                        val scale = targetWidthPx.toFloat() / page.width.coerceAtLeast(1)
                        val w = targetWidthPx.coerceAtLeast(120)
                        val h = (page.height * scale).roundToInt().coerceAtLeast(120)
                        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bitmap)
                        canvas.drawColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        if (nightMode) invertBitmap(bitmap) else bitmap
                    }
                }
            }
        }.getOrNull()

        if (bmp != null) {
            bitmapCache.put(cacheKey, bmp)
        }
        bmp
    }

    fun renderPageSync(file: File, pageIndex: Int, targetWidthPx: Int = 1080): Bitmap? {
        if (!file.exists()) return null
        return runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val safeIdx = pageIndex.coerceIn(0, (renderer.pageCount - 1).coerceAtLeast(0))
                    renderer.openPage(safeIdx).use { page ->
                        val scale = targetWidthPx.toFloat() / page.width.coerceAtLeast(1)
                        val w = targetWidthPx.coerceAtLeast(120)
                        val h = (page.height * scale).roundToInt().coerceAtLeast(120)
                        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bitmap)
                        canvas.drawColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        bitmap
                    }
                }
            }
        }.getOrNull()
    }

    fun generateThumbnail(file: File, thumbsDir: File): String? {
        val bmp = renderPageSync(file, 0, targetWidthPx = 360) ?: return null
        thumbsDir.mkdirs()
        val outFile = File(thumbsDir, "thumb_${file.nameWithoutExtension}_${System.currentTimeMillis()}.jpg")
        return runCatching {
            FileOutputStream(outFile).use { fos ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 82, fos)
            }
            outFile.absolutePath
        }.getOrNull()
    }

    fun invertBitmap(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint()
        val matrix = ColorMatrix(
            floatArrayOf(
                -0.92f, 0f, 0f, 0f, 240f,
                0f, -0.92f, 0f, 0f, 238f,
                0f, 0f, -0.90f, 0f, 244f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        paint.colorFilter = ColorMatrixColorFilter(matrix)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return out
    }

    fun extractBasicText(file: File): String {
        if (!file.exists()) return ""
        return runCatching {
            val bytes = file.readBytes()
            val raw = String(bytes, Charsets.ISO_8859_1)
            val sb = StringBuilder()

            // First check embedded paperpilot text layer comments if present
            val markerRegex = Regex("%PP_TEXT:(.*)")
            val markerMatches = markerRegex.findAll(raw).map { it.groupValues[1].trim() }.toList()
            if (markerMatches.isNotEmpty()) {
                return@runCatching markerMatches.joinToString("\n")
            }

            // Standard PDF text operator scan: (Hello) Tj
            val tjRegex = Regex("\\(([^()]{2,200})\\)\\s*Tj")
            tjRegex.findAll(raw).forEach { match ->
                val cleaned = match.groupValues[1]
                    .replace("\\n", "\n")
                    .replace("\\(", "(")
                    .replace("\\)", ")")
                    .filter { it.code in 32..126 || it == '\n' }
                    .trim()
                if (cleaned.length >= 2) {
                    sb.append(cleaned).append("\n")
                }
            }
            val result = sb.toString().trim()
            result.ifBlank {
                "Document: ${file.name}\nPages: ${getPageCount(file)}\nSize: ${formatFileSize(file.length())}\n(Rendered vector/image PDF — run OCR to extract embedded visual text.)"
            }
        }.getOrDefault("")
    }

    fun applyScanTransform(
        src: Bitmap,
        filter: ScanFilter,
        brightness: Float = 0f, // -0.5..0.5
        contrast: Float = 1f,   // 0.5..2.0
        rotationDegrees: Float = 0f,
        cropLeft: Float = 0f,
        cropTop: Float = 0f,
        cropRight: Float = 1f,
        cropBottom: Float = 1f
    ): Bitmap {
        val oriented = if (rotationDegrees % 360f != 0f) {
            val matrix = Matrix().apply { postRotate(rotationDegrees) }
            Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        } else src

        val x = (oriented.width * cropLeft.coerceIn(0f, 0.9f)).roundToInt()
        val y = (oriented.height * cropTop.coerceIn(0f, 0.9f)).roundToInt()
        val w = (oriented.width * (cropRight - cropLeft).coerceIn(0.1f, 1f)).roundToInt()
            .coerceAtMost(oriented.width - x).coerceAtLeast(1)
        val h = (oriented.height * (cropBottom - cropTop).coerceIn(0.1f, 1f)).roundToInt()
            .coerceAtMost(oriented.height - y).coerceAtLeast(1)

        val cropped = Bitmap.createBitmap(oriented, x, y, w, h)
        if (oriented !== src && oriented !== cropped) {
            oriented.recycle()
        }

        val out = Bitmap.createBitmap(cropped.width, cropped.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val cm = ColorMatrix()

        when (filter) {
            ScanFilter.ORIGINAL -> Unit
            ScanFilter.AUTO_ENHANCE -> {
                cm.postConcat(contrastBrightnessMatrix(1.22f, 12f))
            }
            ScanFilter.GRAYSCALE -> {
                cm.setSaturation(0f)
                cm.postConcat(contrastBrightnessMatrix(1.15f, 8f))
            }
            ScanFilter.BLACK_WHITE -> {
                cm.setSaturation(0f)
                cm.postConcat(contrastBrightnessMatrix(2.4f, -75f))
            }
            ScanFilter.MAGIC_COLOR -> {
                cm.setSaturation(1.35f)
                cm.postConcat(contrastBrightnessMatrix(1.32f, 16f))
            }
        }

        if (contrast != 1f || brightness != 0f) {
            cm.postConcat(contrastBrightnessMatrix(contrast, brightness * 120f))
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(cm)
        }
        canvas.drawBitmap(cropped, 0f, 0f, paint)
        if (cropped !== src) {
            cropped.recycle()
        }
        return out
    }

    private fun contrastBrightnessMatrix(contrast: Float, brightness: Float): ColorMatrix {
        val translate = (-0.5f * contrast + 0.5f) * 255f + brightness
        return ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, translate,
                0f, contrast, 0f, 0f, translate,
                0f, 0f, contrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
    }

    fun decodeSampledBitmap(file: File, maxDimension: Int = 1600): Bitmap? {
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        val maxSide = max(bounds.outWidth, bounds.outHeight)
        while (maxSide / sampleSize > maxDimension) {
            sampleSize *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return null
        return applyExifOrientation(file, decoded)
    }

    private fun applyExifOrientation(file: File, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            android.media.ExifInterface(file.absolutePath)
                .getAttributeInt(
                    android.media.ExifInterface.TAG_ORIENTATION,
                    android.media.ExifInterface.ORIENTATION_NORMAL
                )
        }.getOrDefault(android.media.ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
            android.media.ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            android.media.ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            android.media.ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            android.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            android.media.ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            android.media.ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            android.media.ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        return runCatching {
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated !== bitmap) bitmap.recycle()
            rotated
        }.getOrDefault(bitmap)
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format("%.1f KB", kb)
        val mb = kb / 1024.0
        return String.format("%.2f MB", mb)
    }
}
