package com.example.entimate.data.repository

import com.example.entimate.data.local.DocumentChangeEntity
import com.example.entimate.data.local.DocumentDao
import com.example.entimate.data.local.DocumentEntity
import com.example.entimate.data.local.migrate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@OptIn(ExperimentalCoroutinesApi::class)
class DocumentRepository(
    private val dao: DocumentDao,
    private val folderRepo: FolderRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val adjustMutex = Mutex()
    val allDocumentsFlow: StateFlow<List<DocumentEntity>> =
        folderRepo.currentFolderIdFlow
            .flatMapLatest { fid -> dao.observeAll(fid).map { list -> list.map { it.migrate() } } }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    suspend fun getById(id: Long) = dao.getById(id)?.migrate()
    suspend fun insert(doc: DocumentEntity): Long {
        val id = dao.insert(doc.copy(folderId = folderRepo.currentFolderId()))
        return id
    }
    suspend fun update(doc: DocumentEntity) { dao.update(doc) }
    suspend fun reorder(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id -> dao.setOrder(id, index) }
    }
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun adjust(docId: Long, sign: Int) {
        adjustMutex.withLock {
            val doc = allDocumentsFlow.value.firstOrNull { it.id == docId } ?: dao.getById(docId) ?: return
            val newQty = doc.quantity + sign * doc.step
            dao.updateQuantity(docId, newQty)
        }
    }
    suspend fun delete(doc: DocumentEntity) { dao.delete(doc) }
    suspend fun getAll() = dao.getAll(folderRepo.currentFolderId()).map { it.migrate() }
    suspend fun deleteAll() { dao.deleteAll() }
    suspend fun deleteAllChanges() { dao.deleteAllChanges() }

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
}