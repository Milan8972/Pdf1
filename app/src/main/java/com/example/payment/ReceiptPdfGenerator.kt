package com.example.payment

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.auth.MembershipPlan
import com.example.auth.PaymentTransaction
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Generates a downloadable, shareable Blue & White PaperPilot PDF Payment Receipt
 * saved inside app storage and exposed safely via FileProvider.
 */
object ReceiptPdfGenerator {

    fun generateReceiptPdf(
        context: Context,
        transaction: PaymentTransaction,
        customerEmail: String,
        customerName: String
    ): Result<File> = runCatching {
        val receiptsDir = File(context.filesDir, "receipts").apply { mkdirs() }
        val safeId = (transaction.receiptNumber.ifBlank { transaction.transactionId.ifBlank { "receipt" } })
            .replace(Regex("[^a-zA-Z0-9_-]"), "_")
            .take(40)
        val outFile = File(receiptsDir, "PaperPilot_Receipt_$safeId.pdf")

        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas

        val bluePrimary = Color.parseColor("#0D47A1")
        val blueLightBg = Color.parseColor("#EEF4FF")
        val textDark = Color.parseColor("#0F172A")
        val textMuted = Color.parseColor("#475569")
        val borderGray = Color.parseColor("#CBD5E1")
        val greenSuccess = Color.parseColor("#0E9F6E")

        // Header Banner
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bluePrimary }
        canvas.drawRect(0f, 0f, 595f, 136f, headerPaint)

        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 28f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        canvas.drawText("PaperPilot", 44f, 64f, brandPaint)

        val subBrandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#DBEAFE")
            textSize = 13f
        }
        canvas.drawText("All-in-One PDF Editor • Official Payment Receipt", 44f, 88f, subBrandPaint)

        val receiptNo = transaction.receiptNumber.ifBlank { "PP-${transaction.transactionId.take(10)}" }
        canvas.drawText("Receipt #: $receiptNo", 44f, 114f, subBrandPaint)

        // Status Pill
        val statusBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (transaction.status == "SUCCESS") greenSuccess else Color.parseColor("#D97706")
        }
        canvas.drawRoundRect(RectF(420f, 44f, 551f, 78f), 17f, 17f, statusBg)
        val statusTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 12f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        canvas.drawText(transaction.status.ifBlank { "VERIFIED" }, 485.5f, 65f, statusTextPaint)

        // Amount Highlight Box
        val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = blueLightBg }
        canvas.drawRoundRect(RectF(44f, 162f, 551f, 250f), 14f, 14f, boxPaint)

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textMuted
            textSize = 12f
        }
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textDark
            textSize = 14f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val amountPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = bluePrimary
            textSize = 30f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }

        val planTitle = MembershipPlan.fromTierId(transaction.planTier)?.title ?: transaction.planTier
        canvas.drawText("AMOUNT PAID (${transaction.currency.ifBlank { "INR" }})", 66f, 192f, labelPaint)
        canvas.drawText("₹${transaction.amountInr}.00", 66f, 232f, amountPaint)
        canvas.drawText(planTitle, 320f, 218f, valuePaint)

        // Transaction Details Table
        val fmt = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US)
        val purchaseDate = transaction.purchaseDateMs.takeIf { it > 0L }?.let { fmt.format(Date(it)) } ?: "—"
        val expiryDate = transaction.expiryDateMs.takeIf { it > 0L }?.let { fmt.format(Date(it)) } ?: "—"

        val rows = listOf(
            "Customer Name" to customerName.ifBlank { "PaperPilot Member" },
            "Google Mail Account" to customerEmail.ifBlank { "—" },
            "Membership Plan" to "$planTitle (${transaction.planTier})",
            "Transaction ID" to transaction.transactionId.ifBlank { "—" },
            "Order ID" to transaction.orderId.ifBlank { "—" },
            "Payment Provider" to transaction.paymentProvider.ifBlank { "—" },
            "Payment Method" to transaction.paymentMethod.ifBlank { "—" },
            "Payment Status" to transaction.status.ifBlank { "—" },
            "Purchase Date" to purchaseDate,
            "Valid Until (Expiry)" to expiryDate
        )

        var y = 295f
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = borderGray
            strokeWidth = 1f
        }
        rows.forEach { (k, v) ->
            canvas.drawText(k, 48f, y, labelPaint)
            canvas.drawText(v.take(46), 235f, y, valuePaint)
            canvas.drawLine(44f, y + 14f, 551f, y + 14f, linePaint)
            y += 40f
        }

        // Security Footer
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textMuted
            textSize = 11f
        }
        canvas.drawText(
            "Verified server-side by PaperPilot Payment Services. Keep this receipt for your records.",
            44f,
            780f,
            footerPaint
        )
        canvas.drawText(
            "Package: com.aistudio.paperpilot.xkqmvn • Support: milanmoondal@gmail.com",
            44f,
            798f,
            footerPaint
        )

        doc.finishPage(page)
        FileOutputStream(outFile).use { doc.writeTo(it) }
        doc.close()
        outFile
    }

    fun buildShareReceiptIntent(context: Context, pdfFile: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdfFile)
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "PaperPilot Payment Receipt - ${pdfFile.nameWithoutExtension}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
