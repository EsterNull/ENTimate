package com.example.entimate.data.repository

import androidx.room.withTransaction
import com.example.entimate.data.local.*
import com.example.entimate.settings.SettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class FolderSummary(val docs: Int = 0, val patients: Int = 0)

/** Single atomic snapshot of what the folder pill should display. */
data class FolderBarState(val name: String = "Папки", val docs: Int = 0, val patients: Int = 0)

/**
 * Restarts a shared flow after a transient upstream error so an Eagerly-shared
 * StateFlow cannot get stuck forever on its initial empty value.
 */
private fun <T> Flow<T>.restartOnError(): Flow<T> =
    retryWhen { _, _ -> delay(1000); true }

class FolderRepository(
    private val db: AppDatabase,
    private val settings: SettingsDataStore,
) {
    private val folderDao = db.folderDao()
    private val documentDao = db.documentDao()
    private val patientDao = db.patientDao()
    private val reportDao = db.reportDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Bumping this counter re-subscribes every shared DB flow from scratch so a
     * stale collection (e.g. after importing a backup in the same session) is
     * discarded instead of surviving until the next process start.
     */
    private val refreshTrigger = MutableStateFlow(0)

    fun refresh() {
        refreshTrigger.value++
    }

    val foldersFlow: StateFlow<List<FolderEntity>> =
        refreshTrigger.flatMapLatest {
            folderDao.observeAll()
                .restartOnError()
                .map { list -> list.map { it.migrate() } }
        }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /**
     * The currently selected folder id, clamped to an existing folder so the app
     * always has a valid target (e.g. after importing a backup).
     */
    val currentFolderIdFlow: StateFlow<Long> =
        combine(settings.currentFolderIdFlow(), foldersFlow) { id, folders ->
            if (folders.any { it.id == id }) id else folders.firstOrNull()?.id ?: 1L
        }
            .restartOnError()
            .stateIn(scope, SharingStarted.Eagerly, 1L)

    val summariesFlow: StateFlow<Map<Long, FolderSummary>> =
        refreshTrigger.flatMapLatest {
            combine(
                folderDao.observeFolderDocTotals(),
                folderDao.observeFolderPatientCounts(),
            ) { totals, counts ->
                val docs = totals.associate { it.folderId to it.total }
                val patients = counts.associate { it.folderId to it.count }
                (docs.keys + patients.keys).associateWith {
                    FolderSummary(docs[it] ?: 0, patients[it] ?: 0)
                }
            }
                .restartOnError()
        }
            .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    /**
     * A single combined snapshot for the folder pill. Resolving the name, counts
     * and the selected id from one flow prevents the pill from showing stale
     * defaults when the underlying DB flows are mid-transaction during an import.
     */
    val barStateFlow: StateFlow<FolderBarState> =
        combine(foldersFlow, currentFolderIdFlow, summariesFlow) { folders, currentId, summaries ->
            val current = folders.firstOrNull { it.id == currentId } ?: folders.firstOrNull()
            FolderBarState(
                name = current?.name?.takeIf { it.isNotBlank() } ?: "Папки",
                docs = current?.let { summaries[it.id]?.docs ?: 0 } ?: 0,
                patients = current?.let { summaries[it.id]?.patients ?: 0 } ?: 0,
            )
        }
            .restartOnError()
            .stateIn(scope, SharingStarted.Eagerly, FolderBarState())

    suspend fun currentFolderId(): Long = currentFolderIdFlow.first()

    suspend fun getById(id: Long): FolderEntity? = folderDao.getById(id)?.migrate()

    suspend fun save(folder: FolderEntity): Long {
        val id = if (folder.id == 0L) {
            folderDao.insert(folder.copy(sortOrder = folderDao.getMaxOrder() + 1))
        } else {
            folderDao.update(folder)
            folder.id
        }
        return id
    }

    suspend fun selectFolder(id: Long) {
        settings.setCurrentFolderId(id)
    }

    suspend fun hasDuplicateName(name: String, excludeId: Long = 0L): Boolean {
        val clean = name.trim().lowercase()
        return folderDao.getAll().any { it.id != excludeId && it.name.trim().lowercase() == clean }
    }

    suspend fun reorder(from: Int, to: Int) {
        val ids = foldersFlow.value.map { it.id }.toMutableList()
        if (from !in ids.indices || to !in ids.indices) return
        val id = ids.removeAt(from)
        ids.add(to, id)
        assignOrders(ids)
    }

    suspend fun assignOrders(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id -> folderDao.setOrder(id, index) }
    }

    suspend fun deleteFolder(id: Long) {
        db.withTransaction {
            if (folderDao.getCount() <= 1) return@withTransaction
            val current = currentFolderId()
            patientDao.deleteValuesForFieldsInFolder(id)
            patientDao.deleteCustomFieldsForFolder(id)
            patientDao.deleteLinksForFolder(id)
            patientDao.deleteTemplatesForFolder(id)
            documentDao.deleteForFolder(id)
            patientDao.deleteForFolder(id)
            reportDao.deleteForFolder(id)
            folderDao.deleteById(id)
            if (current == id) {
                val first = folderDao.getAll().firstOrNull() ?: return@withTransaction
                settings.setCurrentFolderId(first.id)
            }
        }
    }

    /**
     * Deep-copies everything inside [id] into a new folder named "<name> (копия)".
     * Ids are remapped carefully so links, embeddings and synced effects keep
     * pointing at the copied rows (never at the originals).
     */
    suspend fun duplicateFolder(id: Long): Long = db.withTransaction {
        val src = folderDao.getById(id) ?: return@withTransaction 0L
        val newId = folderDao.insert(
            src.copy(id = 0, name = "${src.name} (копия)", sortOrder = folderDao.getMaxOrder() + 1, version = CURRENT_DATA_VERSION)
        )

        val docMap = documentDao.getForFolder(id).associate { old ->
            val new = documentDao.insert(old.copy(id = 0, folderId = newId))
            old.id to new
        }
        docMap.forEach { (oldDoc, newDoc) ->
            documentDao.getChanges(oldDoc).forEach { ch ->
                documentDao.insertChange(ch.copy(id = 0, documentId = newDoc))
            }
        }

        val fieldMap = patientDao.getCustomFieldsForFolder(id).associate { old ->
            val new = patientDao.insertCustomField(old.copy(id = 0, folderId = newId))
            old.id to new
        }

        val patientMap = mutableMapOf<Long, Long>()
        patientDao.getAllPatientsWithValues(id).forEach { pw ->
            val newP = patientDao.insertPatient(pw.patient.copy(id = 0, folderId = newId))
            patientMap[pw.patient.id] = newP
            pw.customValues.forEach { v ->
                val targetField = fieldMap[v.fieldId]
                if (targetField != null) {
                    patientDao.insertCustomValue(v.copy(id = 0, patientId = newP, fieldId = targetField))
                }
            }
            patientDao.getEffects(pw.patient.id).forEach { e ->
                val targetDoc = docMap[e.documentId]
                if (targetDoc != null) {
                    patientDao.insertEffect(e.copy(id = 0, patientId = newP, documentId = targetDoc))
                }
            }
        }

        patientDao.getAllLinks().filter { docMap.containsKey(it.documentId) }.forEach { link ->
            patientDao.insertLink(link.copy(id = 0, documentId = docMap.getValue(link.documentId), folderId = newId))
        }

        patientDao.getAllTemplates(id).forEach { t ->
            patientDao.insertTemplate(t.copy(id = 0, folderId = newId))
        }

        val reportMap = reportDao.getForFolder(id).associate { old ->
            val new = reportDao.insertReport(old.copy(id = 0, folderId = newId))
            old.id to new
        }
        reportMap.forEach { (oldReport, newReport) ->
            reportDao.getColumnsForReport(oldReport).forEach { c ->
                reportDao.insertColumn(c.copy(id = 0, reportId = newReport))
            }
            reportDao.getFilters(oldReport).forEach { f ->
                reportDao.insertFilter(f.copy(id = 0, reportId = newReport))
            }
            reportDao.getParagraphs(oldReport).forEach { p ->
                val newPar = reportDao.insertParagraph(p.copy(id = 0, reportId = newReport))
                reportDao.getElements(p.id).forEach { e ->
                    val embedded = if (e.embeddedReportId == 0L) 0L
                    else reportMap[e.embeddedReportId] ?: e.embeddedReportId
                    reportDao.insertElement(e.copy(id = 0, paragraphId = newPar, embeddedReportId = embedded))
                }
            }
        }
        newId
    }
}