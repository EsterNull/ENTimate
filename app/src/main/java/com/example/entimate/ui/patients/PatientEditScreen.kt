package com.example.entimate.ui.patients

import com.example.entimate.ui.navigation.navigateBack

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.entimate.EntimateApplication
import com.example.entimate.data.local.*
import com.example.entimate.ui.components.DateField
import com.example.entimate.ui.components.TextKeyboardOptions
import com.example.entimate.ui.components.TextKeyboardOptionsDone
import com.example.entimate.ui.stripNewlines
import com.example.entimate.viewmodel.PatientsViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private val COLLAPSED_BY_DEFAULT = setOf(
    "number",
    "idSeries",
    "idNumber",
    "serviceDate",
    "rvk",
    "position",
    "referredBy",
    "emergency",
    "illnessStart",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientEditScreen(patientId: Long, templateId: Long = 0L, nav: NavController, vm: PatientsViewModel = viewModel()) {
    val app = LocalContext.current.applicationContext as EntimateApplication
    val repo = app.patientRepository
    val scope = rememberCoroutineScope()
    val customFields by vm.customFields.collectAsStateWithLifecycle()
    val documents by vm.documents.collectAsStateWithLifecycle()
    val templates by vm.templates.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var loaded by remember { mutableStateOf(patientId == 0L) }
    val values = remember { mutableStateMapOf<String, String>() }
    val customValues = remember { mutableStateMapOf<Long, String>() }
    var showErrors by remember { mutableStateOf(false) }
    var expandedMore by remember { mutableStateOf(false) }
    var createdAt by remember { mutableStateOf(0L) }
    var existingId by remember { mutableStateOf(0L) }
    var existingPatient by remember { mutableStateOf<PatientEntity?>(null) }
    var showTemplatePicker by remember { mutableStateOf(false) }
    val birthMaxDate = remember {
        Calendar.getInstance().apply {
            add(Calendar.YEAR, -18)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val todayStart = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val lastFieldKey = remember(customFields, expandedMore) {
        buildList {
            addAll(PATIENT_FIELDS.filter { it.key !in COLLAPSED_BY_DEFAULT }.map { it.key })
            if (expandedMore) addAll(PATIENT_FIELDS.filter { it.key in COLLAPSED_BY_DEFAULT }.map { it.key })
            if (customFields.isNotEmpty()) addAll(customFields.map { "custom:${it.id}" })
        }.lastOrNull()
    }

    LaunchedEffect(Unit) {
        if (patientId != 0L) {
            val pw = repo.getPatient(patientId)
            if (pw != null) {
                existingId = pw.patient.id
                existingPatient = pw.patient
                createdAt = pw.patient.createdAt
                PATIENT_FIELDS.forEach { def ->
                    values[def.key] = when (def.key) {
                        "svo" -> if (pw.patient.svo == 1) "true" else "false"
                        "soch" -> if (pw.patient.soch == 1) "true" else "false"
                        "number" -> if (pw.patient.number != 0) pw.patient.number.toString() else ""
                        else -> patientValue(pw.patient, def.key)
                    }
                }
                pw.customValues.forEach { cv -> customValues[cv.fieldId] = cv.value }
            }
            loaded = true
        } else {
            createdAt = System.currentTimeMillis()
            values["sex"] = "М"
            values["emergency"] = "Нет"
            values["rank"] = "Рядовой"
            values["category"] = "по призыву"
            values["personalNumber"] = "-"
            values["admissionDate"] = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            values["svo"] = "false"
            values["soch"] = "false"
            loaded = true
        }
    }

    LaunchedEffect(customFields) {
        if (patientId == 0L) {
            customFields.forEach { cf ->
                if (cf.defaultValue.isNotBlank() && customValues[cf.id].isNullOrBlank()) {
                    customValues[cf.id] = cf.defaultValue
                }
            }
        }
    }

    fun applyTemplatePayload(payload: PatientTemplatePayload, fields: List<PatientCustomFieldEntity>) {
        payload.builtins.forEach { (k, v) -> values[k] = v }
        val missing = mutableListOf<String>()
        payload.custom.forEach { (label, v) ->
            val target = fields.firstOrNull { it.label.trim() == label.trim() }
            if (target != null) {
                customValues[target.id] = v
            } else {
                missing.add(label)
            }
        }
        if (missing.isNotEmpty()) {
            scope.launch { snackbarHostState.showSnackbar("Не применено: ${missing.joinToString(", ")}") }
        }
    }

    LaunchedEffect(customFields, templateId, loaded) {
        if (patientId == 0L && templateId != 0L && loaded && customFields.isNotEmpty()) {
            repo.templatePayload(templateId)?.let { payload ->
                applyTemplatePayload(payload, customFields)
            }
        }
    }

    fun save() {
        val missing = PATIENT_FIELDS.filter { it.required && (values[it.key]?.isBlank() != false) }
        if (missing.isNotEmpty()) { showErrors = true; return }
        val p = PatientEntity(
            id = existingId,
            number = values["number"]?.toIntOrNull() ?: 0,
            personalNumber = values["personalNumber"]?.trim() ?: "",
            lastName = values["lastName"]?.trim() ?: "",
            firstName = values["firstName"]?.trim() ?: "",
            middleName = values["middleName"]?.trim() ?: "",
            birthDate = values["birthDate"] ?: "",
            sex = values["sex"] ?: "М",
            idSeries = values["idSeries"] ?: "",
            idNumber = values["idNumber"] ?: "",
            serviceDate = values["serviceDate"] ?: "",
            rvk = values["rvk"] ?: "",
            rank = values["rank"] ?: "Рядовой",
            unit = values["unit"] ?: "",
            position = values["position"] ?: "",
            admissionDate = values["admissionDate"] ?: "",
            referredBy = values["referredBy"] ?: "",
            emergency = values["emergency"] ?: "Нет",
            illnessStart = values["illnessStart"] ?: "",
            category = values["category"] ?: "по призыву",
            diagnosis = values["diagnosis"]?.trim() ?: "",
            svo = if (values["svo"] == "true") 1 else 0,
            soch = if (values["soch"] == "true") 1 else 0,
            colorArgb = 0,
            createdAt = if (createdAt == 0L) System.currentTimeMillis() else createdAt,
            sortOrder = existingPatient?.sortOrder ?: 0,
            folderId = existingPatient?.folderId ?: 0,
            discharged = existingPatient?.discharged ?: 0,
            dischargeDate = existingPatient?.dischargeDate ?: "",
            version = existingPatient?.version ?: CURRENT_DATA_VERSION,
        )
        scope.launch {
            repo.savePatient(p, customValues.toMap())
            nav.navigateBack()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (patientId == 0L) "Новый пациент" else "Редактировать пациента") },
                navigationIcon = {
                    IconButton(onClick = { nav.navigateBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
                },
                actions = {
                    if (patientId == 0L && templates.isNotEmpty()) {
                        IconButton(onClick = { showTemplatePicker = true }) {
                            Icon(Icons.Filled.Layers, contentDescription = "Применить шаблон")
                        }
                    }
                    IconButton(onClick = { save() }) { Icon(Icons.Filled.Check, contentDescription = "Сохранить") }
                },
            )
        },
    ) { padding ->
        if (!loaded) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()).imePadding(),
        ) {
            if (showErrors) {
                Text("Заполните обязательные поля (отмечены *).", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(8.dp))
            }
            PATIENT_FIELDS.filter { it.key !in COLLAPSED_BY_DEFAULT }.forEach { def ->
                FieldEditor(def, values[def.key] ?: "", showErrors, { values[def.key] = it }, if (def.key == "birthDate") birthMaxDate else null, if (def.key == "admissionDate") todayStart else null, def.key == lastFieldKey)
                Spacer(Modifier.height(10.dp))
            }

            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expandedMore = !expandedMore },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Дополнительно", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Text(if (expandedMore) "Скрыть" else "Показать")
            }
            if (expandedMore) {
                Spacer(Modifier.height(8.dp))
                PATIENT_FIELDS.filter { it.key in COLLAPSED_BY_DEFAULT }.forEach { def ->
                    FieldEditor(def, values[def.key] ?: "", showErrors, { values[def.key] = it }, if (def.key == "birthDate") birthMaxDate else null, if (def.key == "admissionDate") todayStart else null, def.key == lastFieldKey)
                    Spacer(Modifier.height(10.dp))
                }
            }

            if (customFields.isNotEmpty()) {
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text("Пользовательские поля", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                customFields.forEach { cf ->
                    CustomFieldEditor(
                        cf,
                        customValues[cf.id] ?: "",
                        onValueChange = { customValues[cf.id] = it },
                        documents = documents,
                        isLast = "custom:${cf.id}" == lastFieldKey,
                        customFields = customFields,
                        customValues = customValues,
                        patientNumber = values["number"] ?: "",
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }

    if (showTemplatePicker) {
        AlertDialog(
            onDismissRequest = { showTemplatePicker = false },
            title = { Text("Применить шаблон") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    templates.forEach { t ->
                        OutlinedButton(
                            onClick = {
                                showTemplatePicker = false
                                scope.launch {
                                    repo.templatePayload(t.id)?.let { payload ->
                                        applyTemplatePayload(payload, customFields)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(t.name, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showTemplatePicker = false }) { Text("Отмена") } },
        )
    }
}

fun fieldOptions(def: PatientFieldDef): List<String> = when (def.key) {
    "sex" -> listOf("М", "Ж")
    "emergency" -> listOf("Да", "Нет")
    else -> def.options.split(",").map { it.trim() }.filter { it.isNotBlank() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FieldEditor(def: PatientFieldDef, value: String, showErrors: Boolean, onValueChange: (String) -> Unit, maxBirthDate: Long? = null, minDate: Long? = null, isLast: Boolean = false) {
    val required = def.required
    val error = showErrors && required && value.isBlank()
    val focusManager = LocalFocusManager.current
    when (def.type) {
        "TEXT", "NUMBER" -> {
            val kb = if (def.type == "NUMBER")
                androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number, imeAction = if (isLast) ImeAction.Done else ImeAction.Next)
            else
                if (isLast) TextKeyboardOptionsDone else TextKeyboardOptions
            OutlinedTextField(
                value = value,
                onValueChange = { onValueChange(it.replace("\n", "").replace("\r", "")) },
                label = { Text(def.label + if (required) " *" else "") },
                isError = error,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = kb,
                keyboardActions = if (isLast) KeyboardActions(onDone = { focusManager.clearFocus() }) else KeyboardActions(),
            )
        }
        "DATE" -> {
            DateField(
                value = value,
                onValueChange = onValueChange,
                label = def.label + if (required) " *" else "",
                maxDate = maxBirthDate,
                minDate = minDate,
            )
        }
        "SWITCH" -> {
            val options = fieldOptions(def)
            Column {
                Text(def.label + if (required) " *" else "", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { opt ->
                        FilterChip(
                            selected = value == opt,
                            onClick = { onValueChange(opt) },
                            label = { Text(opt) },
                        )
                    }
                }
                if (error) Text("Обязательное поле", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            }
        }
        "DROPDOWN" -> {
            val options = fieldOptions(def)
            var expanded by remember { mutableStateOf(false) }
            Column {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = value,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(def.label + if (required) " *" else "") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        isError = error,
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable).fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        options.forEach { opt ->
                            DropdownMenuItem(text = { Text(opt) }, onClick = { onValueChange(opt); expanded = false })
                        }
                    }
                }
                if (error) Text("Обязательное поле", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            }
        }
        "CHECKBOX" -> {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Checkbox(checked = value == "true", onCheckedChange = { onValueChange(if (it) "true" else "false") })
                Spacer(Modifier.width(8.dp))
                Text(def.label)
            }
        }
        else -> {
            OutlinedTextField(value = value, onValueChange = { onValueChange(it.stripNewlines()) }, label = { Text(def.label) }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = if (isLast) TextKeyboardOptionsDone else TextKeyboardOptions, keyboardActions = if (isLast) KeyboardActions(onDone = { focusManager.clearFocus() }) else KeyboardActions())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomFieldEditor(
    cf: PatientCustomFieldEntity,
    value: String,
    onValueChange: (String) -> Unit,
    documents: List<DocumentEntity> = emptyList(),
    onDelete: (() -> Unit)? = null,
    isLast: Boolean = false,
    customFields: List<PatientCustomFieldEntity> = emptyList(),
    customValues: Map<Long, String> = emptyMap(),
    patientNumber: String = "",
) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(cf.label, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.weight(1f))
                onDelete?.let {
                    IconButton(onClick = it) { Icon(Icons.Filled.Delete, contentDescription = "Удалить поле", tint = MaterialTheme.colorScheme.error) }
                }
            }
            Spacer(Modifier.height(6.dp))
            val focusManager = LocalFocusManager.current
            when (cf.type) {
                "TEXT", "NUMBER" -> OutlinedTextField(
                    value = value, onValueChange = { onValueChange(it.replace("\n", "").replace("\r", "")) }, label = { Text("Значение") }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = if (cf.type == "NUMBER") androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number, imeAction = if (isLast) ImeAction.Done else ImeAction.Next) else (if (isLast) TextKeyboardOptionsDone else TextKeyboardOptions),
                    keyboardActions = if (isLast) KeyboardActions(onDone = { focusManager.clearFocus() }) else KeyboardActions(),
                )
                "DATE" -> DateField(value = value, onValueChange = onValueChange, label = "Значение")
                "DROPDOWN" -> {
                    val options = cf.options.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = value, onValueChange = {}, readOnly = true, label = { Text("Значение") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable).fillMaxWidth(),
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            options.forEach { o -> DropdownMenuItem(text = { Text(o) }, onClick = { onValueChange(o); expanded = false }) }
                        }
                    }
                }
                "DOCUMENT" -> {
                    val docNames = documents.map { it.name }
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = value, onValueChange = {}, readOnly = true, label = { Text("Значение") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable).fillMaxWidth(),
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            if (docNames.isEmpty()) DropdownMenuItem(text = { Text("Документов нет") }, enabled = false, onClick = {})
                            docNames.forEach { o -> DropdownMenuItem(text = { Text(o) }, onClick = { onValueChange(o); expanded = false }) }
                        }
                    }
                }
                "CHECKBOX" -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = value == "true", onCheckedChange = { onValueChange(if (it) "true" else "false") })
                    Spacer(Modifier.width(8.dp)); Text("Да")
                }
                COMPUTED_TYPE -> ComputedFieldResult(
                    formula = cf.formula,
                    customFields = customFields,
                    customValues = customValues,
                    patientNumber = patientNumber,
                )
                else -> OutlinedTextField(value = value, onValueChange = { onValueChange(it.stripNewlines()) }, label = { Text("Значение") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = if (isLast) TextKeyboardOptionsDone else TextKeyboardOptions, keyboardActions = if (isLast) KeyboardActions(onDone = { focusManager.clearFocus() }) else KeyboardActions())
            }
        }
    }
}

@Composable
private fun ComputedFieldResult(
    formula: String,
    customFields: List<PatientCustomFieldEntity>,
    customValues: Map<Long, String>,
    patientNumber: String,
) {
    val result = patientFormulaResult(formula, customFields, customValues, patientNumber)
    Text(
        if (formula.isBlank()) "Формула не задана" else "Результат: ${if (result.isBlank()) "—" else result}",
        style = MaterialTheme.typography.bodyMedium,
        color = if (formula.isNotBlank() && result.isBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
    )
}
