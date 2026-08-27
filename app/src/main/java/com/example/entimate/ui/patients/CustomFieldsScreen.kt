package com.example.entimate.ui.patients

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
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

private val TYPE_LABELS = mapOf("TEXT" to "Текст", "NUMBER" to "Число", "DATE" to "Дата", "DROPDOWN" to "Список", "CHECKBOX" to "Чекбокс")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomFieldsScreen(nav: NavController) {
    val context = LocalContext.current
    val repo: PatientRepository = (context.applicationContext as EntimateApplication).patientRepository
    val scope = rememberCoroutineScope()
    val fields by repo.customFieldsFlow.collectAsStateWithLifecycle(emptyList())
    var showDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<PatientCustomFieldEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Пользовательские поля") },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") } },
                actions = { IconButton(onClick = { editing = null; showDialog = true }) { Icon(Icons.Filled.Add, contentDescription = "Добавить поле") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()).imePadding()) {
            Text(
                "Добавляйте свои поля пациента (текст, число, дата, список, чекбокс). " +
                    "Они появляются в карточке пациента и могут использоваться в отчётах и связях с документами.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.height(12.dp))
            if (fields.isEmpty()) {
                Text("Нет добавленных полей.", color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.labelSmall)
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp)) {
                    items(fields) { cf ->
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Column(Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(cf.label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                                    Text(TYPE_LABELS[cf.type] ?: cf.type, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                }
                                if (cf.options.isNotBlank()) {
                                    Text("Варианты: ${cf.options}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                }
                                if (cf.defaultValue.isNotBlank()) {
                                    Text("По умолчанию: ${cf.defaultValue}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                }
                                Spacer(Modifier.height(6.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    TextButton(onClick = { editing = cf; showDialog = true }) { Text("Изменить") }
                                    IconButton(onClick = { scope.launch { repo.deleteCustomField(cf) } }) { Icon(Icons.Filled.Delete, contentDescription = "Удалить", tint = MaterialTheme.colorScheme.error) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        AddCustomFieldDialog(
            initial = editing,
            onDismiss = { showDialog = false; editing = null },
            onConfirm = { label, type, options, def ->
                val field = editing?.copy(label = label, type = type, options = options, defaultValue = def)
                    ?: PatientCustomFieldEntity(label = label, type = type, options = options, defaultValue = def, position = fields.size)
                scope.launch { repo.saveCustomField(field) }
                showDialog = false
                editing = null
            },
        )
    }
}
