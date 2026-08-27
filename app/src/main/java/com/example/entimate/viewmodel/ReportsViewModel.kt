package com.example.entimate.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.entimate.EntimateApplication
import com.example.entimate.data.local.*
import com.example.entimate.data.repository.ReportRepository
import com.example.entimate.ui.reports.DocumentRenderer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ReportsViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application as EntimateApplication).reportRepository

    val reports = repo.reportsFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList(),
    )

    fun delete(report: ReportEntity) {
        viewModelScope.launch { repo.deleteReport(report) }
    }

    fun reorder(from: Int, to: Int) = viewModelScope.launch {
        val ids = reports.value.map { it.report.id }.toMutableList()
        if (from !in ids.indices || to !in ids.indices) return@launch
        val id = ids.removeAt(from)
        ids.add(to, id)
        repo.reorder(ids)
    }

    suspend fun duplicateReport(reportId: Long): Long = repo.duplicateReport(reportId)

    suspend fun getReportWithDocument(id: Long) = repo.getReportWithDocument(id)
    suspend fun tableReports(): List<ReportEntity> = repo.tableReports()
    suspend fun reportColumns(reportId: Long): List<ReportColumnEntity> = repo.getColumnsForReport(reportId)
    suspend fun buildDocModel(reportId: Long, dateFormat: String = "dd.MM.yyyy", from: Long = 0L, to: Long = System.currentTimeMillis()) = repo.buildDocModel(reportId, dateFormat, from, to)

    fun saveDocumentReport(
        report: ReportEntity,
        blocks: List<Pair<ReportParagraphEntity, List<ReportDocElementEntity>>>,
    ) = viewModelScope.launch { repo.saveDocumentReport(report, blocks) }

    suspend fun saveDocumentReportSuspended(
        report: ReportEntity,
        blocks: List<Pair<ReportParagraphEntity, List<ReportDocElementEntity>>>,
    ): Long = repo.saveDocumentReport(report, blocks)

    fun buildRtf(doc: DocDocument): ByteArray = DocumentRenderer.buildRtf(doc)
    fun buildDocx(doc: DocDocument): ByteArray = DocumentRenderer.buildDocx(doc)
    fun buildPdf(doc: DocDocument): ByteArray = DocumentRenderer.buildPdf(getApplication(), doc)
}
