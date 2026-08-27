package com.example.entimate.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ReportDao {
    @Transaction
    @Query("SELECT * FROM reports ORDER BY sortOrder ASC, name ASC")
    fun observeAll(): Flow<List<ReportWithColumns>>

    @Transaction
    @Query("SELECT * FROM reports WHERE id = :id")
    suspend fun getWithColumns(id: Long): ReportWithColumns?

    @Transaction
    @Query("SELECT * FROM reports WHERE id = :id")
    suspend fun getWithFilters(id: Long): ReportWithFilters?

    @Query("SELECT * FROM reports WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): ReportEntity?

    @Query("SELECT * FROM reports")
    suspend fun getAll(): List<ReportEntity>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM reports")
    suspend fun getMaxSortOrder(): Int

    @Query("UPDATE reports SET sortOrder = :order WHERE id = :id")
    suspend fun setSortOrder(id: Long, order: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReport(report: ReportEntity): Long

    @Update
    suspend fun updateReport(report: ReportEntity)

    @Delete
    suspend fun deleteReport(report: ReportEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertColumn(column: ReportColumnEntity)

    @Query("DELETE FROM report_columns WHERE reportId = :reportId")
    suspend fun deleteColumnsForReport(reportId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFilter(filter: ReportFilterEntity)

    @Query("DELETE FROM report_filters WHERE reportId = :reportId")
    suspend fun deleteFiltersForReport(reportId: Long)

    @Query("SELECT * FROM report_filters WHERE reportId = :reportId ORDER BY position ASC")
    suspend fun getFilters(reportId: Long): List<ReportFilterEntity>

    @Query("DELETE FROM reports")
    suspend fun deleteAll()

    @Query("SELECT * FROM report_columns")
    suspend fun getAllColumns(): List<ReportColumnEntity>

    @Query("SELECT * FROM report_filters")
    suspend fun getAllFilters(): List<ReportFilterEntity>

    @Transaction
    @Query("SELECT * FROM reports WHERE id = :id")
    suspend fun getWithDocument(id: Long): ReportWithDocument?

    @Query("SELECT * FROM report_paragraphs WHERE reportId = :reportId ORDER BY position ASC")
    suspend fun getParagraphs(reportId: Long): List<ReportParagraphEntity>

    @Query("SELECT * FROM report_doc_elements WHERE paragraphId = :paragraphId ORDER BY position ASC")
    suspend fun getElements(paragraphId: Long): List<ReportDocElementEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParagraph(p: ReportParagraphEntity): Long

    @Update
    suspend fun updateParagraph(p: ReportParagraphEntity)

    @Delete
    suspend fun deleteParagraph(p: ReportParagraphEntity)

    @Query("DELETE FROM report_paragraphs WHERE reportId = :reportId")
    suspend fun deleteParagraphsForReport(reportId: Long)

    @Query("DELETE FROM report_paragraphs")
    suspend fun deleteAllParagraphs()

    @Query("DELETE FROM report_doc_elements")
    suspend fun deleteAllElements()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertElement(e: ReportDocElementEntity): Long

    @Update
    suspend fun updateElement(e: ReportDocElementEntity)

    @Delete
    suspend fun deleteElement(e: ReportDocElementEntity)

    @Query("DELETE FROM report_doc_elements WHERE paragraphId = :paragraphId")
    suspend fun deleteElementsForParagraph(paragraphId: Long)

    @Query("DELETE FROM report_doc_elements WHERE paragraphId IN (SELECT id FROM report_paragraphs WHERE reportId = :reportId)")
    suspend fun deleteElementsForReport(reportId: Long)

    @Query("SELECT * FROM reports WHERE kind != 'DOCUMENT' ORDER BY sortOrder ASC, name ASC")
    suspend fun getTableReports(): List<ReportEntity>

    @Query("SELECT * FROM report_columns WHERE reportId = :reportId ORDER BY position ASC")
    suspend fun getColumnsForReport(reportId: Long): List<ReportColumnEntity>
}
