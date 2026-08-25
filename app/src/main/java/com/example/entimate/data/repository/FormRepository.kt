package com.example.entimate.data.repository

import androidx.room.withTransaction
import com.example.entimate.data.local.*
import kotlinx.coroutines.flow.Flow

class FormRepository(private val db: AppDatabase) {
    private val formDao = db.formDao()
    private val documentDao = db.documentDao()
    private val submissionDao = db.submissionDao()

    val formsWithDetailsFlow: Flow<List<FormWithDetails>> = formDao.observeAllWithDetails()
    val documentsFlow: Flow<List<DocumentEntity>> = documentDao.observeAll()

    suspend fun saveForm(
        form: FormEntity,
        fields: List<FormFieldWithLinks>,
    ): Long = db.withTransaction {
        val formId = if (form.id == 0L) {
            formDao.insertForm(form)
        } else {
            formDao.updateForm(form)
            form.id
        }
        formDao.deleteFieldsForForm(formId)
        formDao.deleteLinksForForm(formId)
        val idMap = mutableMapOf<Long, Long>()
        fields.forEach { fwl ->
            val realFieldId = formDao.insertField(fwl.field.copy(id = 0, formId = formId))
            idMap[fwl.field.id] = realFieldId
            fwl.links.forEach { link ->
                val condFieldId = if (link.conditionFieldId == 0L) 0L else (idMap[link.conditionFieldId] ?: 0L)
                val dynDocFieldId = if (link.dynamicDocFromFieldId == 0L) 0L else (idMap[link.dynamicDocFromFieldId] ?: 0L)
                val amtFieldId = if (link.amountFromFieldId == 0L) 0L else (idMap[link.amountFromFieldId] ?: 0L)
                formDao.insertLink(link.copy(
                    id = 0,
                    fieldId = realFieldId,
                    conditionFieldId = condFieldId,
                    dynamicDocFromFieldId = dynDocFieldId,
                    amountFromFieldId = amtFieldId,
                ))
            }
        }
        formId
    }

    suspend fun getFormWithDetails(id: Long) = formDao.getWithFieldLinks(id)
    suspend fun deleteForm(form: FormEntity) = formDao.deleteForm(form)
    suspend fun getAllForms() = formDao.getAll()
    suspend fun getAllFields() = formDao.getAllFields()
    suspend fun getAllLinks() = formDao.getAllLinks()
    suspend fun deleteAllForms() = formDao.deleteAllForms()
    suspend fun deleteAllFields() = formDao.deleteAllFields()
    suspend fun deleteAllLinks() = formDao.deleteAllLinks()

    suspend fun duplicateForm(formId: Long): Long = db.withTransaction {
        val src = formDao.getWithFieldLinks(formId) ?: return@withTransaction 0L
        val newFormId = formDao.insertForm(src.form.copy(id = 0, name = src.form.name + " (копия)"))
        val idMap = mutableMapOf<Long, Long>()
        src.fields.forEach { fwl ->
            val newFieldId = formDao.insertField(fwl.field.copy(id = 0, formId = newFormId))
            idMap[fwl.field.id] = newFieldId
            fwl.links.forEach { link ->
                val remap = { id: Long -> if (id == 0L) 0L else (idMap[id] ?: 0L) }
                formDao.insertLink(link.copy(
                    id = 0,
                    fieldId = newFieldId,
                    conditionFieldId = remap(link.conditionFieldId),
                    dynamicDocFromFieldId = remap(link.dynamicDocFromFieldId),
                    amountFromFieldId = remap(link.amountFromFieldId),
                ))
            }
        }
        newFormId
    }

    private fun conditionMet(
        link: FormLinkEntity,
        fieldById: Map<Long, FormFieldEntity>,
        values: Map<Long, String>,
    ): Boolean {
        if (link.conditionFieldId == 0L) return true
        val condField = fieldById[link.conditionFieldId] ?: return true
        val condVal = values[link.conditionFieldId] ?: ""
        return when (condField.type) {
            "SWITCH" -> (condVal.toBooleanStrictOrNull() ?: false) == (link.conditionValue == "true")
            "DROPDOWN" -> condVal == link.conditionValue
            "CHECKBOX_LIST" -> {
                val required = link.conditionValue.split(",").map { it.trim() }.filter { it.isNotBlank() }
                val selected = condVal.split(",").map { it.trim() }.filter { it.isNotBlank() }
                required.all { selected.contains(it) }
            }
            "NUMBER" -> {
                val av = condVal.toDoubleOrNull() ?: return false
                val cv = link.conditionValue.toDoubleOrNull() ?: return false
                when (link.conditionOperator) {
                    "GT" -> av > cv
                    "LT" -> av < cv
                    "EQ" -> av == cv
                    "GTE" -> av >= cv
                    "LTE" -> av <= cv
                    else -> false
                }
            }
            else -> true
        }
    }

    suspend fun applyForm(formId: Long, values: Map<Long, String> = emptyMap()) = db.withTransaction {
        val details = formDao.getWithFieldLinks(formId) ?: return@withTransaction
        val fieldById = details.fields.associateBy({ it.field.id }, { it.field })

        val submissionId = submissionDao.insertSubmission(
            SubmissionEntity(formId = formId, timestamp = System.currentTimeMillis()),
        )
        values.forEach { (fieldId, value) ->
            submissionDao.insertSubmissionValue(
                SubmissionFieldValueEntity(submissionId = submissionId, fieldId = fieldId, value = value),
            )
        }

        for (fwl in details.fields) {
            val type = fwl.field.type
            val apply: Boolean
            val overrideAmount: Int? = when (type) {
                "CHECKBOX", "SWITCH" -> {
                    apply = values[fwl.field.id]?.toBooleanStrictOrNull() ?: false
                    null
                }
                "NUMBER" -> {
                    apply = true
                    values[fwl.field.id]?.toIntOrNull()
                }
                else -> {
                    apply = true
                    null
                }
            }
            if (!apply) continue
            for (link in fwl.links) {
                if (!conditionMet(link, fieldById, values)) continue
                val targetDocId = if (link.dynamicDocFromFieldId != 0L) {
                    val sel = values[link.dynamicDocFromFieldId] ?: ""
                    documentDao.getByName(sel)?.id ?: continue
                } else link.documentId
                val doc = documentDao.getById(targetDocId) ?: continue
                val amount = if (link.amountFromFieldId != 0L) {
                    values[link.amountFromFieldId]?.toIntOrNull() ?: 0
                } else (overrideAmount ?: link.amount)
                if (amount == 0) continue
                val delta = if (link.operation == "INCREASE") amount else -amount
                val newQ = (doc.quantity + delta).coerceAtLeast(0)
                documentDao.updateQuantity(doc.id, newQ)
            }
        }
    }
}
