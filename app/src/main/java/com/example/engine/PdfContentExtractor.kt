package com.example.engine

import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.zip.Inflater
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class EditableTextBlock(
    val id: String = UUID.randomUUID().toString(),
    val pageIndex: Int,
    // Original box used for background masking on save
    val origNormX: Float,
    val origNormY: Float,
    val origNormWidth: Float,
    val origNormHeight: Float,
    // Current live box (resizable & draggable)
    val normX: Float = origNormX,
    val normY: Float = origNormY,
    val normWidth: Float = origNormWidth,
    val normHeight: Float = origNormHeight,
    val originalText: String,
    val text: String = originalText,
    val fontSizePt: Float = 14f,
    val textColorArgb: Int = Color.parseColor("#1A1523"),
    val bgColorArgb: Int = Color.WHITE,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val fontFamilyName: String = "Sans",
    val align: TextAlignOption = TextAlignOption.LEFT,
    val lineSpacing: Float = 1.2f,
    val isModified: Boolean = false,
    val isDeleted: Boolean = false,
    val isUserAdded: Boolean = false
)

object PdfContentExtractor {

    suspend fun extractEditableBlocks(
        pdfFile: File,
        pageIndex: Int,
        pageBitmap: Bitmap?
    ): List<EditableTextBlock> = withContext(Dispatchers.IO) {
        if (!pdfFile.exists()) return@withContext emptyList()

        val bytes = runCatching { pdfFile.readBytes() }.getOrNull() ?: return@withContext emptyList()
        val rawLatin = String(bytes, Charsets.ISO_8859_1)

        // Pass 1: Check embedded exact block descriptors (from paperpilot generated PDFs/templates)
        val embedded = parseEmbeddedBlockDescriptors(rawLatin, pageIndex, pageBitmap)
        if (embedded.isNotEmpty()) {
            return@withContext embedded
        }

        // Pass 2: Parse real PDF content streams (both uncompressed and FlateDecode compressed)
        val streamBlocks = parsePdfContentStreams(bytes, rawLatin, pageIndex, pageBitmap)
        if (streamBlocks.isNotEmpty()) {
            return@withContext streamBlocks
        }

        // Pass 3: Visual ink-band fallback for scanned or CID-encoded PDFs
        if (pageBitmap != null) {
            return@withContext detectVisualTextBlocks(pageBitmap, pageIndex)
        }

        emptyList()
    }

    private fun parseEmbeddedBlockDescriptors(
        rawLatin: String,
        pageIndex: Int,
        bitmap: Bitmap?
    ): List<EditableTextBlock> {
        // Format: %PP_BLOCK:page|x|y|w|h|size|colorHex|bgHex|bold|align|text
        val regex = Regex("%PP_BLOCK:([^\\r\\n]+)")
        val list = mutableListOf<EditableTextBlock>()
        regex.findAll(rawLatin).forEach { match ->
            val parts = match.groupValues[1].split("|", limit = 11)
            if (parts.size == 11) {
                val pIdx = parts[0].toIntOrNull() ?: return@forEach
                if (pIdx != pageIndex) return@forEach
                val x = parts[1].toFloatOrNull() ?: return@forEach
                val y = parts[2].toFloatOrNull() ?: return@forEach
                val w = parts[3].toFloatOrNull() ?: return@forEach
                val h = parts[4].toFloatOrNull() ?: return@forEach
                val sizePt = parts[5].toFloatOrNull() ?: 14f
                val textColor = runCatching { Color.parseColor(parts[6]) }.getOrDefault(Color.parseColor("#1A1523"))
                val declaredBg = runCatching { Color.parseColor(parts[7]) }.getOrNull()
                val bold = parts[8] == "1"
                val align = runCatching { TextAlignOption.valueOf(parts[9]) }.getOrDefault(TextAlignOption.LEFT)
                val decodedText = parts[10].replace("\\n", "\n")
                val bg = declaredBg ?: sampleBackgroundColor(bitmap, x, y, w, h)
                list += EditableTextBlock(
                    pageIndex = pageIndex,
                    origNormX = x,
                    origNormY = y,
                    origNormWidth = w,
                    origNormHeight = h,
                    originalText = decodedText,
                    fontSizePt = sizePt,
                    textColorArgb = textColor,
                    bgColorArgb = bg,
                    bold = bold,
                    align = align
                )
            }
        }
        return list
    }

    private fun parsePdfContentStreams(
        bytes: ByteArray,
        rawLatin: String,
        pageIndex: Int,
        bitmap: Bitmap?
    ): List<EditableTextBlock> {
        // Detect page dimensions from MediaBox if available (defaults to A4 595x842)
        val mediaBoxRegex = Regex("/MediaBox\\s*\\[\\s*([\\d.]+)\\s+([\\d.]+)\\s+([\\d.]+)\\s+([\\d.]+)\\s*]")
        val mbMatch = mediaBoxRegex.find(rawLatin)
        val pageW = mbMatch?.groupValues?.getOrNull(3)?.toFloatOrNull()?.takeIf { it > 50f } ?: 595f
        val pageH = mbMatch?.groupValues?.getOrNull(4)?.toFloatOrNull()?.takeIf { it > 50f } ?: 842f

        // Collect all streams in the PDF that contain text blocks (BT ... ET)
        val pageTextStreams = mutableListOf<String>()
        var searchPos = 0
        while (searchPos < rawLatin.length) {
            val streamIdx = rawLatin.indexOf("stream", searchPos)
            if (streamIdx < 0) break
            val endStreamIdx = rawLatin.indexOf("endstream", streamIdx + 6)
            if (endStreamIdx < 0) break

            // Look back for dictionary to see if FlateDecode is used
            val dictStart = (streamIdx - 320).coerceAtLeast(0)
            val dictChunk = rawLatin.substring(dictStart, streamIdx)
            val isFlate = dictChunk.contains("/FlateDecode")

            var dataStart = streamIdx + 6
            if (dataStart < bytes.size && bytes[dataStart] == '\r'.code.toByte()) dataStart++
            if (dataStart < bytes.size && bytes[dataStart] == '\n'.code.toByte()) dataStart++
            var dataEnd = endStreamIdx
            while (dataEnd > dataStart && (bytes[dataEnd - 1] == '\n'.code.toByte() || bytes[dataEnd - 1] == '\r'.code.toByte())) {
                dataEnd--
            }

            if (dataEnd > dataStart) {
                val content = if (isFlate) {
                    inflateBytes(bytes, dataStart, dataEnd - dataStart)
                } else {
                    String(bytes, dataStart, dataEnd - dataStart, Charsets.ISO_8859_1)
                }
                if (content != null && content.contains("BT") && (content.contains("Tj") || content.contains("TJ"))) {
                    pageTextStreams += content
                }
            }
            searchPos = endStreamIdx + 9
        }

        if (pageTextStreams.isEmpty()) return emptyList()
        val targetStream = pageTextStreams.getOrElse(pageIndex) {
            if (pageIndex == 0) pageTextStreams.first() else return emptyList()
        }

        return extractPositionedRunsFromStream(targetStream, pageW, pageH, pageIndex, bitmap)
    }

    private fun inflateBytes(source: ByteArray, offset: Int, length: Int): String? {
        return runCatching {
            val inflater = Inflater()
            inflater.setInput(source, offset, length)
            val out = ByteArrayOutputStream()
            val buf = ByteArray(4096)
            var guard = 0
            while (!inflater.finished() && guard < 200) {
                val count = inflater.inflate(buf)
                if (count <= 0) break
                out.write(buf, 0, count)
                guard++
            }
            inflater.end()
            if (out.size() == 0) null else out.toString(Charsets.ISO_8859_1.name())
        }.getOrNull()
    }

    private data class RawTextRun(
        val xPt: Float,
        val yPtTop: Float,
        val fontSizePt: Float,
        val text: String,
        val colorArgb: Int
    )

    private fun extractPositionedRunsFromStream(
        stream: String,
        pageW: Float,
        pageH: Float,
        pageIndex: Int,
        bitmap: Bitmap?
    ): List<EditableTextBlock> {
        val runs = mutableListOf<RawTextRun>()
        val btRegex = Regex("BT([\\s\\S]*?)ET")
        val tfRegex = Regex("/\\S+\\s+([\\d.]+)\\s+Tf")
        val tmRegex = Regex("([\\d.-]+)\\s+([\\d.-]+)\\s+([\\d.-]+)\\s+([\\d.-]+)\\s+([\\d.-]+)\\s+([\\d.-]+)\\s+Tm")
        val tdRegex = Regex("([\\d.-]+)\\s+([\\d.-]+)\\s+T[dD]")
        val rgRegex = Regex("([\\d.]+)\\s+([\\d.]+)\\s+([\\d.]+)\\s+rg")
        val tjRegex = Regex("\\(([^()\\\\]*(?:\\\\.[^()\\\\]*)*)\\)\\s*Tj")
        val tjArrayRegex = Regex("\\[(.*?)]\\s*TJ")

        var currentColor = Color.parseColor("#1A1523")

        btRegex.findAll(stream).forEach { blockMatch ->
            val block = blockMatch.groupValues[1]
            var fontSize = 12f
            var curX = 48f
            var curYBottom = pageH - 80f

            // Walk line-by-line or token-by-token within BT...ET
            block.lines().forEach { line ->
                rgRegex.find(line)?.let { m ->
                    val r = ((m.groupValues[1].toFloatOrNull() ?: 0f) * 255).roundToInt().coerceIn(0, 255)
                    val g = ((m.groupValues[2].toFloatOrNull() ?: 0f) * 255).roundToInt().coerceIn(0, 255)
                    val b = ((m.groupValues[3].toFloatOrNull() ?: 0f) * 255).roundToInt().coerceIn(0, 255)
                    currentColor = Color.rgb(r, g, b)
                }
                tfRegex.find(line)?.let { m ->
                    fontSize = (m.groupValues[1].toFloatOrNull() ?: 12f).coerceIn(7f, 48f)
                }
                tmRegex.find(line)?.let { m ->
                    val scaleY = abs(m.groupValues[4].toFloatOrNull() ?: 1f)
                    if (scaleY > 4f) fontSize = scaleY.coerceIn(7f, 48f)
                    curX = m.groupValues[5].toFloatOrNull() ?: curX
                    curYBottom = m.groupValues[6].toFloatOrNull() ?: curYBottom
                }
                tdRegex.find(line)?.let { m ->
                    curX += m.groupValues[1].toFloatOrNull() ?: 0f
                    curYBottom += m.groupValues[2].toFloatOrNull() ?: 0f
                }

                val extractedParts = mutableListOf<String>()
                tjRegex.findAll(line).forEach { m ->
                    extractedParts += unescapePdfString(m.groupValues[1])
                }
                tjArrayRegex.findAll(line).forEach { arr ->
                    val inner = arr.groupValues[1]
                    val strRe = Regex("\\(([^()\\\\]*(?:\\\\.[^()\\\\]*)*)\\)")
                    val joined = strRe.findAll(inner).joinToString("") { unescapePdfString(it.groupValues[1]) }
                    if (joined.isNotBlank()) extractedParts += joined
                }

                val clean = extractedParts.joinToString(" ").trim()
                if (clean.isNotEmpty() && clean.any { it.isLetterOrDigit() }) {
                    val yTop = (pageH - curYBottom - fontSize * 0.9f).coerceIn(0f, pageH - 10f)
                    runs += RawTextRun(
                        xPt = curX.coerceIn(0f, pageW - 20f),
                        yPtTop = yTop,
                        fontSizePt = fontSize,
                        text = clean,
                        colorArgb = currentColor
                    )
                }
            }
        }

        if (runs.isEmpty()) return emptyList()

        // Merge runs on closely matching Y baselines into tidy editable boxes
        val sorted = runs.sortedWith(compareBy({ (it.yPtTop / 6f).roundToInt() }, { it.xPt }))
        val merged = mutableListOf<EditableTextBlock>()
        var i = 0
        while (i < sorted.size) {
            val first = sorted[i]
            val group = mutableListOf(first)
            var j = i + 1
            while (j < sorted.size) {
                val next = sorted[j]
                val sameLine = abs(next.yPtTop - first.yPtTop) <= max(6f, first.fontSizePt * 0.55f)
                val closeX = next.xPt - (group.last().xPt + group.last().text.length * group.last().fontSizePt * 0.52f) < 36f
                if (sameLine && closeX) {
                    group += next
                    j++
                } else break
            }
            val fullText = group.joinToString(" ") { it.text }.trim()
            val minX = group.minOf { it.xPt }
            val estWidthPt = (fullText.length * first.fontSizePt * 0.58f + 16f).coerceIn(48f, pageW - minX - 12f)
            val estHeightPt = (first.fontSizePt * 1.45f).coerceIn(16f, 96f)

            val nx = (minX / pageW).coerceIn(0.02f, 0.88f)
            val ny = (first.yPtTop / pageH).coerceIn(0.02f, 0.94f)
            val nw = (estWidthPt / pageW).coerceIn(0.10f, 0.96f - nx)
            val nh = (estHeightPt / pageH).coerceIn(0.025f, 0.16f)

            val sampledBg = sampleBackgroundColor(bitmap, nx, ny, nw, nh)
            merged += EditableTextBlock(
                pageIndex = pageIndex,
                origNormX = nx,
                origNormY = ny,
                origNormWidth = nw,
                origNormHeight = nh,
                originalText = fullText,
                fontSizePt = first.fontSizePt,
                textColorArgb = first.colorArgb,
                bgColorArgb = sampledBg,
                bold = first.fontSizePt >= 16f
            )
            i = j
        }
        return merged.take(40)
    }

    private fun detectVisualTextBlocks(bitmap: Bitmap, pageIndex: Int): List<EditableTextBlock> {
        // Fast downsampled horizontal ink projection to find text bands on scanned / non-ASCII PDFs
        val sampleW = 160
        val sampleH = (160f * bitmap.height / bitmap.width.coerceAtLeast(1)).roundToInt().coerceIn(80, 260)
        val small = Bitmap.createScaledBitmap(bitmap, sampleW, sampleH, true)
        val pixels = IntArray(sampleW * sampleH)
        small.getPixels(pixels, 0, sampleW, 0, 0, sampleW, sampleH)
        small.recycle()

        // Find dominant page background luminance from edges
        val bgColor = sampleBackgroundColor(bitmap, 0.02f, 0.02f, 0.05f, 0.05f)
        val bgLum = luminance(bgColor)

        val rowInk = IntArray(sampleH)
        val rowMinX = IntArray(sampleH) { sampleW }
        val rowMaxX = IntArray(sampleH) { 0 }

        for (y in 4 until sampleH - 4) {
            var count = 0
            for (x in 4 until sampleW - 4) {
                val c = pixels[y * sampleW + x]
                if (abs(luminance(c) - bgLum) > 42) {
                    count++
                    if (x < rowMinX[y]) rowMinX[y] = x
                    if (x > rowMaxX[y]) rowMaxX[y] = x
                }
            }
            rowInk[y] = count
        }

        val blocks = mutableListOf<EditableTextBlock>()
        var y = 4
        var blockNum = 1
        while (y < sampleH - 4 && blocks.size < 18) {
            if (rowInk[y] >= 3) {
                val startY = y
                var minX = rowMinX[y]
                var maxX = rowMaxX[y]
                while (y < sampleH - 4 && rowInk[y] >= 2) {
                    minX = min(minX, rowMinX[y])
                    maxX = max(maxX, rowMaxX[y])
                    y++
                }
                val bandH = y - startY
                val bandW = maxX - minX
                if (bandH in 3..26 && bandW >= 12) {
                    val nx = ((minX - 2).coerceAtLeast(2).toFloat() / sampleW)
                    val ny = ((startY - 1).coerceAtLeast(2).toFloat() / sampleH)
                    val nw = ((bandW + 4).toFloat() / sampleW).coerceIn(0.14f, 0.94f - nx)
                    val nh = ((bandH + 3).toFloat() / sampleH).coerceIn(0.03f, 0.14f)
                    val localBg = sampleBackgroundColor(bitmap, nx, ny, nw, nh)
                    val inkColor = if (luminance(localBg) < 128) Color.WHITE else Color.parseColor("#1A1523")
                    blocks += EditableTextBlock(
                        pageIndex = pageIndex,
                        origNormX = nx,
                        origNormY = ny,
                        origNormWidth = nw,
                        origNormHeight = nh,
                        originalText = "Text Block $blockNum",
                        fontSizePt = (nh * 842f * 0.65f).coerceIn(10f, 28f),
                        textColorArgb = inkColor,
                        bgColorArgb = localBg
                    )
                    blockNum++
                }
            } else {
                y++
            }
        }
        return blocks
    }

    fun sampleBackgroundColor(
        bitmap: Bitmap?,
        normX: Float,
        normY: Float,
        normW: Float,
        normH: Float
    ): Int {
        if (bitmap == null || bitmap.isRecycled) return Color.WHITE
        val w = bitmap.width
        val h = bitmap.height
        val x0 = (normX * w).roundToInt().coerceIn(0, w - 1)
        val y0 = (normY * h).roundToInt().coerceIn(0, h - 1)
        val x1 = ((normX + normW) * w).roundToInt().coerceIn(0, w - 1)
        val y1 = ((normY + normH) * h).roundToInt().coerceIn(0, h - 1)

        // Sample 4 corner perimeter pixels right around the text box and average the brightest/dominant bg
        val samples = listOf(
            bitmap.getPixel((x0 - 2).coerceIn(0, w - 1), (y0 - 2).coerceIn(0, h - 1)),
            bitmap.getPixel((x1 + 2).coerceIn(0, w - 1), (y0 - 2).coerceIn(0, h - 1)),
            bitmap.getPixel((x0 - 2).coerceIn(0, w - 1), (y1 + 2).coerceIn(0, h - 1)),
            bitmap.getPixel((x1 + 2).coerceIn(0, w - 1), (y1 + 2).coerceIn(0, h - 1))
        )
        val r = samples.map { Color.red(it) }.sorted()[2]
        val g = samples.map { Color.green(it) }.sorted()[2]
        val b = samples.map { Color.blue(it) }.sorted()[2]
        return Color.rgb(r, g, b)
    }

    private fun luminance(color: Int): Int {
        return (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)).roundToInt()
    }

    private fun unescapePdfString(s: String): String {
        return s.replace("\\(", "(")
            .replace("\\)", ")")
            .replace("\\\\", "\\")
            .replace("\\n", "\n")
            .replace("\\r", "")
            .filter { it.code in 32..126 || it == '\n' }
    }
}
