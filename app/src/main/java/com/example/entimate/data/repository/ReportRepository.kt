package com.example.entimate.data.repository

import androidx.room.withTransaction
import com.example.entimate.data.local.*
import com.example.entimate.data.local.DocCell
import com.example.entimate.data.local.manualTableFromJson
import com.example.entimate.ui.components.formatIsoDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.*

const val DROPDOWN_EMPTY_MARKER = "\u0000__EMPTY__\u0000"

class ReportRepository(private val db: AppDatabase) {
    private val reportDao = db.reportDao()
    private val patientDao = db.patientDao()
    private val isoFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    val reportsFlow: Flow<List<ReportWithColumns>> =
        reportDao.observeAll().map { list -> list.map { it.copy(report = it.report.migrate()) } }

    suspend fun saveReport(
        report: ReportEntity,
        columns: List<ReportColumnEntity>,
        filters: List<ReportFilterEntity>,
    ): Long = db.withTransaction {
        val id = if (report.id == 0L) {
            reportDao.insertReport(report)
        } else {
            reportDao.updateReport(report)
            report.id
        }
        reportDao.deleteColumnsForReport(id)
        reportDao.deleteFiltersForReport(id)
        columns.forEach { c -> reportDao.insertColumn(c.copy(id = 0, reportId = id)) }
        filters.forEach { f -> reportDao.insertFilter(f.copy(id = 0, reportId = id)) }
        id
    }

    suspend fun saveDocumentReport(
        report: ReportEntity,
        blocks: List<Pair<ReportParagraphEntity, List<ReportDocElementEntity>>>,
    ): Long = db.withTransaction {
        val id = if (report.id == 0L) reportDao.insertReport(report) else { reportDao.updateReport(report); report.id }
        reportDao.deleteParagraphsForReport(id)
        reportDao.deleteElementsForReport(id)
        blocks.forEach { (p, els) ->
            val pid = reportDao.insertParagraph(p.copy(id = 0, reportId = id))
            els.forEach { e -> reportDao.insertElement(e.copy(id = 0, paragraphId = pid)) }
        }
        id
    }

    suspend fun buildDocModel(reportId: Long, dateFormat: String = "dd.MM.yyyy", from: Long = 0L, to: Long = System.currentTimeMillis()): DocDocument {
        val doc = reportDao.getWithDocument(reportId) ?: return DocDocument("", emptyList())
        val paragraphs = doc.paragraphs.map { pw ->
            val elements = pw.elements.map { e ->
                when (e.type) {
                    "REPORT" -> {
                        val table = buildTable(e.embeddedReportId, from, to, dateFormat)
                        var rows = table.rows.map { r -> r.map { DocCell(it) } }.toMutableList()
                        while (rows.size < e.minRows) rows.add(List(table.headers.size) { DocCell("") })
                        val headers = if (e.numberColumn == 1) listOf(DocCell("№")) + table.headers.map { DocCell(it) } else table.headers.map { DocCell(it) }
                        val outRows = if (e.numberColumn == 1) rows.mapIndexed { i, r -> listOf(DocCell((i + 1).toString())) + r } else rows
                        val colAligns = if (e.numberColumn == 1) listOf("CENTER") + table.colAligns else table.colAligns
                        val n = headers.size
                        val parsed = e.colWeights.split(',').mapNotNull { it.trim().toFloatOrNull()?.coerceAtLeast(0.1f) }
                        val weights = if (parsed.isNotEmpty()) List(n) { parsed.getOrElse(it) { 0f } } else List(n) { 0f }
                        DocTableEmbed(e.embeddedTitle, headers, outRows, e.align, e.bold == 1, e.italic == 1, e.underline == 1, e.size, e.colorArgb, e.bgArgb, e.joinPrevious == 1, colAligns, e.border, weights)
                    }
                    "TAB" -> DocTab()
                    "PAGEBREAK" -> DocPageBreak
                    "MANUAL" -> {
                        val t = manualTableFromJson(e.text)
                        val weights = t.headers.flatMap { h -> val span = h.colSpan.coerceAtLeast(1); val w = h.weight; List(span) { if (w > 0f) w / span else 0f } }
                        DocTableEmbed(
                            t.title,
                            t.headers.map { DocCell(it.text, it.colSpan, it.weight, it.align) },
                            t.rows.map { row -> row.map { DocCell(it.text, it.colSpan, it.weight, it.align) } },
                            e.align, e.bold == 1, e.italic == 1, e.underline == 1, e.size, e.colorArgb, e.bgArgb, e.joinPrevious == 1, emptyList(), e.border, weights,
                        )
                    }
                    else -> DocText(e.text, e.bold == 1, e.italic == 1, e.underline == 1, e.size, e.colorArgb, e.bgArgb)
                }
            }
            DocParagraph(
                font = pw.paragraph.font,
                align = pw.paragraph.align,
                indentLeftMm = pw.paragraph.indentLeftMm,
                indentRightMm = pw.paragraph.indentRightMm,
                firstLineMm = pw.paragraph.firstLineMm,
                lineSpacing = pw.paragraph.lineSpacing,
                spaceBeforeMm = pw.paragraph.spaceBeforeMm,
                spaceAfterMm = pw.paragraph.spaceAfterMm,
                elements = elements,
            )
        }
        return DocDocument(
            doc.report.name,
            paragraphs,
            doc.report.marginTopMm,
            doc.report.marginRightMm,
            doc.report.marginBottomMm,
            doc.report.marginLeftMm,
        )
    }

    suspend fun getReportWithColumns(id: Long) = reportDao.getWithColumns(id)?.let { it.copy(report = it.report.migrate()) }
    suspend fun getReportWithFilters(id: Long) = reportDao.getWithFilters(id)?.let { it.copy(report = it.report.migrate()) }
    suspend fun getReportWithDocument(id: Long) = reportDao.getWithDocument(id)?.let { it.copy(report = it.report.migrate()) }
    suspend fun deleteReport(report: ReportEntity) = reportDao.deleteReport(report)
    suspend fun tableReports(): List<ReportEntity> = reportDao.getTableReports().map { it.migrate() }
    suspend fun getColumnsForReport(reportId: Long): List<ReportColumnEntity> = reportDao.getColumnsForReport(reportId)
    suspend fun getAllReports() = reportDao.getAll().map { it.migrate() }
    suspend fun reorder(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id -> reportDao.setSortOrder(id, index) }
    }
    suspend fun patientCustomFields() = patientDao.getAllCustomFields()
    suspend fun getEarliestPatientTime(): Long? = patientDao.getEarliestCreatedAt()

    suspend fun duplicateReport(reportId: Long): Long = db.withTransaction {
        val src = reportDao.getWithFilters(reportId) ?: return@withTransaction 0L
        val existing = reportDao.getAll().map { it.name.lowercase() }
        val base = src.report.name.replace(Regex(""" \(\d+\)$"""), "")
        var n = 1
        var candidate = "$base ($n)"
        while (existing.contains(candidate.lowercase())) {
            n++
            candidate = "$base ($n)"
        }
        val newId = reportDao.insertReport(src.report.copy(id = 0, name = candidate))
        src.columns.forEach { col ->
            reportDao.insertColumn(col.copy(id = 0, reportId = newId))
        }
        src.filters.forEach { f ->
            reportDao.insertFilter(f.copy(id = 0, reportId = newId))
        }
        newId
    }

    data class Table(val headers: List<String>, val rows: List<List<String>>, val colAligns: List<String> = emptyList())

    private fun passes(value: String, operator: String, target: String): Boolean {
        return when (operator) {
            "EQ" -> value == target
            "CONTAINS" -> value.contains(target, ignoreCase = true)
            "GT" -> (value.toDoubleOrNull() ?: Double.MIN_VALUE) > (target.toDoubleOrNull() ?: 0.0)
            "LT" -> (value.toDoubleOrNull() ?: Double.MAX_VALUE) < (target.toDoubleOrNull() ?: 0.0)
            "GTE" -> (value.toDoubleOrNull() ?: Double.MIN_VALUE) >= (target.toDoubleOrNull() ?: 0.0)
            "LTE" -> (value.toDoubleOrNull() ?: Double.MAX_VALUE) <= (target.toDoubleOrNull() ?: 0.0)
            else -> true
        }
    }

    private fun columnKeys(col: ReportColumnEntity): List<String> {
        val raw = col.sourceFieldKeys.ifBlank { col.fieldKey }
        return raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
    }

    fun columnValue(p: PatientEntity, customValues: Map<Long, String>, customLabels: Map<Long, String>, key: String): String {
        if (isCustomKey(key)) {
            val fid = customFieldIdFromKey(key)
            return customValues[fid] ?: ""
        }
        return patientValue(p, key)
    }

    fun columnLabel(key: String, customFields: List<PatientCustomFieldEntity>): String {
        if (isCustomKey(key)) {
            val fid = customFieldIdFromKey(key)
            return customFields.firstOrNull { it.id == fid }?.label ?: "Поле"
        }
        return patientFieldByKey(key)?.label ?: key
    }

    private fun fieldType(key: String, customFields: List<PatientCustomFieldEntity>): String? {
        if (isCustomKey(key)) {
            val fid = customFieldIdFromKey(key)
            return customFields.firstOrNull { it.id == fid }?.type
        }
        return patientFieldByKey(key)?.type
    }

    private fun interpretCheckbox(raw: String, col: ReportColumnEntity): String {
        if (raw.isBlank()) return ""
        val trueText = col.trueText.ifBlank { "" }
        val falseText = col.falseText.ifBlank { "" }
        return if (raw == "true" || raw == "1") trueText else falseText
    }

    private fun interpretDropdown(raw: String, col: ReportColumnEntity, fieldKey: String): String {
        if (col.dropdownMap.isBlank() || raw.isBlank()) return raw
        val map = parseDropdownMap(col.dropdownMap)
        val mapped = map["$fieldKey#$raw"] ?: map[raw]
        return when (mapped) {
            DROPDOWN_EMPTY_MARKER -> ""
            null -> raw
            else -> mapped
        }
    }

    fun resolveColumnValue(
        col: ReportColumnEntity,
        p: PatientEntity,
        customValues: Map<Long, String>,
        customLabels: Map<Long, String>,
        customFields: List<PatientCustomFieldEntity>,
        dateFormat: String = "dd.MM.yyyy",
    ): String {
        val keys = columnKeys(col)
        return keys.map { key ->
            val raw = columnValue(p, customValues, customLabels, key)
            when (fieldType(key, customFields)) {
                "CHECKBOX" -> interpretCheckbox(raw, col)
                "DROPDOWN" -> interpretDropdown(raw, col, key)
                "DATE" -> formatIsoDate(raw, dateFormat)
                else -> raw
            }
        }
            .filter { it.isNotBlank() }
            .joinToString(col.joinSeparator.ifBlank { " " })
    }

    fun resolveColumnHeader(col: ReportColumnEntity, customFields: List<PatientCustomFieldEntity>): String {
        if (col.label.isNotBlank()) return col.label
        val keys = columnKeys(col)
        return keys.firstOrNull()?.let { columnLabel(it, customFields) } ?: "Колонка"
    }

    suspend fun buildTable(reportId: Long, from: Long, to: Long, dateFormat: String = "dd.MM.yyyy", withNumber: Boolean = false): Table {
        val report = reportDao.getWithFilters(reportId) ?: return Table(emptyList(), emptyList())
        val all = patientDao.getAllPatientsWithValues()
        val customFields = patientDao.getAllCustomFields()
        val customLabels = customFields.associate { it.id to it.label }

        val filtered = if (report.filters.isEmpty()) {
            all
        } else {
            all.filter { pw ->
                var result = evaluateFilter(pw, report.filters.first(), customFields)
                report.filters.drop(1).forEach { f ->
                    val ok = evaluateFilter(pw, f, customFields)
                    result = if (f.connector == "OR") result || ok else result && ok
                }
                result
            }
        }

        val inPeriod = filtered.filter {
            val ad = it.patient.admissionDate
            if (ad.isBlank()) false else (isoFmt.parse(ad)?.time ?: 0L) in from..to
        }

        val headers = if (withNumber) listOf("№") + report.columns.map { resolveColumnHeader(it, customFields) }
        else report.columns.map { resolveColumnHeader(it, customFields) }
        val colAligns = (if (withNumber) listOf("CENTER") else emptyList()) + report.columns.map { it.align.ifBlank { "LEFT" } }
        val rows = inPeriod.mapIndexed { idx, pw ->
            val cvMap = pw.customValues.associate { it.fieldId to it.value }
            val row = mutableListOf<String>()
            if (withNumber) row.add((idx + 1).toString())
            report.columns.forEach { col ->
                row.add(resolveColumnValue(col, pw.patient, cvMap, customLabels, customFields, dateFormat))
            }
            row
        }
        return Table(headers, rows, colAligns)
    }

    private fun evaluateFilter(pw: PatientWithValues, filter: ReportFilterEntity, customFields: List<PatientCustomFieldEntity>): Boolean {
        val value = columnValue(pw.patient, pw.customValues.associate { it.fieldId to it.value }, customFields.associate { it.id to it.label }, filter.fieldKey)
        return passes(value, filter.operator, filter.value)
    }
}

internal fun parseDropdownMap(s: String): Map<String, String> {
    if (s.isBlank()) return emptyMap()
    val out = mutableMapOf<String, String>()
    s.split(";").forEach { entry ->
        if (entry.isBlank()) return@forEach
        when {
            entry.contains("#") -> {
                val h = entry.indexOf("#")
                val fk = entry.substring(0, h)
                val rest = entry.substring(h + 1)
                val eq = rest.indexOf("=")
                if (eq > 0) out["$fk#${rest.substring(0, eq)}"] = rest.substring(eq + 1)
            }
            entry.contains("::") -> {
                val idx = entry.indexOf("::")
                if (idx > 0) out[entry.substring(0, idx)] = entry.substring(idx + 2)
            }
            entry.contains("=") -> {
                val idx = entry.indexOf("=")
                if (idx > 0) out[entry.substring(0, idx)] = entry.substring(idx + 1)
            }
        }
    }
    return out
}

internal fun serializeDropdownMap(m: Map<String, String>): String =
    m.entries.joinToString(";") { "${it.key}=${it.value}" }
