package com.example.entimate.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents ORDER BY name ASC")
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

    @Query("UPDATE documents SET quantity = MAX(0, quantity + :delta) WHERE id = :docId")
    suspend fun addQuantity(docId: Long, delta: Int)

    @Query("DELETE FROM documents")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChange(change: DocumentChangeEntity)

    @Query("SELECT * FROM document_changes WHERE documentId = :docId ORDER BY timestamp ASC")
    suspend fun getChanges(docId: Long): List<DocumentChangeEntity>

    @Query("SELECT * FROM document_changes WHERE documentId = :docId ORDER BY timestamp ASC")
    fun observeChanges(docId: Long): Flow<List<DocumentChangeEntity>>

    @Query("SELECT COALESCE(SUM(delta), 0) FROM document_changes WHERE documentId = :docId AND timestamp BETWEEN :from AND :to")
    suspend fun getPeriodDelta(docId: Long, from: Long, to: Long): Int
}
