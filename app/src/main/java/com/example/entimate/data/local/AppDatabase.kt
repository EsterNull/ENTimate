package com.example.entimate.data.local

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Database(
    entities = [
        DocumentEntity::class,
        FormEntity::class,
        FormFieldEntity::class,
        FormLinkEntity::class,
        SubmissionEntity::class,
        SubmissionFieldValueEntity::class,
        ReportEntity::class,
        ReportColumnEntity::class,
        ReportFilterEntity::class,
        DocumentChangeEntity::class,
        PatientEntity::class,
        PatientCustomFieldEntity::class,
        PatientCustomValueEntity::class,
        PatientFieldLinkEntity::class,
        PatientDocumentEffectEntity::class,
    ],
    version = 14,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
    abstract fun formDao(): FormDao
    abstract fun submissionDao(): SubmissionDao
    abstract fun reportDao(): ReportDao
    abstract fun patientDao(): PatientDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MILITARY_RANKS = listOf(
            "Рядовой", "Ефрейтор", "Младший сержант", "Сержант", "Старший сержант", "Старшина",
            "Прапорщик", "Старший прапорщик",
            "Младший лейтенант", "Лейтенант", "Старший лейтенант", "Капитан", "Майор", "Подполковник", "Полковник",
            "Генерал-майор", "Генерал-лейтенант", "Генерал-полковник", "Генерал армии", "Маршал Российской Федерации",
            "Матрос", "Старший матрос", "Старшина 2 статьи", "Старшина 1 статьи", "Главный старшина", "Главный корабельный старшина",
            "Мичман", "Старший мичман",
            "Капитан-лейтенант", "Капитан 3 ранга", "Капитан 2 ранга", "Капитан 1 ранга",
            "Контр-адмирал", "Вице-адмирал", "Адмирал", "Адмирал флота",
        )

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reports ADD COLUMN kind TEXT NOT NULL DEFAULT 'DOCUMENTS'")
                db.execSQL("UPDATE reports SET kind='FORMS' WHERE id IN (SELECT DISTINCT reportId FROM report_columns)")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS report_columns")
                db.execSQL(
                    """CREATE TABLE report_columns (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        reportId INTEGER NOT NULL,
                        fieldKey TEXT NOT NULL,
                        sourceFieldKeys TEXT NOT NULL,
                        joinSeparator TEXT NOT NULL,
                        label TEXT NOT NULL,
                        position INTEGER NOT NULL,
                        FOREIGN KEY(reportId) REFERENCES reports(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_report_columns_reportId ON report_columns(reportId)")

                db.execSQL(
                    """CREATE TABLE report_filters (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        reportId INTEGER NOT NULL,
                        position INTEGER NOT NULL,
                        connector TEXT NOT NULL,
                        fieldKey TEXT NOT NULL,
                        operator TEXT NOT NULL,
                        value TEXT NOT NULL,
                        FOREIGN KEY(reportId) REFERENCES reports(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_report_filters_reportId ON report_filters(reportId)")

                db.execSQL(
                    """CREATE TABLE patients (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        number INTEGER NOT NULL,
                        lastName TEXT NOT NULL,
                        firstName TEXT NOT NULL,
                        middleName TEXT NOT NULL,
                        birthDate TEXT NOT NULL,
                        sex TEXT NOT NULL,
                        idSeries TEXT NOT NULL,
                        idNumber TEXT NOT NULL,
                        serviceDate TEXT NOT NULL,
                        rvk TEXT NOT NULL,
                        rank TEXT NOT NULL,
                        unit TEXT NOT NULL,
                        position TEXT NOT NULL,
                        admissionDate TEXT NOT NULL,
                        referredBy TEXT NOT NULL,
                        emergency TEXT NOT NULL,
                        illnessStart TEXT NOT NULL,
                        category TEXT NOT NULL,
                        svo INTEGER NOT NULL,
                        soch INTEGER NOT NULL,
                        colorArgb INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )"""
                )

                db.execSQL(
                    """CREATE TABLE patient_custom_fields (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        label TEXT NOT NULL,
                        type TEXT NOT NULL,
                        options TEXT NOT NULL,
                        defaultValue TEXT NOT NULL,
                        position INTEGER NOT NULL
                    )"""
                )

                db.execSQL(
                    """CREATE TABLE patient_custom_values (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        patientId INTEGER NOT NULL,
                        fieldId INTEGER NOT NULL,
                        value TEXT NOT NULL,
                        FOREIGN KEY(patientId) REFERENCES patients(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_patient_custom_values_patientId ON patient_custom_values(patientId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_patient_custom_values_fieldId ON patient_custom_values(fieldId)")

                db.execSQL(
                    """CREATE TABLE patient_field_links (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        sourceKey TEXT NOT NULL,
                        conditionValue TEXT NOT NULL,
                        documentId INTEGER NOT NULL,
                        operation TEXT NOT NULL,
                        amount INTEGER NOT NULL,
                        FOREIGN KEY(documentId) REFERENCES documents(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_patient_field_links_documentId ON patient_field_links(documentId)")

                db.execSQL(
                    """CREATE TABLE patient_document_effects (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        patientId INTEGER NOT NULL,
                        documentId INTEGER NOT NULL,
                        netDelta INTEGER NOT NULL,
                        FOREIGN KEY(patientId) REFERENCES patients(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(documentId) REFERENCES documents(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_patient_document_effects_patientId ON patient_document_effects(patientId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_patient_document_effects_documentId ON patient_document_effects(documentId)")

                db.execSQL("DELETE FROM reports WHERE kind IN ('FORMS','DOCUMENTS')")
                seedClinicalData(db)
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE report_columns ADD COLUMN sourceFieldKeys TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE report_columns ADD COLUMN joinSeparator TEXT NOT NULL DEFAULT ' '")
                db.execSQL("ALTER TABLE report_columns ADD COLUMN label TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE report_columns SET sourceFieldKeys = fieldKey WHERE sourceFieldKeys = '' OR sourceFieldKeys IS NULL")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE documents ADD COLUMN version INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE documents ADD COLUMN extras TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE patients ADD COLUMN version INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE patients ADD COLUMN extras TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE patient_custom_fields ADD COLUMN version INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE patient_custom_fields ADD COLUMN extras TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE reports ADD COLUMN version INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE reports ADD COLUMN extras TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE patients ADD COLUMN personalNumber TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE report_columns ADD COLUMN trueText TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE report_columns ADD COLUMN falseText TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE document_changes ADD COLUMN patientId INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE patients ADD COLUMN discharged INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE documents ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE patients ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE reports ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun build(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "entimate.db"
                )                    .addMigrations(MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14)
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            seedDatabase(db)
                        }

                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            val c = db.query("SELECT COUNT(*) FROM documents")
                            c.moveToFirst()
                            val count = c.getInt(0)
                            c.close()
                            if (count == 0) seedDatabase(db)
                        }
                    })
                    .build().also { INSTANCE = it }
            }

        private fun defaultVisibleFieldKeys(): List<String> =
            PATIENT_FIELDS.filter { it.defaultVisible }.map { it.key }

        private fun seedClinicalData(db: SupportSQLiteDatabase) {
            val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val today = dateFmt.format(Date())

                fun insertReport(name: String, desc: String, color: Int): Long {
                    val cv = ContentValues().apply {
                        put("name", name); put("description", desc); put("colorArgb", color); put("sortOrder", 0)
                        put("kind", "DOCUMENTS"); put("version", CURRENT_DATA_VERSION); put("extras", "")
                    }
                    return db.insert("reports", SQLiteDatabase.CONFLICT_IGNORE, cv)
                }
            fun insertCol(reportId: Long, fieldKey: String, position: Int): Long {
                val cv = ContentValues().apply {
                    put("reportId", reportId)
                    put("fieldKey", fieldKey)
                    put("sourceFieldKeys", fieldKey)
                    put("joinSeparator", " ")
                    put("label", "")
                    put("position", position)
                    put("trueText", ""); put("falseText", "")
                }
                return db.insert("report_columns", SQLiteDatabase.CONFLICT_IGNORE, cv)
            }
            fun documentIdByName(name: String): Long {
                val c = db.query("SELECT id FROM documents WHERE name = ?", arrayOf(name))
                val id = if (c.moveToFirst()) c.getLong(0) else 0L
                c.close()
                return id
            }
            fun insertLink(sourceKey: String, conditionValue: String, docId: Long, operation: String, amount: Int): Long {
                val cv = ContentValues().apply {
                    put("sourceKey", sourceKey); put("conditionValue", conditionValue)
                    put("documentId", docId); put("operation", operation); put("amount", amount)
                }
                return db.insert("patient_field_links", SQLiteDatabase.CONFLICT_IGNORE, cv)
            }

            // Сводка по пациентам (видимые поля)
            val rp = insertReport("Сводка по пациентам", "Таблица пациентов с выбранными полями", 0)
            defaultVisibleFieldKeys().forEachIndexed { idx, key -> insertCol(rp, key, idx) }

            // Связь: включённый СВО уменьшает «Анкеты СВО» на 1
            val svoDoc = documentIdByName("Анкеты СВО")
            if (svoDoc != 0L) insertLink("svo", "true", svoDoc, "DECREASE", 1)
        }

        private fun seedDatabase(db: SupportSQLiteDatabase) {
            db.beginTransaction()
            try {
                val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val today = dateFmt.format(Date())

                var docOrder = 0
                fun insertDoc(name: String, desc: String, color: Int, qty: Int, step: Int = 1): Long {
                    val cv = ContentValues().apply {
                        put("name", name); put("description", desc); put("colorArgb", color); put("quantity", qty); put("step", step); put("sortOrder", docOrder++)
                        put("version", CURRENT_DATA_VERSION); put("extras", "")
                    }
                    return db.insert("documents", SQLiteDatabase.CONFLICT_IGNORE, cv)
                }

                insertDoc("Анкеты СВО", "", 0, 0)
                insertDoc("Выписной эпикриз (Г)", "Габибуллаев", 0, 0)
                insertDoc("Лист назначений", "", 0, 0)
                insertDoc("Запись осмотра лечащим врачом", "", 0, 0)
                insertDoc("Лечебная пункция", "", 0, 0)
                insertDoc("Переведён", "", 0, 0)
                insertDoc("Жалобы на насморк", "", 0xFFE5484D.toInt(), 0)
                insertDoc("Обход начальника ЛОР отделения", "", 0xFFFF9800.toInt(), 0)
                insertDoc("Восстановление", "", 0xFF7DCFFF.toInt(), 0)
                insertDoc("Самочувствие хорошее", "", 0xFF9ECE6A.toInt(), 0)

                seedClinicalData(db)
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }
}
