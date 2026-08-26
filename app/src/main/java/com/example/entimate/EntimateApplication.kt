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
    val documentRepository by lazy { DocumentRepository(database.documentDao()) }
    val formRepository by lazy { FormRepository(database) }
    val settingsDataStore by lazy { SettingsDataStore(this) }
    val backupRepository by lazy { BackupRepository(database, settingsDataStore) }
    val reportRepository by lazy { ReportRepository(database) }
    val patientRepository by lazy { PatientRepository(database) }

    override fun onCreate() {
        super.onCreate()
    }
}
