package com.example.entimate.data.repository

import androidx.room.withTransaction
import com.example.entimate.data.local.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PatientRepository(private val db: AppDatabase) {
    private val patientDao = db.patientDao()
    private val documentDao = db.documentDao()

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
        val id = if (patient.id == 0L) {
            patientDao.insertPatient(patient)
        } else {
            revertEffects(patient.id)
            patientDao.updatePatient(patient)
            patient.id
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
        revertEffects(patient.id)
        patientDao.deletePatient(patient)
    }

    suspend fun dischargePatient(patient: PatientEntity) = db.withTransaction {
        patientDao.updatePatient(patient.copy(discharged = 1))
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

    suspend fun recomputeAllEffects() = db.withTransaction {
        val all = patientDao.getAllPatientsWithValues()
        all.forEach { p ->
            revertEffects(p.patient.id)
            applyEffects(p.patient.id)
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
        for (docId in docIds) {
            val net = links.filter { it.documentId == docId }.sumOf { link ->
                when {
                    link.sourceKey == PATIENT_GLOBAL_KEY -> if (link.operation == "INCREASE") link.amount else -link.amount
                    valueFor(p.patient, cvMap, link.sourceKey) == link.conditionValue -> if (link.operation == "INCREASE") link.amount else -link.amount
                    else -> 0
                }
            }
            if (net != 0) {
                val oldQty = documentDao.getById(docId)?.quantity ?: 0
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
            }
        }
    }

    private suspend fun revertEffects(patientId: Long) {
        val effects = patientDao.getEffects(patientId)
        for (e in effects) documentDao.addQuantity(e.documentId, -e.netDelta)
        patientDao.deleteEffects(patientId)
    }
}
