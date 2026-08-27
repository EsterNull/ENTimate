package com.example.entimate.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.entimate.EntimateApplication
import com.example.entimate.data.local.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class PatientsViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application as EntimateApplication).patientRepository

    val patients: StateFlow<List<PatientWithValues>> = repo.patientsFlow
        .catch { emit(emptyList<PatientWithValues>()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customFields: StateFlow<List<PatientCustomFieldEntity>> = repo.customFieldsFlow
        .catch { emit(emptyList<PatientCustomFieldEntity>()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val documents: StateFlow<List<DocumentEntity>> = repo.documentsFlow
        .catch { emit(emptyList<DocumentEntity>()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val effectEvents: Flow<String> = repo.effectLog

    suspend fun getPatient(id: Long) = repo.getPatient(id)
    suspend fun getAllLinks() = repo.getAllLinks()

    fun savePatient(patient: PatientEntity, customValues: Map<Long, String>) =
        viewModelScope.launch { repo.savePatient(patient, customValues) }

    fun deletePatient(patient: PatientEntity) = viewModelScope.launch { repo.deletePatient(patient) }

    fun dischargePatient(patient: PatientEntity) = viewModelScope.launch { repo.dischargePatient(patient) }

    fun reregisterPatient(patient: PatientEntity, admissionDate: String) =
        viewModelScope.launch { repo.reregisterPatient(patient, admissionDate) }

    fun saveCustomField(f: PatientCustomFieldEntity) = viewModelScope.launch { repo.saveCustomField(f) }
    fun deleteCustomField(f: PatientCustomFieldEntity) = viewModelScope.launch { repo.deleteCustomField(f) }

    fun saveLink(link: PatientFieldLinkEntity) = viewModelScope.launch { repo.saveLink(link) }
    fun deleteLink(link: PatientFieldLinkEntity) = viewModelScope.launch { repo.deleteLink(link) }

    fun reorder(from: Int, to: Int) = viewModelScope.launch {
        val all = patients.value
        val active = all.filter { it.patient.discharged != 1 }
        val discharged = all.filter { it.patient.discharged == 1 }
        if (from !in active.indices || to !in active.indices) return@launch
        val ids = active.map { it.patient.id }.toMutableList()
        val id = ids.removeAt(from)
        ids.add(to, id)
        repo.reorder(ids + discharged.map { it.patient.id })
    }
}
