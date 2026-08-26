package com.example.entimate.data.repository

import com.example.entimate.data.local.DocumentChangeEntity
import com.example.entimate.data.local.DocumentDao
import com.example.entimate.data.local.DocumentEntity
import com.example.entimate.data.local.migrate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DocumentRepository(private val dao: DocumentDao) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _documents = MutableStateFlow<List<DocumentEntity>>(emptyList())
    private val adjustMutex = Mutex()
    val allDocumentsFlow: StateFlow<List<DocumentEntity>> = _documents.asStateFlow()

    init {
        dao.observeAll()
            .map { list -> list.map { it.migrate() } }
            .onEach { _documents.value = it }
            .launchIn(scope)
    }

    suspend fun getById(id: Long) = dao.getById(id)?.migrate()
    suspend fun insert(doc: DocumentEntity): Long {
        val id = dao.insert(doc)
        refresh()
        return id
    }
    suspend fun update(doc: DocumentEntity) { dao.update(doc); refresh() }
    suspend fun reorder(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id -> dao.setOrder(id, index) }
        refresh()
    }
    suspend fun adjust(docId: Long, sign: Int) {
        adjustMutex.withLock {
            val doc = _documents.value.firstOrNull { it.id == docId } ?: dao.getById(docId) ?: return
            val newQty = doc.quantity + sign * doc.step
            dao.updateQuantity(docId, newQty)
            _documents.value = _documents.value.map { d ->
                if (d.id == docId) d.copy(quantity = newQty) else d
            }
        }
    }
    suspend fun delete(doc: DocumentEntity) { dao.delete(doc); refresh() }
    suspend fun getAll() = dao.getAll().map { it.migrate() }
    suspend fun deleteAll() { dao.deleteAll(); refresh() }

    suspend fun recordChange(docId: Long, delta: Int, patientId: Long = 0) {
        if (delta == 0) return
        val qty = dao.getById(docId)?.quantity ?: 0
        dao.insertChange(
            DocumentChangeEntity(
                documentId = docId,
                timestamp = System.currentTimeMillis(),
                delta = delta,
                qtyAfter = qty,
                patientId = patientId,
            )
        )
    }

    suspend fun recordInitial(docId: Long, qty: Int) {
        dao.insertChange(
            DocumentChangeEntity(
                documentId = docId,
                timestamp = System.currentTimeMillis(),
                delta = 0,
                qtyAfter = qty,
                patientId = 0,
            )
        )
    }

    suspend fun getChanges(docId: Long) = dao.getChanges(docId)
    fun changesFlow(docId: Long): Flow<List<DocumentChangeEntity>> = dao.observeChanges(docId)

    private suspend fun refresh() {
        _documents.value = dao.getAll().map { it.migrate() }
    }
}
