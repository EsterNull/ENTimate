package com.example.entimate.data.repository

import android.util.Log
import androidx.room.withTransaction
import com.example.entimate.data.local.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun todayIso(): String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

@OptIn(ExperimentalCoroutinesApi::class)
class PatientRepository(
    private val db: AppDatabase,
    private val folderRepo: FolderRepository,
) {
    private val patientDao = db.patientDao()
    private val documentDao = db.documentDao()

    val effectLog = MutableSharedFlow<String>(extraBufferCapacity = 16)

    val patientsFlow: Flow<List<PatientWithValues>> =
        folderRepo.currentFolderIdFlow.flatMapLatest { fid ->
            patientDao.observeAll(fid).map { list -> list.map { pw -> pw.copy(patient = pw.patient.migrate()) } }
        }
    val customFieldsFlow: Flow<List<PatientCustomFieldEntity>> =
        folderRepo.currentFolderIdFlow.flatMapLatest { fid ->
            patientDao.observeCustomFields(fid).map { list -> list.map { it.migrate() } }
        }
    val documentsFlow: Flow<List<DocumentEntity>> =
        folderRepo.currentFolderIdFlow.flatMapLatest { fid ->
            documentDao.observeAll(fid).map { list -> list.map { it.migrate() } }
        }
    val linksFlow: Flow<List<PatientFieldLinkEntity>> =
        folderRepo.currentFolderIdFlow.flatMapLatest { fid ->
            patientDao.observeLinksForFolder(fid)
        }

    suspend fun getAllDocuments() = documentDao.getAll(folderRepo.currentFolderId()).map { it.migrate() }
    suspend fun getAllLinks() = patientDao.getAllLinksForFolder(folderRepo.currentFolderId())
    suspend fun getLinks(sourceKey: String) = patientDao.getLinks(sourceKey)
    suspend fun getPatient(id: Long) = patientDao.getWithValues(id)?.let { it.copy(patient = it.patient.migrate()) }

    suspend fun savePatient(patient: PatientEntity, customValues: Map<Long, String>): Long = db.withTransaction {
        val folder = if (patient.id == 0L) folderRepo.currentFolderId() else patient.folderId
        val toSave = if (patient.id == 0L) {
            patient.copy(folderId = folder, sortOrder = patientDao.getMaxSortOrder(folder) + 1)
        } else {
            patient
        }
        val id = if (toSave.id == 0L) {
            patientDao.insertPatient(toSave)
        } else {
            patientDao.updatePatient(toSave)
            toSave.id
        }
        patientDao.deleteCustomValues(id)
        customValues.forEach { (fid, value) ->
            if (value.isNotBlank()) {
                patientDao.insertCustomValue(PatientCustomValueEntity(patientId = id, fieldId = fid, value = value))
            }
        }
        syncEffects(id, recordStats = true)
        id
    }

    suspend fun deletePatient(patient: PatientEntity) = db.withTransaction {
        revertEffects(patient.id, recordStats = true)
        patientDao.deletePatient(patient)
    }

    suspend fun dischargePatient(patient: PatientEntity) = db.withTransaction {
        patientDao.updatePatient(patient.copy(discharged = 1, dischargeDate = todayIso()))
    }

    suspend fun reregisterPatient(old: PatientEntity, admissionDate: String, newNumber: Int? = null): Long = db.withTransaction {
        patientDao.updatePatient(old.copy(discharged = 1, dischargeDate = todayIso()))
        val fresh = old.copy(
            id = 0,
            folderId = old.folderId,
            number = newNumber ?: old.number,
            admissionDate = admissionDate,
            illnessStart = admissionDate,
            referredBy = "",
            discharged = 0,
            dischargeDate = "",
            createdAt = System.currentTimeMillis(),
            sortOrder = patientDao.getMaxSortOrder(old.folderId) + 1,
            version = CURRENT_DATA_VERSION,
        )
        val id = patientDao.insertPatient(fresh)
        syncEffects(id, recordStats = true)
        id
    }

    suspend fun saveCustomField(f: PatientCustomFieldEntity): Long {
        val toSave = if (f.id == 0L) f.copy(folderId = folderRepo.currentFolderId()) else f
        val id = if (toSave.id != 0L) { patientDao.updateCustomField(toSave); toSave.id } else patientDao.insertCustomField(toSave)
        recomputeAllEffects()
        return id
    }

    suspend fun deleteCustomField(f: PatientCustomFieldEntity) = db.withTransaction {
        patientDao.deleteValuesForField(f.id)
        patientDao.deleteCustomField(f)
        recomputeAllEffects()
    }

    suspend fun reorderFields(orderedIds: List<Long>) = db.withTransaction {
        orderedIds.forEachIndexed { index, id -> patientDao.setFieldPosition(id, index) }
    }

    suspend fun saveLink(link: PatientFieldLinkEntity): Long = db.withTransaction {
        val toSave = if (link.id == 0L) link.copy(folderId = folderRepo.currentFolderId()) else link
        val id = patientDao.insertLink(toSave)
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
            syncEffects(p.patient.id)
        }
    }

    suspend fun syncEffectRecords() = db.withTransaction {
        val all = patientDao.getAllPatientsWithValues()
        for (p in all) {
            patientDao.deleteEffects(p.patient.id)
            val links = folderLinksFor(p.patient)
            val cvMap = p.customValues.associate { it.fieldId to it.value }
            val docIds = links.map { it.documentId }.toSet()
            for (docId in docIds) {
                val net = links.filter { it.documentId == docId }.sumOf { link -> effectFor(link, p.patient, cvMap) }
                if (net != 0) {
                    patientDao.insertEffect(PatientDocumentEffectEntity(patientId = p.patient.id, documentId = docId, netDelta = net))
                }
            }
        }
    }

    /**
     * Only links belonging to the patient's folder may affect that patient —
     * folders are isolated workspaces.
     */
    private suspend fun folderLinksFor(p: PatientEntity): List<PatientFieldLinkEntity> =
        patientDao.getAllLinksForFolder(p.folderId)

    private fun valueFor(p: PatientEntity, customValues: Map<Long, String>, key: String): String {
        if (isCustomKey(key)) return customValues[customFieldIdFromKey(key)] ?: ""
        return patientValue(p, key)
    }

    private fun netsFor(links: List<PatientFieldLinkEntity>, p: PatientEntity, cvMap: Map<Long, String>): Map<Long, Int> {
        val docIds = links.map { it.documentId }.distinct()
        return docIds.associateWith { docId ->
            links.filter { it.documentId == docId }.sumOf { link -> effectFor(link, p, cvMap) }
        }
    }

    /**
     * Brings the patient's linked-document effects in sync with the current field values:
     * applies only the difference between the desired net and the already-stored net for
     * each document. When nothing relevant changed, nothing is applied and no statistics
     * entry is created.
     */
    private suspend fun syncEffects(patientId: Long, recordStats: Boolean = false) {
        val p = patientDao.getWithValues(patientId) ?: return
        val cvMap = p.customValues.associate { it.fieldId to it.value }
        val links = folderLinksFor(p.patient)
        val newNets = netsFor(links, p.patient, cvMap)
        val oldEffects = patientDao.getEffects(patientId).associate { it.documentId to it.netDelta }
        val docIds = (newNets.keys + oldEffects.keys).toSet()
        for (docId in docIds) {
            val newNet = newNets[docId] ?: 0
            val oldNet = oldEffects[docId] ?: 0
            val delta = newNet - oldNet
            if (delta != 0) {
                val d = documentDao.getById(docId)
                val oldQty = d?.quantity ?: 0
                documentDao.addQuantity(docId, delta)
                if (recordStats) {
                    documentDao.insertChange(
                        DocumentChangeEntity(
                            documentId = docId,
                            timestamp = System.currentTimeMillis(),
                            delta = delta,
                            qtyAfter = oldQty + delta,
                            patientId = patientId,
                        )
                    )
                }
                effectLog.tryEmit("Связь «${d?.name ?: "#$docId"}»: ${if (delta > 0) "+" else ""}$delta")
                Log.d("ENT", "syncEffects patient=$patientId doc=$docId delta=$delta")
            }
        }
        patientDao.deleteEffects(patientId)
        newNets.forEach { (docId, net) ->
            if (net != 0) {
                patientDao.insertEffect(PatientDocumentEffectEntity(patientId = patientId, documentId = docId, netDelta = net))
            }
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