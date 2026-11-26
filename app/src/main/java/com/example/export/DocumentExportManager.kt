package com.example.export

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.example.BuildConfig
import com.example.ai.LatexMathTopic
import com.example.ai.MathComputationResult
import com.example.ai.MathVerificationSource
import com.example.ai.PlotSeries
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.abs

enum class DocTemplateType(val displayName: String, val accentHex: String) {
    RAPPORT("Rapport technique", "0F4C81"),
    EXPOSE("Exposé structuré", "4F46E5"),
    MEMOIRE("Mémoire & Synthèse", "1E293B"),
    FICHE_PEDAGOGIQUE("Fiche pédagogique LaTeX", "0284C7")
}

data class FormulaRecord(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val topicId: String = LatexMathTopic.ANALYSE.name,
    val verificationSource: String = MathVerificationSource.COMPUTED_LOCAL.name,
    val inputExpression: String,
    val primaryResult: String,
    val unicodeFormula: String,
    val latexFormula: String,
    val steps: List<String> = emptyList(),
    val aiExplanation: String = "",
    val createdAtMs: Long = System.currentTimeMillis()
)

data class ExportSection(
    val heading: String,
    val topicBadge: String = "",
    val verificationLabel: String = "",
    val formulaText: String = "",
    val latexText: String = "",
    val bodyText: String = "",
    val tableRows: List<List<String>> = emptyList(),
    val chartPlot: PlotSeries? = null,
    val extraBlocks: List<Pair<String, String>> = emptyList()
)

data class ExportedDocumentResult(
    val file: File,
    val mimeType: String,
    val formatLabel: String
)

object DocumentExportManager {

    const val MIME_PDF = "application/pdf"
    const val MIME_DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

    fun parseSavedFormulas(rawJson: String): List<FormulaRecord> {
        if (rawJson.isBlank() || rawJson == "[]") return emptyList()
        return runCatching {
            val arr = JSONArray(rawJson)
            (0 until arr.length()).mapNotNull { idx ->
                val o = arr.optJSONObject(idx) ?: return@mapNotNull null
                val stepsArr = o.optJSONArray("steps") ?: JSONArray()
                val steps = (0 until stepsArr.length()).mapNotNull { i ->
                    stepsArr.optString(i).takeIf { it.isNotBlank() }
                }
                val topicFallback = o.optString("topicId").ifBlank { LatexMathTopic.ANALYSE.name }
                FormulaRecord(
                    id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                    title = o.optString("title", "Formule LaTeX"),
                    topicId = LatexMathTopic.fromId(topicFallback).name,
                    verificationSource = o.optString("verificationSource", MathVerificationSource.COMPUTED_LOCAL.name),
                    inputExpression = o.optString("inputExpression"),
                    primaryResult = o.optString("primaryResult"),
                    unicodeFormula = o.optString("unicodeFormula"),
                    latexFormula = o.optString("latexFormula"),
                    steps = steps,
                    aiExplanation = o.optString("aiExplanation"),
                    createdAtMs = o.optLong("createdAtMs", System.currentTimeMillis())
                )
            }
        }.getOrDefault(emptyList())
    }

    fun serializeSavedFormulas(list: List<FormulaRecord>): String {
        val arr = JSONArray()
        list.take(100).forEach { f ->
            arr.put(JSONObject().apply {
                put("id", f.id)
                put("title", f.title)
                put("topicId", f.topicId)
                put("verificationSource", f.verificationSource)
                put("inputExpression", f.inputExpression)
                put("primaryResult", f.primaryResult)
                put("unicodeFormula", f.unicodeFormula)
                put("latexFormula", f.latexFormula)
                put("steps", JSONArray(f.steps))
                put("aiExplanation", f.aiExplanation)
                put("createdAtMs", f.createdAtMs)
            })
        }
        return arr.toString()
    }

    fun sectionFromMathResult(
        result: MathComputationResult,
        aiExplanation: String = ""
    ): ExportSection {
        val body = buildString {
            appendLine("Expression LaTeX : ${result.rawInput}")
            appendLine("Résultat : ${result.primaryResult}")
            if (result.steps.isNotEmpty()) {
                appendLine()
                appendLine("Étapes de résolution :")
                result.steps.forEachIndexed { idx, s -> appendLine("${idx + 1}. $s") }
            }
            if (aiExplanation.isNotBlank()) {
                appendLine()
                appendLine("Analyse détaillée :")
                appendLine(aiExplanation)
            }
        }.trim()
        val table = if (result.steps.isNotEmpty()) {
            listOf(listOf("Étape", "Détail")) +
                result.steps.mapIndexed { idx, s -> listOf("${idx + 1}", s) }
        } else emptyList()

        return ExportSection(
            heading = result.operationLabel,
            topicBadge = result.topic.displayName,
            verificationLabel = result.verificationSource.labelFr,
            formulaText = result.unicodeFormula,
            latexText = result.latexFormula,
            bodyText = body,
            tableRows = table,
            chartPlot = result.plotSeries,
            extraBlocks = result.relatedFormulas
        )
    }

    fun sectionFromFormulaRecord(record: FormulaRecord): ExportSection {
        val topic = LatexMathTopic.fromId(record.topicId)
        val verif = runCatching { MathVerificationSource.valueOf(record.verificationSource) }
            .getOrDefault(MathVerificationSource.COMPUTED_LOCAL)
        val body = buildString {
            appendLine("Expression : ${record.inputExpression}")
            appendLine("Résultat : ${record.primaryResult}")
            if (record.steps.isNotEmpty()) {
                appendLine()
                appendLine("Étapes :")
                record.steps.forEachIndexed { i, st -> appendLine("${i + 1}. $st") }
            }
            if (record.aiExplanation.isNotBlank()) {
                appendLine()
                appendLine("Explication IA :")
                appendLine(record.aiExplanation)
            }
        }.trim()
        return ExportSection(
            heading = record.title.ifBlank { "Formule mathématique LaTeX" },
            topicBadge = topic.displayName,
            verificationLabel = verif.labelFr,
            formulaText = record.unicodeFormula,
            latexText = record.latexFormula,
            bodyText = body
        )
    }

    fun exportToPdf(
        context: Context,
        title: String,
        subtitle: String,
        sections: List<ExportSection>,
        fileNamePrefix: String = "brav_document",
        template: DocTemplateType = DocTemplateType.RAPPORT
    ): ExportedDocumentResult {
        val outFile = createExportFile(context, fileNamePrefix, "pdf")
        writePdfToFile(outFile, title, subtitle, sections, template)
        return ExportedDocumentResult(outFile, MIME_PDF, "PDF (${template.displayName})")
    }

    fun writePdfToFile(
        outputFile: File,
        title: String,
        subtitle: String,
        sections: List<ExportSection>,
        template: DocTemplateType = DocTemplateType.RAPPORT
    ) {
        outputFile.parentFile?.mkdirs()
        val nativeOk = runCatching {
            writeNativeAndroidPdf(outputFile, title, subtitle, sections, template)
        }.isSuccess
        if (!nativeOk || !outputFile.exists() || outputFile.length() < 64L) {
            writePurePdfFallback(outputFile, title, subtitle, sections, template)
        }
    }

    private fun writeNativeAndroidPdf(
        outputFile: File,
        title: String,
        subtitle: String,
        sections: List<ExportSection>,
        template: DocTemplateType
    ) {
        val pageW = 595
        val pageH = 842
        val margin = 42f
        val footerAreaH = 28f
        val contentWidth = (pageW - 2 * margin).toInt()
        val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(Date())

        val doc = PdfDocument()
        try {
            var pageNum = 1
            var page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create())
            var canvas = page.canvas
            var y = margin + 12f

            val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(18, 52, 86)
                textSize = 18f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val subtitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(95, 110, 128)
                textSize = 10f
            }
            val headerFooterPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(100, 116, 139)
                textSize = 8.5f
            }
            val headingPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(15, 76, 129)
                textSize = 13f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val badgePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(2, 132, 199)
                textSize = 9.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val formulaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(22, 32, 46)
                textSize = 11.5f
                typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            }
            val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(38, 45, 56)
                textSize = 10.5f
            }
            val codePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(30, 41, 59)
                textSize = 9.5f
                typeface = Typeface.MONOSPACE
            }
            val boxBgPaint = Paint().apply {
                color = Color.rgb(241, 245, 249)
                style = Paint.Style.FILL
            }
            val linePaint = Paint().apply {
                color = Color.rgb(203, 213, 225)
                strokeWidth = 1f
            }
            val tableBorderPaint = Paint().apply {
                color = Color.rgb(180, 194, 210)
                style = Paint.Style.STROKE
                strokeWidth = 1f
            }

            fun drawPageChrome(c: Canvas, pNum: Int) {
                c.drawText("BRAV Chat • ${template.displayName}", margin, 26f, headerFooterPaint)
                c.drawText(dateStr, pageW - margin - 90f, 26f, headerFooterPaint)
                c.drawLine(margin, 32f, pageW - margin, 32f, linePaint)
                c.drawLine(margin, pageH - 30f, pageW - margin, pageH - 30f, linePaint)
                c.drawText("Page $pNum • Document généré par BRAV Chat LaTeX", margin, pageH - 16f, headerFooterPaint)
            }

            drawPageChrome(canvas, pageNum)

            fun ensureSpace(neededHeight: Float) {
                if (y + neededHeight > pageH - margin - footerAreaH) {
                    doc.finishPage(page)
                    pageNum++
                    page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create())
                    canvas = page.canvas
                    drawPageChrome(canvas, pageNum)
                    y = margin + 8f
                }
            }

            fun drawWrapped(text: String, paint: TextPaint, xOffset: Float = margin, width: Int = contentWidth, extraPadBottom: Float = 6f) {
                if (text.isBlank()) return
                val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(2f, 1.15f)
                    .build()
                ensureSpace(layout.height + extraPadBottom)
                canvas.save()
                canvas.translate(xOffset, y)
                layout.draw(canvas)
                canvas.restore()
                y += layout.height + extraPadBottom
            }

            drawWrapped(title, titlePaint, extraPadBottom = 4f)
            drawWrapped("$subtitle • ${template.displayName} • $dateStr", subtitlePaint, extraPadBottom = 10f)
            canvas.drawLine(margin, y, pageW - margin, y, linePaint)
            y += 14f

            sections.forEach { sec ->
                drawWrapped(sec.heading, headingPaint, extraPadBottom = 2f)
                val badgeLine = listOf(sec.topicBadge, sec.verificationLabel).filter { it.isNotBlank() }.joinToString(" • ")
                if (badgeLine.isNotBlank()) {
                    drawWrapped(badgeLine, badgePaint, extraPadBottom = 4f)
                }
                if (sec.formulaText.isNotBlank() || sec.latexText.isNotBlank()) {
                    val combined = buildString {
                        if (sec.formulaText.isNotBlank()) append(sec.formulaText)
                        if (sec.latexText.isNotBlank()) {
                            if (isNotEmpty()) append("\n")
                            append("LaTeX : ${sec.latexText}")
                        }
                    }
                    val fLayout = StaticLayout.Builder.obtain(combined, 0, combined.length, formulaPaint, contentWidth - 16)
                        .setLineSpacing(2f, 1.15f)
                        .build()
                    ensureSpace(fLayout.height + 16f)
                    canvas.drawRect(margin, y, pageW - margin, y + fLayout.height + 12f, boxBgPaint)
                    canvas.save()
                    canvas.translate(margin + 8f, y + 6f)
                    fLayout.draw(canvas)
                    canvas.restore()
                    y += fLayout.height + 18f
                }
                if (sec.bodyText.isNotBlank()) {
                    drawWrapped(sec.bodyText, bodyPaint, extraPadBottom = 8f)
                }
                if (sec.tableRows.isNotEmpty()) {
                    val cols = sec.tableRows.maxOfOrNull { it.size } ?: 1
                    val colW = contentWidth.toFloat() / cols.coerceAtLeast(1)
                    sec.tableRows.take(20).forEachIndexed { rIdx, row ->
                        ensureSpace(22f)
                        if (rIdx == 0) {
                            canvas.drawRect(margin, y, margin + contentWidth, y + 20f, boxBgPaint)
                        }
                        canvas.drawRect(margin, y, margin + contentWidth, y + 20f, tableBorderPaint)
                        row.take(cols).forEachIndexed { cIdx, cell ->
                            val cellText = cell.take(42)
                            canvas.drawText(
                                cellText,
                                margin + cIdx * colW + 4f,
                                y + 14f,
                                if (rIdx == 0) badgePaint else bodyPaint
                            )
                        }
                        y += 20f
                    }
                    y += 8f
                }
                sec.chartPlot?.let { plot ->
                    val png = renderChartPngBytes(plot, widthPx = 720, heightPx = 340)
                    val bmp = BitmapFactory.decodeByteArray(png, 0, png.size)
                    if (bmp != null) {
                        val drawH = 190f
                        ensureSpace(drawH + 12f)
                        val dst = Rect(margin.toInt(), y.toInt(), (pageW - margin).toInt(), (y + drawH).toInt())
                        canvas.drawBitmap(bmp, null, dst, null)
                        bmp.recycle()
                        y += drawH + 10f
                    }
                }
                if (sec.extraBlocks.isNotEmpty()) {
                    sec.extraBlocks.forEach { (k, v) ->
                        drawWrapped("$k : $v", codePaint, xOffset = margin + 6f, width = contentWidth - 12, extraPadBottom = 4f)
                    }
                }
                canvas.drawLine(margin, y, pageW - margin, y, linePaint)
                y += 12f
            }

            doc.finishPage(page)
            FileOutputStream(outputFile).use { fos ->
                doc.writeTo(fos)
            }
        } finally {
            runCatching { doc.close() }
        }
    }

    private fun writePurePdfFallback(
        outputFile: File,
        title: String,
        subtitle: String,
        sections: List<ExportSection>,
        template: DocTemplateType
    ) {
        data class PdfLine(val text: String, val font: String, val size: Int, val gap: Float)
        val lines = mutableListOf<PdfLine>()
        val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(Date())

        fun addWrapped(raw: String, font: String, size: Int, maxChars: Int, gap: Float) {
            raw.lines().forEach { paragraph ->
                if (paragraph.isBlank()) {
                    lines.add(PdfLine("", font, size, gap))
                } else {
                    var idx = 0
                    while (idx < paragraph.length) {
                        val end = (idx + maxChars).coerceAtMost(paragraph.length)
                        lines.add(PdfLine(paragraph.substring(idx, end), font, size, gap))
                        idx = end
                    }
                }
            }
        }

        addWrapped(title, "F2", 16, 62, 22f)
        addWrapped("$subtitle - ${template.displayName} - $dateStr", "F1", 10, 92, 18f)
        sections.forEach { sec ->
            addWrapped(sec.heading, "F2", 13, 74, 18f)
            val badge = listOf(sec.topicBadge, sec.verificationLabel).filter { it.isNotBlank() }.joinToString(" | ")
            if (badge.isNotBlank()) addWrapped(badge, "F2", 10, 92, 15f)
            if (sec.formulaText.isNotBlank()) addWrapped(sec.formulaText, "F2", 11, 82, 16f)
            if (sec.latexText.isNotBlank()) addWrapped("LaTeX : ${sec.latexText}", "F3", 9, 95, 14f)
            if (sec.bodyText.isNotBlank()) addWrapped(sec.bodyText, "F1", 10, 90, 14f)
            if (sec.tableRows.isNotEmpty()) {
                sec.tableRows.take(20).forEach { row ->
                    addWrapped(row.joinToString(" | "), "F3", 9, 94, 13f)
                }
            }
            if (sec.chartPlot != null) {
                addWrapped("[Graphique LaTeX : ${sec.chartPlot.title} (${sec.chartPlot.xValues.size} points)]", "F2", 9, 90, 14f)
            }
            lines.add(PdfLine("------------------------------------------------------------", "F1", 9, 14f))
        }

        val pagesContent = mutableListOf<String>()
        var stream = StringBuilder()
        var y = 785f
        for (line in lines) {
            if (y < 60f) {
                pagesContent.add(stream.toString())
                stream = StringBuilder()
                y = 785f
            }
            if (line.text.isNotEmpty()) {
                val safe = pdfEscapeAscii(line.text)
                stream.append("BT /${line.font} ${line.size} Tf 42 $y Td ($safe) Tj ET\n")
            }
            y -= line.gap
        }
        // Draw simple vector chart on last page if present
        val firstChart = sections.firstNotNullOfOrNull { it.chartPlot }
        if (firstChart != null && firstChart.xValues.size >= 2 && y > 160f) {
            val minX = firstChart.xValues.minOrNull() ?: 0.0
            val maxX = (firstChart.xValues.maxOrNull() ?: 1.0).let { if (it <= minX) minX + 1.0 else it }
            val minY = firstChart.yValues.minOrNull() ?: 0.0
            val maxY = (firstChart.yValues.maxOrNull() ?: 1.0).let { if (it <= minY) minY + 1.0 else it }
            val boxX = 48f
            val boxY = (y - 100f).coerceAtLeast(64f)
            val boxW = 320f
            val boxH = 85f
            stream.append("0.3 0.3 0.8 RG 1.2 w ")
            firstChart.xValues.indices.forEach { i ->
                val px = boxX + boxW * ((firstChart.xValues[i] - minX) / (maxX - minX)).toFloat()
                val py = boxY + boxH * ((firstChart.yValues[i] - minY) / (maxY - minY)).toFloat()
                stream.append(if (i == 0) "$px $py m " else "$px $py l ")
            }
            stream.append("S\n")
        }
        if (stream.isNotEmpty() || pagesContent.isEmpty()) pagesContent.add(stream.toString())

        val totalPages = pagesContent.size
        val finalizedPages = pagesContent.mapIndexed { idx, content ->
            val hdr = "BT /F1 8 Tf 42 818 Td (${pdfEscapeAscii("BRAV Chat - ${template.displayName}")}) Tj ET\n"
            val ftr = "BT /F1 8 Tf 42 26 Td (${pdfEscapeAscii("Page ${idx + 1}/$totalPages")}) Tj ET\n"
            hdr + content + ftr
        }

        val objects = mutableListOf<String>()
        objects.add("<< /Type /Catalog /Pages 2 0 R >>")
        val pageObjIds = finalizedPages.indices.map { 6 + it * 2 }
        objects.add("<< /Type /Pages /Kids [${pageObjIds.joinToString(" ") { "$it 0 R" }}] /Count ${pageObjIds.size} >>")
        objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>")
        objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>")
        objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Courier /Encoding /WinAnsiEncoding >>")

        finalizedPages.forEachIndexed { idx, contentStream ->
            val contentId = 6 + idx * 2 + 1
            val contentBytes = contentStream.toByteArray(Charsets.ISO_8859_1)
            objects.add(
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] " +
                    "/Resources << /Font << /F1 3 0 R /F2 4 0 R /F3 5 0 R >> >> /Contents $contentId 0 R >>"
            )
            objects.add("<< /Length ${contentBytes.size} >>\nstream\n$contentStream\nendstream")
        }

        val baos = ByteArrayOutputStream()
        fun writeAscii(s: String) = baos.write(s.toByteArray(Charsets.ISO_8859_1))
        writeAscii("%PDF-1.4\n")
        val offsets = mutableListOf<Int>()
        objects.forEachIndexed { idx, body ->
            offsets.add(baos.size())
            writeAscii("${idx + 1} 0 obj\n$body\nendobj\n")
        }
        val xrefPos = baos.size()
        writeAscii("xref\n0 ${objects.size + 1}\n")
        writeAscii("0000000000 65535 f \n")
        offsets.forEach { off ->
            writeAscii(String.format(Locale.US, "%010d 00000 n \n", off))
        }
        writeAscii("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$xrefPos\n%%EOF\n")
        FileOutputStream(outputFile).use { it.write(baos.toByteArray()) }
    }

    private fun pdfEscapeAscii(raw: String): String {
        val sb = StringBuilder(raw.length)
        for (ch in raw) {
            val mapped = when (ch) {
                '∫' -> "Int"
                'π' -> "pi"
                '∞' -> "inf"
                '⇒' -> "=>"
                '→' -> "->"
                '∈' -> "in"
                '⁻' -> "-"
                '¹' -> "1"
                '²' -> "2"
                '³' -> "3"
                '·' -> "*"
                '×' -> "x"
                '÷' -> "/"
                '−' -> "-"
                '•' -> "-"
                '—' -> "-"
                else -> ch.toString()
            }
            for (c in mapped) {
                when {
                    c == '\\' -> sb.append("\\\\")
                    c == '(' -> sb.append("\\(")
                    c == ')' -> sb.append("\\)")
                    c.code in 32..255 -> sb.append(c)
                    else -> sb.append('?')
                }
            }
        }
        return sb.toString()
    }

    fun exportToDocx(
        context: Context,
        title: String,
        subtitle: String,
        sections: List<ExportSection>,
        fileNamePrefix: String = "brav_document",
        template: DocTemplateType = DocTemplateType.RAPPORT
    ): ExportedDocumentResult {
        val outFile = createExportFile(context, fileNamePrefix, "docx")
        writeDocxToFile(outFile, title, subtitle, sections, template)
        return ExportedDocumentResult(outFile, MIME_DOCX, "Word (${template.displayName})")
    }

    fun writeDocxToFile(
        outputFile: File,
        title: String,
        subtitle: String,
        sections: List<ExportSection>,
        template: DocTemplateType = DocTemplateType.RAPPORT
    ) {
        outputFile.parentFile?.mkdirs()
        val bytes = buildDocxBytes(title, subtitle, sections, template)
        FileOutputStream(outputFile).use { it.write(bytes) }
    }

    fun buildDocxBytes(
        title: String,
        subtitle: String,
        sections: List<ExportSection>,
        template: DocTemplateType = DocTemplateType.RAPPORT
    ): ByteArray {
        val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(Date())

        // Pre-render embedded chart PNGs
        val chartEntries = mutableListOf<Triple<Int, String, ByteArray>>()
        var relSeq = 4 // rId1=styles, rId2=header1, rId3=footer1
        val sectionToChartRel = mutableMapOf<Int, Pair<String, Int>>()
        sections.forEachIndexed { secIdx, sec ->
            sec.chartPlot?.let { plot ->
                val pngBytes = renderChartPngBytes(plot, widthPx = 720, heightPx = 340)
                if (pngBytes.isNotEmpty()) {
                    val chartIndex = chartEntries.size + 1
                    val relId = "rId$relSeq"
                    relSeq++
                    val fileName = "media/chart_$chartIndex.png"
                    chartEntries.add(Triple(chartIndex, fileName, pngBytes))
                    sectionToChartRel[secIdx] = relId to chartIndex
                }
            }
        }

        val contentTypesXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Default Extension="png" ContentType="image/png"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
  <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
  <Override PartName="/word/header1.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.header+xml"/>
  <Override PartName="/word/footer1.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml"/>
</Types>"""

        val rootRelsXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

        val docRelsXml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
            append("""<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
            append("""<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/header" Target="header1.xml"/>""")
            append("""<Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/footer" Target="footer1.xml"/>""")
            sectionToChartRel.values.forEach { (relId, chartIndex) ->
                append("""<Relationship Id="$relId" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/chart_$chartIndex.png"/>""")
            }
            append("""</Relationships>""")
        }

        val headerXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:hdr xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:p><w:pPr><w:jc w:val="right"/></w:pPr><w:r><w:rPr><w:color w:val="64748B"/><w:sz w:val="18"/></w:rPr><w:t>${escapeXml("BRAV Chat • ${template.displayName} • $dateStr")}</w:t></w:r></w:p>
</w:hdr>"""

        val footerXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:ftr xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:rPr><w:color w:val="64748B"/><w:sz w:val="18"/></w:rPr><w:t>Document BRAV Chat • Page </w:t></w:r><w:fldSimple w:instr="PAGE"/></w:p>
</w:ftr>"""

        val stylesXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:style w:type="paragraph" w:default="1" w:styleId="Normal">
    <w:name w:val="Normal"/>
    <w:rPr><w:rFonts w:ascii="Calibri" w:hAnsi="Calibri"/><w:sz w:val="22"/></w:rPr>
  </w:style>
  <w:style w:type="paragraph" w:styleId="Title">
    <w:name w:val="Title"/>
    <w:basedOn w:val="Normal"/>
    <w:rPr><w:b/><w:color w:val="${template.accentHex}"/><w:sz w:val="36"/></w:rPr>
  </w:style>
  <w:style w:type="paragraph" w:styleId="Heading1">
    <w:name w:val="heading 1"/>
    <w:basedOn w:val="Normal"/>
    <w:rPr><w:b/><w:color w:val="${template.accentHex}"/><w:sz w:val="28"/></w:rPr>
  </w:style>
  <w:style w:type="paragraph" w:styleId="FormulaBlock">
    <w:name w:val="FormulaBlock"/>
    <w:basedOn w:val="Normal"/>
    <w:pPr><w:shd w:val="clear" w:color="auto" w:fill="F1F5F9"/></w:pPr>
    <w:rPr><w:rFonts w:ascii="Cambria Math" w:hAnsi="Cambria Math"/><w:b/><w:color w:val="0F172A"/><w:sz w:val="24"/></w:rPr>
  </w:style>
  <w:style w:type="paragraph" w:styleId="CodeBlock">
    <w:name w:val="CodeBlock"/>
    <w:basedOn w:val="Normal"/>
    <w:pPr><w:shd w:val="clear" w:color="auto" w:fill="F8FAFC"/></w:pPr>
    <w:rPr><w:rFonts w:ascii="Courier New" w:hAnsi="Courier New"/><w:color w:val="1E293B"/><w:sz w:val="19"/></w:rPr>
  </w:style>
</w:styles>"""

        val docXml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">""")
            append("<w:body>")
            append(docxParagraph(title, style = "Title"))
            append(docxParagraph("$subtitle — ${template.displayName} • Généré le $dateStr", italic = true, color = "5F6E80"))

            sections.forEachIndexed { secIdx, sec ->
                append(docxParagraph(sec.heading, style = "Heading1"))
                val badge = listOf(sec.topicBadge, sec.verificationLabel).filter { it.isNotBlank() }.joinToString(" • ")
                if (badge.isNotBlank()) {
                    append(docxParagraph(badge, bold = true, color = "0284C7"))
                }
                if (sec.formulaText.isNotBlank()) {
                    append(docxParagraph(sec.formulaText, style = "FormulaBlock"))
                }
                if (sec.latexText.isNotBlank()) {
                    append(docxParagraph("LaTeX : ${sec.latexText}", style = "CodeBlock"))
                }
                sec.bodyText.lines().forEach { line ->
                    append(docxParagraph(line))
                }
                if (sec.tableRows.isNotEmpty()) {
                    append(docxTable(sec.tableRows, template.accentHex))
                }
                sectionToChartRel[secIdx]?.let { (relId, chartIdx) ->
                    append(docxImageParagraph(relId, chartIdx, sec.chartPlot?.title ?: "Graphique LaTeX"))
                }
                if (sec.extraBlocks.isNotEmpty()) {
                    sec.extraBlocks.forEach { (label, code) ->
                        append(docxParagraph("$label : $code", style = "CodeBlock"))
                    }
                }
            }
            append("""<w:sectPr><w:headerReference w:type="default" r:id="rId2"/><w:footerReference w:type="default" r:id="rId3"/><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1134" w:right="1134" w:bottom="1134" w:left="1134"/></w:sectPr>""")
            append("</w:body></w:document>")
        }

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            putZipEntry(zos, "[Content_Types].xml", contentTypesXml)
            putZipEntry(zos, "_rels/.rels", rootRelsXml)
            putZipEntry(zos, "word/_rels/document.xml.rels", docRelsXml)
            putZipEntry(zos, "word/styles.xml", stylesXml)
            putZipEntry(zos, "word/header1.xml", headerXml)
            putZipEntry(zos, "word/footer1.xml", footerXml)
            chartEntries.forEach { (_, pathInWord, pngBytes) ->
                putZipBytes(zos, "word/$pathInWord", pngBytes)
            }
            putZipEntry(zos, "word/document.xml", docXml)
        }
        return baos.toByteArray()
    }

    private fun docxTable(rows: List<List<String>>, accentHex: String): String = buildString {
        append("""<w:tbl><w:tblPr><w:tblW w:w="9200" w:type="dxa"/><w:tblBorders><w:top w:val="single" w:sz="4" w:color="CBD5E1"/><w:left w:val="single" w:sz="4" w:color="CBD5E1"/><w:bottom w:val="single" w:sz="4" w:color="CBD5E1"/><w:right w:val="single" w:sz="4" w:color="CBD5E1"/><w:insideH w:val="single" w:sz="4" w:color="E2E8F0"/><w:insideV w:val="single" w:sz="4" w:color="E2E8F0"/></w:tblBorders></w:tblPr>""")
        rows.forEachIndexed { rIdx, row ->
            append("<w:tr>")
            row.forEach { cell ->
                val fill = if (rIdx == 0) accentHex else "FFFFFF"
                val color = if (rIdx == 0) "FFFFFF" else "1E293B"
                append("""<w:tc><w:tcPr><w:shd w:val="clear" w:color="auto" w:fill="$fill"/></w:tcPr>""")
                append(docxParagraph(cell, bold = (rIdx == 0), color = color))
                append("</w:tc>")
            }
            append("</w:tr>")
        }
        append("</w:tbl>")
    }

    private fun docxImageParagraph(relId: String, docPrId: Int, caption: String): String {
        val cx = 5000000L // ~13.9 cm
        val cy = 2360000L // ~6.5 cm
        val safeName = escapeXml(caption.take(40))
        return """
<w:p>
  <w:pPr><w:jc w:val="center"/><w:spacing w:before="120" w:after="120"/></w:pPr>
  <w:r>
    <w:drawing>
      <wp:inline distT="0" distB="0" distL="0" distR="0">
        <wp:extent cx="$cx" cy="$cy"/>
        <wp:docPr id="$docPrId" name="Chart$docPrId" descr="$safeName"/>
        <a:graphic>
          <a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">
            <pic:pic>
              <pic:nvPicPr>
                <pic:cNvPr id="$docPrId" name="chart_$docPrId.png"/>
                <pic:cNvPicPr/>
              </pic:nvPicPr>
              <pic:blipFill>
                <a:blip r:embed="$relId"/>
                <a:stretch><a:fillRect/></a:stretch>
              </pic:blipFill>
              <pic:spPr>
                <a:xfrm><a:off x="0" y="0"/><a:ext cx="$cx" cy="$cy"/></a:xfrm>
                <a:prstGeom prst="rect"><a:avLst/></a:prstGeom>
              </pic:spPr>
            </pic:pic>
          </a:graphicData>
        </a:graphic>
      </wp:inline>
    </w:drawing>
  </w:r>
</w:p>""".trimIndent()
    }

    fun renderChartPngBytes(series: PlotSeries, widthPx: Int = 720, heightPx: Int = 340): ByteArray {
        val androidPng = runCatching {
            val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawColor(Color.WHITE)

            val padL = 56f
            val padR = 24f
            val padT = 36f
            val padB = 34f
            val w = widthPx - padL - padR
            val h = heightPx - padT - padB

            val gridPaint = Paint().apply { color = Color.rgb(226, 232, 240); strokeWidth = 1f }
            val axisPaint = Paint().apply { color = Color.rgb(100, 116, 139); strokeWidth = 2f }
            val curvePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(79, 70, 229)
                style = Paint.Style.STROKE
                strokeWidth = 4f
            }
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(15, 23, 42)
                textSize = 20f
                typeface = Typeface.DEFAULT_BOLD
            }

            canvas.drawRect(padL, padT, padL + w, padT + h, gridPaint)
            canvas.drawText(series.title, padL, 24f, titlePaint)

            if (series.xValues.size >= 2 && series.xValues.size == series.yValues.size) {
                val minX = series.xValues.minOrNull() ?: 0.0
                val maxX = (series.xValues.maxOrNull() ?: 1.0).let { if (it <= minX) minX + 1.0 else it }
                val minY = series.yValues.minOrNull() ?: 0.0
                val maxY = (series.yValues.maxOrNull() ?: 1.0).let { if (it <= minY) minY + 1.0 else it }

                if (minY <= 0.0 && maxY >= 0.0) {
                    val y0 = padT + h * (1.0 - (0.0 - minY) / (maxY - minY)).toFloat()
                    canvas.drawLine(padL, y0, padL + w, y0, axisPaint)
                }
                val path = Path()
                series.xValues.indices.forEach { i ->
                    val px = padL + w * ((series.xValues[i] - minX) / (maxX - minX)).toFloat()
                    val py = padT + h * (1.0 - (series.yValues[i] - minY) / (maxY - minY)).toFloat()
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                canvas.drawPath(path, curvePaint)
            }
            val baos = ByteArrayOutputStream()
            val ok = bmp.compress(Bitmap.CompressFormat.PNG, 95, baos)
            bmp.recycle()
            val bytes = baos.toByteArray()
            if (ok && bytes.size > 64) bytes else null
        }.getOrNull()

        if (androidPng != null) return androidPng
        return buildPureJvmChartPng(series, width = 320, height = 160)
    }

    /** Pure-JVM RGBA PNG generator so Robolectric/unit tests produce a genuine valid PNG image with plotted curve. */
    private fun buildPureJvmChartPng(series: PlotSeries, width: Int, height: Int): ByteArray {
        val pixels = ByteArray(width * height * 4) { 255.toByte() } // White opaque background
        fun setPixel(x: Int, y: Int, r: Int, g: Int, b: Int) {
            if (x !in 0 until width || y !in 0 until height) return
            val idx = (y * width + x) * 4
            pixels[idx] = r.toByte()
            pixels[idx + 1] = g.toByte()
            pixels[idx + 2] = b.toByte()
            pixels[idx + 3] = 255.toByte()
        }
        fun drawLine(x0: Int, y0: Int, x1: Int, y1: Int, r: Int, g: Int, b: Int) {
            val steps = maxOf(abs(x1 - x0), abs(y1 - y0), 1)
            for (s in 0..steps) {
                val px = x0 + (x1 - x0) * s / steps
                val py = y0 + (y1 - y0) * s / steps
                setPixel(px, py, r, g, b)
                setPixel(px, py + 1, r, g, b)
            }
        }
        // Border
        drawLine(12, 12, width - 12, 12, 200, 210, 224)
        drawLine(12, height - 12, width - 12, height - 12, 200, 210, 224)
        if (series.xValues.size >= 2 && series.xValues.size == series.yValues.size) {
            val minX = series.xValues.minOrNull() ?: 0.0
            val maxX = (series.xValues.maxOrNull() ?: 1.0).let { if (it <= minX) minX + 1.0 else it }
            val minY = series.yValues.minOrNull() ?: 0.0
            val maxY = (series.yValues.maxOrNull() ?: 1.0).let { if (it <= minY) minY + 1.0 else it }
            val w = (width - 28).toDouble()
            val h = (height - 28).toDouble()
            for (i in 1 until series.xValues.size) {
                val x0 = (14 + w * (series.xValues[i - 1] - minX) / (maxX - minX)).toInt()
                val y0 = (14 + h * (1.0 - (series.yValues[i - 1] - minY) / (maxY - minY))).toInt()
                val x1 = (14 + w * (series.xValues[i] - minX) / (maxX - minX)).toInt()
                val y1 = (14 + h * (1.0 - (series.yValues[i] - minY) / (maxY - minY))).toInt()
                drawLine(x0, y0, x1, y1, 79, 70, 229)
            }
        }
        return encodeRgbaPng(width, height, pixels)
    }

    private fun encodeRgbaPng(width: Int, height: Int, rgba: ByteArray): ByteArray {
        val raw = ByteArrayOutputStream(height * (1 + width * 4))
        for (y in 0 until height) {
            raw.write(0) // filter type 0
            raw.write(rgba, y * width * 4, width * 4)
        }
        val deflater = Deflater()
        val rawBytes = raw.toByteArray()
        deflater.setInput(rawBytes)
        deflater.finish()
        val idat = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        while (!deflater.finished()) {
            val count = deflater.deflate(buf)
            idat.write(buf, 0, count)
        }
        deflater.end()

        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))
        fun writeChunk(type: String, data: ByteArray) {
            val typeBytes = type.toByteArray(Charsets.US_ASCII)
            writeIntBE(out, data.size)
            out.write(typeBytes)
            out.write(data)
            val crc = CRC32().apply { update(typeBytes); update(data) }.value
            writeIntBE(out, crc.toInt())
        }
        val ihdr = ByteArrayOutputStream(13).apply {
            writeIntBE(this, width)
            writeIntBE(this, height)
            write(8) // bit depth
            write(6) // color type RGBA
            write(0); write(0); write(0)
        }.toByteArray()
        writeChunk("IHDR", ihdr)
        writeChunk("IDAT", idat.toByteArray())
        writeChunk("IEND", ByteArray(0))
        return out.toByteArray()
    }

    private fun writeIntBE(out: ByteArrayOutputStream, v: Int) {
        out.write((v ushr 24) and 0xFF)
        out.write((v ushr 16) and 0xFF)
        out.write((v ushr 8) and 0xFF)
        out.write(v and 0xFF)
    }

    private fun docxParagraph(
        text: String,
        style: String? = null,
        bold: Boolean = false,
        italic: Boolean = false,
        color: String? = null
    ): String {
        val pPr = if (style != null) """<w:pPr><w:pStyle w:val="$style"/><w:spacing w:after="100"/></w:pPr>"""
        else """<w:pPr><w:spacing w:after="90"/></w:pPr>"""
        val rPr = buildString {
            if (bold || italic || color != null) {
                append("<w:rPr>")
                if (bold) append("<w:b/>")
                if (italic) append("<w:i/>")
                if (color != null) append("""<w:color w:val="$color"/>""")
                append("</w:rPr>")
            }
        }
        return """<w:p>$pPr<w:r>$rPr<w:t xml:space="preserve">${escapeXml(text)}</w:t></w:r></w:p>"""
    }

    private fun escapeXml(raw: String): String = buildString(raw.length) {
        for (ch in raw) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> if (ch == '\t' || ch == '\n' || ch == '\r' || ch.code >= 0x20) append(ch)
            }
        }
    }

    private fun putZipEntry(zos: ZipOutputStream, path: String, content: String) {
        zos.putNextEntry(ZipEntry(path))
        zos.write(content.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
    }

    private fun putZipBytes(zos: ZipOutputStream, path: String, bytes: ByteArray) {
        zos.putNextEntry(ZipEntry(path))
        zos.write(bytes)
        zos.closeEntry()
    }

    private fun createExportFile(context: Context, prefix: String, ext: String): File {
        val dir = File(context.filesDir, "exports").apply { mkdirs() }
        val safePrefix = prefix.replace(Regex("""[^A-Za-z0-9_-]"""), "_").take(40).ifBlank { "brav_doc" }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return File(dir, "${safePrefix}_$stamp.$ext")
    }

    fun getShareUri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file)

    fun createShareIntent(context: Context, exported: ExportedDocumentResult): Intent {
        val uri = getShareUri(context, exported.file)
        return Intent(Intent.ACTION_SEND).apply {
            type = exported.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, exported.file.nameWithoutExtension)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun createViewIntent(context: Context, exported: ExportedDocumentResult): Intent {
        val uri = getShareUri(context, exported.file)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, exported.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun copyFileToUri(context: Context, source: File, destUri: Uri): Result<Unit> = runCatching {
        context.contentResolver.openOutputStream(destUri)?.use { out ->
            source.inputStream().use { inp -> inp.copyTo(out) }
        } ?: error("Impossible d'ouvrir la destination")
    }
}
