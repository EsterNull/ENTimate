package com.example.entimate.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.entimate.EntimateApplication
import com.example.entimate.data.local.DocumentEntity
import com.example.entimate.data.repository.DocumentRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class DocumentsViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application as EntimateApplication).documentRepository

    val documents: StateFlow<List<DocumentEntity>> = repo.allDocumentsFlow
        .catch { emit(emptyList<DocumentEntity>()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun save(doc: DocumentEntity) = viewModelScope.launch {
        if (doc.id == 0L) repo.insert(doc) else repo.update(doc)
    }

    fun delete(doc: DocumentEntity) = viewModelScope.launch {
        repo.delete(doc)
    }

    fun adjust(docId: Long, sign: Int) {
        viewModelScope.launch {
            val step = repo.getById(docId)?.step ?: 1
            repo.adjust(docId, sign * step)
        }
    }

    fun recordChange(docId: Long, steps: Int) {
        viewModelScope.launch {
            val step = repo.getById(docId)?.step ?: 1
            repo.recordChange(docId, steps * step)
        }
    }

    suspend fun duplicate(doc: DocumentEntity): Long {
        val copy = doc.copy(id = 0, name = "${doc.name} (копия)")
        return repo.insert(copy)
    }
}
