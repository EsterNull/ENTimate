package com.example.entimate.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

data class FolderDocTotal(val folderId: Long, val total: Int)
data class FolderPatientCount(val folderId: Long, val count: Int)

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders ORDER BY sortOrder ASC, id ASC")
    suspend fun getAll(): List<FolderEntity>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getById(id: Long): FolderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(folder: FolderEntity): Long

    @Update
    suspend fun update(folder: FolderEntity)

    @Delete
    suspend fun delete(folder: FolderEntity)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM folders")
    suspend fun deleteAll()

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM folders")
    suspend fun getMaxOrder(): Int

    @Query("SELECT COUNT(*) FROM folders")
    suspend fun getCount(): Int

    @Query("UPDATE folders SET sortOrder = :order WHERE id = :id")
    suspend fun setOrder(id: Long, order: Int)

    @Query("SELECT folderId, COALESCE(SUM(quantity), 0) AS total FROM documents GROUP BY folderId")
    fun observeFolderDocTotals(): Flow<List<FolderDocTotal>>

    @Query("SELECT folderId, COUNT(*) AS count FROM patients WHERE discharged != 1 GROUP BY folderId")
    fun observeFolderPatientCounts(): Flow<List<FolderPatientCount>>
}