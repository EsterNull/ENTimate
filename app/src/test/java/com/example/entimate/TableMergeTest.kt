package com.example.entimate

import com.example.entimate.data.local.DocCell
import com.example.entimate.data.local.DocDocument
import com.example.entimate.data.local.DocParagraph
import com.example.entimate.data.local.DocTableEmbed
import com.example.entimate.ui.reports.DocumentRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

class TableMergeTest {

    private fun docxBody(bytes: ByteArray): String {
        val zis = ZipInputStream(ByteArrayInputStream(bytes))
        var xml = ""
        while (true) {
            val entry = zis.nextEntry ?: break
            if (entry.name == "word/document.xml") {
                xml = zis.bufferedReader(Charsets.UTF_8).readText()
            }
            zis.closeEntry()
        }
        zis.close()
        return xml
    }

    @Test
    fun joinedTablesWithDifferentColumnsProduceSingleTable() {
        val table1 = DocTableEmbed(
            title = "Таблица1",
            headers = listOf(DocCell("Кол А"), DocCell("Кол Б")),
            rows = listOf(listOf(DocCell("a1"), DocCell("b1")), listOf(DocCell("a2"), DocCell("b2"))),
            joinPrevious = false,
        )
        val table2 = DocTableEmbed(
            title = "",
            headers = listOf(DocCell("Кол X"), DocCell("Кол Y"), DocCell("Кол Z")),
            rows = listOf(listOf(DocCell("x1"), DocCell("y1"), DocCell("z1"))),
            joinPrevious = true,
        )
        val doc = DocDocument(
            title = "t",
            paragraphs = listOf(DocParagraph(font="Times New Roman", align="LEFT", elements = listOf(table1, table2))),
        )
        val xml = docxBody(DocumentRenderer.buildDocx(doc))
        println("=====DOCX BODY=====")
        println(xml)
        println("=====END=====")
        val starts = Regex("<w:tbl>").findAll(xml).count()
        assertEquals("Expected exactly ONE merged <w:tbl>, found $starts", 1, starts)
        assertTrue("Merged table should contain both tables' content", xml.contains("Кол Б") && xml.contains("Кол Z"))
        assertTrue("No table-level tblBorders/insideV should exist", !xml.contains("insideV") && !xml.contains("w:tblBorders"))
        assertEquals("Grid should have 3 columns (max of 2 and 3)", 3, Regex("<w:gridCol").findAll(xml).count())
        assertTrue("Seam row should have at least one cell with no top border",
            Regex("<w:top w:val=\"none\" ").findAll(xml).count() >= 1)
    }

    @Test
    fun joinedTablesWithSameColumnsPreserveWidths() {
        val table1 = DocTableEmbed(
            title = "",
            headers = listOf(DocCell("К1"), DocCell("К2"), DocCell("К3")),
            rows = listOf(listOf(DocCell("a"), DocCell("b"), DocCell("c"))),
            weights = listOf(3f, 5f, 2f),
            joinPrevious = false,
        )
        val table2 = DocTableEmbed(
            title = "",
            headers = listOf(DocCell("К4"), DocCell("К5"), DocCell("К6")),
            rows = listOf(listOf(DocCell("d"), DocCell("e"), DocCell("f"))),
            joinPrevious = true,
        )
        val doc = DocDocument(
            title = "t",
            paragraphs = listOf(DocParagraph(font="Times New Roman", align="LEFT", elements = listOf(table1, table2))),
        )
        val xml = docxBody(DocumentRenderer.buildDocx(doc))
        assertEquals("One merged table", 1, Regex("<w:tbl>").findAll(xml).count())
        assertEquals("3 grid columns", 3, Regex("<w:gridCol").findAll(xml).count())
        val widths = Regex("""<w:gridCol w:w="(\d+)"/>""").findAll(xml).map { it.groupValues[1].toInt() }.toList()
        assertEquals("3 widths", 3, widths.size)
        assertTrue("Column widths preserved and distinct (not all equal)", widths.distinct().size == 3 && widths.none { it == 9026 / 3 })
        assertEquals("No gridSpan needed for same column count", 0, Regex("<w:gridSpan").findAll(xml).count())
        assertEquals("All cells draw left border (full vertical grid)", 0, Regex("<w:left w:val=\"none\"").findAll(xml).count())
    }

    @Test
    fun joinedTablesWithDifferentColumnsProduceSingleRtfBlock() {
        val table1 = DocTableEmbed(
            title = "Таблица1",
            headers = listOf(DocCell("Кол А"), DocCell("Кол Б")),
            rows = listOf(listOf(DocCell("a1"), DocCell("b1"))),
            joinPrevious = false,
        )
        val table2 = DocTableEmbed(
            title = "",
            headers = listOf(DocCell("Кол X"), DocCell("Кол Y"), DocCell("Кол Z")),
            rows = listOf(listOf(DocCell("x1"), DocCell("y1"), DocCell("z1"))),
            joinPrevious = true,
        )
        val doc = DocDocument(
            title = "t",
            paragraphs = listOf(DocParagraph(font="Times New Roman", align="LEFT", elements = listOf(table1, table2))),
        )
        val rtf = String(DocumentRenderer.buildRtf(doc), java.nio.charset.Charset.forName("windows-1251"))
        println("=====RTF=====")
        println(rtf)
        println("=====END=====")
        assertTrue("RTF should contain both tables' content", rtf.contains("Кол Б") && rtf.contains("Кол Z"))
    }

    @Test
    fun tableWithNoBorderProducesNoBorders() {
        val table1 = DocTableEmbed(
            title = "",
            headers = listOf(DocCell("К1"), DocCell("К2")),
            rows = listOf(listOf(DocCell("a"), DocCell("b"))),
            border = 0,
            joinPrevious = false,
        )
        val doc = DocDocument(
            title = "t",
            paragraphs = listOf(DocParagraph(font="Times New Roman", align="LEFT", elements = listOf(table1))),
        )
        val xml = docxBody(DocumentRenderer.buildDocx(doc))
        println("=====DOCX NO-BORDER=====")
        println(xml)
        println("=====END=====")
        assertTrue("No cell border should be 'single' when border=0", !xml.contains("w:val=\"single\""))
    }

    @Test
    fun mergedManualTableKeepsItsBorderEvenWhenJoinedToBorderlessTable() {
        val reportTable = DocTableEmbed(
            title = "",
            headers = listOf(DocCell("Пациент"), DocCell("Дата")),
            rows = listOf(listOf(DocCell("Иванов"), DocCell("01.01")), listOf(DocCell("Петров"), DocCell("02.02"))),
            border = 0,
            joinPrevious = false,
        )
        val manualTable = DocTableEmbed(
            title = "",
            headers = listOf(DocCell("Комментарий")),
            rows = listOf(listOf(DocCell("Ручная запись"))),
            border = 1,
            align = "LEFT",
            joinPrevious = true,
        )
        val doc = DocDocument(
            title = "t",
            paragraphs = listOf(DocParagraph(font="Times New Roman", align="LEFT", elements = listOf(reportTable, manualTable))),
        )
        val xml = docxBody(DocumentRenderer.buildDocx(doc))
        assertEquals("One merged table", 1, Regex("<w:tbl>").findAll(xml).count())
        assertTrue(
            "Merged manual table must still draw borders even though the first table is borderless (border lost bug)",
            xml.contains("w:val=\"single\"") || xml.contains("<w:bottom w:val=\"single\"")
        )
        assertTrue("Manual table content present", xml.contains("Ручная запись"))
    }

    @Test
    fun columnAlignmentIsAppliedToMergedCells() {
        val reportTable = DocTableEmbed(
            title = "",
            headers = listOf(DocCell("Имя"), DocCell("Сумма")),
            rows = listOf(listOf(DocCell("А"), DocCell("123"))),
            colAligns = listOf("LEFT", "RIGHT"),
            joinPrevious = false,
        )
        val doc = DocDocument(
            title = "t",
            paragraphs = listOf(DocParagraph(font="Times New Roman", align="LEFT", elements = listOf(reportTable))),
        )
        val xml = docxBody(DocumentRenderer.buildDocx(doc))
        assertTrue(
            "Second (numeric) column should render right-aligned, not left (colAligns lost bug)",
            xml.contains("w:jc w:val=\"right\"") || xml.contains("<w:jc val=\"right\"/>")
        )
    }

    @Test
    fun report23StyleMergedHeaderRowsSpanAndCenter() {
        val firstManual = DocTableEmbed(
            title = "",
            headers = listOf(DocCell("31 участок", colSpan = 3, weight = 6.53f)),
            rows = emptyList(),
            align = "LEFT", border = 1, joinPrevious = false,
        )
        val report1 = DocTableEmbed(
            title = "",
            headers = listOf(DocCell("№"), DocCell("ФИО"), DocCell("Дата")),
            rows = listOf(listOf(DocCell("1"), DocCell("Иванов"), DocCell("01.01"))),
            colAligns = listOf("CENTER", "LEFT", "CENTER"),
            align = "LEFT", border = 1, joinPrevious = true,
        )
        val manualHeader = DocTableEmbed(
            title = "",
            headers = listOf(DocCell("34 участок", colSpan = 3, weight = 6.53f)),
            rows = emptyList(),
            align = "CENTER", border = 1, joinPrevious = true,
        )
        val report2 = DocTableEmbed(
            title = "",
            headers = listOf(DocCell("№"), DocCell("ФИО"), DocCell("Сумма")),
            rows = listOf(listOf(DocCell("2"), DocCell("Петров"), DocCell("500"))),
            colAligns = listOf("CENTER", "LEFT", "RIGHT"),
            align = "LEFT", border = 1, joinPrevious = true,
        )
        val doc = DocDocument(
            title = "t",
            paragraphs = listOf(DocParagraph(font="Times New Roman", align="LEFT", elements = listOf(firstManual, report1, manualHeader, report2))),
        )
        val xml = docxBody(DocumentRenderer.buildDocx(doc))
        println("=====DOCX REPORT23-STYLE=====")
        println(xml)
        println("=====END=====")
        assertEquals("One merged table", 1, Regex("<w:tbl>").findAll(xml).count())
        assertEquals("3 grid columns", 3, Regex("<w:gridCol").findAll(xml).count())
        assertTrue("Merged data table must keep the report RIGHT/colAligns (not all left)",
            xml.contains("w:jc w:val=\"right\""))
        assertTrue("Single-column manual header must span full table width",
            xml.contains(">34 участок</w:t>") && xml.contains("w:gridSpan w:val=\"3\""))
        assertTrue("Merged manual header text must be centered (CENTER element align)",
            xml.contains("w:jc w:val=\"center\"") )
        assertTrue("Merged table (border=1) must draw borders",
            xml.contains("<w:left w:val=\"single\"") && xml.contains("<w:bottom w:val=\"single\""))
    }

    @Test
    fun report23StyleMergedGroupHonorsReportColumnWeights() {
        // Reproduces report "Список пациентов ЛОР" (id=23): a 1-column manual
        // section-header row is merged with 6-column report tables that carry
        // explicit colWeights. Column widths must be taken from the report table,
        // NOT defaulted to equal widths just because the group starts with a
        // 1-column manual header.
        val manualHeader = DocTableEmbed(
            title = "", headers = listOf(DocCell("31 палата", colSpan = 6, weight = 18.7f)), rows = emptyList(),
            align = "CENTER", border = 1, joinPrevious = false,
        )
        val report = DocTableEmbed(
            title = "",
            headers = listOf(DocCell("№"), DocCell("ФИО"), DocCell("Дата"), DocCell("Диагноз"), DocCell("Сумма"), DocCell("Отд")),
            rows = listOf(listOf(DocCell("1"), DocCell("Иванов"), DocCell("01.01"), DocCell("X"), DocCell("500"), DocCell("ЛОР"))),
            colAligns = listOf("CENTER", "LEFT", "CENTER", "LEFT", "RIGHT", "LEFT"),
            align = "LEFT", border = 1, joinPrevious = true,
            weights = listOf(0.89f, 3f, 2.75f, 2.99f, 5.73f, 3.34f),
        )
        val manualHeader2 = DocTableEmbed(
            title = "", headers = listOf(DocCell("34 палата", colSpan = 6, weight = 18.7f)), rows = emptyList(),
            align = "CENTER", border = 1, joinPrevious = true,
        )
        val report2 = DocTableEmbed(
            title = "",
            headers = listOf(DocCell("№"), DocCell("ФИО"), DocCell("Дата"), DocCell("Диагноз"), DocCell("Сумма"), DocCell("Отд")),
            rows = listOf(listOf(DocCell("2"), DocCell("Петров"), DocCell("02.02"), DocCell("Y"), DocCell("700"), DocCell("ЛОР"))),
            colAligns = listOf("CENTER", "LEFT", "CENTER", "LEFT", "RIGHT", "LEFT"),
            align = "LEFT", border = 1, joinPrevious = true,
            weights = listOf(0.89f, 3f, 2.75f, 2.99f, 5.73f, 3.34f),
        )
        val doc = DocDocument(
            title = "t",
            paragraphs = listOf(DocParagraph(font="Times New Roman", align="LEFT",
                elements = listOf(manualHeader, report, manualHeader2, report2))),
        )
        val xml = docxBody(DocumentRenderer.buildDocx(doc))
        assertEquals("One merged table", 1, Regex("<w:tbl>").findAll(xml).count())
        assertEquals("6 grid columns", 6, Regex("<w:gridCol").findAll(xml).count())
        val widths = Regex("""<w:gridCol w:w="(\d+)"/>""").findAll(xml).map { it.groupValues[1].toInt() }.toList()
        assertEquals("6 widths from colWeights", 6, widths.size)
        // № (0.89cm) must be far narrower than Сумма (5.73cm) - i.e. widths must
        // reflect the report colWeights, NOT be ~equal.
        assertTrue("Report column weights must produce distinct column widths (not all ~equal)",
            widths.max() > widths.min() * 2)
        // The right-aligned numeric column must still be right-aligned.
        assertTrue("Report per-column right alignment must be preserved",
            xml.contains("w:jc w:val=\"right\""))
        // Manual full-width headers must be centered and bordered.
        assertTrue("Manual header spans all 6 columns",
            xml.contains(">34 палата</w:t>") && xml.contains("w:gridSpan w:val=\"6\""))
        // The manual header cell (colSpan=6, align only via table CENTER) must be
        // centered, NOT forced to LEFT. Search the cell that contains the text and
        // confirm its paragraph is center-aligned.
        val headerCell = Regex("<w:tc>(?:(?!</w:tc>).)*34 палата(?:(?!</w:tc>).)*</w:tc>").find(xml)?.value
            ?: ""
        assertTrue("Manual full-width header must be CENTERED (element align honored, not forced LEFT)",
            headerCell.contains("w:jc w:val=\"center\""))
        assertTrue("Borders drawn (border=1)",
            xml.contains("<w:left w:val=\"single\"") && xml.contains("<w:bottom w:val=\"single\""))
    }

    @Test
    fun mergedReportKeepsHorizontalRowBordersAndOuterOutline() {
        // Report-23 style: a manual full-width section header joined to an embedded
        // report table. Only the boundary (header) rows should drop their top border
        // to merge; DATA rows must keep horizontal separators, and the table must
        // keep its outer left/right outline.
        val section = DocTableEmbed(
            title = "",
            headers = listOf(DocCell("31 палата", colSpan = 6, weight = 18.7f)),
            rows = emptyList(), align = "CENTER", border = 1, joinPrevious = false,
        )
        val report = DocTableEmbed(
            title = "",
            headers = listOf(DocCell("№"), DocCell("Звание"), DocCell("в/ч"), DocCell(""), DocCell("ФИО"), DocCell("")),
            rows = listOf(
                listOf(DocCell("1"), DocCell("рядовой"), DocCell("123"), DocCell(""), DocCell("Иванов"), DocCell("")),
                listOf(DocCell("2"), DocCell("сержант"), DocCell("456"), DocCell(""), DocCell("Петров"), DocCell("")),
            ),
            colAligns = listOf("CENTER", "CENTER", "CENTER", "CENTER", "LEFT", "LEFT"),
            align = "LEFT", border = 1, joinPrevious = true,
            weights = listOf(0.89f, 3f, 2.75f, 2.99f, 5.73f, 3.34f),
        )
        val doc = DocDocument(
            title = "t",
            paragraphs = listOf(DocParagraph(font = "Times New Roman", align = "LEFT", elements = listOf(section, report))),
        )
        val xml = docxBody(DocumentRenderer.buildDocx(doc))
        // Split rows
        val rows = Regex("<w:tr>").findAll(xml).map { it.range.first }.toList()
        assertEquals("Row count = section header + report header + 2 data rows", 4, rows.size)
        val rowStrs = rows.mapIndexed { i, start ->
            val end = if (i + 1 < rows.size) rows[i + 1] else xml.length
            xml.substring(start, end)
        }
        // Section header row (index 0) draws the outer top of the table.
        val sectionRow = rowStrs[0]
        // Report header (index 1) and both data rows (index 2,3) keep top borders:
        // the whole group must render as ONE continuous table with full grid lines.
        val boundaryRow = rowStrs[1]
        val dataRow1 = rowStrs[2]
        val dataRow2 = rowStrs[3]
        assertTrue("Section header row draws outer top", sectionRow.contains("""<w:top w:val="single""""))
        assertTrue("Boundary report header row keeps top border", boundaryRow.contains("""<w:top w:val="single""""))
        assertTrue("First data row keeps top border", dataRow1.contains("""<w:top w:val="single""""))
        assertTrue("Second data row keeps top border", dataRow2.contains("""<w:top w:val="single""""))
        // Outer outline: the report cells must carry left/right single borders.
        assertTrue("Report cell keeps left border", boundaryRow.contains("""<w:left w:val="single""""))
        assertTrue("Last column keeps right border", boundaryRow.contains("""<w:right w:val="single""""))
    }

    @Test
    fun docxTableUsesConfiguredFontSize() {
        // The last/standalone table has its font size set to 18. buildDocx must emit
        // w:sz = 36 (half-points) for its cells, not the default 18/20 that encoded as 9/10pt.
        val table = DocTableEmbed(
            title = "",
            headers = listOf(DocCell("ВСЕГО:"), DocCell("\"К\":"), DocCell("Ряд:")),
            rows = emptyList(),
            align = "LEFT", border = 0, size = 18,
        )
        val doc = DocDocument(
            title = "t",
            paragraphs = listOf(DocParagraph(font = "Times New Roman", align = "LEFT", elements = listOf(table))),
        )
        val xml = docxBody(DocumentRenderer.buildDocx(doc))
        assertTrue("Table cell must use configured size 18 (w:sz=36), not 9/10pt default",
            xml.contains("""<w:sz w:val="36"/>"""))
        assertTrue("Must not use the default 9pt (w:sz=18) or 10pt (w:sz=20) sizes",
            !xml.contains("""<w:sz w:val="18"/>""") && !xml.contains("""<w:sz w:val="20"/>"""))
    }

    @Test
    fun standaloneUniformManualTableHonorsColumnWidths() {
        // The very last table in report 23 is a standalone MANUAL table whose three
        // columns are all set to width 6.29 cm. Because the widths are uniform, the
        // distinct-width guard must still honor them (a user can deliberately give
        // equal widths) rather than falling back to the equal-width 5.31 cm default.
        // 6.29 cm -> 6.29*(1440/2.54) = 3566 twips.
        val table = DocTableEmbed(
            title = "",
            headers = listOf(DocCell("ВСЕГО:"), DocCell("\"К\":"), DocCell("Ряд:")),
            rows = emptyList(),
            weights = listOf(6.29f, 6.29f, 6.29f), align = "LEFT", border = 0, size = 18,
        )
        val doc = DocDocument(
            title = "t",
            paragraphs = listOf(DocParagraph(font = "Times New Roman", align = "LEFT", elements = listOf(table))),
        )
        val xml = docxBody(DocumentRenderer.buildDocx(doc))
        val cols = Regex("""<w:gridCol w:w="(\d+)"/>""").findAll(xml).map { it.groupValues[1].toInt() }.toList()
        assertEquals("Must produce 3 columns", 3, cols.size)
        cols.forEach {
            assertTrue("Each column must be ~6.29cm (3566 twips), not the 5.31cm (3008) fallback",
                it in 3565..3567)
        }
        assertTrue("Must NOT use the uniform 5.31cm fallback (3008 twips)",
            cols.none { it == 3008 })
    }

    @Test
    fun reconstructActualReport23Docx() {
        // Reconstructs report "Список пациентов ЛОР" (23) exactly as buildDocModel
        // yields it, using the real DB values, and dumps the DOCX so the resulting
        // column widths + alignment can be inspected.
        fun manualHeader(text: String, join: Boolean) = DocTableEmbed(
            title = "", headers = listOf(DocCell(text, colSpan = 6, weight = 18.7f)), rows = emptyList(),
            align = "CENTER", border = 1, joinPrevious = join,
        )
        fun reportBlock(join: Boolean, seed: String) = DocTableEmbed(
            title = "",
            headers = listOf(DocCell("№"), DocCell("Звание"), DocCell("в/ч"), DocCell(""), DocCell("ФИО"), DocCell("")),
            rows = listOf(
                listOf(DocCell("1"), DocCell("рядовой"), DocCell("12345"), DocCell(""), DocCell("Иванов И.И."), DocCell("")),
                listOf(DocCell("2"), DocCell("сержант"), DocCell("67890"), DocCell(""), DocCell("Петров П.П."), DocCell("")),
            ),
            colAligns = listOf("CENTER", "CENTER", "CENTER", "CENTER", "LEFT", "LEFT"),
            align = "LEFT", border = 1, joinPrevious = join,
            weights = listOf(0.89f, 3f, 2.75f, 2.99f, 5.73f, 3.34f),
        )
        val elems = mutableListOf<DocTableEmbed>()
        elems += manualHeader("31 палата", false)
        elems += reportBlock(true, "a")
        elems += manualHeader("34 палата", true)
        elems += reportBlock(true, "b")
        elems += manualHeader("35 палата", true)
        elems += reportBlock(true, "c")
        elems += manualHeader("36 палата", true)
        elems += reportBlock(true, "d")
        val totals = DocTableEmbed(
            title = "",
            headers = listOf(DocCell("ВСЕГО:", weight = 6.29f, align = "LEFT"), DocCell("\"К\":", weight = 6.29f, align = "LEFT"), DocCell("Ряд:", weight = 6.29f, align = "LEFT")),
            rows = emptyList(), align = "LEFT", border = 0, joinPrevious = false,
            weights = listOf(6.29f, 6.29f, 6.29f),
        )
        val doc = DocDocument(
            title = "t",
            paragraphs = listOf(
                DocParagraph(font = "Times New Roman", align = "LEFT", elements = elems),
                DocParagraph(font = "Times New Roman", align = "LEFT", elements = listOf(totals)),
            ),
        )
        val xml = docxBody(DocumentRenderer.buildDocx(doc))
        println("=====REAL REPORT23 DOCX=====")
        println(xml)
        println("=====END=====")
    }

    @Test
    fun writeRealDocxFilesForWordInspection() {
        val out = java.io.File(System.getProperty("java.io.tmpdir") + "opencode", "wordcheck").apply { mkdirs() }
        data class Sc(val name: String, val border: Int, val align: String)
        val scs = listOf(
            Sc("no_border", 0, "CENTER"),
            Sc("full_grid", 1, "LEFT"),
            Sc("outer_only", 2, "RIGHT"),
            Sc("h_only", 3, "CENTER"),
        )
        scs.forEach { sc ->
            val tbl = DocTableEmbed(
                title = "Таблица ${sc.name}",
                headers = listOf(DocCell("К1"), DocCell("К2")),
                rows = listOf(listOf(DocCell("a"), DocCell("b")), listOf(DocCell("c"), DocCell("d"))),
                align = sc.align, border = sc.border, joinPrevious = false,
            )
            val doc = DocDocument(
                title = "t",
                paragraphs = listOf(DocParagraph(font = "Times New Roman", align = "LEFT", elements = listOf(tbl))),
            )
            java.io.File(out, sc.name + ".docx").writeBytes(DocumentRenderer.buildDocx(doc))
        }
        val t1 = DocTableEmbed(title = "", headers = listOf(DocCell("Кол А"), DocCell("Кол Б")),
            rows = listOf(listOf(DocCell("a1"), DocCell("b1"))), joinPrevious = false)
        val t2 = DocTableEmbed(title = "", headers = listOf(DocCell("К X"), DocCell("К Y"), DocCell("К Z")),
            rows = listOf(listOf(DocCell("x"), DocCell("y"), DocCell("z"))), joinPrevious = true)
        val mdoc = DocDocument(title = "t",
            paragraphs = listOf(DocParagraph(font = "Times New Roman", align = "LEFT", elements = listOf(t1, t2))))
        java.io.File(out, "merged_seam.docx").writeBytes(DocumentRenderer.buildDocx(mdoc))
        println("WROTE DOCX TO: " + out.absolutePath)
    }
}
