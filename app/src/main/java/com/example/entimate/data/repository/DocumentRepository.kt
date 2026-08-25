package com.example.entimate.data.repository

import com.example.entimate.data.local.DocumentChangeEntity
import com.example.entimate.data.local.DocumentDao
import com.example.entimate.data.local.DocumentEntity
import com.example.entimate.data.local.migrate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DocumentRepository(private val dao: DocumentDao) {
    val allDocumentsFlow: Flow<List<DocumentEntity>> = dao.observeAll().map { list -> list.map { it.migrate() } }

    suspend fun getById(id: Long) = dao.getById(id)?.migrate()
    suspend fun insert(doc: DocumentEntity) = dao.insert(doc)
    suspend fun update(doc: DocumentEntity) = dao.update(doc)
    suspend fun adjust(docId: Long, delta: Int) = dao.addQuantity(docId, delta)
    suspend fun delete(doc: DocumentEntity) = dao.delete(doc)
    suspend fun getAll() = dao.getAll().map { it.migrate() }
    suspend fun deleteAll() = dao.deleteAll()

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
