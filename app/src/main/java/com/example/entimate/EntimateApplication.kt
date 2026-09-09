package com.example.entimate

import android.app.Application
import com.example.entimate.data.local.AppDatabase
import com.example.entimate.data.repository.*
import com.example.entimate.settings.SettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class EntimateApplication : Application() {
    val database by lazy { AppDatabase.build(this) }
    val settingsDataStore by lazy { SettingsDataStore(this) }
    val folderRepository by lazy { FolderRepository(database, settingsDataStore) }
    val documentRepository by lazy { DocumentRepository(database.documentDao(), folderRepository) }
    val formRepository by lazy { FormRepository(database, folderRepository) }
    val backupRepository by lazy { BackupRepository(database, settingsDataStore, folderRepository) }
    val reportRepository by lazy { ReportRepository(database, folderRepository) }
    val patientRepository by lazy { PatientRepository(database, folderRepository) }

    override fun onCreate() {
        super.onCreate()
    }
}
