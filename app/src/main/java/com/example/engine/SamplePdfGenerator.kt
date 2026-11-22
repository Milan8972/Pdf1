package com.example.engine

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.data.DocumentEntity
import com.example.data.DocumentSource
import java.io.File
import java.io.FileOutputStream

enum class DocumentTemplate(
    val title: String,
    val subtitle: String,
    val defaultFileName: String
) {
    RESUME("Modern Resume", "Clean two-section CV with contact & experience", "Resume_Template.pdf"),
    INVOICE("Client Invoice", "Itemized billing table with subtotal & signature line", "Invoice_Template.pdf"),
    MEETING_NOTES("Meeting Notes", "Structured agenda, decisions, and action owners", "Meeting_Notes.pdf"),
    LINED_NOTEBOOK("Lined Sheet", "Crisp ruled paper ready for notes & sketches", "Lined_Notebook.pdf")
}

object SamplePdfGenerator {
    private const val PAGE_W = 595
    private const val PAGE_H = 842

    fun generateDefaultSamples(docsDir: File, thumbsDir: File): List<DocumentEntity> {
        docsDir.mkdirs()
        thumbsDir.mkdirs()
        val now = System.currentTimeMillis()
        val results = mutableListOf<DocumentEntity>()

        // 1. Welcome Guide (4 pages)
        val guideFile = File(docsDir, "paperpilot_welcome_guide.pdf")
        val guideText = createWelcomeGuidePdf(guideFile)
        results += DocumentEntity(
            name = "paperpilot Welcome Guide.pdf",
            filePath = guideFile.absolutePath,
            mimeType = "application/pdf",
            sizeBytes = guideFile.length(),
            pageCount = 4,
            source = DocumentSource.OPENED.name,
            isFavorite = true,
            createdAt = now - 3_600_000L,
            lastOpenedAt = now - 600_000L,
            thumbnailPath = PdfRenderHelper.generateThumbnail(guideFile, thumbsDir),
            extractedText = guideText
        )

        // 2. Q4 Product Roadmap (3 pages)
        val roadmapFile = File(docsDir, "Q4_Product_Roadmap.pdf")
        val roadmapText = createRoadmapPdf(roadmapFile)
        results += DocumentEntity(
            name = "Q4 Product Roadmap.pdf",
            filePath = roadmapFile.absolutePath,
            mimeType = "application/pdf",
            sizeBytes = roadmapFile.length(),
            pageCount = 3,
            source = DocumentSource.CREATED.name,
            isFavorite = false,
            createdAt = now - 86_400_000L,
            lastOpenedAt = now - 7_200_000L,
            thumbnailPath = PdfRenderHelper.generateThumbnail(roadmapFile, thumbsDir),
            extractedText = roadmapText
        )

        // 3. Permission / Expense Form (2 pages)
        val formFile = File(docsDir, "Travel_Approval_Form.pdf")
        val formText = createFormPdf(formFile)
        results += DocumentEntity(
            name = "Travel Approval Form.pdf",
            filePath = formFile.absolutePath,
            mimeType = "application/pdf",
            sizeBytes = formFile.length(),
            pageCount = 2,
            source = DocumentSource.DOWNLOADS.name,
            isFavorite = true,
            createdAt = now - 172_800_000L,
            lastOpenedAt = now - 14_400_000L,
            thumbnailPath = PdfRenderHelper.generateThumbnail(formFile, thumbsDir),
            extractedText = formText
        )

        return results
    }

    fun refreshSamplePdfFilesIfNeeded(docsDir: File) {
        val samplePairs: List<Pair<String, (File) -> String>> = listOf(
            "paperpilot_welcome_guide.pdf" to ::createWelcomeGuidePdf,
            "Q4_Product_Roadmap.pdf" to ::createRoadmapPdf,
            "Travel_Approval_Form.pdf" to ::createFormPdf
        )
        samplePairs.forEach { (fileName, generator) ->
            val file = File(docsDir, fileName)
            if (file.exists()) {
                val hasMeta = runCatching {
                    file.readText(Charsets.ISO_8859_1).contains("%PP_BLOCK:")
                }.getOrDefault(false)
                if (!hasMeta) {
                    runCatching { generator(file) }
                }
            }
        }
    }

    fun generateFromTemplate(template: DocumentTemplate, docsDir: File): Pair<File, String> {
        docsDir.mkdirs()
        val file = File(docsDir, "${System.currentTimeMillis()}_${template.defaultFileName}")
        val pdf = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create()
        val page = pdf.startPage(pageInfo)
        val c = page.canvas
        val textContent = StringBuilder()

        when (template) {
            DocumentTemplate.RESUME -> {
                c.drawColor(Color.WHITE)
                drawHeaderBand(c, 0xFF1A1523.toInt(), 0xFFD03A44.toInt(), "ALEX RIVERA", "Senior Product Designer  |  alex@example.com  |  Portland, OR")
                drawSectionBlock(c, 165f, "PROFILE", listOf(
                    "Human-centered designer with 7+ years crafting mobile-first productivity tools.",
                    "Focused on accessible systems, offline-first utility apps, and clear typography."
                ))
                drawSectionBlock(c, 285f, "EXPERIENCE", listOf(
                    "Lead Designer, Northstar Studio (2023 - Present)",
                    "  • Led redesign of cross-platform document workflow used by 400k people.",
                    "  • Built shared component library and dark-mode contrast system.",
                    "",
                    "Product Designer, Harbor Labs (2020 - 2023)",
                    "  • Shipped camera scanning and instant annotation tools on Android."
                ))
                drawSectionBlock(c, 505f, "SKILLS & EDUCATION", listOf(
                    "Design Systems, Prototyping, Accessibility (WCAG AA), Kotlin & Compose UI",
                    "B.S. Interaction Design — Pacific Northwest University"
                ))
                textContent.append("ALEX RIVERA\nSenior Product Designer\nExperience: Lead Designer at Northstar Studio, Product Designer at Harbor Labs.")
            }

            DocumentTemplate.INVOICE -> {
                c.drawColor(Color.WHITE)
                drawHeaderBand(c, 0xFFD03A44.toInt(), 0xFFFFB020.toInt(), "INVOICE #PP-2048", "Issued: Nov 21, 2026  |  Due: Dec 05, 2026")
                drawSectionBlock(c, 160f, "BILL TO", listOf(
                    "Cedar & Pine Creative LLC",
                    "420 Alder Street, Suite 300",
                    "Seattle, WA 98104"
                ))
                val tablePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFF8F5.toInt() }
                c.drawRoundRect(RectF(44f, 290f, PAGE_W - 44f, 520f), 14f, 14f, tablePaint)
                drawSectionBlock(c, 315f, "LINE ITEMS", listOf(
                    "1. Mobile UX Architecture & Flows ............................ $1,800.00",
                    "2. Brand Iconography & Dark Mode Tokens ...................... $1,200.00",
                    "3. Interactive Prototype & Handoff ............................. $950.00",
                    "",
                    "Subtotal: $3,950.00   |   Tax (0%): $0.00   |   TOTAL DUE: $3,950.00"
                ))
                drawSignatureBox(c, 600f, "Authorized Signature")
                textContent.append("INVOICE #PP-2048\nBill To: Cedar & Pine Creative LLC\nTotal Due: $3,950.00")
            }

            DocumentTemplate.MEETING_NOTES -> {
                c.drawColor(Color.WHITE)
                drawHeaderBand(c, 0xFF14B8A6.toInt(), 0xFF0F766E.toInt(), "MEETING NOTES", "Weekly Product Sync  |  Date: Nov 21, 2026")
                drawSectionBlock(c, 165f, "ATTENDEES", listOf("Maya Lin, Devon Brooks, Priya Patel, Sam Chen"))
                drawSectionBlock(c, 250f, "KEY DECISIONS", listOf(
                    "• Keep all document processing 100% on-device with zero server uploads.",
                    "• Ship 5-tab adaptive navigation with tablet rail support.",
                    "• Default editor exports to a new copy to protect original PDFs."
                ))
                drawSectionBlock(c, 420f, "ACTION ITEMS", listOf(
                    "[ ] Finalize batch scanner perspective crop handles — Maya",
                    "[ ] Verify WCAG AA contrast on light coral primary actions — Devon",
                    "[ ] Run offline verification suite on low-memory devices — Priya"
                ))
                textContent.append("MEETING NOTES\nWeekly Product Sync\nKey decisions: Keep all processing 100% on-device.")
            }

            DocumentTemplate.LINED_NOTEBOOK -> {
                c.drawColor(0xFFFFFBF8.toInt())
                val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFF1A1523.toInt()
                    textSize = 20f
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                }
                c.drawText("Notes", 48f, 68f, titlePaint)
                val marginPaint = Paint().apply {
                    color = 0x44FF6B6B
                    strokeWidth = 2f
                }
                c.drawLine(72f, 92f, 72f, PAGE_H - 48f, marginPaint)
                val linePaint = Paint().apply {
                    color = 0xFFDDD4CE.toInt()
                    strokeWidth = 1f
                }
                var y = 116f
                while (y < PAGE_H - 54f) {
                    c.drawLine(44f, y, PAGE_W - 44f, y, linePaint)
                    y += 28f
                }
                textContent.append("Lined Notebook Sheet created in paperpilot")
            }
        }

        drawFooter(c, "Created with paperpilot  •  On-device private PDF", 1, 1)
        pdf.finishPage(page)
        FileOutputStream(file).use { pdf.writeTo(it) }
        pdf.close()

        val blockLines = when (template) {
            DocumentTemplate.RESUME -> listOf(
                blockDesc(0, 0.07f, 0.045f, 0.78f, 0.042f, 24f, "#FFFFFF", "#4B1F2E", true, "LEFT", "ALEX RIVERA"),
                blockDesc(0, 0.07f, 0.095f, 0.85f, 0.028f, 13f, "#FFFFFF", "#6A2835", false, "LEFT", "Senior Product Designer  |  alex@example.com  |  Portland, OR"),
                blockDesc(0, 0.08f, 0.180f, 0.30f, 0.024f, 13f, "#D03A44", "#FFFFFF", true, "LEFT", "PROFILE"),
                blockDesc(0, 0.08f, 0.210f, 0.84f, 0.068f, 13.5f, "#1A1523", "#FFFFFF", false, "LEFT", "Human-centered designer with 7+ years crafting mobile-first productivity tools.\\nFocused on accessible systems, offline-first utility apps, and clear typography."),
                blockDesc(0, 0.08f, 0.320f, 0.35f, 0.024f, 13f, "#D03A44", "#FFFFFF", true, "LEFT", "EXPERIENCE"),
                blockDesc(0, 0.08f, 0.350f, 0.84f, 0.180f, 13.5f, "#1A1523", "#FFFFFF", false, "LEFT", "Lead Designer, Northstar Studio (2023 - Present)\\n  • Led redesign of cross-platform document workflow used by 400k people.\\n  • Built shared component library and dark-mode contrast system."),
                blockDesc(0, 0.08f, 0.580f, 0.45f, 0.024f, 13f, "#D03A44", "#FFFFFF", true, "LEFT", "SKILLS & EDUCATION"),
                blockDesc(0, 0.08f, 0.610f, 0.84f, 0.068f, 13.5f, "#1A1523", "#FFFFFF", false, "LEFT", "Design Systems, Prototyping, Accessibility (WCAG AA), Kotlin & Compose UI\\nB.S. Interaction Design — Pacific Northwest University")
            )
            DocumentTemplate.INVOICE -> listOf(
                blockDesc(0, 0.07f, 0.045f, 0.72f, 0.042f, 24f, "#FFFFFF", "#D9513D", true, "LEFT", "INVOICE #PP-2048"),
                blockDesc(0, 0.07f, 0.095f, 0.84f, 0.028f, 13f, "#FFFFFF", "#E47332", false, "LEFT", "Issued: Nov 21, 2026  |  Due: Dec 05, 2026"),
                blockDesc(0, 0.08f, 0.175f, 0.30f, 0.024f, 13f, "#D03A44", "#FFFFFF", true, "LEFT", "BILL TO"),
                blockDesc(0, 0.08f, 0.205f, 0.75f, 0.086f, 13.5f, "#1A1523", "#FFFFFF", false, "LEFT", "Cedar & Pine Creative LLC\\n420 Alder Street, Suite 300\\nSeattle, WA 98104"),
                blockDesc(0, 0.08f, 0.360f, 0.34f, 0.024f, 13f, "#D03A44", "#FFF8F5", true, "LEFT", "LINE ITEMS"),
                blockDesc(0, 0.08f, 0.390f, 0.84f, 0.150f, 13.5f, "#1A1523", "#FFF8F5", false, "LEFT", "1. Mobile UX Architecture & Flows ............................ $1,800.00\\n2. Brand Iconography & Dark Mode Tokens ...................... $1,200.00\\n3. Interactive Prototype & Handoff ............................. $950.00\\nSubtotal: $3,950.00   |   Tax (0%): $0.00   |   TOTAL DUE: $3,950.00")
            )
            DocumentTemplate.MEETING_NOTES -> listOf(
                blockDesc(0, 0.07f, 0.045f, 0.72f, 0.042f, 24f, "#FFFFFF", "#129B8E", true, "LEFT", "MEETING NOTES"),
                blockDesc(0, 0.07f, 0.095f, 0.84f, 0.028f, 13f, "#FFFFFF", "#118B7F", false, "LEFT", "Weekly Product Sync  |  Date: Nov 21, 2026"),
                blockDesc(0, 0.08f, 0.210f, 0.82f, 0.036f, 13.5f, "#1A1523", "#FFFFFF", false, "LEFT", "Maya Lin, Devon Brooks, Priya Patel, Sam Chen"),
                blockDesc(0, 0.08f, 0.310f, 0.84f, 0.095f, 13.5f, "#1A1523", "#FFFFFF", false, "LEFT", "• Keep all document processing 100% on-device with zero server uploads.\\n• Ship 5-tab adaptive navigation with tablet rail support.\\n• Default editor exports to a new copy to protect original PDFs.")
            )
            DocumentTemplate.LINED_NOTEBOOK -> listOf(
                blockDesc(0, 0.08f, 0.056f, 0.45f, 0.036f, 20f, "#1A1523", "#FFFBF8", true, "LEFT", "Notes")
            )
        }
        appendBlockMetadata(file, blockLines)
        return file to textContent.toString()
    }

    private fun blockDesc(
        page: Int,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        size: Float,
        colorHex: String,
        bgHex: String,
        bold: Boolean,
        align: String,
        text: String
    ): String = "%PP_BLOCK:$page|$x|$y|$w|$h|$size|$colorHex|$bgHex|${if (bold) "1" else "0"}|$align|$text"

    private fun appendBlockMetadata(file: File, lines: List<String>) {
        if (lines.isEmpty()) return
        file.appendText("\n" + lines.joinToString("\n") + "\n", Charsets.ISO_8859_1)
    }

    private fun createWelcomeGuidePdf(file: File): String {
        val pdf = PdfDocument()
        val allText = StringBuilder()

        // Page 1: Cover
        run {
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create())
            val c = page.canvas
            c.drawColor(0xFFFFF8F5.toInt())

            // Decorative coral-amber gradient arc
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, 0f, PAGE_W.toFloat(), 440f,
                    0xFFFF6B6B.toInt(), 0xFFFFB020.toInt(), Shader.TileMode.CLAMP
                )
            }
            c.drawRoundRect(RectF(36f, 44f, PAGE_W - 36f, 440f), 32f, 32f, bgPaint)

            // White badge + folded paper plane logo
            val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            val bx = PAGE_W / 2f - 48f
            val by = 110f
            c.drawRoundRect(RectF(bx, by, bx + 96f, by + 96f), 27f, 27f, badgePaint)
            val planePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(bx, by, bx + 96f, by + 96f, 0xFFFF6B6B.toInt(), 0xFFFFB020.toInt(), Shader.TileMode.CLAMP)
            }
            val topWing = Path().apply {
                moveTo(bx + 20f, by + 50f)
                lineTo(bx + 76f, by + 22f)
                lineTo(bx + 44f, by + 58f)
                close()
            }
            val bottomWing = Path().apply {
                moveTo(bx + 49f, by + 61f)
                lineTo(bx + 76f, by + 25f)
                lineTo(bx + 62f, by + 76f)
                close()
            }
            c.drawPath(topWing, planePaint)
            c.drawPath(bottomWing, planePaint)

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 42f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            c.drawText("paperpilot", PAGE_W / 2f, 274f, titlePaint)

            val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xF2FFFFFF.toInt()
                textSize = 18f
                textAlign = Paint.Align.CENTER
            }
            c.drawText("Welcome Guide  •  Private On-Device PDF Toolkit", PAGE_W / 2f, 314f, subPaint)

            val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFFFFF }
            c.drawRoundRect(RectF(170f, 344f, PAGE_W - 170f, 384f), 20f, 20f, pillPaint)
            val pillText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 13f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            c.drawText("100% OFFLINE  •  ZERO SERVER UPLOADS", PAGE_W / 2f, 369f, pillText)

            drawSectionBlock(c, 500f, "WHAT YOU CAN DO", listOf(
                "• Merge, split, rotate, reorder, and number pages effortlessly.",
                "• Compress large PDFs with Light, Balanced, or Strong presets.",
                "• Scan physical documents with perspective crop and filters.",
                "• Annotate, highlight, fill forms, and add handwritten signatures."
            ))
            drawFooter(c, "paperpilot Welcome Guide", 1, 4)
            pdf.finishPage(page)
            allText.appendLine("paperpilot Welcome Guide - Private On-Device PDF Toolkit. Merge, split, rotate, reorder, compress, scan, annotate, and sign PDFs completely offline.")
        }

        // Page 2: Toolkit Overview
        run {
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 2).create())
            val c = page.canvas
            c.drawColor(Color.WHITE)
            drawHeaderBand(c, 0xFFF59E0B.toInt(), 0xFFD03A44.toInt(), "ONE CONSISTENT FLOW", "Pick files  →  Configure options  →  Process  →  Save or Share")
            drawSectionBlock(c, 175f, "ORGANIZE & OPTIMIZE", listOf(
                "Combine multiple PDFs in custom order, split out exact page ranges like 1-3, 5,",
                "or export split sections into a single ZIP bundle. Shrink files on-device and",
                "inspect exact before/after file size savings immediately."
            ))
            drawSectionBlock(c, 340f, "CONVERT & EXTRACT", listOf(
                "Turn gallery photos into A4 or Letter PDFs with configurable margins, export",
                "PDF pages to crisp JPEGs, pull raw text into .txt with one-tap copy, or run",
                "multi-language OCR for searchable document archives."
            ))
            drawSectionBlock(c, 505f, "PROTECT & WATERMARK", listOf(
                "Stamp diagonal or tiled watermarks with adjustable opacity and angle.",
                "Add password protection with print/copy/edit permission flags and unlock",
                "protected PDFs anytime on your device."
            ))
            drawFooter(c, "paperpilot Welcome Guide", 2, 4)
            pdf.finishPage(page)
            allText.appendLine("One Consistent Flow: Pick files, Configure options, Process, Save or Share. Organize, Optimize, Convert, Extract, Protect, and Watermark.")
        }

        // Page 3: Editor & Scanner
        run {
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 3).create())
            val c = page.canvas
            c.drawColor(Color.WHITE)
            drawHeaderBand(c, 0xFF8B5CF6.toInt(), 0xFF14B8A6.toInt(), "EDIT, SIGN & SCAN", "Annotate cleanly without altering your original file")
            drawSectionBlock(c, 175f, "NON-DESTRUCTIVE EDITOR", listOf(
                "Tap the Edit button from the PDF Viewer to place styled text boxes, draw",
                "freehand strokes, highlight or underline key passages, attach sticky notes,",
                "and drop saved handwritten or typed signatures directly onto any page."
            ))
            drawSectionBlock(c, 355f, "DOCUMENT SCANNER", listOf(
                "Use the Scan tab to capture multi-page paper documents with live edge guides,",
                "4-corner crop handles, and Auto-enhance, Grayscale, Black & White, or Magic",
                "color filters before saving straight to PDF."
            ))
            drawSignatureBox(c, 550f, "Try signing right inside this box in the Editor!")
            drawFooter(c, "paperpilot Welcome Guide", 3, 4)
            pdf.finishPage(page)
            allText.appendLine("Edit, Sign & Scan: Non-destructive editor with text, highlight, underline, draw, shapes, notes, and signatures. Multi-page document scanner with filters.")
        }

        // Page 4: Privacy Guarantee
        run {
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 4).create())
            val c = page.canvas
            c.drawColor(Color.WHITE)
            drawHeaderBand(c, 0xFF10B8A6.toInt(), 0xFF1E2540.toInt(), "PRIVATE BY DESIGN", "Your documents never leave your Android device")
            drawSectionBlock(c, 185f, "LOCAL-FIRST ARCHITECTURE", listOf(
                "• Zero cloud uploads: every merge, split, edit, and scan runs locally.",
                "• Works offline on flights, commutes, and restricted networks.",
                "• 100% local workspace: no accounts, no sign-ins, and no cloud sync.",
                "• Original files remain untouched whenever you export edited copies."
            ))
            drawFooter(c, "paperpilot Welcome Guide", 4, 4)
            pdf.finishPage(page)
            allText.appendLine("Private by Design: Zero cloud uploads, works offline, no account required, files stay on your device.")
        }

        FileOutputStream(file).use { pdf.writeTo(it) }
        pdf.close()
        appendBlockMetadata(
            file,
            listOf(
                // Page 1
                blockDesc(0, 0.16f, 0.278f, 0.68f, 0.062f, 40f, "#FFFFFF", "#FF8649", true, "CENTER", "paperpilot"),
                blockDesc(0, 0.12f, 0.348f, 0.76f, 0.036f, 18f, "#FFFFFF", "#FF933D", false, "CENTER", "Welcome Guide  •  Private On-Device PDF Toolkit"),
                blockDesc(0, 0.28f, 0.415f, 0.44f, 0.034f, 13f, "#FFFFFF", "#FFB257", true, "CENTER", "100% OFFLINE  •  ZERO SERVER UPLOADS"),
                blockDesc(0, 0.08f, 0.576f, 0.40f, 0.024f, 13f, "#D03A44", "#FFF8F5", true, "LEFT", "WHAT YOU CAN DO"),
                blockDesc(0, 0.08f, 0.608f, 0.84f, 0.125f, 13.5f, "#1A1523", "#FFF8F5", false, "LEFT", "• Merge, split, rotate, reorder, and number pages effortlessly.\\n• Compress large PDFs with Light, Balanced, or Strong presets.\\n• Scan physical documents with perspective crop and filters.\\n• Annotate, highlight, fill forms, and add handwritten signatures."),
                blockDesc(0, 0.07f, 0.946f, 0.44f, 0.024f, 11f, "#6B6475", "#FFF8F5", false, "LEFT", "paperpilot Welcome Guide"),
                // Page 2
                blockDesc(1, 0.07f, 0.048f, 0.76f, 0.040f, 24f, "#FFFFFF", "#E26C27", true, "LEFT", "ONE CONSISTENT FLOW"),
                blockDesc(1, 0.07f, 0.092f, 0.86f, 0.028f, 13f, "#FFFFFF", "#DF642C", false, "LEFT", "Pick files  →  Configure options  →  Process  →  Save or Share"),
                blockDesc(1, 0.08f, 0.192f, 0.44f, 0.024f, 13f, "#D03A44", "#FFFFFF", true, "LEFT", "ORGANIZE & OPTIMIZE"),
                blockDesc(1, 0.08f, 0.222f, 0.84f, 0.095f, 13.5f, "#1A1523", "#FFFFFF", false, "LEFT", "Combine multiple PDFs in custom order, split out exact page ranges like 1-3, 5, or export split sections into a single ZIP bundle."),
                blockDesc(1, 0.08f, 0.388f, 0.44f, 0.024f, 13f, "#D03A44", "#FFFFFF", true, "LEFT", "CONVERT & EXTRACT"),
                blockDesc(1, 0.08f, 0.418f, 0.84f, 0.095f, 13.5f, "#1A1523", "#FFFFFF", false, "LEFT", "Turn gallery photos into A4 or Letter PDFs with configurable margins, export PDF pages to crisp JPEGs, or run multi-language OCR."),
                // Page 3
                blockDesc(2, 0.07f, 0.048f, 0.76f, 0.040f, 24f, "#FFFFFF", "#5B81D0", true, "LEFT", "EDIT, SIGN & SCAN"),
                blockDesc(2, 0.07f, 0.092f, 0.86f, 0.028f, 13f, "#FFFFFF", "#4E89CB", false, "LEFT", "Annotate cleanly without altering your original file"),
                blockDesc(2, 0.08f, 0.192f, 0.48f, 0.024f, 13f, "#D03A44", "#FFFFFF", true, "LEFT", "NON-DESTRUCTIVE EDITOR"),
                blockDesc(2, 0.08f, 0.222f, 0.84f, 0.095f, 13.5f, "#1A1523", "#FFFFFF", false, "LEFT", "Tap any text block to edit inline, draw freehand strokes, highlight passages, attach sticky notes, and drop saved signatures."),
                // Page 4
                blockDesc(3, 0.07f, 0.048f, 0.76f, 0.040f, 24f, "#FFFFFF", "#157C7D", true, "LEFT", "PRIVATE BY DESIGN"),
                blockDesc(3, 0.07f, 0.092f, 0.86f, 0.028f, 13f, "#FFFFFF", "#176B73", false, "LEFT", "Your documents never leave your Android device"),
                blockDesc(3, 0.08f, 0.204f, 0.52f, 0.024f, 13f, "#D03A44", "#FFFFFF", true, "LEFT", "LOCAL-FIRST ARCHITECTURE"),
                blockDesc(3, 0.08f, 0.236f, 0.84f, 0.125f, 13.5f, "#1A1523", "#FFFFFF", false, "LEFT", "• Zero cloud uploads: every merge, split, edit, and scan runs locally.\\n• Works offline on flights, commutes, and restricted networks.\\n• 100% local workspace: no accounts, no sign-ins, and no cloud sync.")
            )
        )
        return allText.toString().trim()
    }

    private fun createRoadmapPdf(file: File): String {
        val pdf = PdfDocument()
        val text = StringBuilder()
        val titles = listOf(
            "Q4 PRODUCT ROADMAP" to "Executive Priorities & Milestones",
            "MILESTONE SCHEDULE" to "October – December Delivery Plan",
            "SIGN-OFF & METRICS" to "Success Criteria & Stakeholder Review"
        )
        val bodies = listOf(
            listOf(
                "Objective: Deliver a fast, private, mobile-first PDF workspace.",
                "• Reduce median file merge latency below 400ms on mid-tier devices.",
                "• Support full Light and Dark themes with accessible Deep Coral CTAs.",
                "• Ship adaptive tablet navigation rail and side thumbnail strips."
            ),
            listOf(
                "Milestone 1 (Oct): Core file library, thumbnails, favorites, and search.",
                "Milestone 2 (Nov): 28 on-device tools with unified 4-step workflow.",
                "Milestone 3 (Dec): Live CameraX document scanner & full annotation editor."
            ),
            listOf(
                "Target Crash-Free Sessions: 99.8%",
                "Offline Reliability Score: 100% core tools available in airplane mode.",
                "Accessibility: 48dp minimum touch targets and >= 4.5:1 text contrast."
            )
        )
        titles.forEachIndexed { idx, (head, sub) ->
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, idx + 1).create())
            val c = page.canvas
            c.drawColor(Color.WHITE)
            drawHeaderBand(c, 0xFF1A1523.toInt(), 0xFFD03A44.toInt(), head, sub)
            drawSectionBlock(c, 180f, "KEY DETAILS", bodies[idx])
            if (idx == 2) drawSignatureBox(c, 480f, "Product Lead Approval")
            drawFooter(c, "Q4 Product Roadmap", idx + 1, 3)
            pdf.finishPage(page)
            text.appendLine("$head - $sub: ${bodies[idx].joinToString(" ")}")
        }
        FileOutputStream(file).use { pdf.writeTo(it) }
        pdf.close()
        val roadmapBlocks = titles.flatMapIndexed { idx, (head, sub) ->
            listOf(
                blockDesc(idx, 0.07f, 0.048f, 0.80f, 0.040f, 24f, "#FFFFFF", "#58222E", true, "LEFT", head),
                blockDesc(idx, 0.07f, 0.092f, 0.84f, 0.028f, 13f, "#FFFFFF", "#6A2733", false, "LEFT", sub),
                blockDesc(idx, 0.08f, 0.198f, 0.34f, 0.024f, 13f, "#D03A44", "#FFFFFF", true, "LEFT", "KEY DETAILS"),
                blockDesc(idx, 0.08f, 0.228f, 0.84f, 0.125f, 13.5f, "#1A1523", "#FFFFFF", false, "LEFT", bodies[idx].joinToString("\\n"))
            )
        }
        appendBlockMetadata(file, roadmapBlocks)
        return text.toString().trim()
    }

    private fun createFormPdf(file: File): String {
        val pdf = PdfDocument()
        val text = StringBuilder()

        // Page 1
        val p1 = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create())
        val c1 = p1.canvas
        c1.drawColor(Color.WHITE)
        drawHeaderBand(c1, 0xFF14B8A6.toInt(), 0xFF1E2540.toInt(), "TRAVEL APPROVAL FORM", "Fillable Form Sample  •  Tap fields in Fill & Sign")

        val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFF7EFEA.toInt()
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFD03A44.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF6B6475.toInt()
            textSize = 12f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val fields = listOf(
            "Employee Name" to 180f,
            "Department / Team" to 260f,
            "Destination City" to 340f,
            "Estimated Budget ($)" to 420f
        )
        fields.forEach { (label, y) ->
            c1.drawText(label, 48f, y, labelPaint)
            val rect = RectF(48f, y + 10f, PAGE_W - 48f, y + 48f)
            c1.drawRoundRect(rect, 8f, 8f, boxPaint)
            c1.drawRoundRect(rect, 8f, 8f, borderPaint)
        }
        drawSignatureBox(c1, 540f, "Traveler Signature")
        drawFooter(c1, "Travel Approval Form", 1, 2)
        pdf.finishPage(p1)
        text.appendLine("Travel Approval Form - Employee Name, Department, Destination City, Estimated Budget, Traveler Signature.")

        // Page 2
        val p2 = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 2).create())
        val c2 = p2.canvas
        c2.drawColor(Color.WHITE)
        drawHeaderBand(c2, 0xFF64748B.toInt(), 0xFF1E2540.toInt(), "EXPENSE GUIDELINES", "Policy Summary & Receipts Checklist")
        drawSectionBlock(c2, 180f, "CHECKLIST", listOf(
            "1. Attach scanned hotel and transit receipts as a merged PDF.",
            "2. Submit within 10 business days of return date.",
            "3. Ensure manager signature is stamped before finance review."
        ))
        drawSignatureBox(c2, 460f, "Manager Approval Signature")
        drawFooter(c2, "Travel Approval Form", 2, 2)
        pdf.finishPage(p2)
        text.appendLine("Expense Guidelines - Attach scanned receipts, submit within 10 business days, manager approval signature.")

        FileOutputStream(file).use { pdf.writeTo(it) }
        pdf.close()
        appendBlockMetadata(
            file,
            listOf(
                blockDesc(0, 0.07f, 0.048f, 0.82f, 0.040f, 24f, "#FFFFFF", "#187D7B", true, "LEFT", "TRAVEL APPROVAL FORM"),
                blockDesc(0, 0.07f, 0.092f, 0.84f, 0.028f, 13f, "#FFFFFF", "#196C72", false, "LEFT", "Fillable Form Sample  •  Tap fields in Fill & Sign"),
                blockDesc(0, 0.08f, 0.198f, 0.42f, 0.024f, 12f, "#6B6475", "#FFFFFF", true, "LEFT", "Employee Name"),
                blockDesc(0, 0.08f, 0.293f, 0.42f, 0.024f, 12f, "#6B6475", "#FFFFFF", true, "LEFT", "Department / Team"),
                blockDesc(0, 0.08f, 0.388f, 0.42f, 0.024f, 12f, "#6B6475", "#FFFFFF", true, "LEFT", "Destination City"),
                blockDesc(0, 0.08f, 0.483f, 0.45f, 0.024f, 12f, "#6B6475", "#FFFFFF", true, "LEFT", "Estimated Budget ($)"),
                blockDesc(1, 0.07f, 0.048f, 0.80f, 0.040f, 24f, "#FFFFFF", "#434E68", true, "LEFT", "EXPENSE GUIDELINES"),
                blockDesc(1, 0.08f, 0.228f, 0.84f, 0.105f, 13.5f, "#1A1523", "#FFFFFF", false, "LEFT", "1. Attach scanned hotel and transit receipts as a merged PDF.\\n2. Submit within 10 business days of return date.\\n3. Ensure manager signature is stamped before finance review.")
            )
        )
        return text.toString().trim()
    }

    private fun drawHeaderBand(c: Canvas, startColor: Int, endColor: Int, title: String, subtitle: String) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, PAGE_W.toFloat(), 124f, startColor, endColor, Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, PAGE_W.toFloat(), 124f, paint)
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 24f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xE6FFFFFF.toInt()
            textSize = 13f
        }
        c.drawText(title, 44f, 64f, titlePaint)
        c.drawText(subtitle, 44f, 92f, subPaint)
    }

    private fun drawSectionBlock(c: Canvas, startY: Float, heading: String, lines: List<String>) {
        val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFD03A44.toInt()
            textSize = 13f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF1A1523.toInt()
            textSize = 13.5f
        }
        c.drawText(heading, 48f, startY, headPaint)
        var y = startY + 26f
        lines.forEach { line ->
            c.drawText(line, 48f, y, bodyPaint)
            y += 22f
        }
    }

    private fun drawSignatureBox(c: Canvas, topY: Float, caption: String) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFF8F5.toInt() }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFE7DDD8.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        val rect = RectF(48f, topY, PAGE_W - 48f, topY + 110f)
        c.drawRoundRect(rect, 14f, 14f, fill)
        c.drawRoundRect(rect, 14f, 14f, stroke)

        val line = Paint().apply {
            color = 0xFF6B6475.toInt()
            strokeWidth = 1f
        }
        c.drawLine(72f, topY + 76f, PAGE_W - 72f, topY + 76f, line)
        val capPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF6B6475.toInt()
            textSize = 11.5f
        }
        c.drawText(caption, 72f, topY + 95f, capPaint)
    }

    private fun drawFooter(c: Canvas, label: String, page: Int, total: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF6B6475.toInt()
            textSize = 11f
        }
        c.drawText(label, 44f, PAGE_H - 28f, paint)
        val rightPaint = Paint(paint).apply { textAlign = Paint.Align.RIGHT }
        c.drawText("Page $page of $total", PAGE_W - 44f, PAGE_H - 28f, rightPaint)
    }
}
