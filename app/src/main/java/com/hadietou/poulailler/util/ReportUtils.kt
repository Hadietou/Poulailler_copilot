package com.hadietou.poulailler.util

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.hadietou.poulailler.ui.DashboardViewModel
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object ReportUtils {

    fun generateAndShareReport(
        context: Context, 
        viewModel: DashboardViewModel,
        prodChartBitmap: Bitmap? = null,
        expChartBitmap: Bitmap? = null
    ) {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 Size
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas
        
        val titlePaint = Paint().apply {
            textSize = 20f
            isFakeBoldText = true
            color = Color.BLACK
        }
        val textPaint = Paint().apply {
            textSize = 11f
            color = Color.DKGRAY
        }
        val headerPaint = Paint().apply {
            textSize = 13f
            isFakeBoldText = true
            color = Color.BLACK
        }
        val subHeaderPaint = Paint().apply {
            textSize = 11f
            isFakeBoldText = true
            color = Color.BLACK
        }

        var y = 50f
        val x = 40f
        val margin = 40f
        val pageWidth = 595f

        // Header
        canvas.drawText("RAPPORT DE PERFORMANCE - KOURKOUROU", x, y, titlePaint)
        y += 25f
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        canvas.drawText("Généré le : ${sdf.format(Date())}", x, y, textPaint)
        y += 35f

        val farmName = viewModel.farmInfo.value?.farmName ?: "Ma Ferme"
        val batch = viewModel.selectedBatch.value
        val batchName = batch?.name ?: "N/A"

        canvas.drawText("FERME : ${farmName.uppercase()}", x, y, headerPaint)
        y += 18f
        canvas.drawText("LOT : $batchName (${batch?.typeLot})", x, y, textPaint)
        y += 30f

        // 1. Performance Summary
        drawSectionHeader(canvas, "RÉSUMÉ TECHNIQUE", x, y, headerPaint)
        y += 25f
        canvas.drawText("Taux de ponte : ${String.format(Locale.getDefault(), "%.1f", viewModel.layingRate.value ?: 0.0)}%", x, y, textPaint)
        canvas.drawText("Taux de survie : ${String.format(Locale.getDefault(), "%.1f", viewModel.survivalRate.value ?: 0.0)}%", x + 200f, y, textPaint)
        y += 18f
        canvas.drawText("Indice de Conso (IC) : ${String.format(Locale.getDefault(), "%.2f", viewModel.feedConversionRatio.value ?: 0.0)}", x, y, textPaint)
        canvas.drawText("Écart / Standard : ${String.format(Locale.getDefault(), "%.1f", viewModel.layingGapVsStandard.value ?: 0.0)}%", x + 200f, y, textPaint)
        y += 35f

        // 2. Production Charts (if available)
        if (prodChartBitmap != null) {
            drawSectionHeader(canvas, "ÉVOLUTION DE LA PONTE (15 DERNIERS JOURS)", x, y, headerPaint)
            y += 15f
            val destRect = RectF(x, y, pageWidth - margin, y + 150f)
            canvas.drawBitmap(prodChartBitmap, null, destRect, null)
            y += 165f
        }

        // 3. Financial Summary
        drawSectionHeader(canvas, "SITUATION FINANCIÈRE", x, y, headerPaint)
        y += 25f
        val currency = viewModel.farmInfo.value?.currency ?: "MRU"
        canvas.drawText("Ventes totales : ${viewModel.totalSales.value} $currency", x, y, textPaint)
        canvas.drawText("Dépenses totales : ${viewModel.totalExpenses.value} $currency", x + 200f, y, textPaint)
        y += 18f
        val profit = viewModel.netProfit.value ?: 0.0
        val profitPaint = Paint(textPaint).apply { 
            color = if (profit >= 0) Color.rgb(0, 100, 0) else Color.RED
            isFakeBoldText = true 
        }
        canvas.drawText("BÉNÉFICE NET GLOBAL : $profit $currency", x, y, profitPaint)
        y += 30f

        // 4. Expenses Chart
        if (expChartBitmap != null) {
            drawSectionHeader(canvas, "RÉPARTITION DES DÉPENSES", x, y, headerPaint)
            y += 15f
            val destRect = RectF(x, y, pageWidth - margin, y + 150f)
            canvas.drawBitmap(expChartBitmap, null, destRect, null)
            y += 170f
        }

        // New Page for details if needed
        if (y > 600f) {
            pdfDocument.finishPage(page)
            val newPageInfo = PdfDocument.PageInfo.Builder(595, 842, 2).create()
            page = pdfDocument.startPage(newPageInfo)
            canvas = page.canvas
            y = 50f
        }

        // 5. Sanitary & Mortality Details
        drawSectionHeader(canvas, "DÉTAILS SANITAIRES ET MORTALITÉ", x, y, headerPaint)
        y += 25f
        
        // Vaccines
        canvas.drawText("CALENDRIER SANITAIRE (PROCHAINS / RÉCENTS)", x, y, subHeaderPaint)
        y += 15f
        val vaccines = viewModel.allVaccines.value?.filter { it.batchId == batch?.firestoreId }?.take(5) ?: emptyList()
        if (vaccines.isEmpty()) {
            canvas.drawText("- Aucun vaccin enregistré", x + 10, y, textPaint)
            y += 15f
        } else {
            val dateSdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            vaccines.forEach { v ->
                canvas.drawText("- ${dateSdf.format(Date(v.date))} : ${v.name} ${if (v.remarks.isNullOrEmpty()) "" else "(${v.remarks})"}", x + 10, y, textPaint)
                y += 15f
            }
        }
        y += 10f

        // Mortality
        canvas.drawText("MORTALITÉ RÉCENTE", x, y, subHeaderPaint)
        y += 15f
        val mortalities = viewModel.allMortalities.value?.filter { it.batchId == batch?.firestoreId }?.take(5) ?: emptyList()
        if (mortalities.isEmpty()) {
            canvas.drawText("- Aucune mortalité enregistrée", x + 10, y, textPaint)
            y += 15f
        } else {
            val dateSdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            mortalities.forEach { m ->
                canvas.drawText("- ${dateSdf.format(Date(m.date))} : ${m.count} sujets ${if (m.cause.isNullOrEmpty()) "" else "(Cause: ${m.cause})"}", x + 10, y, textPaint)
                y += 15f
            }
        }

        pdfDocument.finishPage(page)

        val fileName = "Rapport_${batchName.replace(" ", "_")}_${SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())}.pdf"
        val file = File(context.cacheDir, fileName)
        
        try {
            pdfDocument.writeTo(FileOutputStream(file))
            shareFile(context, file)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            pdfDocument.close()
        }
    }

    private fun drawSectionHeader(canvas: Canvas, title: String, x: Float, y: Float, paint: Paint) {
        val linePaint = Paint().apply {
            color = Color.rgb(200, 200, 200)
            strokeWidth = 1f
        }
        canvas.drawText(title, x, y, paint)
        canvas.drawLine(x, y + 5f, 555f, y + 5f, linePaint)
    }

    private fun shareFile(context: Context, file: File) {
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Partager le rapport"))
    }
}
