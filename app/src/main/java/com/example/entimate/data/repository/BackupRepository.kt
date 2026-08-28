package com.example.entimate.data.repository

import android.util.Log
import androidx.room.withTransaction
import com.example.entimate.data.local.*
import com.example.entimate.settings.SettingsDataStore
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

class BackupRepository(private val db: AppDatabase, private val settings: SettingsDataStore) {

    suspend fun exportJson(): String = db.withTransaction {
        val s = settings.settingsFlow.first()
        val docs = db.documentDao().getAll()
        val forms = db.formDao().getAll()
        val fields = db.formDao().getAllFields()
        val links = db.formDao().getAllLinks()
        val submissions = db.submissionDao().getWithValuesInPeriod(0L, Long.MAX_VALUE)
        val reports = db.reportDao().getAll()
        val columns = db.reportDao().getAllColumns()
        val filters = db.reportDao().getAllFilters()
        val patients = db.patientDao().getAllPatients()
        val customFields = db.patientDao().getAllCustomFields()
        val customValues = db.patientDao().getAllCustomValues()
        val patientLinks = db.patientDao().getAllLinks()
        val changes = db.documentDao().getAllChanges()
        val effects = db.patientDao().getAllEffects()

        val root = JSONObject()
        root.put("version", 3)
        root.put("documents", JSONArray(docs.map {
            JSONObject().apply {
                put("id", it.id)
                put("name", it.name)
                put("description", it.description)
                put("colorArgb", it.colorArgb)
                put("quantity", it.quantity)
                put("step", it.step)
            }
        }))
        root.put("forms", JSONArray(forms.map {
            JSONObject().apply {
                put("id", it.id)
                put("name", it.name)
                put("description", it.description)
                put("colorArgb", it.colorArgb)
            }
        }))
        root.put("formFields", JSONArray(fields.map {
            JSONObject().apply {
                put("id", it.id)
                put("formId", it.formId)
                put("type", it.type)
                put("label", it.label)
                put("position", it.position)
                put("options", it.options)
                put("required", it.required)
                put("defaultValue", it.defaultValue)
            }
        }))
        root.put("formLinks", JSONArray(links.map {
            JSONObject().apply {
                put("id", it.id)
                put("fieldId", it.fieldId)
                put("documentId", it.documentId)
                put("operation", it.operation)
                put("amount", it.amount)
                put("conditionFieldId", it.conditionFieldId)
                put("conditionOperator", it.conditionOperator)
                put("conditionValue", it.conditionValue)
                put("dynamicDocFromFieldId", it.dynamicDocFromFieldId)
                put("amountFromFieldId", it.amountFromFieldId)
            }
        }))
        root.put("submissions", JSONArray(submissions.map {
            JSONObject().apply {
                put("id", it.submission.id)
                put("formId", it.submission.formId)
                put("timestamp", it.submission.timestamp)
                put("values", JSONArray(it.values.map { v ->
                    JSONObject().apply {
                        put("id", v.id)
                        put("fieldId", v.fieldId)
                        put("value", v.value)
                    }
                }))
            }
        }))
        root.put("reports", JSONArray(reports.map {
            JSONObject().apply {
                put("id", it.id)
                put("name", it.name)
                put("description", it.description)
                put("colorArgb", it.colorArgb)
                put("kind", it.kind)
                put("sortOrder", it.sortOrder)
                put("marginTopMm", it.marginTopMm)
                put("marginRightMm", it.marginRightMm)
                put("marginBottomMm", it.marginBottomMm)
                put("marginLeftMm", it.marginLeftMm)
            }
        }))
        root.put("reportColumns", JSONArray(columns.map {
            JSONObject().apply {
                put("id", it.id)
                put("reportId", it.reportId)
                put("fieldKey", it.fieldKey)
                put("sourceFieldKeys", it.sourceFieldKeys)
                put("joinSeparator", it.joinSeparator)
                put("label", it.label)
                put("trueText", it.trueText)
                put("falseText", it.falseText)
                put("position", it.position)
                put("align", it.align)
                put("dropdownMap", it.dropdownMap)
                put("hideValues", it.hideValues)
            }
        }))
        root.put("reportFilters", JSONArray(filters.map {
            JSONObject().apply {
                put("id", it.id)
                put("reportId", it.reportId)
                put("position", it.position)
                put("connector", it.connector)
                put("fieldKey", it.fieldKey)
                put("operator", it.operator)
                put("value", it.value)
            }
        }))
        val reportParagraphs = mutableListOf<JSONObject>()
        val reportElements = mutableListOf<JSONObject>()
        for (r in reports) {
            for (p in db.reportDao().getParagraphs(r.id)) {
                reportParagraphs.add(JSONObject().apply {
                    put("id", p.id)
                    put("reportId", p.reportId)
                    put("position", p.position)
                    put("font", p.font)
                    put("align", p.align)
                    put("indentLeftMm", p.indentLeftMm)
                    put("indentRightMm", p.indentRightMm)
                    put("firstLineMm", p.firstLineMm)
                    put("lineSpacing", p.lineSpacing)
                    put("spaceBeforeMm", p.spaceBeforeMm)
                    put("spaceAfterMm", p.spaceAfterMm)
                })
                for (e in db.reportDao().getElements(p.id)) {
                    reportElements.add(JSONObject().apply {
                        put("id", e.id)
                        put("paragraphId", e.paragraphId)
                        put("position", e.position)
                        put("type", e.type)
                        put("text", e.text)
                        put("bold", e.bold)
                        put("italic", e.italic)
                        put("underline", e.underline)
                        put("size", e.size)
                        put("colorArgb", e.colorArgb)
                        put("bgArgb", e.bgArgb)
                        put("embeddedReportId", e.embeddedReportId)
                        put("embeddedTitle", e.embeddedTitle)
                        put("align", e.align)
                        put("minRows", e.minRows)
                        put("numberColumn", e.numberColumn)
                        put("joinPrevious", e.joinPrevious)
                        put("border", e.border)
                        put("colWeights", e.colWeights)
                    })
                }
            }
        }
        root.put("reportParagraphs", JSONArray(reportParagraphs))
        root.put("reportElements", JSONArray(reportElements))
        root.put("patients", JSONArray(patients.map {
            JSONObject().apply {
                put("id", it.id)
                put("number", it.number)
                put("lastName", it.lastName)
                put("firstName", it.firstName)
                put("middleName", it.middleName)
                put("birthDate", it.birthDate)
                put("sex", it.sex)
                put("idSeries", it.idSeries)
                put("idNumber", it.idNumber)
                put("serviceDate", it.serviceDate)
                put("rvk", it.rvk)
                put("rank", it.rank)
                put("unit", it.unit)
                put("position", it.position)
                put("admissionDate", it.admissionDate)
                put("referredBy", it.referredBy)
                put("emergency", it.emergency)
                put("illnessStart", it.illnessStart)
                put("category", it.category)
                put("svo", it.svo)
                put("soch", it.soch)
                put("personalNumber", it.personalNumber)
                put("discharged", it.discharged)
                put("colorArgb", it.colorArgb)
                put("createdAt", it.createdAt)
            }
        }))
        root.put("patientCustomFields", JSONArray(customFields.map {
            JSONObject().apply {
                put("id", it.id)
                put("label", it.label)
                put("type", it.type)
                put("options", it.options)
                put("defaultValue", it.defaultValue)
                put("position", it.position)
            }
        }))
        root.put("patientCustomValues", JSONArray(customValues.map {
            JSONObject().apply {
                put("id", it.id)
                put("patientId", it.patientId)
                put("fieldId", it.fieldId)
                put("value", it.value)
            }
        }))
        root.put("patientLinks", JSONArray(patientLinks.map {
            JSONObject().apply {
                put("id", it.id)
                put("sourceKey", it.sourceKey)
                put("conditionValue", it.conditionValue)
                put("documentId", it.documentId)
                put("operation", it.operation)
                put("amount", it.amount)
            }
        }))
        root.put("documentChanges", JSONArray(changes.map {
            JSONObject().apply {
                put("id", it.id)
                put("documentId", it.documentId)
                put("timestamp", it.timestamp)
                put("delta", it.delta)
                put("qtyAfter", it.qtyAfter)
                put("patientId", it.patientId)
            }
        }))
        root.put("patientEffects", JSONArray(effects.map {
            JSONObject().apply {
                put("id", it.id)
                put("patientId", it.patientId)
                put("documentId", it.documentId)
                put("netDelta", it.netDelta)
            }
        }))
        root.put("settings", JSONObject().apply {
            put("preset", s.preset)
            put("darkMode", s.darkMode)
            put("customColor", s.customColor)
            put("customBg", s.customBg)
            put("customSecondary", s.customSecondary)
            put("dateFormat", s.dateFormat)
        })
        root.toString(2)
    }

    suspend fun importJson(json: String) = db.withTransaction {
        val root = JSONObject(json)
        val hasParagraphs = root.has("reportParagraphs")
        val hasElements = root.has("reportElements")
        db.documentDao().deleteAll()
        db.documentDao().deleteAllChanges()
        db.formDao().deleteAllForms()
        db.formDao().deleteAllFields()
        db.formDao().deleteAllLinks()
        db.submissionDao().deleteAll()
        db.reportDao().deleteAll()
        if (hasParagraphs) db.reportDao().deleteAllParagraphs()
        if (hasElements) db.reportDao().deleteAllElements()
        db.patientDao().clearEffects()
        db.documentDao().deleteAllChanges()

        val docs = root.optJSONArray("documents")
        if (docs != null) {
            for (i in 0 until docs.length()) {
                val o = docs.getJSONObject(i)
                db.documentDao().insert(
                    DocumentEntity(
                        id = o.optLong("id", 0),
                        name = o.getString("name"),
                        description = o.optString("description", ""),
                        colorArgb = o.optInt("colorArgb", 0xFF6750A4.toInt()),
                        quantity = o.optInt("quantity", 0),
                        step = o.optInt("step", 1)
                    )
                )
            }
        }
        val forms = root.optJSONArray("forms")
        if (forms != null) {
            for (i in 0 until forms.length()) {
                val o = forms.getJSONObject(i)
                db.formDao().insertForm(
                    FormEntity(
                        id = o.optLong("id", 0),
                        name = o.getString("name"),
                        description = o.optString("description", ""),
                        colorArgb = o.optInt("colorArgb", 0)
                    )
                )
            }
        }
        val fields = root.optJSONArray("formFields")
        if (fields != null) {
            for (i in 0 until fields.length()) {
                val o = fields.getJSONObject(i)
                db.formDao().insertField(
                    FormFieldEntity(
                        id = o.optLong("id", 0),
                        formId = o.optLong("formId", 0),
                        type = o.optString("type", "TEXT"),
                        label = o.optString("label", ""),
                        position = o.optInt("position", 0),
                        options = o.optString("options", ""),
                        required = o.optInt("required", 0),
                        defaultValue = o.optString("defaultValue", "")
                    )
                )
            }
        }
        val links = root.optJSONArray("formLinks")
        if (links != null) {
            for (i in 0 until links.length()) {
                val o = links.getJSONObject(i)
                db.formDao().insertLink(
                    FormLinkEntity(
                        id = o.optLong("id", 0),
                        fieldId = o.optLong("fieldId", 0),
                        documentId = o.optLong("documentId", 0),
                        operation = o.optString("operation", "INCREASE"),
                        amount = o.optInt("amount", 0),
                        conditionFieldId = o.optLong("conditionFieldId", 0),
                        conditionOperator = o.optString("conditionOperator", ""),
                        conditionValue = o.optString("conditionValue", ""),
                        dynamicDocFromFieldId = o.optLong("dynamicDocFromFieldId", 0),
                        amountFromFieldId = o.optLong("amountFromFieldId", 0)
                    )
                )
            }
        }
        val subs = root.optJSONArray("submissions")
        if (subs != null) {
            for (i in 0 until subs.length()) {
                val o = subs.getJSONObject(i)
                val subId = db.submissionDao().insertSubmission(
                    SubmissionEntity(
                        id = o.optLong("id", 0),
                        formId = o.optLong("formId", 0),
                        timestamp = o.optLong("timestamp", System.currentTimeMillis())
                    )
                )
                val values = o.optJSONArray("values")
                if (values != null) {
                    for (j in 0 until values.length()) {
                        val v = values.getJSONObject(j)
                        db.submissionDao().insertSubmissionValue(
                            SubmissionFieldValueEntity(
                                id = v.optLong("id", 0),
                                submissionId = subId,
                                fieldId = v.optLong("fieldId", 0),
                                value = v.optString("value", "")
                            )
                        )
                    }
                }
            }
        }
        val reports = root.optJSONArray("reports")
        if (reports != null) {
            for (i in 0 until reports.length()) {
                val o = reports.getJSONObject(i)
                db.reportDao().insertReport(
                    ReportEntity(
                        id = o.optLong("id", 0),
                        name = o.getString("name"),
                        description = o.optString("description", ""),
                        colorArgb = o.optInt("colorArgb", 0),
                        kind = o.optString("kind", "DOCUMENTS"),
                        sortOrder = o.optInt("sortOrder", 0),
                        marginTopMm = o.optDouble("marginTopMm", 25.4).toFloat(),
                        marginRightMm = o.optDouble("marginRightMm", 25.4).toFloat(),
                        marginBottomMm = o.optDouble("marginBottomMm", 25.4).toFloat(),
                        marginLeftMm = o.optDouble("marginLeftMm", 25.4).toFloat(),
                    )
                )
            }
        }
        val reportParagraphs = root.optJSONArray("reportParagraphs")
        if (reportParagraphs != null) {
            for (i in 0 until reportParagraphs.length()) {
                val o = reportParagraphs.getJSONObject(i)
                db.reportDao().insertParagraph(
                    ReportParagraphEntity(
                        id = o.optLong("id", 0),
                        reportId = o.optLong("reportId", 0),
                        position = o.optInt("position", 0),
                        font = o.optString("font", "Times New Roman"),
                        align = o.optString("align", "LEFT"),
                        indentLeftMm = o.optDouble("indentLeftMm", 0.0).toFloat(),
                        indentRightMm = o.optDouble("indentRightMm", 0.0).toFloat(),
                        firstLineMm = o.optDouble("firstLineMm", 0.0).toFloat(),
                        lineSpacing = o.optDouble("lineSpacing", 1.0).toFloat(),
                        spaceBeforeMm = o.optDouble("spaceBeforeMm", 0.0).toFloat(),
                        spaceAfterMm = o.optDouble("spaceAfterMm", 0.0).toFloat(),
                    )
                )
            }
        }
        val reportElements = root.optJSONArray("reportElements")
        if (reportElements != null) {
            for (i in 0 until reportElements.length()) {
                val o = reportElements.getJSONObject(i)
                db.reportDao().insertElement(
                    ReportDocElementEntity(
                        id = o.optLong("id", 0),
                        paragraphId = o.optLong("paragraphId", 0),
                        position = o.optInt("position", 0),
                        type = o.optString("type", "TEXT"),
                        text = o.optString("text", ""),
                        bold = o.optInt("bold", 0),
                        italic = o.optInt("italic", 0),
                        underline = o.optInt("underline", 0),
                        size = o.optInt("size", 0),
                        colorArgb = o.optInt("colorArgb", 0),
                        bgArgb = o.optInt("bgArgb", 0),
                        embeddedReportId = o.optLong("embeddedReportId", 0),
                        embeddedTitle = o.optString("embeddedTitle", ""),
                        align = o.optString("align", "LEFT"),
                        minRows = o.optInt("minRows", 0),
                        numberColumn = o.optInt("numberColumn", 0),
                        joinPrevious = o.optInt("joinPrevious", 0),
                        border = o.optInt("border", 1),
                        colWeights = o.optString("colWeights", ""),
                    )
                )
            }
        }
        val columns = root.optJSONArray("reportColumns")
        if (columns != null) {
            for (i in 0 until columns.length()) {
                val o = columns.getJSONObject(i)
                db.reportDao().insertColumn(
                    ReportColumnEntity(
                        id = o.optLong("id", 0),
                        reportId = o.optLong("reportId", 0),
                        fieldKey = o.optString("fieldKey", ""),
                        sourceFieldKeys = o.optString("sourceFieldKeys", ""),
                        joinSeparator = o.optString("joinSeparator", " "),
                        label = o.optString("label", ""),
                        trueText = o.optString("trueText", ""),
                        falseText = o.optString("falseText", ""),
                        position = o.optInt("position", 0),
                        align = o.optString("align", "LEFT"),
                        dropdownMap = o.optString("dropdownMap", ""),
                        hideValues = o.optInt("hideValues", 0),
                    )
                )
            }
        }
        val filters = root.optJSONArray("reportFilters")
        if (filters != null) {
            for (i in 0 until filters.length()) {
                val o = filters.getJSONObject(i)
                db.reportDao().insertFilter(
                    ReportFilterEntity(
                        id = o.optLong("id", 0),
                        reportId = o.optLong("reportId", 0),
                        position = o.optInt("position", 0),
                        connector = o.optString("connector", "AND"),
                        fieldKey = o.optString("fieldKey", ""),
                        operator = o.optString("operator", "EQ"),
                        value = o.optString("value", "")
                    )
                )
            }
        }
        val patients = root.optJSONArray("patients")
        if (patients != null) {
            for (i in 0 until patients.length()) {
                val o = patients.getJSONObject(i)
                db.patientDao().insertPatient(
                    PatientEntity(
                        id = o.optLong("id", 0),
                        number = o.optInt("number", 0),
                        lastName = o.optString("lastName", ""),
                        firstName = o.optString("firstName", ""),
                        middleName = o.optString("middleName", ""),
                        birthDate = o.optString("birthDate", ""),
                        sex = o.optString("sex", "М"),
                        idSeries = o.optString("idSeries", ""),
                        idNumber = o.optString("idNumber", ""),
                        serviceDate = o.optString("serviceDate", ""),
                        rvk = o.optString("rvk", ""),
                        rank = o.optString("rank", "Рядовой"),
                        unit = o.optString("unit", ""),
                        position = o.optString("position", ""),
                        admissionDate = o.optString("admissionDate", ""),
                        referredBy = o.optString("referredBy", ""),
                        emergency = o.optString("emergency", "Нет"),
                        illnessStart = o.optString("illnessStart", ""),
                        category = o.optString("category", "по призыву"),
                        svo = o.optInt("svo", 0),
                        soch = o.optInt("soch", 0),
                        personalNumber = o.optString("personalNumber", ""),
                        discharged = o.optInt("discharged", 0),
                        colorArgb = o.optInt("colorArgb", 0),
                        createdAt = o.optLong("createdAt", 0L)
                    )
                )
            }
        }
        val customFields = root.optJSONArray("patientCustomFields")
        if (customFields != null) {
            for (i in 0 until customFields.length()) {
                val o = customFields.getJSONObject(i)
                db.patientDao().insertCustomField(
                    PatientCustomFieldEntity(
                        id = o.optLong("id", 0),
                        label = o.optString("label", ""),
                        type = o.optString("type", "TEXT"),
                        options = o.optString("options", ""),
                        defaultValue = o.optString("defaultValue", ""),
                        position = o.optInt("position", 0)
                    )
                )
            }
        }
        val customValues = root.optJSONArray("patientCustomValues")
        if (customValues != null) {
            for (i in 0 until customValues.length()) {
                val o = customValues.getJSONObject(i)
                db.patientDao().insertCustomValue(
                    PatientCustomValueEntity(
                        id = o.optLong("id", 0),
                        patientId = o.optLong("patientId", 0),
                        fieldId = o.optLong("fieldId", 0),
                        value = o.optString("value", "")
                    )
                )
            }
        }
        val patientLinks = root.optJSONArray("patientLinks")
        if (patientLinks != null) {
            for (i in 0 until patientLinks.length()) {
                val o = patientLinks.getJSONObject(i)
                db.patientDao().insertLink(
                    PatientFieldLinkEntity(
                        id = o.optLong("id", 0),
                        sourceKey = o.optString("sourceKey", ""),
                        conditionValue = o.optString("conditionValue", ""),
                        documentId = o.optLong("documentId", 0),
                        operation = o.optString("operation", "DECREASE"),
                        amount = o.optInt("amount", 0)
                    )
                )
            }
        }
        val documentChanges = root.optJSONArray("documentChanges")
        if (documentChanges != null) {
            for (i in 0 until documentChanges.length()) {
                val o = documentChanges.getJSONObject(i)
                db.documentDao().insertChange(
                    DocumentChangeEntity(
                        id = o.optLong("id", 0),
                        documentId = o.optLong("documentId", 0),
                        timestamp = o.optLong("timestamp", 0L),
                        delta = o.optInt("delta", 0),
                        qtyAfter = o.optInt("qtyAfter", 0),
                        patientId = o.optLong("patientId", 0),
                    )
                )
            }
        }
        val patientEffects = root.optJSONArray("patientEffects")
        if (patientEffects != null) {
            for (i in 0 until patientEffects.length()) {
                val o = patientEffects.getJSONObject(i)
                db.patientDao().insertEffect(
                    PatientDocumentEffectEntity(
                        id = o.optLong("id", 0),
                        patientId = o.optLong("patientId", 0),
                        documentId = o.optLong("documentId", 0),
                        netDelta = o.optInt("netDelta", 0),
                    )
                )
            }
        }
        val settingsObj = root.optJSONObject("settings")
        if (settingsObj != null) {
            settings.update(
                preset = settingsObj.optString("preset", "tokyonight"),
                darkMode = settingsObj.optString("darkMode", "system"),
                customColor = settingsObj.optLong("customColor", 0xFF6750A4),
                customBg = settingsObj.optLong("customBg", 0L),
                customSecondary = settingsObj.optLong("customSecondary", 0L),
                dateFormat = settingsObj.optString("dateFormat", "dd.MM.yyyy"),
            )
        }
        try {
            PatientRepository(db).syncEffectRecords()
        } catch (e: Exception) {
            Log.e("ENT", "syncEffectRecords skipped during import: ${e.message}")
        }
    }
}
