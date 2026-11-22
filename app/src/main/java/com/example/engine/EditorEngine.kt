package com.example.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

enum class EditorTool(val label: String) {
    EDIT_CONTENT("Edit Text"),
    TEXT("Add Text"),
    HIGHLIGHT("Highlight"),
    UNDERLINE("Underline"),
    STRIKETHROUGH("Strike"),
    DRAW("Draw"),
    SHAPES("Shapes"),
    NOTE("Note"),
    IMAGE("Image"),
    SIGNATURE("Signature"),
    FORM("Fill Form"),
    ERASER("Eraser")
}

enum class TextAlignOption { LEFT, CENTER, RIGHT, JUSTIFY }
enum class ShapeKind { RECTANGLE, CIRCLE, ARROW }

sealed class PageAnnotation {
    abstract val id: String
    abstract val pageIndex: Int

    data class TextBox(
        override val id: String,
        override val pageIndex: Int,
        val normX: Float,
        val normY: Float,
        val text: String,
        val fontSizeSp: Float = 16f,
        val colorArgb: Int = Color.parseColor("#1A1523"),
        val bold: Boolean = false,
        val italic: Boolean = false,
        val fontFamilyName: String = "Sans",
        val align: TextAlignOption = TextAlignOption.LEFT,
        val lineSpacing: Float = 1.2f
    ) : PageAnnotation()

    data class FreehandStroke(
        override val id: String,
        override val pageIndex: Int,
        val points: List<Pair<Float, Float>>, // normalized 0..1
        val colorArgb: Int,
        val strokeWidthDp: Float,
        val alpha: Float = 1f,
        val isStraightLine: Boolean = false
    ) : PageAnnotation()

    data class ShapeItem(
        override val id: String,
        override val pageIndex: Int,
        val kind: ShapeKind,
        val startX: Float,
        val startY: Float,
        val endX: Float,
        val endY: Float,
        val colorArgb: Int,
        val strokeWidthDp: Float = 3f
    ) : PageAnnotation()

    data class StickyNote(
        override val id: String,
        override val pageIndex: Int,
        val normX: Float,
        val normY: Float,
        val message: String,
        val colorArgb: Int = Color.parseColor("#FFB020")
    ) : PageAnnotation()

    data class ImageOverlay(
        override val id: String,
        override val pageIndex: Int,
        val imagePath: String,
        val normX: Float,
        val normY: Float,
        val normWidth: Float = 0.34f,
        val normHeight: Float = 0.14f,
        val rotationDegrees: Float = 0f,
        val isSignature: Boolean = false
    ) : PageAnnotation()
}

object PdfFlattener {
    suspend fun flattenAnnotationsToPdf(
        sourcePdf: File,
        outputPdf: File,
        annotations: List<PageAnnotation>,
        editedContentBlocks: List<EditableTextBlock> = emptyList()
    ): Int = withContext(Dispatchers.IO) {
        val totalPages = PdfRenderHelper.getPageCount(sourcePdf)
        val pdf = PdfDocument()

        for (pageIdx in 0 until totalPages) {
            val baseBmp = PdfRenderHelper.renderPageSync(sourcePdf, pageIdx, 1180) ?: continue
            val w = 595
            val h = (595f * baseBmp.height / baseBmp.width).roundToInt().coerceAtLeast(200)
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(w, h, pageIdx + 1).create())
            val canvas = page.canvas
            canvas.drawBitmap(baseBmp, null, RectF(0f, 0f, w.toFloat(), h.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
            baseBmp.recycle()

            // First apply in-place content text replacements (masking original box + drawing updated text)
            val pageBlocks = editedContentBlocks.filter { it.pageIndex == pageIdx && (it.isModified || it.isDeleted || it.isUserAdded) }
            pageBlocks.forEach { block ->
                drawEditedContentBlock(canvas, w.toFloat(), h.toFloat(), block)
            }

            // Then composite annotations (highlights, strokes, notes, signatures, shapes)
            val pageAnns = annotations.filter { it.pageIndex == pageIdx }
            pageAnns.forEach { ann ->
                drawAnnotationOnCanvas(canvas, w.toFloat(), h.toFloat(), ann)
            }
            pdf.finishPage(page)
        }
        FileOutputStream(outputPdf).use { pdf.writeTo(it) }
        pdf.close()

        // Append updated %PP_BLOCK metadata so re-opening the edited PDF preserves exact editable blocks
        if (editedContentBlocks.isNotEmpty()) {
            val activeBlocks = editedContentBlocks.filterNot { it.isDeleted }
            if (activeBlocks.isNotEmpty()) {
                val sb = StringBuilder("\n")
                activeBlocks.forEach { b ->
                    val colorHex = String.format("#%06X", 0xFFFFFF and b.textColorArgb)
                    val bgHex = String.format("#%06X", 0xFFFFFF and b.bgColorArgb)
                    val encodedText = b.text.replace("\n", "\\n")
                    sb.append("%PP_BLOCK:")
                        .append(b.pageIndex).append("|")
                        .append(b.normX).append("|")
                        .append(b.normY).append("|")
                        .append(b.normWidth).append("|")
                        .append(b.normHeight).append("|")
                        .append(b.fontSizePt).append("|")
                        .append(colorHex).append("|")
                        .append(bgHex).append("|")
                        .append(if (b.bold) "1" else "0").append("|")
                        .append(b.align.name).append("|")
                        .append(encodedText).append("\n")
                }
                outputPdf.appendText(sb.toString(), Charsets.ISO_8859_1)
            }
        }
        totalPages
    }

    private fun drawEditedContentBlock(
        canvas: Canvas,
        pageW: Float,
        pageH: Float,
        block: EditableTextBlock
    ) {
        // Cover original underlying PDF text seamlessly with sampled background color
        if (!block.isUserAdded) {
            val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = block.bgColorArgb
                style = Paint.Style.FILL
            }
            val origRect = RectF(
                (block.origNormX * pageW - 2f).coerceAtLeast(0f),
                (block.origNormY * pageH - 2f).coerceAtLeast(0f),
                ((block.origNormX + block.origNormWidth) * pageW + 2f).coerceAtMost(pageW),
                ((block.origNormY + block.origNormHeight) * pageH + 2f).coerceAtMost(pageH)
            )
            canvas.drawRect(origRect, maskPaint)
        }

        if (block.isDeleted || block.text.isBlank()) return

        val style = when {
            block.bold && block.italic -> Typeface.BOLD_ITALIC
            block.bold -> Typeface.BOLD
            block.italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        val baseFamily = when (block.fontFamilyName) {
            "Serif" -> Typeface.SERIF
            "Mono" -> Typeface.MONOSPACE
            else -> Typeface.SANS_SERIF
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = block.textColorArgb
            textSize = block.fontSizePt
            typeface = Typeface.create(baseFamily, style)
        }

        val left = block.normX * pageW
        val boxW = (block.normWidth * pageW).coerceAtLeast(36f)
        val lineHeight = block.fontSizePt * block.lineSpacing
        var y = block.normY * pageH + block.fontSizePt

        val wrappedLines = mutableListOf<String>()
        block.text.split("\n").forEach { para ->
            if (para.isEmpty()) {
                wrappedLines += ""
            } else {
                var current = ""
                para.split(" ").forEach { word ->
                    val candidate = if (current.isEmpty()) word else "$current $word"
                    if (paint.measureText(candidate) <= boxW || current.isEmpty()) {
                        current = candidate
                    } else {
                        wrappedLines += current
                        current = word
                    }
                }
                if (current.isNotEmpty()) wrappedLines += current
            }
        }

        wrappedLines.forEachIndexed { idx, line ->
            when (block.align) {
                TextAlignOption.LEFT -> {
                    paint.textAlign = Paint.Align.LEFT
                    canvas.drawText(line, left, y, paint)
                }
                TextAlignOption.CENTER -> {
                    paint.textAlign = Paint.Align.CENTER
                    canvas.drawText(line, left + boxW / 2f, y, paint)
                }
                TextAlignOption.RIGHT -> {
                    paint.textAlign = Paint.Align.RIGHT
                    canvas.drawText(line, left + boxW, y, paint)
                }
                TextAlignOption.JUSTIFY -> {
                    paint.textAlign = Paint.Align.LEFT
                    val words = line.split(" ").filter { it.isNotEmpty() }
                    val isLastLine = idx == wrappedLines.lastIndex
                    if (words.size <= 1 || isLastLine) {
                        canvas.drawText(line, left, y, paint)
                    } else {
                        val wordsWidth = words.sumOf { paint.measureText(it).toDouble() }.toFloat()
                        val gap = ((boxW - wordsWidth) / (words.size - 1)).coerceAtLeast(paint.measureText(" "))
                        var curX = left
                        words.forEach { w ->
                            canvas.drawText(w, curX, y, paint)
                            curX += paint.measureText(w) + gap
                        }
                    }
                }
            }
            y += lineHeight
        }
    }

    private fun drawAnnotationOnCanvas(canvas: Canvas, pageW: Float, pageH: Float, ann: PageAnnotation) {
        when (ann) {
            is PageAnnotation.TextBox -> {
                val style = when {
                    ann.bold && ann.italic -> Typeface.BOLD_ITALIC
                    ann.bold -> Typeface.BOLD
                    ann.italic -> Typeface.ITALIC
                    else -> Typeface.NORMAL
                }
                val baseTypeface = when (ann.fontFamilyName) {
                    "Serif" -> Typeface.SERIF
                    "Mono" -> Typeface.MONOSPACE
                    else -> Typeface.SANS_SERIF
                }
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = ann.colorArgb
                    textSize = ann.fontSizeSp
                    typeface = Typeface.create(baseTypeface, style)
                    textAlign = when (ann.align) {
                        TextAlignOption.LEFT, TextAlignOption.JUSTIFY -> Paint.Align.LEFT
                        TextAlignOption.CENTER -> Paint.Align.CENTER
                        TextAlignOption.RIGHT -> Paint.Align.RIGHT
                    }
                }
                val x = ann.normX * pageW
                var y = ann.normY * pageH
                ann.text.lines().forEach { line ->
                    canvas.drawText(line, x, y, paint)
                    y += ann.fontSizeSp * ann.lineSpacing
                }
            }

            is PageAnnotation.FreehandStroke -> {
                if (ann.points.size < 2) return
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = ann.colorArgb
                    alpha = (ann.alpha * 255).roundToInt()
                    style = Paint.Style.STROKE
                    strokeWidth = ann.strokeWidthDp * 1.4f
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
                val path = Path().apply {
                    val first = ann.points.first()
                    moveTo(first.first * pageW, first.second * pageH)
                    if (ann.isStraightLine) {
                        val last = ann.points.last()
                        lineTo(last.first * pageW, last.second * pageH)
                    } else {
                        ann.points.drop(1).forEach { pt ->
                            lineTo(pt.first * pageW, pt.second * pageH)
                        }
                    }
                }
                canvas.drawPath(path, paint)
            }

            is PageAnnotation.ShapeItem -> {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = ann.colorArgb
                    style = Paint.Style.STROKE
                    strokeWidth = ann.strokeWidthDp * 1.3f
                    strokeCap = Paint.Cap.ROUND
                }
                val rect = RectF(
                    minOf(ann.startX, ann.endX) * pageW,
                    minOf(ann.startY, ann.endY) * pageH,
                    maxOf(ann.startX, ann.endX) * pageW,
                    maxOf(ann.startY, ann.endY) * pageH
                )
                when (ann.kind) {
                    ShapeKind.RECTANGLE -> canvas.drawRoundRect(rect, 6f, 6f, paint)
                    ShapeKind.CIRCLE -> canvas.drawOval(rect, paint)
                    ShapeKind.ARROW -> {
                        canvas.drawLine(
                            ann.startX * pageW, ann.startY * pageH,
                            ann.endX * pageW, ann.endY * pageH,
                            paint
                        )
                    }
                }
            }

            is PageAnnotation.StickyNote -> {
                val x = ann.normX * pageW
                val y = ann.normY * pageH
                val card = RectF(x, y, (x + 150f).coerceAtMost(pageW - 12f), (y + 52f).coerceAtMost(pageH - 12f))
                val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ann.colorArgb }
                canvas.drawRoundRect(card, 10f, 10f, bg)
                val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#1A1523")
                    textSize = 10f
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                }
                canvas.drawText(ann.message.take(26), card.left + 8f, card.top + 22f, txt)
            }

            is PageAnnotation.ImageOverlay -> {
                val file = File(ann.imagePath)
                if (!file.exists()) return
                val bmp = BitmapFactory.decodeFile(file.absolutePath) ?: return
                val cx = ann.normX * pageW
                val cy = ann.normY * pageH
                val dw = ann.normWidth * pageW
                val dh = ann.normHeight * pageH
                canvas.save()
                canvas.rotate(ann.rotationDegrees, cx, cy)
                canvas.drawBitmap(bmp, null, RectF(cx - dw / 2f, cy - dh / 2f, cx + dw / 2f, cy + dh / 2f), Paint(Paint.FILTER_BITMAP_FLAG))
                canvas.restore()
                bmp.recycle()
            }
        }
    }

    fun renderTypedSignatureBitmap(text: String, colorArgb: Int, outFile: File): File {
        val bmp = Bitmap.createBitmap(640, 220, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.TRANSPARENT)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorArgb
            textSize = 72f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
        }
        canvas.drawText(text.ifBlank { "Signature" }, 320f, 132f, paint)
        val underline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorArgb
            alpha = 90
            strokeWidth = 3f
            pathEffect = DashPathEffect(floatArrayOf(8f, 6f), 0f)
        }
        canvas.drawLine(80f, 162f, 560f, 162f, underline)
        FileOutputStream(outFile).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        return outFile
    }

    fun renderDrawnSignatureBitmap(strokes: List<List<Pair<Float, Float>>>, colorArgb: Int, outFile: File): File {
        val bmp = Bitmap.createBitmap(640, 260, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.TRANSPARENT)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorArgb
            style = Paint.Style.STROKE
            strokeWidth = 7f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        strokes.forEach { pts ->
            if (pts.size >= 2) {
                val path = Path().apply {
                    moveTo(pts.first().first * 640f, pts.first().second * 260f)
                    pts.drop(1).forEach { (nx, ny) -> lineTo(nx * 640f, ny * 260f) }
                }
                canvas.drawPath(path, paint)
            }
        }
        FileOutputStream(outFile).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        return outFile
    }
}
