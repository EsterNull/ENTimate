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
import kotlin.comparisons.compareBy
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
        val copy = doc.copy(id = 0, name = "${doc.name} (копия)", sortOrder = 0)
        val newId = repo.insert(copy)
        val others = repo.getAll()
            .filter { it.id != newId }
            .sortedWith(compareBy<DocumentEntity> { it.sortOrder }.thenBy { it.name })
        val origPos = others.indexOfFirst { it.id == doc.id }.coerceAtLeast(0)
        val ordered = others.toMutableList()
        ordered.add(origPos + 1, doc.copy(id = newId, name = copy.name, sortOrder = 0))
        repo.reorder(ordered.map { it.id })
        return newId
    }
}
