package com.example.entimate.ui.reports

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.TextPaint
import com.example.entimate.data.local.AUTO_COLOR
import com.example.entimate.data.local.DocDocument
import com.example.entimate.data.local.DocElement
import com.example.entimate.data.local.DocPageBreak
import com.example.entimate.data.local.DocTab
import com.example.entimate.data.local.DocCell
import com.example.entimate.data.local.DocTableEmbed
import com.example.entimate.data.local.DocText
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

object DocumentRenderer {

    private class FamilyFaces(
        val regular: Typeface,
        val bold: Typeface,
        val italic: Typeface,
        val boldItalic: Typeface,
    )

    private const val MM_TO_TWIPS = 1440f / 25.4f
    private fun mmToTwips(mm: Float) = (mm * MM_TO_TWIPS).toInt()
    private const val MM_TO_PX = 72f / 25.4f

    private fun autoColor(argb: Int): Int = if (argb == AUTO_COLOR || argb == 0) AndroidColor.BLACK else argb
    private fun hasColor(argb: Int): Boolean = argb != AUTO_COLOR && argb != 0

    fun buildRtf(doc: DocDocument): ByteArray {
        val usedColors = LinkedHashMap<Int, Int>()
        fun colorIndex(argb: Int): Int {
            if (!hasColor(argb)) return 0
            return usedColors.getOrPut(argb) { usedColors.size + 1 }
        }
        val sb = StringBuilder()
        sb.append("{\\rtf1\\ansi\\ansicpg1251\\deff0\n")
        sb.append("{\\fonttbl{\\f0\\fnil Times New Roman;}}\n")
        sb.append("{\\colortbl;")
        usedColors.keys.forEach { argb ->
            sb.append("\\red${AndroidColor.red(argb)}\\green${AndroidColor.green(argb)}\\blue${AndroidColor.blue(argb)};")
        }
        sb.append("}\n")

        fun rtfEscape(s: String): String = s
            .replace("\\", "\\\\")
            .replace("{", "\\{")
            .replace("}", "\\}")

        var pendingTables: MutableList<DocTableEmbed> = mutableListOf()
        fun renderRtfTable(t: DocTableEmbed) {
            val titleFmt = buildString {
                if (t.bold) append("\\b")
                if (t.italic) append("\\i")
                if (t.underline) append("\\ul")
                append("\\fs${if (t.size > 0) t.size * 2 else 22}")
                if (hasColor(t.colorArgb)) append("\\cf${colorIndex(t.colorArgb)}")
                if (hasColor(t.bgArgb)) append("\\highlight${colorIndex(t.bgArgb)}")
            }
            if (t.title.isNotBlank()) sb.append("{\\$titleFmt ${rtfEscape(t.title)}}\\par\n")
            val cellFmt = buildString {
                if (t.bold) append("\\b")
                if (t.italic) append("\\i")
                if (t.underline) append("\\ul")
                append("\\fs${if (t.size > 0) t.size * 2 else 18}")
                if (hasColor(t.colorArgb)) append("\\cf${colorIndex(t.colorArgb)}")
                if (hasColor(t.bgArgb)) append("\\highlight${colorIndex(t.bgArgb)}")
            }
            val header = t.headers.joinToString(" | ") { "{$cellFmt ${rtfEscape(it.text)}}" }
            if (t.headers.isNotEmpty()) sb.append("{$header}\\par\n")
            t.rows.forEach { row ->
                sb.append("{${row.joinToString(" | ") { "{$cellFmt ${rtfEscape(it.text)}}" }}}\\par\n")
            }
        }
        doc.paragraphs.forEach { para ->
            val align = when (para.align) {
                "CENTER" -> "\\qc"
                "RIGHT" -> "\\qr"
                "JUSTIFY" -> "\\qj"
                else -> "\\ql"
            }
            val ppr = buildString {
                append(align)
                if (para.indentLeftMm != 0f) append("\\li${mmToTwips(para.indentLeftMm)}")
                if (para.indentRightMm != 0f) append("\\ri${mmToTwips(para.indentRightMm)}")
                if (para.firstLineMm != 0f) append("\\fi${mmToTwips(para.firstLineMm)}")
                append("\\sl${(para.lineSpacing * 240).toInt()}\\slmult1")
                if (para.spaceBeforeMm != 0f) append("\\sb${mmToTwips(para.spaceBeforeMm)}")
                if (para.spaceAfterMm != 0f) append("\\sa${mmToTwips(para.spaceAfterMm)}")
            }
            sb.append("\\pard$ppr\\f0\n")
            para.elements.forEach { el ->
                when (el) {
                    is DocText -> {
                        if (pendingTables.isNotEmpty()) { pendingTables.forEach { renderRtfTable(it) }; pendingTables.clear() }
                        val size = if (el.size > 0) el.size * 2 else 20
                        val fmt = buildString {
                            if (el.bold) append("\\b")
                            if (el.italic) append("\\i")
                            if (el.underline) append("\\ul")
                            append("\\fs$size")
                            if (hasColor(el.colorArgb)) append("\\cf${colorIndex(el.colorArgb)}")
                            if (hasColor(el.bgArgb)) append("\\highlight${colorIndex(el.bgArgb)}")
                        }
                        sb.append("{$fmt ${rtfEscape(el.text)}}")
                    }
                    is DocTab -> { if (pendingTables.isNotEmpty()) { pendingTables.forEach { renderRtfTable(it) }; pendingTables.clear() }; sb.append("\\tab") }
                    is DocPageBreak -> { if (pendingTables.isNotEmpty()) { pendingTables.forEach { renderRtfTable(it) }; pendingTables.clear() }; sb.append("\\page") }
                        is DocTableEmbed -> {
                            if (el.joinPrevious && pendingTables.isNotEmpty()) {
                                pendingTables.add(el)
                            } else {
                                pendingTables.forEach { renderRtfTable(it) }
                                pendingTables.clear()
                                pendingTables.add(el)
                            }
                        }
                }
            }
            sb.append("\\par\n")
        }
        pendingTables.forEach { renderRtfTable(it) }
        pendingTables.clear()
        sb.append("}")
        return sb.toString().toByteArray(charset("CP1251"))
    }

    fun buildDocx(doc: DocDocument): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun add(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            add("[Content_Types].xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
 <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
 <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
 <Default Extension="xml" ContentType="application/xml"/>
 <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
 <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
 </Types>""")
            add("_rels/.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
  <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
  </Relationships>""")
            add("word/_rels/document.xml.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
  <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
  </Relationships>""")
            add("word/styles.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
  <w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:docDefaults><w:rPrDefault><w:rPr><w:sz w:val="20"/></w:rPr></w:rPrDefault></w:docDefaults>
  <w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/><w:tblPr><w:tblLayout w:type="fixed"/></w:tblPr></w:style>
  <w:style w:type="table" w:styleId="TableGrid"><w:name w:val="TableGrid"/><w:tblPr><w:tblLayout w:type="fixed"/></w:tblPr></w:style>
  </w:styles>""")

            val body = StringBuilder()
            body.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            body.append("""<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>""")

            fun pPr(align: String, spaceBefore: Float, spaceAfter: Float, firstLine: Float = 0f, indentLeft: Float = 0f, indentRight: Float = 0f): String {
                val jc = when (align) {
                    "CENTER" -> "center"
                    "RIGHT" -> "right"
                    "JUSTIFY" -> "both"
                    else -> "left"
                }
                val li = mmToTwips(indentLeft)
                val ri = mmToTwips(indentRight)
                val fi = mmToTwips(firstLine)
                val ind = buildString {
                    if (firstLine != 0f) {
                        if (firstLine > 0) append(""" w:firstLine="$fi"""")
                        else append(""" w:hanging="${-fi}"""")
                    }
                    if (li != 0 || ri != 0) append(""" w:left="$li" w:right="$ri"""")
                }
                return """<w:pPr><w:spacing w:before="${mmToTwips(spaceBefore)}" w:after="${mmToTwips(spaceAfter)}" w:line="240" w:lineRule="auto"/>${if (ind.isNotBlank()) "<w:ind$ind/>" else ""}<w:jc w:val="$jc"/></w:pPr>"""
            }

            var pendingTables: MutableList<DocTableEmbed> = mutableListOf()
            val pendingSizes = mutableListOf<Float>()
            fun flushTables(suppressEmptyPara: Boolean = false) {
                if (pendingTables.isEmpty()) return
                val spec = mergedRowsAndGrid(pendingTables)
                if (!suppressEmptyPara) body.append("""<w:p/>""")
                body.append(buildDocxTable(spec.rows, spec.grid, spec.border, spec.align, (pendingSizes.maxOrNull() ?: 0f).toInt()))
                pendingTables.clear()
                pendingSizes.clear()
            }

            doc.paragraphs.forEach { para ->
                val font = para.font.ifBlank { "Times New Roman" }
                var open = false
                fun openPara() { if (!open) { body.append("<w:p>${pPr(para.align, para.spaceBeforeMm, para.spaceAfterMm, para.firstLineMm, para.indentLeftMm, para.indentRightMm)}"); open = true } }
                fun closePara() { if (open) { body.append("</w:p>"); open = false } }
                para.elements.forEach { el ->
                    when (el) {
                        is DocText -> {
                            if (pendingTables.isNotEmpty()) { closePara(); flushTables(suppressEmptyPara = true) }
                            openPara()
                            val size = if (el.size > 0) el.size * 2 else 20
                            val rpr = buildString {
                                append("""<w:rFonts w:ascii="$font" w:hAnsi="$font"/>""")
                                if (el.bold) append("<w:b/>")
                                if (el.italic) append("<w:i/>")
                                if (hasColor(el.colorArgb)) append("""<w:color w:val="${argbToHex(el.colorArgb)}"/>""")
                                append("""<w:sz w:val="$size"/>""")
                                if (el.underline) append("""<w:u w:val="single"/>""")
                                if (hasColor(el.bgArgb)) append("""<w:shd w:val="clear" w:color="auto" w:fill="${argbToHex(el.bgArgb)}"/>""")
                            }
                            body.append("""<w:r><w:rPr>$rpr</w:rPr><w:t xml:space="preserve">${escapeXml(el.text)}</w:t></w:r>""")
                        }
                        is DocTab -> { if (pendingTables.isNotEmpty()) { closePara(); flushTables(suppressEmptyPara = true) }; openPara(); body.append("""<w:r><w:tab/></w:r>""") }
                        is DocPageBreak -> { if (pendingTables.isNotEmpty()) { closePara(); flushTables(suppressEmptyPara = true) }; openPara(); body.append("""<w:r><w:br w:type="page"/></w:r>""") }
                        is DocTableEmbed -> {
                            if (el.joinPrevious && pendingTables.isNotEmpty()) {
                                closePara()
                                pendingTables.add(el); pendingSizes.add(el.size.toFloat())
                            } else {
                                closePara()
                                flushTables(suppressEmptyPara = true)
                                pendingTables.add(el); pendingSizes.add(el.size.toFloat())
                            }
                        }
                    }
                }
                closePara()
            }
            flushTables()
            val mt = mmToTwips(doc.marginTopMm)
            val mr = mmToTwips(doc.marginRightMm)
            val mb = mmToTwips(doc.marginBottomMm)
            val ml = mmToTwips(doc.marginLeftMm)
            body.append("""<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="$mt" w:right="$mr" w:bottom="$mb" w:left="$ml" w:header="708" w:footer="708" w:gutter="0"/></w:sectPr></w:body></w:document>""")
            add("word/document.xml", body.toString())
        }
        return out.toByteArray()
    }

    private data class MergedCell(
        val text: String,
        val colSpan: Int,
        val weight: Float,
        val align: String,
        val isFiller: Boolean,
    )

    private data class MergedRow(
        val cells: List<MergedCell>,
        val isHeader: Boolean,
        val seamStart: Boolean,
    )

    private data class TableSpec(
        val rows: List<MergedRow>,
        val grid: List<Int>,
        val colAligns: List<String>,
        val border: Int,
        val align: String,
        val title: String,
    )

    private fun mergedColsOf(t: DocTableEmbed): Int =
        (t.headers.map { it.colSpan.coerceAtLeast(1) }.sum()).coerceAtLeast(1)

    private fun isFiller(mc: MergedCell): Boolean = mc.isFiller && mc.text.isBlank()

    // Align a row's real cells left, by grid index, then pad the right with an
    // explicit borderless filler cell so every row fills the global grid without
    // introducing interior lines at the merged boundary.
    private fun normMerged(cells: List<DocCell>, totalCols: Int, ownCols: Int, colAligns: List<String>, tableAlign: String = "LEFT"): List<MergedCell> {
        val norm = normalizeCells(cells, ownCols)
        var col = 0
        val base = norm.map { c ->
            var align = c.align
            if (align.isBlank()) {
                val ca = colAligns.getOrNull(col)
                align = if (ca.isNullOrBlank()) (tableAlign.ifBlank { "LEFT" }) else ca
            }
            val span = c.colSpan.coerceAtLeast(1)
            val mc = MergedCell(c.text, span, c.weight, align, false)
            col += span
            mc
        }
        val used = base.sumOf { it.colSpan }
        val fill = totalCols - used
        return if (fill > 0) base + MergedCell("", fill, 0f, "", true) else base
    }

    private fun mergedRowsAndGrid(tables: List<DocTableEmbed>): TableSpec {
        val colCounts = tables.map { mergedColsOf(it) }
        val totalCols = colCounts.max()
        // Column widths must come from a table that actually spans every merged
        // column AND carries explicit per-column widths (e.g. an embedded report
        // table's colWeights or a multi-column manual table's cell weights).
        // Looking only at tables.first() (as before) dropped the report table's
        // widths whenever the group started with a 1-column manual header, making
        // every column equal width.
        var grid: List<Int> = emptyList()
        var uniformGrid: List<Int>? = null
        for (t in tables) {
            val own = mergedColsOf(t)
            // A table must span every merged column and carry one width per column.
            // Prefer non-uniform widths (real report colWeights / a genuine multi-column
            // manual table); single-column full-width manual section headers export a
            // uniform per-column width equal to (full width / cols) and must NOT win
            // over the meaningful colWeights of an embedded report table. But a table
            // whose ONLY width is uniform (e.g. a standalone manual table where the user
            // set every column equal) is still meaningful, so fall back to it when no
            // distinct-width table is present.
            if (own == totalCols && t.weights.size == totalCols && t.weights.any { it > 0f }) {
                if (t.weights.distinct().size > 1) {
                    grid = distributeWidths(t.weights, 9026, 1440f / 2.54f)
                    break
                } else if (uniformGrid == null) {
                    uniformGrid = distributeWidths(t.weights, 9026, 1440f / 2.54f)
                }
            }
        }
        if (grid.isEmpty()) {
            if (uniformGrid != null) {
                grid = uniformGrid
            } else {
                val each = (9026f / totalCols).roundToInt().coerceAtLeast(1)
                grid = List(totalCols) { each }
            }
        }
        val f = tables.first()
        // If ANY table in a merged group explicitly requested a border (0 = no
        // border), honor the strongest request. A manually-created table that is
        // joined to a borderless table above it must not have its border dropped.
        val border = tables.map { it.border }.filter { it != 0 }.maxOrNull() ?: f.border
        val rows = mutableListOf<MergedRow>()
        tables.forEachIndexed { ti, t ->
            val own = mergedColsOf(t)
            val tColAligns = t.colAligns
            // Manual tables carry no per-column alignments, so their cells (and
            // especially their full-width section headers) must fall back to the
            // table's own align (e.g. CENTER), not to a forced LEFT.
            val tableAlign = t.align.ifBlank { "LEFT" }

            fun buildRow(cells: List<DocCell>, isHeader: Boolean): MergedRow {
                val single = normalizeCells(cells, own)
                val spansAll = own == totalCols && single.size == 1 && single.first().colSpan.coerceAtLeast(1) >= own
                if (spansAll && tColAligns.isEmpty()) {
                    val c = single.first()
                    val a = c.align.ifBlank { tableAlign }
                    return MergedRow(listOf(MergedCell(c.text, totalCols, c.weight, a, false)), isHeader, false)
                }
                return MergedRow(normMerged(cells, totalCols, own, tColAligns, tableAlign), isHeader, false)
            }
            // Every row gets its own top border (seamStart=false): a joined group of
            // tables renders as ONE continuous bordered table, so the first row draws
            // the table's outer top and every following row draws a horizontal row
            // separator. Suppressing any row's top here (the old seam mechanism) left
            // the table with missing horizontal outline strokes.
            rows.add(buildRow(t.headers, true))
            t.rows.forEach { r -> rows.add(buildRow(r, false)) }
        }
        return TableSpec(rows, grid, f.colAligns, border, f.align, f.title)
    }

    private fun buildDocxTable(rows: List<MergedRow>, colW: List<Int>, border: Int, tableAlign: String, size: Int = 0): String {
        val totalCols = colW.size
        val tableW = colW.sum()
        val lastRow = rows.size - 1

        fun alignWord(a: String): String = when (a) {
            "CENTER" -> "center"
            "RIGHT" -> "right"
            "JUSTIFY" -> "both"
            else -> "left"
        }
        fun widthFrom(col: Int, span: Int): Int {
            var w = 0
            for (i in col until col + span) w += colW.getOrElse(i) { 0 }
            return w
        }

        val sb = StringBuilder()
        sb.append("""<w:tbl><w:tblPr><w:tblW w:w="$tableW" w:type="dxa"/><w:jc w:val="${alignWord(tableAlign)}"/><w:tblLayout w:type="fixed"/></w:tblPr><w:tblGrid>""")
        colW.forEach { sb.append("""<w:gridCol w:w="$it"/>""") }
        sb.append("</w:tblGrid>")

        rows.forEachIndexed { ri, row ->
            sb.append("<w:tr>")
            var col = 0
            for (mc in row.cells) {
                val span = mc.colSpan.coerceAtLeast(1)
                val w = widthFrom(col, span)
                val isLastCell = (col + span) >= totalCols

                val top: String
                val bottom: String
                val left: String
                val right: String
                if (isFiller(mc)) {
                    top = "none"; bottom = "none"; left = "none"; right = "none"
                } else when (border) {
                    0 -> { top = "none"; bottom = "none"; left = "none"; right = "none" }
                    3 -> {
                        top = if (row.seamStart) "none" else "single"
                        bottom = if (ri == lastRow) "single" else "none"
                        left = "none"
                        right = "none"
                    }
                    2 -> {
                        top = if (ri == 0) "single" else "none"
                        bottom = if (ri == lastRow) "single" else "none"
                        left = if (col == 0) "single" else "none"
                        right = if (isLastCell) "single" else "none"
                    }
                    else -> {
                        top = if (row.seamStart) "none" else "single"
                        bottom = if (ri == lastRow) "single" else "none"
                        left = "single"
                        right = if (isLastCell) "single" else "none"
                    }
                }
                val tcBorders = buildString {
                    append("""<w:tcBorders><w:top w:val="$top" w:sz="4" w:space="0" w:color="auto"/>""")
                    append("""<w:left w:val="$left" w:sz="4" w:space="0" w:color="auto"/>""")
                    append("""<w:bottom w:val="$bottom" w:sz="4" w:space="0" w:color="auto"/>""")
                    append("""<w:right w:val="$right" w:sz="4" w:space="0" w:color="auto"/></w:tcBorders>""")
                }

                val effAlign = if (mc.align.isNotBlank()) mc.align else if (row.isHeader) "CENTER" else "LEFT"
                val jc = alignWord(effAlign)
                val grid = if (span > 1) """<w:gridSpan w:val="$span"/>""" else ""
                val sz = if (size > 0) size * 2 else if (row.isHeader) 20 else 18
                val fontSizePt = if (size > 0) size.toFloat() else if (row.isHeader) 10f else 9f
                val rpr = buildString {
                    append("""<w:rFonts w:ascii="Times New Roman" w:hAnsi="Times New Roman" w:cs="Times New Roman"/>""")
                    if (row.isHeader) append("<w:b/>")
                    append("""<w:sz w:val="$sz"/>""")
                }
                val runInner = wrapDocxRun(mc.text, w, fontSizePt)
                sb.append("""<w:tc><w:tcPr><w:tcW w:w="$w" w:type="dxa"/>$grid$tcBorders</w:tcPr><w:p><w:pPr><w:spacing w:before="0" w:after="0" w:line="240" w:lineRule="auto"/><w:jc w:val="$jc"/></w:pPr><w:r><w:rPr>$rpr</w:rPr>$runInner</w:r></w:p></w:tc>""")
                col += span
            }
            sb.append("</w:tr>")
        }
        sb.append("</w:tbl>")
        return sb.toString()
    }

    private fun distributeWidths(cmValues: List<Float>, maxW: Int, cmToUnit: Float): List<Int> {
        val n = cmValues.size
        if (n == 0) return emptyList()
        val total = cmValues.filter { it > 0f }.sum()
        if (total <= 0f) return List(n) { (maxW / n).coerceAtLeast(1) }
        // Preserve the user's absolute widths (cm -> twips). PDF scales the grid to
        // fill the page, DOCX uses these twips directly, so both honor the settings.
        return cmValues.map { if (it > 0f) (it * cmToUnit).roundToInt().coerceAtLeast(1) else 0 }
    }

    private fun normalizeCells(cells: List<DocCell>, totalCols: Int): List<DocCell> {
        var list = cells
        var sum = list.sumOf { it.colSpan.coerceAtLeast(1) }
        while (sum < totalCols) { list = list + DocCell(""); sum += 1 }
        while (sum > totalCols && list.isNotEmpty()) {
            val last = list.last()
            val s = last.colSpan.coerceAtLeast(1)
            list = if (s > 1) list.dropLast(1) + DocCell(last.text, s - 1) else list.dropLast(1)
            sum -= 1
        }
        return list
    }

    fun buildPdf(context: Context, doc: DocDocument): ByteArray {
        val pdf = PdfDocument()
        val pageW = 595
        val pageH = 842
        val topM = doc.marginTopMm * MM_TO_PX
        val bottomM = doc.marginBottomMm * MM_TO_PX
        val left = doc.marginLeftMm * MM_TO_PX
        val right = pageW - doc.marginRightMm * MM_TO_PX
        val tabW = 12.5f * MM_TO_PX

        // Bundled metric-compatible fonts so the app's PDF lays out exactly like
        // Word renders the same DOCX (wrapping, character widths, line heights).
        //   Tinos     ~ Times New Roman  (serif / default)
        //   Arimo     ~ Arial
        //   Carlito   ~ Calibri
        //   Cousine   ~ Courier New
        // Falls back to the platform family if any asset is missing.
        fun loadFamily(prefix: String, fallback: Typeface): FamilyFaces {
            fun asset(name: String, fb: Typeface): Typeface = try {
                Typeface.createFromAsset(context.assets, name)
            } catch (_: Throwable) {
                fb
            }
            return FamilyFaces(
                regular = asset("${prefix}Regular.ttf", fallback),
                bold = asset("${prefix}Bold.ttf", Typeface.create(fallback, Typeface.BOLD)),
                italic = asset("${prefix}Italic.ttf", Typeface.create(fallback, Typeface.ITALIC)),
                boldItalic = asset("${prefix}BoldItalic.ttf", Typeface.create(fallback, Typeface.BOLD_ITALIC)),
            )
        }
        val serif = loadFamily("fonts/Tinos-", Typeface.SERIF)
        val sans = loadFamily("fonts/Arimo-", Typeface.SANS_SERIF)
        val calibri = loadFamily("fonts/Carlito-", sans.regular)
        val mono = loadFamily("fonts/Cousine-", Typeface.MONOSPACE)

        val basePaint = TextPaint().apply {
            typeface = serif.regular
            textSize = 10f
            color = AndroidColor.BLACK
        }
        val space = basePaint.measureText(" ")

        var page = pdf.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, 1).create())
        var canvas: Canvas = page.canvas
        var y = topM

        fun newPage() {
            pdf.finishPage(page)
            page = pdf.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pdf.pages.size + 1).create())
            canvas = page.canvas
            y = topM
        }
        fun ensure(need: Float) {
            if (y + need > pageH - bottomM) newPage()
        }

        fun typeface(font: String, bold: Boolean, italic: Boolean): Typeface {
            val fam = when {
                font.contains("courier", true) -> mono
                font.contains("calibri", true) -> calibri
                font.contains("arial", true) -> sans
                else -> serif
            }
            return when {
                bold && italic -> fam.boldItalic
                bold -> fam.bold
                italic -> fam.italic
                else -> fam.regular
            }
        }

        fun nextTabStop(x: Float, base: Float): Float {
            var stop = base + tabW
            while (stop <= x) stop += tabW
            return stop
        }

        fun drawTable(spec: TableSpec, font: String, size: Float) {
            val rows = spec.rows
            val gridTwips = spec.grid
            val totalCols = gridTwips.size
            val tableW = (right - left)
            val originX = when (spec.align) {
                "CENTER" -> left + (right - left - tableW) / 2f
                "RIGHT" -> right - tableW
                else -> left
            }
            val ts = if (size > 0f) size else 9f
            val k = ts / 9f
            val paint = TextPaint(basePaint).apply { textSize = ts }
            // Table-header rows are bold at 10pt, matching the DOCX table writer
            // (<w:b/> + sz 20 -> 10pt) so Word and the app PDF look identical.
            val headerPaint = TextPaint(basePaint).apply {
                typeface = serif.bold
                textSize = if (size > 0f) ts else 10f
            }
            if (spec.title.isNotBlank()) {
                ensure(20f * k)
                val tw = paint.measureText(spec.title)
                val tx = when (spec.align) {
                    "CENTER" -> left + (right - left - tw) / 2f
                    "RIGHT" -> right - tw
                    else -> left
                }
                canvas.drawText(spec.title, tx, y, paint)
                y += 18f * k
            }
            val scale = tableW / gridTwips.sum().coerceAtLeast(1).toFloat()
            val colW = gridTwips.map { it.toFloat() * scale }
            val tableRight = originX + colW.sum()
            val lastRow = rows.size - 1
            val border = spec.border
            rows.forEachIndexed { ri, row ->
                val rowPaint = if (row.isHeader) headerPaint else paint
                val rts = rowPaint.textSize
                val rk = rts / 9f
                val cellLayouts = mutableListOf<CellLayout>()
                var col = 0
                for (mc in row.cells) {
                    val span = mc.colSpan.coerceAtLeast(1)
                    val w = (0 until span).fold(0f) { acc, i -> acc + colW.getOrElse(col + i) { 0f } }
                    val cellLeft = originX + (0 until col).fold(0f) { acc, i -> acc + colW.getOrElse(i) { 0f } }
                    val a = if (mc.align.isNotBlank()) mc.align else if (row.isHeader) "CENTER" else "LEFT"
                    val lines = if (isFiller(mc)) emptyList() else wrapPdfLines(mc.text, (w - 4f).coerceAtLeast(1f), rowPaint)
                    cellLayouts.add(CellLayout(cellLeft, w, a, isFiller(mc), lines))
                    col += span
                }
                val maxLines = (cellLayouts.maxOfOrNull { it.lines.size } ?: 0).coerceAtLeast(1)
                // Word renders a single-lined cell ~12pt tall (single line spacing);
                // each extra wrapped line adds one more line height.
                val lineH = 12f * rk
                val rowH = lineH * maxLines
                ensure(rowH)
                val bTop = y
                val bBot = y + rowH
                for (cl in cellLayouts) {
                    if (cl.filler) continue
                    cl.lines.forEachIndexed { li, line ->
                        val tw = rowPaint.measureText(line)
                        val cx = when (cl.align) {
                            "RIGHT" -> cl.left + cl.w - tw - 2f
                            "CENTER" -> cl.left + (cl.w - tw) / 2f
                            else -> cl.left + 2f
                        }
                        canvas.drawText(line, cx, bTop + lineH * 0.68f + li * lineH, rowPaint)
                    }
                }
                if (border != 0) {
                    when (border) {
                        2 -> {
                            if (ri == 0) canvas.drawLine(originX, bTop, tableRight, bTop, paint)
                            if (ri == lastRow) canvas.drawLine(originX, bBot, tableRight, bBot, paint)
                            canvas.drawLine(originX, bTop, originX, bBot, paint)
                            canvas.drawLine(tableRight, bTop, tableRight, bBot, paint)
                        }
                        3 -> {
                            if (!row.seamStart) canvas.drawLine(originX, bTop, tableRight, bTop, paint)
                            if (ri == lastRow) canvas.drawLine(originX, bBot, tableRight, bBot, paint)
                        }
                        else -> {
                            if (!row.seamStart) canvas.drawLine(originX, bTop, tableRight, bTop, paint)
                            if (ri == lastRow) canvas.drawLine(originX, bBot, tableRight, bBot, paint)
                            val bxs = (cellLayouts.flatMap { if (!it.filler) listOf(it.left, it.left + it.w) else emptyList() } + originX + tableRight).distinct().sorted()
                            for (bx in bxs) {
                                canvas.drawLine(bx, bTop, bx, bBot, paint)
                            }
                        }
                    }
                }
                y += rowH
            }
        }

        val tablePending = mutableListOf<DocTableEmbed>()
        val tablePendingFonts = mutableListOf<String>()
        val tablePendingSizes = mutableListOf<Float>()
        fun flushTableGroup() {
            if (tablePending.isEmpty()) return
            val spec = mergedRowsAndGrid(tablePending)
            // Use the largest configured font size among the joined tables so a
            // table set to a larger size is never clipped and rows are tall enough.
            val size = tablePendingSizes.maxOrNull() ?: 0f
            drawTable(spec, tablePendingFonts.firstOrNull() ?: "Times New Roman", size)
            tablePending.clear()
            tablePendingFonts.clear()
            tablePendingSizes.clear()
            y += 6f
        }

        doc.paragraphs.forEach { para ->
            val font = para.font.ifBlank { "Times New Roman" }
            val align = para.align
            val leftEdge = left + para.indentLeftMm * MM_TO_PX
            val rightEdge = right - para.indentRightMm * MM_TO_PX
            val firstPx = para.firstLineMm * MM_TO_PX
            val spaceBeforePx = para.spaceBeforeMm * MM_TO_PX
            val spaceAfterPx = para.spaceAfterMm * MM_TO_PX

            if (spaceBeforePx > 0f && tablePending.isEmpty()) { ensure(spaceBeforePx); y += spaceBeforePx }

            val spans = mutableListOf<SpanWord>()

            fun flushParagraph() {
                if (spans.isEmpty()) return
                var line = mutableListOf<SpanWord>()
                var lineW = 0f
                var firstLine = true
                fun flush() {
                    if (line.isEmpty()) return
                    val lineHeight = 12f
                    ensure(lineHeight)
                    val total = line.fold(0f) { acc, w -> acc + (if (w.tab) tabW else w.paint.measureText(w.text) + space) } - space
                    val indent = if (firstLine) firstPx else 0f
                    val lineLeft = leftEdge + indent
                    val lineUsable = rightEdge - lineLeft
                    val startX = when (align) {
                        "CENTER" -> lineLeft + (lineUsable - total) / 2f
                        "RIGHT" -> rightEdge - total
                        else -> lineLeft
                    }
                    var x = startX
                    val bgPaint = Paint()
                    line.forEach { w ->
                        if (w.tab) {
                            x = nextTabStop(x, leftEdge)
                            return@forEach
                        }
                        if (w.bg != 0) {
                            bgPaint.color = w.bg
                            canvas.drawRect(x, y - 11f, x + w.paint.measureText(w.text), y + 4f, bgPaint)
                        }
                        canvas.drawText(w.text, x, y, w.paint)
                        if (w.underline) canvas.drawLine(x, y + 2f, x + w.paint.measureText(w.text), y + 2f, w.paint)
                        x += w.paint.measureText(w.text) + space
                    }
                    y += lineHeight
                    firstLine = false
                }
                spans.forEach { w ->
                    if (w.tab) { line.add(w); lineW += tabW; return@forEach }
                    val ww = w.paint.measureText(w.text) + space
                    if (line.isNotEmpty() && (lineW + ww) > (rightEdge - (if (firstLine) leftEdge + firstPx else leftEdge))) {
                        flush()
                        line = mutableListOf()
                        lineW = 0f
                    }
                    line.add(w)
                    lineW += ww
                }
                flush()
            }

            var endedWithTable = false
            para.elements.forEach { el ->
                when (el) {
                    is DocText -> {
                        if (tablePending.isNotEmpty()) { flushTableGroup() }
                        endedWithTable = false
                        val paint = TextPaint(basePaint).apply {
                            typeface = typeface(font, el.bold, el.italic)
                            textSize = if (el.size > 0) el.size.toFloat() else 10f
                            color = autoColor(el.colorArgb)
                        }
                        el.text.split(" ").filter { it.isNotEmpty() }.forEach {
                            spans.add(SpanWord(it, paint, el.underline, if (hasColor(el.bgArgb)) el.bgArgb else 0))
                        }
                    }
                    is DocTab -> {
                        if (tablePending.isNotEmpty()) { flushTableGroup() }
                        endedWithTable = false; spans.add(SpanWord("", basePaint, false, 0, tab = true)) }
                    is DocPageBreak -> {
                        // Explicit page breaks are ignored in the PDF export so the
                        // content flows continuously (auto-pagination via ensure() only
                        // kicks in once the page is actually full).
                        if (tablePending.isNotEmpty()) { flushTableGroup() }
                        endedWithTable = false
                    }
                    is DocTableEmbed -> {
                        endedWithTable = true
                        flushParagraph(); spans.clear()
                        if (el.joinPrevious && tablePending.isNotEmpty()) {
                            tablePending.add(el); tablePendingFonts.add(font); tablePendingSizes.add(el.size.toFloat())
                        } else {
                            flushTableGroup()
                            tablePending.add(el); tablePendingFonts.add(font); tablePendingSizes.add(el.size.toFloat())
                        }
                    }
                }
            }
            flushParagraph()
            spans.clear()
            if (spaceAfterPx > 0f && !endedWithTable) { y += spaceAfterPx }
        }

        flushTableGroup()
        pdf.finishPage(page)
        val stream = ByteArrayOutputStream()
        pdf.writeTo(stream)
        pdf.close()
        return stream.toByteArray()
    }

    private data class SpanWord(val text: String, val paint: Paint, val underline: Boolean, val bg: Int, val tab: Boolean = false)

    private fun argbToHex(argb: Int): String {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return "%02X%02X%02X".format(r, g, b)
    }

    private data class CellLayout(
        val left: Float,
        val w: Float,
        val align: String,
        val filler: Boolean,
        val lines: List<String>,
    )

    private fun wrapDocxRun(text: String, widthTwips: Int, fontSizePt: Float): String {
        if (text.isEmpty()) return """<w:t xml:space="preserve"></w:t>"""
        // Pure-Kotlin width estimate (no Android Paint, so it runs on the JVM in
        // unit tests). Word re-wraps the text natively inside the fixed-width cell
        // anyway, so an approximate per-character width is enough to trigger
        // line breaks and grow the row height when a value is wider than its column.
        val charW = (fontSizePt * 10f).coerceAtLeast(1f)
        val maxW = widthTwips.toFloat().coerceAtLeast(1f)
        val sb = StringBuilder()
        val lineBuf = StringBuilder()
        var lineW = 0f
        fun flushLine(withBreak: Boolean) {
            if (lineBuf.isNotEmpty()) {
                sb.append("""<w:t xml:space="preserve">${escapeXml(lineBuf.toString().trimEnd())}</w:t>""")
                lineBuf.setLength(0)
            }
            if (withBreak) sb.append("<w:br/>")
            lineW = 0f
        }
        for (raw in text.split(" ")) {
            val wordW = raw.length * charW
            if (lineBuf.isNotEmpty() && lineW + charW + wordW > maxW) flushLine(withBreak = true)
            var rem = raw
            while (rem.isNotEmpty()) {
                val remW = rem.length * charW
                if (remW <= maxW - lineW || rem.length == 1) {
                    lineBuf.append(rem)
                    lineW += remW
                    rem = ""
                } else {
                    val cut = minOf(rem.length, maxOf(1, ((maxW - lineW) / charW).toInt()))
                    lineBuf.append(rem.substring(0, cut))
                    flushLine(withBreak = true)
                    rem = rem.substring(cut)
                }
            }
            lineBuf.append(" ")
            lineW += charW
        }
        flushLine(withBreak = false)
        return sb.toString()
    }

    private fun wrapPdfLines(text: String, maxW: Float, paint: TextPaint): List<String> {
        if (text.isEmpty()) return emptyList()
        val lines = mutableListOf<String>()
        val spaceW = paint.measureText(" ")
        var line = StringBuilder()
        var lineW = 0f
        fun flush() { if (line.isNotEmpty()) { lines.add(line.toString().trimEnd()); line = StringBuilder(); lineW = 0f } }
        for (word in text.split(" ")) {
            val wordW = paint.measureText(word)
            if (line.isNotEmpty() && lineW + spaceW + wordW > maxW) flush()
            var rem = word
            while (rem.isNotEmpty()) {
                val w = paint.measureText(rem)
                if (w <= maxW - lineW || rem.length == 1) {
                    line.append(rem); lineW += w; rem = ""
                } else {
                    var cut = rem.length
                    while (cut > 1 && paint.measureText(rem.substring(0, cut)) > maxW - lineW) cut--
                    line.append(rem.substring(0, cut))
                    flush()
                    rem = rem.substring(cut)
                }
            }
            line.append(" ")
            lineW += spaceW
        }
        flush()
        return lines
    }

    private fun escapeXml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
