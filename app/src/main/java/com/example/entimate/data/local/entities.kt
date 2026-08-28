package com.example.entimate.data.local

import androidx.room.*

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String,
    val colorArgb: Int,
    val quantity: Int,
    val step: Int = 1,
    val sortOrder: Int = 0,
    override var version: Int = CURRENT_DATA_VERSION,
    override var extras: String = "",
) : Versioned

@Entity(tableName = "reports")
data class ReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String,
    val colorArgb: Int = 0,
    val kind: String = "DOCUMENTS",
    val sortOrder: Int = 0,
    val marginTopMm: Float = 25.4f,
    val marginRightMm: Float = 25.4f,
    val marginBottomMm: Float = 25.4f,
    val marginLeftMm: Float = 25.4f,
    override var version: Int = CURRENT_DATA_VERSION,
    override var extras: String = "",
) : Versioned

@Entity(
    tableName = "report_columns",
    foreignKeys = [ForeignKey(entity = ReportEntity::class, parentColumns = ["id"], childColumns = ["reportId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["reportId"])],
)
data class ReportColumnEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reportId: Long,
    val fieldKey: String = "",
    val sourceFieldKeys: String = "",
    val joinSeparator: String = " ",
    val label: String = "",
    val trueText: String = "",
    val falseText: String = "",
    val position: Int = 0,
    val align: String = "LEFT",
    val dropdownMap: String = "",
    val hideValues: Int = 0,
)

@Entity(
    tableName = "report_filters",
    foreignKeys = [ForeignKey(entity = ReportEntity::class, parentColumns = ["id"], childColumns = ["reportId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["reportId"])],
)
data class ReportFilterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reportId: Long,
    val position: Int = 0,
    val connector: String = "AND",
    val fieldKey: String,
    val operator: String,
    val value: String = "",
)

@Entity(tableName = "document_changes")
data class DocumentChangeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val timestamp: Long,
    val delta: Int,
    val qtyAfter: Int,
    val patientId: Long = 0,
)

@Entity(tableName = "patients")
data class PatientEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val number: Int,
    val personalNumber: String = "",
    val lastName: String,
    val firstName: String,
    val middleName: String = "",
    val birthDate: String = "",
    val sex: String = "М",
    val idSeries: String = "",
    val idNumber: String = "",
    val serviceDate: String = "",
    val rvk: String = "",
    val rank: String = "Рядовой",
    val unit: String = "",
    val position: String = "",
    val admissionDate: String = "",
    val referredBy: String = "",
    val emergency: String = "Нет",
    val illnessStart: String = "",
    val category: String = "по призыву",
    val svo: Int = 0,
    val soch: Int = 0,
    val discharged: Int = 0,
    val colorArgb: Int = 0,
    val createdAt: Long = 0L,
    val sortOrder: Int = 0,
    override var version: Int = CURRENT_DATA_VERSION,
    override var extras: String = "",
) : Versioned

@Entity(tableName = "patient_custom_fields")
data class PatientCustomFieldEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val type: String,
    val options: String = "",
    val defaultValue: String = "",
    val position: Int = 0,
    override var version: Int = CURRENT_DATA_VERSION,
    override var extras: String = "",
) : Versioned

@Entity(
    tableName = "patient_custom_values",
    foreignKeys = [ForeignKey(entity = PatientEntity::class, parentColumns = ["id"], childColumns = ["patientId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["patientId"]), Index(value = ["fieldId"])],
)
data class PatientCustomValueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patientId: Long,
    val fieldId: Long,
    val value: String = "",
)

@Entity(
    tableName = "patient_field_links",
    foreignKeys = [ForeignKey(entity = DocumentEntity::class, parentColumns = ["id"], childColumns = ["documentId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["documentId"])],
)
data class PatientFieldLinkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceKey: String,
    val conditionValue: String = "",
    val documentId: Long,
    val operation: String,
    val amount: Int,
)

@Entity(
    tableName = "patient_document_effects",
    foreignKeys = [
        ForeignKey(entity = PatientEntity::class, parentColumns = ["id"], childColumns = ["patientId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = DocumentEntity::class, parentColumns = ["id"], childColumns = ["documentId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["patientId"]), Index(value = ["documentId"])],
)
data class PatientDocumentEffectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patientId: Long,
    val documentId: Long,
    val netDelta: Int,
)

data class PatientWithValues(
    @Embedded val patient: PatientEntity,
    @Relation(parentColumn = "id", entityColumn = "patientId") val customValues: List<PatientCustomValueEntity> = emptyList(),
)

data class ReportWithColumns(
    @Embedded val report: ReportEntity,
    @Relation(parentColumn = "id", entityColumn = "reportId") val columns: List<ReportColumnEntity> = emptyList(),
)

data class ReportWithFilters(
    @Embedded val report: ReportEntity,
    @Relation(parentColumn = "id", entityColumn = "reportId") val columns: List<ReportColumnEntity> = emptyList(),
    @Relation(parentColumn = "id", entityColumn = "reportId") val filters: List<ReportFilterEntity> = emptyList(),
)

@Entity(
    tableName = "report_paragraphs",
    foreignKeys = [ForeignKey(entity = ReportEntity::class, parentColumns = ["id"], childColumns = ["reportId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["reportId"])],
)
data class ReportParagraphEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reportId: Long,
    val position: Int = 0,
    val font: String = "Times New Roman",
    val align: String = "LEFT",
    val indentLeftMm: Float = 0f,
    val indentRightMm: Float = 0f,
    val firstLineMm: Float = 0f,
    val lineSpacing: Float = 1f,
    val spaceBeforeMm: Float = 0f,
    val spaceAfterMm: Float = 0f,
)

@Entity(
    tableName = "report_doc_elements",
    foreignKeys = [ForeignKey(entity = ReportParagraphEntity::class, parentColumns = ["id"], childColumns = ["paragraphId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["paragraphId"])],
)
data class ReportDocElementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val paragraphId: Long,
    val position: Int = 0,
    val type: String = "TEXT",
    val text: String = "",
    val bold: Int = 0,
    val italic: Int = 0,
    val underline: Int = 0,
    val size: Int = 0,
    val colorArgb: Int = 0,
    val bgArgb: Int = 0,
    val embeddedReportId: Long = 0,
    val embeddedTitle: String = "",
    val align: String = "LEFT",
    val minRows: Int = 0,
    val numberColumn: Int = 0,
    val joinPrevious: Int = 0,
    val border: Int = 1,
    val colWeights: String = "",
)

data class ReportParagraphWithElements(
    @Embedded val paragraph: ReportParagraphEntity,
    @Relation(parentColumn = "id", entityColumn = "paragraphId", entity = ReportDocElementEntity::class) val elements: List<ReportDocElementEntity> = emptyList(),
)

data class ReportWithDocument(
    @Embedded val report: ReportEntity,
    @Relation(parentColumn = "id", entityColumn = "reportId", entity = ReportParagraphEntity::class) val paragraphs: List<ReportParagraphWithElements> = emptyList(),
)

@Entity(tableName = "forms")
data class FormEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String,
    val colorArgb: Int,
)

@Entity(
    tableName = "form_fields",
    foreignKeys = [ForeignKey(entity = FormEntity::class, parentColumns = ["id"], childColumns = ["formId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["formId"])],
)
data class FormFieldEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val formId: Long,
    val type: String,
    val label: String = "",
    val position: Int = 0,
    val options: String = "",
    val required: Int = 0,
    val defaultValue: String = "",
)

@Entity(
    tableName = "form_links",
    foreignKeys = [
        ForeignKey(entity = FormFieldEntity::class, parentColumns = ["id"], childColumns = ["fieldId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = DocumentEntity::class, parentColumns = ["id"], childColumns = ["documentId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["fieldId"]), Index(value = ["documentId"])],
)
data class FormLinkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fieldId: Long,
    val documentId: Long,
    val operation: String,
    val amount: Int,
    val conditionFieldId: Long = 0,
    val conditionOperator: String = "",
    val conditionValue: String = "",
    val dynamicDocFromFieldId: Long = 0,
    val amountFromFieldId: Long = 0,
)

@Entity(tableName = "submissions")
data class SubmissionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val formId: Long,
    val timestamp: Long,
)

@Entity(
    tableName = "submission_values",
    foreignKeys = [ForeignKey(entity = SubmissionEntity::class, parentColumns = ["id"], childColumns = ["submissionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["submissionId"])],
)
data class SubmissionFieldValueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val submissionId: Long,
    val fieldId: Long,
    val value: String = "",
)

data class FormFieldWithLinks(
    @Embedded val field: FormFieldEntity,
    @Relation(parentColumn = "id", entityColumn = "fieldId") val links: List<FormLinkEntity> = emptyList(),
)

data class FormWithDetails(
    @Embedded val form: FormEntity,
    @Relation(parentColumn = "id", entityColumn = "formId", entity = FormFieldEntity::class) val fields: List<FormFieldEntity> = emptyList(),
)

data class FormWithFieldLinks(
    @Embedded val form: FormEntity,
    @Relation(parentColumn = "id", entityColumn = "formId", entity = FormFieldEntity::class) val fields: List<FormFieldWithLinks>,
)

data class SubmissionWithValues(
    @Embedded val submission: SubmissionEntity,
    @Relation(parentColumn = "id", entityColumn = "submissionId") val values: List<SubmissionFieldValueEntity> = emptyList(),
)
