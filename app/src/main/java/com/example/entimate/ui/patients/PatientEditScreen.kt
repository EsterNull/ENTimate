package com.example.entimate.ui.patients

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.entimate.EntimateApplication
import com.example.entimate.data.local.*
import com.example.entimate.ui.components.DateField
import com.example.entimate.ui.stripNewlines
import com.example.entimate.viewmodel.PatientsViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientEditScreen(patientId: Long, nav: NavController, vm: PatientsViewModel = viewModel()) {
    val app = LocalContext.current.applicationContext as EntimateApplication
    val repo = app.patientRepository
    val scope = rememberCoroutineScope()
    val customFields by vm.customFields.collectAsStateWithLifecycle()

    var loaded by remember { mutableStateOf(patientId == 0L) }
    val values = remember { mutableStateMapOf<String, String>() }
    val customValues = remember { mutableStateMapOf<Long, String>() }
    var showErrors by remember { mutableStateOf(false) }
    var expandedAdvanced by remember { mutableStateOf(false) }
    var createdAt by remember { mutableStateOf(0L) }
    var existingId by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        if (patientId != 0L) {
            val pw = repo.getPatient(patientId)
            if (pw != null) {
                existingId = pw.patient.id
                createdAt = pw.patient.createdAt
                PATIENT_FIELDS.forEach { def ->
                    values[def.key] = when (def.key) {
                        "svo" -> if (pw.patient.svo == 1) "true" else "false"
                        "soch" -> if (pw.patient.soch == 1) "true" else "false"
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
            values["category"] = "По призыву"
            values["personalNumber"] = "-"
            values["admissionDate"] = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            values["svo"] = "false"
            values["soch"] = "false"
            loaded = true
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
            category = values["category"] ?: "По призыву",
            svo = if (values["svo"] == "true") 1 else 0,
            soch = if (values["soch"] == "true") 1 else 0,
            colorArgb = 0,
            createdAt = if (createdAt == 0L) System.currentTimeMillis() else createdAt,
        )
        scope.launch {
            repo.savePatient(p, customValues.toMap())
            nav.popBackStack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (patientId == 0L) "Новый пациент" else "Редактировать пациента") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
                },
                actions = {
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
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
        ) {
            if (showErrors) {
                Text("Заполните обязательные поля (отмечены *).", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(8.dp))
            }
            PATIENT_FIELDS.filter { it.defaultVisible }.forEach { def ->
                FieldEditor(def, values[def.key] ?: "", showErrors, { values[def.key] = it })
                Spacer(Modifier.height(10.dp))
            }

            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expandedAdvanced = !expandedAdvanced },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Расширенные параметры", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Text(if (expandedAdvanced) "Скрыть" else "Показать")
            }
            if (expandedAdvanced) {
                Spacer(Modifier.height(8.dp))
                PATIENT_FIELDS.filter { !it.defaultVisible }.forEach { def ->
                    FieldEditor(def, values[def.key] ?: "", showErrors, { values[def.key] = it })
                    Spacer(Modifier.height(10.dp))
                }

                // Custom fields (managed in Settings; here only filled in)
                customFields.forEach { cf ->
                    CustomFieldEditor(cf, customValues[cf.id] ?: "", onValueChange = { customValues[cf.id] = it }, onDelete = {
                        vm.deleteCustomField(cf)
                        customValues.remove(cf.id)
                    })
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

private fun fieldOptions(def: PatientFieldDef): List<String> = when (def.key) {
    "sex" -> listOf("М", "Ж")
    "emergency" -> listOf("Да", "Нет")
    else -> def.options.split(",").map { it.trim() }.filter { it.isNotBlank() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FieldEditor(def: PatientFieldDef, value: String, showErrors: Boolean, onValueChange: (String) -> Unit) {
    val required = def.required
    val error = showErrors && required && value.isBlank()
    when (def.type) {
        "TEXT", "NUMBER" -> {
            OutlinedTextField(
                value = value,
                onValueChange = { onValueChange(it.replace("\n", "").replace("\r", "")) },
                label = { Text(def.label + if (required) " *" else "") },
                isError = error,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = if (def.type == "NUMBER") androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number) else androidx.compose.foundation.text.KeyboardOptions.Default,
            )
        }
        "DATE" -> {
            DateField(value = value, onValueChange = onValueChange, label = def.label + if (required) " *" else "")
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
            OutlinedTextField(value = value, onValueChange = { onValueChange(it.stripNewlines()) }, label = { Text(def.label) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomFieldEditor(cf: PatientCustomFieldEntity, value: String, onValueChange: (String) -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(cf.label, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Удалить поле", tint = MaterialTheme.colorScheme.error) }
            }
            Spacer(Modifier.height(6.dp))
            when (cf.type) {
                "TEXT", "NUMBER" -> OutlinedTextField(
                    value = value, onValueChange = { onValueChange(it.replace("\n", "").replace("\r", "")) }, label = { Text("Значение") }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = if (cf.type == "NUMBER") androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number) else androidx.compose.foundation.text.KeyboardOptions.Default,
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
                "CHECKBOX" -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = value == "true", onCheckedChange = { onValueChange(if (it) "true" else "false") })
                    Spacer(Modifier.width(8.dp)); Text("Да")
                }
                else -> OutlinedTextField(value = value, onValueChange = { onValueChange(it.stripNewlines()) }, label = { Text("Значение") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
        }
    }
}
