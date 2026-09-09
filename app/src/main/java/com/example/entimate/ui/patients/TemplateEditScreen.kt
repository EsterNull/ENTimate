package com.example.entimate.ui.patients

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.entimate.data.local.*
import com.example.entimate.ui.navigation.navigateBack
import com.example.entimate.viewmodel.PatientsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateEditScreen(nav: NavController, templateId: Long = 0L, vm: PatientsViewModel = viewModel()) {
    val customFields by vm.customFields.collectAsStateWithLifecycle()
    val documents by vm.documents.collectAsStateWithLifecycle()
    val templates by vm.templates.collectAsStateWithLifecycle()
    val editing = templates.firstOrNull { it.id == templateId }
    val values = remember { mutableStateMapOf<String, String>() }
    val customValues = remember { mutableStateMapOf<Long, String>() }
    var name by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf(false) }
    var prefilled by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val fields = PATIENT_FIELDS.filter { it.key !in TEMPLATE_EXCLUDED_FIELD_KEYS }
    val lastFieldKey = remember(customFields) {
        buildList {
            addAll(fields.map { it.key })
            if (customFields.isNotEmpty()) addAll(customFields.map { "custom:${it.id}" })
        }.lastOrNull()
    }

    LaunchedEffect(editing?.id, customFields) {
        if (editing != null && !prefilled) {
            val payload = decodePatientTemplatePayload(editing.payload)
            if (payload.custom.isEmpty() || customFields.isNotEmpty()) {
                name = editing.name
                values.clear()
                payload.builtins.forEach { (k, v) -> values[k] = v }
                customValues.clear()
                payload.custom.forEach { (label, v) ->
                    customFields.firstOrNull { it.label == label }?.let { customValues[it.id] = v }
                }
                prefilled = true
            }
        }
    }

    LaunchedEffect(Unit) {
        if (editing == null) {
            values["sex"] = "М"
            values["emergency"] = "Нет"
            values["rank"] = "Рядовой"
            values["category"] = "по призыву"
        }
    }

    LaunchedEffect(customFields) {
        customFields.forEach { cf ->
            if (cf.defaultValue.isNotBlank() && customValues[cf.id].isNullOrBlank()) {
                customValues[cf.id] = cf.defaultValue
            }
        }
    }

    fun buildTemplatePayload(): PatientTemplatePayload {
        val builtins = fields
            .mapNotNull { def -> values[def.key]?.takeIf { it.isNotBlank() }?.let { def.key to it } }
            .toMap()
        val custom = customValues.mapNotNull { (fid, v) ->
            if (v.isBlank()) null
            else customFields.firstOrNull { it.id == fid }?.let { it.label to v }
        }.toMap()
        return PatientTemplatePayload(builtins = builtins, custom = custom)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (editing != null) "Редактировать шаблон" else "Новый шаблон") },
                navigationIcon = {
                    IconButton(onClick = { nav.navigateBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
                },
                actions = {
                    IconButton(onClick = {
                        val payload = buildTemplatePayload()
                        when {
                            name.isBlank() -> nameError = true
                            payload.builtins.isEmpty() && payload.custom.isEmpty() ->
                                scope.launch { snackbarHostState.showSnackbar("Укажите хотя бы одно значение") }
                            editing != null -> { vm.updateTemplate(editing.id, name.trim(), payload); nav.navigateBack() }
                            else -> { vm.saveTemplate(name.trim(), payload); nav.navigateBack() }
                        }
                    }) { Icon(Icons.Filled.Check, contentDescription = "Сохранить") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()).imePadding(),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; nameError = false },
                label = { Text("Название шаблона") },
                isError = nameError,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Что заполнять в новом пациенте по умолчанию. ФИО, номер, удостоверение и даты в шаблон не попадают.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            if (nameError) {
                Text("Введите название шаблона.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(8.dp))
            }

            fields.forEach { def ->
                FieldEditor(def, values[def.key] ?: "", false, { values[def.key] = it }, null, null, def.key == lastFieldKey)
                Spacer(Modifier.height(10.dp))
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
}