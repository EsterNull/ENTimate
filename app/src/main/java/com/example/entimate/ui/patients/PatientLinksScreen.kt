package com.example.entimate.ui.patients

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.entimate.EntimateApplication
import com.example.entimate.data.local.*

import com.example.entimate.ui.stripNewlines
import com.example.entimate.viewmodel.PatientsViewModel
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientLinksScreen(nav: NavController, vm: PatientsViewModel = viewModel()) {
    val app = LocalContext.current.applicationContext as EntimateApplication
    val repo = app.patientRepository
    val documents by vm.documents.collectAsStateWithLifecycle()
    val customFields by vm.customFields.collectAsStateWithLifecycle()
    var allLinks by remember { mutableStateOf(listOf<PatientFieldLinkEntity>()) }
    var showDialogFor by remember { mutableStateOf<PatientFieldDef?>(null) }
    var showDialogCustom by remember { mutableStateOf<PatientCustomFieldEntity?>(null) }
    var showGlobalDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        allLinks = repo.getAllLinks()
    }

    fun linksFor(key: String) = allLinks.filter { it.sourceKey == key }
    val globalLinks = allLinks.filter { it.sourceKey == PATIENT_GLOBAL_KEY }
    val docNameById = documents.associate { it.id to it.name }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Связи с документами") },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
        ) {
            Text(
                "Настройте, как значения полей пациента влияют на количество документов. " +
                    "Например: включённый флаг «СВО» уменьшает «Анкеты СВО» на 1.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.height(12.dp))
            PATIENT_FIELDS.forEach { def ->
                val key = def.key
                LinkCard(
                    title = def.label,
                    links = linksFor(key),
                    documentName = { docNameById[it] ?: "#$it" },
                    onAdd = { showDialogFor = def },
                    onDelete = { link -> allLinks = allLinks - link; scope.launch { repo.deleteLink(link) } },
                )
                Spacer(Modifier.height(10.dp))
            }
            customFields.forEach { cf ->
                val key = "custom:${cf.id}"
                LinkCard(
                    title = cf.label + " (своё поле)",
                    links = linksFor(key),
                    documentName = { docNameById[it] ?: "#$it" },
                    onAdd = { showDialogCustom = cf },
                    onDelete = { link -> allLinks = allLinks - link; scope.launch { repo.deleteLink(link) } },
                )
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text("Влияние при добавлении пациента (независимо от полей)", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Эти связи применяются к документам сразу при добавлении любого пациента.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.height(8.dp))
            LinkCard(
                title = "При добавлении пациента",
                links = globalLinks,
                documentName = { docNameById[it] ?: "#$it" },
                onAdd = { showGlobalDialog = true },
                onDelete = { link -> allLinks = allLinks - link; scope.launch { repo.deleteLink(link) } },
            )
        }
    }

    if (showDialogFor != null) {
        val def = showDialogFor!!
        AddLinkDialog(
            documents = documents,
            conditionOptions = fieldLinkOptions(def),
            defaultCondition = when (def.type) {
                "CHECKBOX" -> "true"
                "SWITCH", "DROPDOWN" -> fieldLinkOptions(def).firstOrNull() ?: ""
                else -> ""
            },
            onDismiss = { showDialogFor = null },
            onConfirm = { docId, operation, amount, cond ->
                val link = PatientFieldLinkEntity(sourceKey = def.key, conditionValue = cond, documentId = docId, operation = operation, amount = amount)
                allLinks = allLinks + link
                showDialogFor = null
                scope.launch { repo.saveLink(link) }
            },
        )
    }

    if (showDialogCustom != null) {
        val cf = showDialogCustom!!
        val opts = cf.options.split(",").map { it.trim() }.filter { it.isNotBlank() }
        AddLinkDialog(
            documents = documents,
            conditionOptions = opts,
            defaultCondition = if (cf.type == "CHECKBOX") "true" else opts.firstOrNull() ?: "",
            onDismiss = { showDialogCustom = null },
            onConfirm = { docId, operation, amount, cond ->
                val link = PatientFieldLinkEntity(sourceKey = "custom:${cf.id}", conditionValue = cond, documentId = docId, operation = operation, amount = amount)
                allLinks = allLinks + link
                showDialogCustom = null
                scope.launch { repo.saveLink(link) }
            },
        )
    }

    if (showGlobalDialog) {
        AddLinkDialog(
            documents = documents,
            conditionOptions = emptyList(),
            defaultCondition = "",
            showCondition = false,
            onDismiss = { showGlobalDialog = false },
            onConfirm = { docId, operation, amount, _ ->
                val link = PatientFieldLinkEntity(sourceKey = PATIENT_GLOBAL_KEY, conditionValue = "", documentId = docId, operation = operation, amount = amount)
                allLinks = allLinks + link
                showGlobalDialog = false
                scope.launch { repo.saveLink(link) }
            },
        )
    }
}

private fun fieldLinkOptions(def: PatientFieldDef): List<String> = when (def.key) {
    "sex" -> listOf("М", "Ж")
    "emergency" -> listOf("Да", "Нет")
    else -> def.options.split(",").map { it.trim() }.filter { it.isNotBlank() }
}

@Composable
private fun LinkCard(
    title: String,
    links: List<PatientFieldLinkEntity>,
    documentName: (Long) -> String,
    onAdd: () -> Unit,
    onDelete: (PatientFieldLinkEntity) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onAdd) { Icon(Icons.Filled.Add, contentDescription = "Добавить связь") }
            }
            if (links.isEmpty()) {
                Text("Нет связей.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            } else {
                links.forEach { link ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            buildString {
                                append("Документ «${documentName(link.documentId)}»: ")
                                append(if (link.operation == "INCREASE") "+" else "−")
                                append(link.amount)
                                if (link.conditionValue.isNotBlank()) append(" (если значение = ${link.conditionValue})")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { onDelete(link) }) { Icon(Icons.Filled.Delete, contentDescription = "Удалить", tint = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddLinkDialog(
    documents: List<DocumentEntity>,
    conditionOptions: List<String>,
    defaultCondition: String,
    showCondition: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: (docId: Long, operation: String, amount: Int, conditionValue: String) -> Unit,
) {
    var docId by remember { mutableStateOf(documents.firstOrNull()?.id ?: 0L) }
    var docExpanded by remember { mutableStateOf(false) }
    var operation by remember { mutableStateOf("DECREASE") }
    var opExpanded by remember { mutableStateOf(false) }
    var amount by remember { mutableStateOf("1") }
    var condition by remember { mutableStateOf(defaultCondition) }
    var condExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новая связь") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(expanded = docExpanded, onExpandedChange = { docExpanded = it }, modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = documents.firstOrNull { it.id == docId }?.name ?: "Документ",
                        onValueChange = {}, readOnly = true, label = { Text("Документ") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(docExpanded) }, modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable).fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = docExpanded, onDismissRequest = { docExpanded = false }) {
                        documents.forEach { d -> DropdownMenuItem(text = { Text(d.name) }, onClick = { docId = d.id; docExpanded = false }) }
                    }
                }
                ExposedDropdownMenuBox(expanded = opExpanded, onExpandedChange = { opExpanded = it }, modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = if (operation == "INCREASE") "Увеличить" else "Уменьшить",
                        onValueChange = {}, readOnly = true, label = { Text("Действие") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(opExpanded) }, modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable).fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = opExpanded, onDismissRequest = { opExpanded = false }) {
                        DropdownMenuItem(text = { Text("Увеличить") }, onClick = { operation = "INCREASE"; opExpanded = false })
                        DropdownMenuItem(text = { Text("Уменьшить") }, onClick = { operation = "DECREASE"; opExpanded = false })
                    }
                }
                OutlinedTextField(value = amount, onValueChange = { amount = it.stripNewlines() }, label = { Text("На сколько") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                if (showCondition) {
                    if (conditionOptions.isNotEmpty()) {
                        ExposedDropdownMenuBox(expanded = condExpanded, onExpandedChange = { condExpanded = it }, modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = condition, onValueChange = {}, readOnly = true, label = { Text("При значении") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(condExpanded) }, modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable).fillMaxWidth(),
                            )
                            ExposedDropdownMenu(expanded = condExpanded, onDismissRequest = { condExpanded = false }) {
                                conditionOptions.forEach { o -> DropdownMenuItem(text = { Text(o) }, onClick = { condition = o; condExpanded = false }) }
                            }
                        }
                    } else {
                        OutlinedTextField(value = condition, onValueChange = { condition = it.stripNewlines() }, label = { Text("При значении (равно)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amt = amount.toIntOrNull() ?: 0
                if (docId != 0L && amt > 0) onConfirm(docId, operation, amt, if (showCondition) condition else "")
            }) { Text("Добавить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
