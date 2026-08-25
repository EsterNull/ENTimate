package com.example.entimate.data.repository

import androidx.room.withTransaction
import com.example.entimate.data.local.*
import com.example.entimate.ui.components.formatIsoDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.*

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

    suspend fun getReportWithColumns(id: Long) = reportDao.getWithColumns(id)?.let { it.copy(report = it.report.migrate()) }
    suspend fun getReportWithFilters(id: Long) = reportDao.getWithFilters(id)?.let { it.copy(report = it.report.migrate()) }
    suspend fun deleteReport(report: ReportEntity) = reportDao.deleteReport(report)
    suspend fun getAllReports() = reportDao.getAll().map { it.migrate() }
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

    data class Table(val headers: List<String>, val rows: List<List<String>>)

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
        val trueText = col.trueText.ifBlank { "Да" }
        val falseText = col.falseText.ifBlank { "Нет" }
        return if (raw == "true") trueText else falseText
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

    suspend fun buildTable(reportId: Long, from: Long, to: Long, dateFormat: String = "dd.MM.yyyy"): Table {
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

        val headers = listOf("№") + report.columns.map { resolveColumnHeader(it, customFields) }
        val rows = inPeriod.mapIndexed { idx, pw ->
            val cvMap = pw.customValues.associate { it.fieldId to it.value }
            val row = mutableListOf((idx + 1).toString())
            report.columns.forEach { col ->
                row.add(resolveColumnValue(col, pw.patient, cvMap, customLabels, customFields, dateFormat))
            }
            row
        }
        return Table(headers, rows)
    }

    private fun evaluateFilter(pw: PatientWithValues, filter: ReportFilterEntity, customFields: List<PatientCustomFieldEntity>): Boolean {
        val value = columnValue(pw.patient, pw.customValues.associate { it.fieldId to it.value }, customFields.associate { it.id to it.label }, filter.fieldKey)
        return passes(value, filter.operator, filter.value)
    }
}
