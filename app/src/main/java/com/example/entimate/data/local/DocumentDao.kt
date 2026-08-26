package com.example.entimate.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents ORDER BY sortOrder ASC, name ASC")
    fun observeAll(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents")
    suspend fun getAll(): List<DocumentEntity>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getById(id: Long): DocumentEntity?

    @Query("SELECT * FROM documents WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): DocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(doc: DocumentEntity): Long

    @Update
    suspend fun update(doc: DocumentEntity)

    @Delete
    suspend fun delete(doc: DocumentEntity)

    @Query("UPDATE documents SET quantity = :quantity WHERE id = :id")
    suspend fun updateQuantity(id: Long, quantity: Int)

    @Transaction
    suspend fun addQuantity(docId: Long, delta: Int) {
        val doc = getById(docId) ?: return
        update(doc.copy(quantity = doc.quantity + delta))
    }

    @Query("DELETE FROM documents")
    suspend fun deleteAll()

    @Query("DELETE FROM document_changes")
    suspend fun deleteAllChanges()

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM documents")
    suspend fun getMaxOrder(): Int

    @Query("UPDATE documents SET sortOrder = :order WHERE id = :id")
    suspend fun setOrder(id: Long, order: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChange(change: DocumentChangeEntity)

    @Query("SELECT * FROM document_changes WHERE documentId = :docId ORDER BY timestamp ASC")
    suspend fun getChanges(docId: Long): List<DocumentChangeEntity>

    @Query("SELECT * FROM document_changes WHERE documentId = :docId ORDER BY timestamp ASC")
    fun observeChanges(docId: Long): Flow<List<DocumentChangeEntity>>

    @Query("SELECT COALESCE(SUM(delta), 0) FROM document_changes WHERE documentId = :docId AND timestamp BETWEEN :from AND :to")
    suspend fun getPeriodDelta(docId: Long, from: Long, to: Long): Int
}
