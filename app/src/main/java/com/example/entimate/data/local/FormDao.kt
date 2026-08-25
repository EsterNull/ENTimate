package com.example.entimate.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FormDao {
    @Transaction
    @Query("SELECT * FROM forms ORDER BY name ASC")
    fun observeAllWithDetails(): Flow<List<FormWithDetails>>

    @Transaction
    @Query("SELECT * FROM forms WHERE id = :id")
    suspend fun getWithDetails(id: Long): FormWithDetails?

    @Query("SELECT * FROM forms WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): FormEntity?

    @Transaction
    @Query("SELECT * FROM forms WHERE id = :id")
    suspend fun getWithFieldLinks(id: Long): FormWithFieldLinks?

    @Query("SELECT * FROM forms")
    suspend fun getAll(): List<FormEntity>

    @Query("SELECT * FROM form_fields")
    suspend fun getAllFields(): List<FormFieldEntity>

    @Query("SELECT * FROM form_links")
    suspend fun getAllLinks(): List<FormLinkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertForm(form: FormEntity): Long

    @Update
    suspend fun updateForm(form: FormEntity)

    @Delete
    suspend fun deleteForm(form: FormEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertField(field: FormFieldEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLink(link: FormLinkEntity): Long

    @Query("DELETE FROM form_fields WHERE formId = :formId")
    suspend fun deleteFieldsForForm(formId: Long)

    @Query("DELETE FROM form_links WHERE fieldId IN (SELECT id FROM form_fields WHERE formId = :formId)")
    suspend fun deleteLinksForForm(formId: Long)

    @Query("DELETE FROM forms")
    suspend fun deleteAllForms()

    @Query("DELETE FROM form_fields")
    suspend fun deleteAllFields()

    @Query("DELETE FROM form_links")
    suspend fun deleteAllLinks()
}
