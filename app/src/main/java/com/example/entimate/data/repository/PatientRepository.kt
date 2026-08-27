package com.example.entimate.data.repository

import android.util.Log
import androidx.room.withTransaction
import com.example.entimate.data.local.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map

class PatientRepository(private val db: AppDatabase) {
    private val patientDao = db.patientDao()
    private val documentDao = db.documentDao()

    val effectLog = MutableSharedFlow<String>(extraBufferCapacity = 16)

    val patientsFlow: Flow<List<PatientWithValues>> =
        patientDao.observeAll().map { list -> list.map { pw -> pw.copy(patient = pw.patient.migrate()) } }
    val customFieldsFlow: Flow<List<PatientCustomFieldEntity>> =
        patientDao.observeCustomFields().map { list -> list.map { it.migrate() } }
    val documentsFlow: Flow<List<DocumentEntity>> =
        documentDao.observeAll().map { list -> list.map { it.migrate() } }

    suspend fun getAllDocuments() = documentDao.getAll().map { it.migrate() }
    suspend fun getAllLinks() = patientDao.getAllLinks()
    suspend fun getLinks(sourceKey: String) = patientDao.getLinks(sourceKey)
    suspend fun getPatient(id: Long) = patientDao.getWithValues(id)?.let { it.copy(patient = it.patient.migrate()) }

    suspend fun savePatient(patient: PatientEntity, customValues: Map<Long, String>): Long = db.withTransaction {
        val toSave = if (patient.id == 0L) patient.copy(sortOrder = patientDao.getMaxSortOrder() + 1) else patient
        val id = if (toSave.id == 0L) {
            patientDao.insertPatient(toSave)
        } else {
            revertEffects(toSave.id, recordStats = true)
            patientDao.updatePatient(toSave)
            toSave.id
        }
        patientDao.deleteCustomValues(id)
        customValues.forEach { (fid, value) ->
            if (value.isNotBlank()) {
                patientDao.insertCustomValue(PatientCustomValueEntity(patientId = id, fieldId = fid, value = value))
            }
        }
        applyEffects(id, recordStats = true)
        id
    }

    suspend fun deletePatient(patient: PatientEntity) = db.withTransaction {
        revertEffects(patient.id, recordStats = true)
        patientDao.deletePatient(patient)
    }

    suspend fun dischargePatient(patient: PatientEntity) = db.withTransaction {
        patientDao.updatePatient(patient.copy(discharged = 1))
    }

    suspend fun reregisterPatient(old: PatientEntity, admissionDate: String): Long = db.withTransaction {
        patientDao.updatePatient(old.copy(discharged = 1))
        val oldValues = patientDao.getCustomValues(old.id)
        val fresh = old.copy(
            id = 0,
            admissionDate = admissionDate,
            illnessStart = admissionDate,
            referredBy = "",
            discharged = 0,
            createdAt = System.currentTimeMillis(),
            sortOrder = patientDao.getMaxSortOrder() + 1,
            version = CURRENT_DATA_VERSION,
        )
        val id = patientDao.insertPatient(fresh)
        oldValues.forEach { v -> patientDao.insertCustomValue(v.copy(id = 0, patientId = id)) }
        applyEffects(id, recordStats = true)
        id
    }

    suspend fun saveCustomField(f: PatientCustomFieldEntity): Long {
        val id = if (f.id != 0L) { patientDao.updateCustomField(f); f.id } else patientDao.insertCustomField(f)
        recomputeAllEffects()
        return id
    }

    suspend fun deleteCustomField(f: PatientCustomFieldEntity) = db.withTransaction {
        patientDao.deleteValuesForField(f.id)
        patientDao.deleteCustomField(f)
        recomputeAllEffects()
    }

    suspend fun saveLink(link: PatientFieldLinkEntity): Long = db.withTransaction {
        val id = patientDao.insertLink(link)
        recomputeAllEffects()
        id
    }

    suspend fun deleteLink(link: PatientFieldLinkEntity) = db.withTransaction {
        patientDao.deleteLink(link)
        recomputeAllEffects()
    }

    suspend fun reorder(orderedIds: List<Long>) = db.withTransaction {
        orderedIds.forEachIndexed { index, id -> patientDao.setSortOrder(id, index) }
    }

    suspend fun recomputeAllEffects() = db.withTransaction {
        val all = patientDao.getAllPatientsWithValues()
        all.forEach { p ->
            revertEffects(p.patient.id)
            applyEffects(p.patient.id)
        }
    }

    suspend fun syncEffectRecords() = db.withTransaction {
        val all = patientDao.getAllPatientsWithValues()
        for (p in all) {
            patientDao.deleteEffects(p.patient.id)
            val cvMap = p.customValues.associate { it.fieldId to it.value }
            val links = patientDao.getAllLinks()
            val docIds = links.map { it.documentId }.toSet()
            for (docId in docIds) {
                val net = links.filter { it.documentId == docId }.sumOf { link -> effectFor(link, p.patient, cvMap) }
                if (net != 0) {
                    patientDao.insertEffect(PatientDocumentEffectEntity(patientId = p.patient.id, documentId = docId, netDelta = net))
                }
            }
        }
    }

    private fun valueFor(p: PatientEntity, customValues: Map<Long, String>, key: String): String {
        if (isCustomKey(key)) return customValues[customFieldIdFromKey(key)] ?: ""
        return patientValue(p, key)
    }

    private suspend fun applyEffects(patientId: Long, recordStats: Boolean = false) {
        val p = patientDao.getWithValues(patientId) ?: return
        val cvMap = p.customValues.associate { it.fieldId to it.value }
        val links = patientDao.getAllLinks()
        val docIds = links.map { it.documentId }.toSet()
        var totalNet = 0
        for (docId in docIds) {
            val net = links.filter { it.documentId == docId }.sumOf { link -> effectFor(link, p.patient, cvMap) }
            totalNet += net
            if (net != 0) {
                val d = documentDao.getById(docId)
                val oldQty = d?.quantity ?: 0
                documentDao.addQuantity(docId, net)
                patientDao.insertEffect(PatientDocumentEffectEntity(patientId = patientId, documentId = docId, netDelta = net))
                if (recordStats) {
                    documentDao.insertChange(
                        DocumentChangeEntity(
                            documentId = docId,
                            timestamp = System.currentTimeMillis(),
                            delta = net,
                            qtyAfter = oldQty + net,
                            patientId = patientId,
                        )
                    )
                }
                effectLog.tryEmit("Связь «${d?.name ?: "#$docId"}»: ${if (net > 0) "+" else ""}$net")
            }
            Log.d("ENT", "applyEffects patient=$patientId doc=$docId net=$net")
        }
        if (totalNet == 0 && links.isNotEmpty()) {
            effectLog.tryEmit("Связи: ни одно условие не совпало — счётчик не изменён")
        }
    }

    private fun effectFor(link: PatientFieldLinkEntity, p: PatientEntity, cvMap: Map<Long, String>): Int {
        val sign = if (link.operation == "INCREASE") link.amount else -link.amount
        return when {
            link.sourceKey == PATIENT_GLOBAL_KEY -> sign
            link.conditionValue.isBlank() -> sign
            valueFor(p, cvMap, link.sourceKey) == link.conditionValue -> sign
            else -> 0
        }
    }

    private suspend fun revertEffects(patientId: Long, recordStats: Boolean = false) {
        val effects = patientDao.getEffects(patientId)
        for (e in effects) {
            documentDao.addQuantity(e.documentId, -e.netDelta)
            if (recordStats) {
                val qty = documentDao.getById(e.documentId)?.quantity ?: 0
                documentDao.insertChange(
                    DocumentChangeEntity(
                        documentId = e.documentId,
                        timestamp = System.currentTimeMillis(),
                        delta = -e.netDelta,
                        qtyAfter = qty,
                        patientId = patientId,
                    )
                )
            }
        }
        patientDao.deleteEffects(patientId)
    }
}
