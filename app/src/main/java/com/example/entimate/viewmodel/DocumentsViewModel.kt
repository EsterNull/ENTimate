package com.example.entimate.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun save(doc: DocumentEntity) = viewModelScope.launch {
        if (doc.id == 0L) repo.insert(doc) else repo.update(doc)
    }

    fun delete(doc: DocumentEntity) = viewModelScope.launch {
        repo.delete(doc)
    }

    fun reorder(from: Int, to: Int) = viewModelScope.launch {
        val ids = documents.value.map { it.id }.toMutableList()
        if (from !in ids.indices || to !in ids.indices) return@launch
        val id = ids.removeAt(from)
        ids.add(to, id)
        repo.reorder(ids)
    }

    fun adjust(docId: Long, sign: Int) {
        viewModelScope.launch {
            repo.adjust(docId, sign)
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
