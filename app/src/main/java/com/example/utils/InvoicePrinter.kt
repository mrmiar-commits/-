package com.example.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import com.example.model.Customer
import com.example.model.GoldTransaction
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.io.FileOutputStream

object InvoicePrinter {

    fun printTransactionInvoice(
        context: Context,
        shopName: String,
        ownerName: String,
        tx: GoldTransaction
    ) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val jobName = "Zarnegar_Invoice_${tx.invoiceNumber}"
        printManager.print(
            jobName,
            object : PrintDocumentAdapter() {
                override fun onLayout(
                    oldAttributes: PrintAttributes?,
                    newAttributes: PrintAttributes,
                    cancellationSignal: CancellationSignal?,
                    callback: LayoutResultCallback,
                    extras: Bundle?
                ) {
                    if (cancellationSignal?.isCanceled == true) {
                        callback.onLayoutCancelled()
                        return
                    }
                    val info = PrintDocumentInfo.Builder("$jobName.pdf")
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .setPageCount(1)
                        .build()
                    callback.onLayoutFinished(info, true)
                }

                override fun onWrite(
                    pages: Array<out PageRange>?,
                    destination: ParcelFileDescriptor,
                    cancellationSignal: CancellationSignal?,
                    callback: WriteResultCallback
                ) {
                    val pdf = PdfDocument()
                    val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
                    val page = pdf.startPage(pageInfo)
                    drawInvoicePage(page.canvas, shopName, ownerName, tx)
                    pdf.finishPage(page)

                    try {
                        FileOutputStream(destination.fileDescriptor).use { out ->
                            pdf.writeTo(out)
                        }
                        callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                    } catch (e: Exception) {
                        callback.onWriteFailed(e.message)
                    } finally {
                        pdf.close()
                    }
                }
            },
            PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A5).build()
        )
    }

    fun printCustomerLedger(
        context: Context,
        shopName: String,
        customer: Customer,
        transactions: List<GoldTransaction>
    ) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val jobName = "Zarnegar_Customer_${customer.fullName}"
        printManager.print(
            jobName,
            object : PrintDocumentAdapter() {
                override fun onLayout(
                    oldAttributes: PrintAttributes?,
                    newAttributes: PrintAttributes,
                    cancellationSignal: CancellationSignal?,
                    callback: LayoutResultCallback,
                    extras: Bundle?
                ) {
                    if (cancellationSignal?.isCanceled == true) {
                        callback.onLayoutCancelled()
                        return
                    }
                    val info = PrintDocumentInfo.Builder("$jobName.pdf")
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .setPageCount(1)
                        .build()
                    callback.onLayoutFinished(info, true)
                }

                override fun onWrite(
                    pages: Array<out PageRange>?,
                    destination: ParcelFileDescriptor,
                    cancellationSignal: CancellationSignal?,
                    callback: WriteResultCallback
                ) {
                    val pdf = PdfDocument()
                    val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
                    val page = pdf.startPage(pageInfo)
                    drawCustomerFilePage(page.canvas, shopName, customer, transactions)
                    pdf.finishPage(page)
                    try {
                        FileOutputStream(destination.fileDescriptor).use { out -> pdf.writeTo(out) }
                        callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                    } catch (e: Exception) {
                        callback.onWriteFailed(e.message)
                    } finally {
                        pdf.close()
                    }
                }
            },
            null
        )
    }

    fun printFinancialReport(
        context: Context,
        shopName: String,
        periodLabel: String,
        summaryLines: List<Pair<String, String>>
    ) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val jobName = "Zarnegar_Report_$periodLabel"
        printManager.print(
            jobName,
            object : PrintDocumentAdapter() {
                override fun onLayout(
                    oldAttributes: PrintAttributes?,
                    newAttributes: PrintAttributes,
                    cancellationSignal: CancellationSignal?,
                    callback: LayoutResultCallback,
                    extras: Bundle?
                ) {
                    if (cancellationSignal?.isCanceled == true) {
                        callback.onLayoutCancelled()
                        return
                    }
                    val info = PrintDocumentInfo.Builder("$jobName.pdf")
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .setPageCount(1)
                        .build()
                    callback.onLayoutFinished(info, true)
                }

                override fun onWrite(
                    pages: Array<out PageRange>?,
                    destination: ParcelFileDescriptor,
                    cancellationSignal: CancellationSignal?,
                    callback: WriteResultCallback
                ) {
                    val pdf = PdfDocument()
                    val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
                    val page = pdf.startPage(pageInfo)
                    val canvas = page.canvas
                    val headerBg = Paint().apply { color = Color.rgb(17, 19, 24) }
                    val titlePaint = Paint().apply {
                        color = Color.rgb(230, 184, 76)
                        textSize = 20f
                        isFakeBoldText = true
                        textAlign = Paint.Align.RIGHT
                    }
                    val subtitlePaint = Paint().apply {
                        color = Color.WHITE
                        textSize = 12f
                        textAlign = Paint.Align.RIGHT
                    }
                    val labelPaint = Paint().apply {
                        color = Color.rgb(30, 30, 36)
                        textSize = 13f
                        isFakeBoldText = true
                        textAlign = Paint.Align.RIGHT
                    }
                    val valPaint = Paint().apply {
                        color = Color.rgb(50, 50, 60)
                        textSize = 13f
                        textAlign = Paint.Align.LEFT
                    }
                    val divider = Paint().apply { color = Color.rgb(225, 228, 232); strokeWidth = 1f }

                    canvas.drawRect(0f, 0f, 595f, 100f, headerBg)
                    canvas.drawText("$shopName — گزارش مالی و انبار", 560f, 44f, titlePaint)
                    canvas.drawText("بازه گزارش: $periodLabel  |  تاریخ صدور: ${Formatters.formatMillis(System.currentTimeMillis())}", 560f, 74f, subtitlePaint)

                    var y = 140f
                    summaryLines.forEach { (k, v) ->
                        canvas.drawText(k, 560f, y, labelPaint)
                        canvas.drawText(v, 40f, y, valPaint)
                        canvas.drawLine(40f, y + 10f, 560f, y + 10f, divider)
                        y += 32f
                    }
                    pdf.finishPage(page)
                    try {
                        FileOutputStream(destination.fileDescriptor).use { out -> pdf.writeTo(out) }
                        callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                    } catch (e: Exception) {
                        callback.onWriteFailed(e.message)
                    } finally {
                        pdf.close()
                    }
                }
            },
            null
        )
    }

    private fun drawInvoicePage(canvas: Canvas, shopName: String, ownerName: String, tx: GoldTransaction) {
        val headerBg = Paint().apply { color = Color.rgb(18, 22, 31) }
        val goldPaint = Paint().apply { color = Color.rgb(212, 155, 39); strokeWidth = 2f }
        val titlePaint = Paint().apply {
            color = Color.rgb(245, 196, 81)
            textSize = 22f
            isFakeBoldText = true
            textAlign = Paint.Align.RIGHT
        }
        val subtitlePaint = Paint().apply {
            color = Color.WHITE
            textSize = 13f
            textAlign = Paint.Align.RIGHT
        }
        val bodyPaint = Paint().apply {
            color = Color.rgb(24, 24, 27)
            textSize = 13f
            textAlign = Paint.Align.RIGHT
        }
        val boldBodyPaint = Paint().apply {
            color = Color.rgb(24, 24, 27)
            textSize = 15f
            isFakeBoldText = true
            textAlign = Paint.Align.RIGHT
        }
        val borderPaint = Paint().apply {
            color = Color.rgb(210, 214, 220)
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }

        // Header banner
        canvas.drawRect(0f, 0f, 595f, 115f, headerBg)
        canvas.drawText(shopName, 560f, 45f, titlePaint)
        canvas.drawText("مدیریت: $ownerName  |  فاکتور رسمی معاملات طلا و ارز", 560f, 74f, subtitlePaint)
        canvas.drawText("شماره فاکتور: ${tx.invoiceNumber}   |   تاریخ: ${Formatters.formatTimestamp(tx.createdAt)}", 560f, 98f, subtitlePaint)

        // Box around invoice summary
        canvas.drawRect(32f, 135f, 563f, 520f, borderPaint)

        var y = 170f
        val rows = listOf(
            "نام مشتری / طرف حساب:" to (tx.customerName ?: "مشتری حضوری"),
            "نوع عملیات:" to tx.txTypeEnum.labelFa,
            "دسته کالا / ارز:" to tx.categoryEnum.labelFa,
            "وزن / مقدار:" to "${Formatters.formatGrams(tx.weightGrams)} (عیار ${Formatters.toPersianDigits(tx.karat.toString())})",
            "نرخ پایه هر واحد:" to Formatters.formatToman(tx.unitPriceToman),
            "اجرت ساخت:" to Formatters.formatPercent(tx.wagePercent),
            "سود فروش:" to Formatters.formatPercent(tx.profitMarginPercent),
            "مالیات ارزش افزوده:" to Formatters.formatPercent(tx.taxPercent),
            "حساب مغازه:" to tx.accountName,
            "روش پرداخت:" to "${tx.paymentMethodEnum.labelFa} (${tx.paymentStatusEnum.labelFa})",
            "کد پیگیری درگاه:" to (tx.gatewayRefId ?: "-")
        )

        rows.forEach { (label, value) ->
            canvas.drawText(label, 540f, y, boldBodyPaint)
            canvas.drawText(value, 320f, y, bodyPaint)
            y += 28f
        }

        canvas.drawLine(32f, y + 6f, 563f, y + 6f, goldPaint)
        y += 34f
        canvas.drawText("مبلغ کل قابل پرداخت:", 540f, y, boldBodyPaint)
        canvas.drawText(Formatters.formatToman(tx.totalAmountToman), 320f, y, boldBodyPaint)

        // Draw verification QR code at bottom left
        drawQrToCanvas(
            canvas,
            "ZARNEGAR|${tx.invoiceNumber}|${tx.totalAmountToman.toLong()}|${tx.weightGrams}",
            40f,
            555f,
            110
        )
        canvas.drawText("مهر و امضای فروشنده", 540f, 610f, boldBodyPaint)
        canvas.drawText("مهر و امضای خریدار", 330f, 610f, boldBodyPaint)
        if (!tx.notes.isNullOrBlank()) {
            canvas.drawText("توضیحات: ${tx.notes}", 540f, 680f, bodyPaint)
        }
    }

    private fun drawCustomerFilePage(
        canvas: Canvas,
        shopName: String,
        customer: Customer,
        transactions: List<GoldTransaction>
    ) {
        val headerBg = Paint().apply { color = Color.rgb(18, 22, 31) }
        val titlePaint = Paint().apply {
            color = Color.rgb(245, 196, 81)
            textSize = 20f
            isFakeBoldText = true
            textAlign = Paint.Align.RIGHT
        }
        val subtitlePaint = Paint().apply {
            color = Color.WHITE
            textSize = 13f
            textAlign = Paint.Align.RIGHT
        }
        val bodyPaint = Paint().apply {
            color = Color.rgb(24, 24, 27)
            textSize = 12f
            textAlign = Paint.Align.RIGHT
        }
        val boldPaint = Paint().apply {
            color = Color.rgb(24, 24, 27)
            textSize = 13f
            isFakeBoldText = true
            textAlign = Paint.Align.RIGHT
        }

        canvas.drawRect(0f, 0f, 595f, 110f, headerBg)
        canvas.drawText("$shopName - پرونده اختصاصی مشتری", 560f, 44f, titlePaint)
        canvas.drawText("مشتری: ${customer.fullName}  |  تلفن: ${customer.phone}", 560f, 72f, subtitlePaint)
        canvas.drawText(
            "تراز طلا: ${Formatters.formatGrams(customer.goldBalanceGrams)}  |  مانده نقدی: ${Formatters.formatToman(customer.cashBalanceToman)}",
            560f,
            96f,
            subtitlePaint
        )

        var y = 145f
        canvas.drawText("سوابق تراکنش‌های ثبت‌شده (${Formatters.toPersianDigits(transactions.size.toString())} مورد):", 560f, y, boldPaint)
        y += 28f

        transactions.take(22).forEach { tx ->
            val line = "${tx.invoiceNumber} | ${tx.txTypeEnum.labelFa} (${tx.categoryEnum.labelFa}) | ${Formatters.formatGrams(tx.weightGrams)} | ${Formatters.formatToman(tx.totalAmountToman)}"
            canvas.drawText(line, 560f, y, bodyPaint)
            y += 24f
        }
    }

    private fun drawQrToCanvas(canvas: Canvas, content: String, left: Float, top: Float, size: Int) {
        try {
            val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
            val paint = Paint().apply { color = Color.BLACK }
            for (x in 0 until size) {
                for (y in 0 until size) {
                    if (matrix[x, y]) {
                        canvas.drawPoint(left + x, top + y, paint)
                    }
                }
            }
        } catch (_: Exception) {
        }
    }
}
