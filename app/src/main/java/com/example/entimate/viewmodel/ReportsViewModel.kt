package com.example.entimate.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.entimate.EntimateApplication
import com.example.entimate.data.local.ReportEntity
import com.example.entimate.data.repository.ReportRepository
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

    suspend fun duplicateReport(reportId: Long): Long = repo.duplicateReport(reportId)
}
