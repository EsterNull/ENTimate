package com.example.entimate.ui.patients

import com.example.entimate.ui.navigation.navigateBack

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.entimate.EntimateApplication
import com.example.entimate.data.local.PatientCustomFieldEntity
import com.example.entimate.data.repository.PatientRepository
import com.example.entimate.ui.components.AddCustomFieldDialog
import kotlinx.coroutines.launch

private val TYPE_LABELS = mapOf(
    "TEXT" to "Текст",
    "NUMBER" to "Число",
    "DATE" to "Дата",
    "DROPDOWN" to "Список",
    "CHECKBOX" to "Чекбокс",
    "DOCUMENT" to "Документ",
    "COMPUTED" to "Вычисляемое",
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CustomFieldsScreen(nav: NavController) {
    val context = LocalContext.current
    val repo: PatientRepository = (context.applicationContext as EntimateApplication).patientRepository
    val scope = rememberCoroutineScope()
    val fields by repo.customFieldsFlow.collectAsStateWithLifecycle(emptyList())
    val documents by repo.documentsFlow.collectAsStateWithLifecycle(emptyList())
    var showDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<PatientCustomFieldEntity?>(null) }
    var pendingDelete by remember { mutableStateOf<PatientCustomFieldEntity?>(null) }
    var reordering by remember { mutableStateOf(false) }

    fun reorderFields(from: Int, to: Int) {
        val ids = fields.map { it.id }.toMutableList()
        if (from !in ids.indices || to !in ids.indices) return
        val id = ids.removeAt(from)
        ids.add(to, id)
        scope.launch { repo.reorderFields(ids) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Пользовательские поля") },
                navigationIcon = { IconButton(onClick = { nav.navigateBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") } },
                actions = {
                    if (reordering) {
                        IconButton(onClick = { reordering = false }) {
                            Icon(Icons.Filled.Check, contentDescription = "Завершить изменение порядка")
                        }
                    } else {
                        IconButton(onClick = { reordering = true }) {
                            Icon(Icons.Filled.DragHandle, contentDescription = "Изменить порядок")
                        }
                        IconButton(onClick = { editing = null; showDialog = true }) {
                            Icon(Icons.Filled.Add, contentDescription = "Добавить поле")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()).imePadding()) {
            Text(
                "Добавляйте свои поля пациента (текст, число, дата, список, чекбокс, документ, вычисляемое). " +
                    "Они появляются в карточке пациента и могут использоваться в отчётах и связях с документами.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.height(12.dp))
            if (fields.isEmpty()) {
                Text("Нет добавленных полей.", color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.labelSmall)
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp)) {
                    if (reordering) {
                        itemsIndexed(fields, key = { _, cf -> cf.id }) { index, cf ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).animateItem(),
                            ) {
                                Column {
                                    IconButton(
                                        onClick = { if (index > 0) reorderFields(index, index - 1) },
                                        enabled = index > 0,
                                    ) { Icon(Icons.Filled.ArrowDropUp, contentDescription = "Вверх") }
                                    IconButton(
                                        onClick = { if (index < fields.lastIndex) reorderFields(index, index + 1) },
                                        enabled = index < fields.lastIndex,
                                    ) { Icon(Icons.Filled.ArrowDropDown, contentDescription = "Вниз") }
                                }
                                Box(Modifier.weight(1f)) {
                                    CustomFieldCard(
                                        cf = cf,
                                        onEdit = { editing = cf; showDialog = true },
                                        onDelete = { pendingDelete = cf },
                                    )
                                }
                            }
                        }
                    } else {
                        itemsIndexed(fields, key = { _, cf -> cf.id }) { _, cf ->
                            CustomFieldCard(
                                cf = cf,
                                modifier = Modifier.padding(vertical = 4.dp),
                                onEdit = { editing = cf; showDialog = true },
                                onDelete = { pendingDelete = cf },
                            )
                        }
                    }
                }
            }
        }
    }

    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Удалить поле?") },
            text = { Text("Поле «${pendingDelete!!.label}» и его значения у всех пациентов будут удалены безвозвратно.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repo.deleteCustomField(pendingDelete!!) }
                    pendingDelete = null
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Отмена") } },
        )
    }

    if (showDialog) {
        AddCustomFieldDialog(
            initial = editing,
            documents = documents,
            customFields = fields,
            onDismiss = { showDialog = false; editing = null },
            onConfirm = { label, type, options, def, formula ->
                val field = editing?.copy(label = label, type = type, options = options, defaultValue = def, formula = formula)
                    ?: PatientCustomFieldEntity(label = label, type = type, options = options, defaultValue = def, formula = formula, position = fields.size)
                scope.launch { repo.saveCustomField(field) }
                showDialog = false
                editing = null
            },
        )
    }
}

@Composable
private fun CustomFieldCard(
    cf: PatientCustomFieldEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(cf.label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Text(TYPE_LABELS[cf.type] ?: cf.type, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
            if (cf.options.isNotBlank()) {
                Text("Варианты: ${cf.options}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
            if (cf.type == "COMPUTED") {
                Text("Формула: ${cf.formula.ifBlank { "—" }}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
            if (cf.defaultValue.isNotBlank()) {
                Text("По умолчанию: ${cf.defaultValue}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onEdit) { Text("Изменить") }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Удалить", tint = MaterialTheme.colorScheme.error) }
            }
        }
    }
}
