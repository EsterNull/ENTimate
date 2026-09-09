package com.example.entimate.data.repository

import android.util.Log
import androidx.room.withTransaction
import com.example.entimate.data.local.*
import kotlin.comparisons.compareBy
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
    val templatesFlow: Flow<List<PatientTemplateEntity>> =
        folderRepo.currentFolderIdFlow.flatMapLatest { fid ->
            patientDao.observeTemplates(fid)
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
        folderRepo.refresh()
        id
    }

    suspend fun deletePatient(patient: PatientEntity) = db.withTransaction {
        revertEffects(patient.id, recordStats = true)
        patientDao.deletePatient(patient)
        folderRepo.refresh()
    }

    suspend fun dischargePatient(patient: PatientEntity) = db.withTransaction {
        patientDao.updatePatient(patient.copy(discharged = 1, dischargeDate = todayIso()))
        folderRepo.refresh()
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
        folderRepo.refresh()
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

    suspend fun saveTemplate(name: String, payload: PatientTemplatePayload): Long =
        patientDao.insertTemplate(
            PatientTemplateEntity(
                name = name,
                folderId = folderRepo.currentFolderId(),
                payload = encodePatientTemplatePayload(payload),
                createdAt = System.currentTimeMillis(),
            )
        )

    suspend fun updateTemplate(id: Long, name: String, payload: PatientTemplatePayload) {
        val existing = patientDao.getTemplate(id) ?: return
        patientDao.updateTemplate(
            existing.copy(
                name = name,
                payload = encodePatientTemplatePayload(payload),
            )
        )
    }

    suspend fun renameTemplate(t: PatientTemplateEntity, newName: String) =
        patientDao.updateTemplate(t.copy(name = newName))

    suspend fun duplicateTemplate(t: PatientTemplateEntity): Long =
        patientDao.insertTemplate(
            t.copy(id = 0, folderId = folderRepo.currentFolderId(), createdAt = System.currentTimeMillis())
        )

    suspend fun deleteTemplate(t: PatientTemplateEntity) = patientDao.deleteTemplate(t)

    suspend fun templatePayload(id: Long): PatientTemplatePayload? =
        patientDao.getTemplate(id)?.let { decodePatientTemplatePayload(it.payload) }

    suspend fun getAllTemplates() = patientDao.getAllTemplates()

    suspend fun deleteLink(link: PatientFieldLinkEntity) = db.withTransaction {
        patientDao.deleteLink(link)
        recomputeAllEffects()
    }

    suspend fun reorder(from: Int, to: Int) {
        val all = patientDao.getAllPatientsWithValues(folderRepo.currentFolderId())
            .sortedWith(compareBy<PatientWithValues> { it.patient.sortOrder }.thenBy { it.patient.number })
        val active = all.filter { it.patient.discharged != 1 }
        val discharged = all.filter { it.patient.discharged == 1 }
        if (from !in active.indices || to !in active.indices) return
        val ids = active.map { it.patient.id }.toMutableList()
        val id = ids.removeAt(from)
        ids.add(to, id)
        db.withTransaction {
            (ids + discharged.map { it.patient.id }).forEachIndexed { index, pid ->
                patientDao.setSortOrder(pid, index)
            }
        }
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
            val cvMap = resolveComputedValues(
                patientDao.getAllCustomFields(p.patient.folderId),
                p.customValues.associate { it.fieldId to it.value },
                p.patient.number.toString(),
            )
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
        val cvMap = resolveComputedValues(
            patientDao.getAllCustomFields(p.patient.folderId),
            p.customValues.associate { it.fieldId to it.value },
            p.patient.number.toString(),
        )
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
        val amount = if (link.amountFieldKey.isBlank()) {
            link.amount
        } else {
            valueFor(p, cvMap, link.amountFieldKey).trim().replace(',', '.').toDoubleOrNull()?.toInt() ?: 0
        }
        if (amount <= 0) return 0
        val sign = if (link.operation == "INCREASE") amount else -amount
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