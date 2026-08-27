package com.example.entimate.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PatientDao {
    @Transaction
    @Query("SELECT * FROM patients ORDER BY sortOrder ASC, number ASC")
    fun observeAll(): Flow<List<PatientWithValues>>

    @Transaction
    @Query("SELECT * FROM patients ORDER BY sortOrder ASC, number ASC")
    suspend fun getAllPatients(): List<PatientEntity>

    @Transaction
    @Query("SELECT * FROM patients")
    suspend fun getAllPatientsWithValues(): List<PatientWithValues>

    @Transaction
    @Query("SELECT * FROM patients WHERE id = :id")
    suspend fun getWithValues(id: Long): PatientWithValues?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPatient(p: PatientEntity): Long

    @Update
    suspend fun updatePatient(p: PatientEntity)

    @Delete
    suspend fun deletePatient(p: PatientEntity)

    @Query("SELECT * FROM patient_custom_fields ORDER BY position ASC")
    suspend fun getAllCustomFields(): List<PatientCustomFieldEntity>

    @Query("SELECT * FROM patient_custom_fields ORDER BY position ASC")
    fun observeCustomFields(): Flow<List<PatientCustomFieldEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomField(f: PatientCustomFieldEntity): Long

    @Update
    suspend fun updateCustomField(f: PatientCustomFieldEntity)

    @Delete
    suspend fun deleteCustomField(f: PatientCustomFieldEntity)

    @Query("DELETE FROM patient_custom_values WHERE fieldId = :fieldId")
    suspend fun deleteValuesForField(fieldId: Long)

    @Query("SELECT * FROM patient_custom_values WHERE patientId = :patientId")
    suspend fun getCustomValues(patientId: Long): List<PatientCustomValueEntity>

    @Query("SELECT * FROM patient_custom_values")
    suspend fun getAllCustomValues(): List<PatientCustomValueEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomValue(v: PatientCustomValueEntity)

    @Query("DELETE FROM patient_custom_values WHERE patientId = :patientId")
    suspend fun deleteCustomValues(patientId: Long)

    @Query("SELECT * FROM patient_field_links")
    suspend fun getAllLinks(): List<PatientFieldLinkEntity>

    @Query("SELECT * FROM patient_field_links WHERE sourceKey = :sourceKey")
    suspend fun getLinks(sourceKey: String): List<PatientFieldLinkEntity>

    @Query("SELECT * FROM patient_field_links WHERE documentId = :docId")
    suspend fun getLinksForDocument(docId: Long): List<PatientFieldLinkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLink(l: PatientFieldLinkEntity): Long

    @Update
    suspend fun updateLink(l: PatientFieldLinkEntity)

    @Delete
    suspend fun deleteLink(l: PatientFieldLinkEntity)

    @Query("DELETE FROM patient_field_links WHERE sourceKey = :sourceKey")
    suspend fun deleteLinks(sourceKey: String)

    @Query("SELECT * FROM patient_document_effects WHERE patientId = :patientId")
    suspend fun getEffects(patientId: Long): List<PatientDocumentEffectEntity>

    @Query("SELECT MIN(createdAt) FROM patients")
    suspend fun getEarliestCreatedAt(): Long?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM patients")
    suspend fun getMaxSortOrder(): Int

    @Query("UPDATE patients SET sortOrder = :order WHERE id = :id")
    suspend fun setSortOrder(id: Long, order: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEffect(e: PatientDocumentEffectEntity)

    @Query("DELETE FROM patient_document_effects WHERE patientId = :patientId")
    suspend fun deleteEffects(patientId: Long)

    @Query("DELETE FROM patient_document_effects")
    suspend fun clearEffects()

    @Query("SELECT * FROM patient_document_effects ORDER BY id ASC")
    suspend fun getAllEffects(): List<PatientDocumentEffectEntity>
}
