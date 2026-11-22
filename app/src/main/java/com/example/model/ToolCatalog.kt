package com.example.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.automirrored.rounded.CallMerge
import androidx.compose.material.icons.automirrored.rounded.CallSplit
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.BorderColor
import androidx.compose.material.icons.rounded.BrandingWatermark
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Filter
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PhotoSizeSelectLarge
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.TextSnippet
import androidx.compose.material.icons.rounded.Transform
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.ui.theme.TintConvert
import com.example.ui.theme.TintEditSign
import com.example.ui.theme.TintExtract
import com.example.ui.theme.TintImageUtils
import com.example.ui.theme.TintOptimize
import com.example.ui.theme.TintOrganize
import com.example.ui.theme.TintProtect

enum class ToolCategory(val title: String, val tint: Color) {
    CONVERT("Convert", TintConvert),
    ORGANIZE("Organize", TintOrganize),
    OPTIMIZE("Optimize", TintOptimize),
    EDIT_SIGN("Edit & Sign", TintEditSign),
    PROTECT("Protect", TintProtect),
    EXTRACT("Extract", TintExtract),
    IMAGE_UTILS("Image utilities", TintImageUtils)
}

enum class FileAcceptType {
    PDF_SINGLE,
    PDF_MULTI,
    IMAGE_SINGLE,
    IMAGE_MULTI,
    OFFICE_DOC
}

enum class ToolId(
    val title: String,
    val shortDescription: String,
    val category: ToolCategory,
    val icon: ImageVector,
    val acceptType: FileAcceptType,
    val isBasicConversion: Boolean = false,
    val isProTool: Boolean = false,
    val opensEditorDirectly: Boolean = false,
    val supportsReorder: Boolean = false
) {
    // Convert
    PDF_TO_WORD(
        title = "PDF to Word",
        shortDescription = "Extract document layout into editable Word text (.rtf/.doc)",
        category = ToolCategory.CONVERT,
        icon = Icons.Rounded.Description,
        acceptType = FileAcceptType.PDF_SINGLE,
        isBasicConversion = true
    ),
    PDF_TO_EXCEL(
        title = "PDF to Excel",
        shortDescription = "Export structured tables and rows to spreadsheet (.csv)",
        category = ToolCategory.CONVERT,
        icon = Icons.Rounded.TableChart,
        acceptType = FileAcceptType.PDF_SINGLE,
        isBasicConversion = true
    ),
    PDF_TO_PPT(
        title = "PDF to PowerPoint",
        shortDescription = "Turn PDF pages into a presentation outline package",
        category = ToolCategory.CONVERT,
        icon = Icons.Rounded.Slideshow,
        acceptType = FileAcceptType.PDF_SINGLE,
        isBasicConversion = true
    ),
    PDF_TO_JPG(
        title = "PDF to JPG",
        shortDescription = "Render every PDF page into crisp JPEG images or ZIP",
        category = ToolCategory.CONVERT,
        icon = Icons.Rounded.Collections,
        acceptType = FileAcceptType.PDF_SINGLE
    ),
    WORD_TO_PDF(
        title = "Word to PDF",
        shortDescription = "Convert text or Word documents into a clean PDF",
        category = ToolCategory.CONVERT,
        icon = Icons.AutoMirrored.Rounded.Article,
        acceptType = FileAcceptType.OFFICE_DOC,
        isBasicConversion = true
    ),
    EXCEL_TO_PDF(
        title = "Excel to PDF",
        shortDescription = "Render CSV or spreadsheet rows into paginated PDF tables",
        category = ToolCategory.CONVERT,
        icon = Icons.Rounded.TableChart,
        acceptType = FileAcceptType.OFFICE_DOC,
        isBasicConversion = true
    ),
    PPT_TO_PDF(
        title = "PowerPoint to PDF",
        shortDescription = "Convert slide notes and deck text into a PDF",
        category = ToolCategory.CONVERT,
        icon = Icons.Rounded.Slideshow,
        acceptType = FileAcceptType.OFFICE_DOC,
        isBasicConversion = true
    ),
    IMAGE_TO_PDF(
        title = "Image to PDF",
        shortDescription = "Combine photos into a neatly sized PDF document",
        category = ToolCategory.CONVERT,
        icon = Icons.Rounded.PictureAsPdf,
        acceptType = FileAcceptType.IMAGE_MULTI,
        supportsReorder = true
    ),

    // Organize
    MERGE(
        title = "Merge PDF",
        shortDescription = "Combine multiple PDF files in your preferred order",
        category = ToolCategory.ORGANIZE,
        icon = Icons.AutoMirrored.Rounded.CallMerge,
        acceptType = FileAcceptType.PDF_MULTI,
        supportsReorder = true
    ),
    SPLIT(
        title = "Split PDF",
        shortDescription = "Extract page ranges, split every N pages, or export ZIP",
        category = ToolCategory.ORGANIZE,
        icon = Icons.AutoMirrored.Rounded.CallSplit,
        acceptType = FileAcceptType.PDF_SINGLE
    ),
    ROTATE(
        title = "Rotate PDF",
        shortDescription = "Rotate all pages or specific pages by 90°, 180°, or 270°",
        category = ToolCategory.ORGANIZE,
        icon = Icons.AutoMirrored.Rounded.RotateRight,
        acceptType = FileAcceptType.PDF_SINGLE
    ),
    ORGANIZE_PAGES(
        title = "Organize pages",
        shortDescription = "Reorder, duplicate, rotate, or remove individual pages",
        category = ToolCategory.ORGANIZE,
        icon = Icons.Rounded.GridView,
        acceptType = FileAcceptType.PDF_SINGLE
    ),
    PAGE_NUMBERS(
        title = "Page numbers",
        shortDescription = "Stamp clean page numbers in any corner or margin",
        category = ToolCategory.ORGANIZE,
        icon = Icons.Rounded.FormatListNumbered,
        acceptType = FileAcceptType.PDF_SINGLE
    ),

    // Optimize
    COMPRESS(
        title = "Compress PDF",
        shortDescription = "Shrink file size with Light, Balanced, or Strong optimization",
        category = ToolCategory.OPTIMIZE,
        icon = Icons.Rounded.Compress,
        acceptType = FileAcceptType.PDF_SINGLE
    ),
    REPAIR(
        title = "Repair PDF",
        shortDescription = "Rebuild PDF page structure cleanly into a fresh file",
        category = ToolCategory.OPTIMIZE,
        icon = Icons.Rounded.Build,
        acceptType = FileAcceptType.PDF_SINGLE
    ),

    // Edit & Sign
    EDIT_PDF(
        title = "Edit PDF",
        shortDescription = "Add styled text, shapes, images, and freehand marks",
        category = ToolCategory.EDIT_SIGN,
        icon = Icons.Rounded.EditNote,
        acceptType = FileAcceptType.PDF_SINGLE,
        opensEditorDirectly = true
    ),
    ANNOTATE(
        title = "Annotate",
        shortDescription = "Highlight, underline, strikethrough, and attach sticky notes",
        category = ToolCategory.EDIT_SIGN,
        icon = Icons.Rounded.BorderColor,
        acceptType = FileAcceptType.PDF_SINGLE,
        opensEditorDirectly = true
    ),
    FILL_SIGN(
        title = "Fill & Sign",
        shortDescription = "Fill interactive form fields and place handwritten signatures",
        category = ToolCategory.EDIT_SIGN,
        icon = Icons.Rounded.Draw,
        acceptType = FileAcceptType.PDF_SINGLE,
        opensEditorDirectly = true
    ),

    // Protect
    PROTECT(
        title = "Protect PDF",
        shortDescription = "Lock with password verification and print/copy/edit controls",
        category = ToolCategory.PROTECT,
        icon = Icons.Rounded.Lock,
        acceptType = FileAcceptType.PDF_SINGLE
    ),
    UNLOCK(
        title = "Unlock PDF",
        shortDescription = "Verify password and save an unrestricted copy",
        category = ToolCategory.PROTECT,
        icon = Icons.Rounded.LockOpen,
        acceptType = FileAcceptType.PDF_SINGLE
    ),
    WATERMARK(
        title = "Watermark",
        shortDescription = "Overlay custom text or tiled stamps with opacity & angle",
        category = ToolCategory.PROTECT,
        icon = Icons.Rounded.BrandingWatermark,
        acceptType = FileAcceptType.PDF_SINGLE
    ),

    // Extract
    EXTRACT_TEXT(
        title = "Extract text",
        shortDescription = "Pull readable text into a .txt file with one-tap copy",
        category = ToolCategory.EXTRACT,
        icon = Icons.Rounded.TextSnippet,
        acceptType = FileAcceptType.PDF_SINGLE
    ),
    EXTRACT_IMAGES(
        title = "Extract images",
        shortDescription = "Export high-resolution page snapshots as PNG/ZIP",
        category = ToolCategory.EXTRACT,
        icon = Icons.Rounded.PhotoLibrary,
        acceptType = FileAcceptType.PDF_SINGLE
    ),
    OCR(
        title = "OCR PDF",
        shortDescription = "Recognize document text in multiple languages to PDF or TXT",
        category = ToolCategory.EXTRACT,
        icon = Icons.Rounded.DocumentScanner,
        acceptType = FileAcceptType.PDF_SINGLE,
        isProTool = true
    ),

    // Image utilities
    COMPRESS_IMAGE(
        title = "Compress image",
        shortDescription = "Reduce JPEG/PNG photo file size on-device",
        category = ToolCategory.IMAGE_UTILS,
        icon = Icons.Rounded.AutoFixHigh,
        acceptType = FileAcceptType.IMAGE_SINGLE
    ),
    RESIZE_IMAGE(
        title = "Resize image",
        shortDescription = "Scale image dimensions precisely with aspect lock",
        category = ToolCategory.IMAGE_UTILS,
        icon = Icons.Rounded.PhotoSizeSelectLarge,
        acceptType = FileAcceptType.IMAGE_SINGLE
    ),
    CONVERT_TO_JPG(
        title = "Convert to JPG",
        shortDescription = "Convert PNG or WebP images into universal JPG format",
        category = ToolCategory.IMAGE_UTILS,
        icon = Icons.Rounded.Image,
        acceptType = FileAcceptType.IMAGE_SINGLE
    ),
    CONVERT_FROM_JPG(
        title = "Convert from JPG",
        shortDescription = "Export JPG images as lossless PNG or compact WebP",
        category = ToolCategory.IMAGE_UTILS,
        icon = Icons.Rounded.Transform,
        acceptType = FileAcceptType.IMAGE_SINGLE
    );

    companion object {
        val defaultQuickTools: List<ToolId> = listOf(
            MERGE, COMPRESS, SPLIT, IMAGE_TO_PDF, FILL_SIGN, PDF_TO_JPG
        )

        fun byCategory(): Map<ToolCategory, List<ToolId>> =
            ToolCategory.entries.associateWith { cat -> entries.filter { it.category == cat } }
    }
}
