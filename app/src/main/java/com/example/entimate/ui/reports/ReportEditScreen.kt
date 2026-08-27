package com.example.entimate.ui.reports

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.entimate.EntimateApplication
import com.example.entimate.data.local.*
import com.example.entimate.data.repository.parseDropdownMap
import com.example.entimate.data.repository.serializeDropdownMap
import com.example.entimate.data.repository.DROPDOWN_EMPTY_MARKER
import com.example.entimate.ui.components.ColorRow
import com.example.entimate.ui.components.TextKeyboardOptions
import com.example.entimate.ui.components.TextKeyboardOptionsDone
import com.example.entimate.ui.stripNewlines
import com.example.entimate.viewmodel.ReportsViewModel
import kotlinx.coroutines.launch

private data class FieldOpt(val key: String, val label: String, val type: String, val options: String)

private fun buildFieldOpts(customFields: List<PatientCustomFieldEntity>): List<FieldOpt> {
    val fixed = PATIENT_FIELDS.map { FieldOpt(it.key, it.label, it.type, it.options) }
    val special = REPORT_SPECIAL_FIELDS.map { FieldOpt(it.key, it.label, it.type, it.options) }
    val custom = customFields.map { FieldOpt("custom:${it.id}", it.label, it.type, it.options) }
    return fixed + special + custom
}

private fun optList(fo: FieldOpt): List<String> = when (fo.key) {
    "sex" -> listOf("М", "Ж")
    "emergency" -> listOf("Да", "Нет")
    else -> fo.options.split(",").map { it.trim() }.filter { it.isNotBlank() }
}

private fun operatorOptions(type: String): List<Pair<String, String>> = when (type) {
    "TEXT" -> listOf("EQ" to "совпадает", "CONTAINS" to "совпадает частично")
    "NUMBER" -> listOf("EQ" to "равно", "GT" to "больше", "GTE" to "больше или равно", "LT" to "меньше", "LTE" to "меньше или равно")
    "DATE" -> listOf("EQ" to "равно", "GTE" to "после", "LTE" to "до")
    else -> listOf("EQ" to "равно")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportEditScreen(reportId: Long, nav: NavController, vm: ReportsViewModel = viewModel()) {
    val app = LocalContext.current.applicationContext as EntimateApplication
    val repo = app.reportRepository
    var chosen by remember { mutableStateOf(if (reportId != 0L) "LOADING" else null) }
    LaunchedEffect(Unit) {
        if (reportId != 0L) {
            val kind = repo.getReportWithColumns(reportId)?.report?.kind
                ?: repo.getReportWithDocument(reportId)?.report?.kind
            chosen = if (kind == "DOCUMENT") "DOCUMENT" else "TABLE"
        }
    }
    when {
        reportId != 0L && chosen == "LOADING" ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        reportId == 0L && chosen == null ->
            ChooserScaffold(nav) { chosen = it }
        chosen == "DOCUMENT" ->
            DocumentReportEditor(reportId = reportId, nav = nav)
        else ->
            TableReportEditor(reportId = reportId, nav = nav)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChooserScaffold(nav: NavController, onChoose: (String) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Новый отчёт") },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Выберите тип отчёта", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            Button(onClick = { onChoose("TABLE") }, modifier = Modifier.fillMaxWidth()) { Text("Таблица") }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { onChoose("DOCUMENT") }, modifier = Modifier.fillMaxWidth()) { Text("Документ") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TableReportEditor(reportId: Long, nav: NavController) {
    val app = LocalContext.current.applicationContext as EntimateApplication
    val repo = app.reportRepository
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(0) }
    var nameError by remember { mutableStateOf(false) }
    var columns by remember { mutableStateOf(listOf<ReportColumnEntity>()) }
    var filters by remember { mutableStateOf(listOf<ReportFilterEntity>()) }
    var customFields by remember { mutableStateOf(listOf<PatientCustomFieldEntity>()) }
    var loaded by remember { mutableStateOf(reportId == 0L) }
    var showColumnPicker by remember { mutableStateOf(false) }
    var showFilterPicker by remember { mutableStateOf(false) }
    var editingColumn by remember { mutableStateOf(-1) }

    val fieldOpts by remember(customFields) { mutableStateOf(buildFieldOpts(customFields)) }
    val optByKey = remember(fieldOpts) { fieldOpts.associateBy { it.key } }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        customFields = repo.patientCustomFields()
        if (reportId != 0L) {
            val r = repo.getReportWithFilters(reportId)
            if (r != null) {
                name = r.report.name
                description = r.report.description
                color = r.report.colorArgb
                columns = r.columns.map { it.copy() }
                filters = r.filters.map { it.copy() }
            }
            loaded = true
        }
    }

    fun saveAnd(action: (Long) -> Unit) {
        if (name.isBlank()) { nameError = true; return }
        scope.launch {
            val dup = repo.getAllReports().any { it.id != reportId && it.name.equals(name.trim(), ignoreCase = true) }
            if (dup) { nameError = true; return@launch }
            val id = repo.saveReport(
                ReportEntity(id = reportId, name = name.trim(), description = description.trim(), colorArgb = color),
                columns = columns.mapIndexed { i, c -> c.copy(position = i) },
                filters = filters.mapIndexed { i, f -> f.copy(position = i) },
            )
            action(id)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (reportId == 0L) "Новый отчёт" else "Редактировать отчёт") },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") } },
                actions = {
                    IconButton(onClick = { saveAnd { id -> nav.navigate("reports/preview/$id/0/${System.currentTimeMillis()}") } }) {
                        Icon(Icons.Filled.TableChart, contentDescription = "Предпросмотр")
                    }
                    IconButton(onClick = { saveAnd { nav.popBackStack() } }) {
                        Icon(Icons.Filled.Check, contentDescription = "Сохранить")
                    }
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
            OutlinedTextField(value = name, onValueChange = { name = it.stripNewlines(); nameError = false }, label = { Text("Название") }, isError = nameError, singleLine = true, keyboardOptions = TextKeyboardOptions, modifier = Modifier.fillMaxWidth())
            if (nameError) Text("Укажите название и оно не должно повторяться.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = description, onValueChange = { description = it.stripNewlines() }, label = { Text("Описание") }, keyboardOptions = TextKeyboardOptions, modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp), maxLines = 4)
            Spacer(Modifier.height(16.dp))
            Text("Цвет карточки", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            ColorRow(color = color, onColorChange = { color = it })
            Spacer(Modifier.height(20.dp))

            Text("Поля отчёта (столбцы)", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (columns.isEmpty()) {
                Text("Нет выбранных полей.", color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.labelSmall)
            } else {
                columns.forEachIndexed { idx, col ->
                    val header = if (col.label.isNotBlank()) col.label else (col.sourceFieldKeys.ifBlank { col.fieldKey }).split(",").firstOrNull()?.let { optByKey[it]?.label ?: it } ?: "Колонка"
                    val colKeys = col.sourceFieldKeys.ifBlank { col.fieldKey }.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    val containsCheckbox = colKeys.any { optByKey[it]?.type == "CHECKBOX" }
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { editingColumn = idx; showColumnPicker = true }) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${idx + 1}. $header", modifier = Modifier.weight(1f))
                                IconButton(onClick = { columns = columns.toMutableList().also { it.removeAt(idx) } }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Удалить", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                            if (containsCheckbox) {
                                Spacer(Modifier.height(6.dp))
                                Text("Интерпретация флажка в таблице (пустое — пустая ячейка):", style = MaterialTheme.typography.labelMedium)
                                Spacer(Modifier.height(4.dp))
                                OutlinedTextField(
                                    value = col.trueText,
                                    onValueChange = { v -> columns = columns.toMutableList().also { it[idx] = it[idx].copy(trueText = v.stripNewlines()) } },
                                    label = { Text("Если отмечено") },
                                    singleLine = true,
                                    keyboardOptions = TextKeyboardOptions,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                     OutlinedTextField(
                         value = col.falseText,
                         onValueChange = { v -> columns = columns.toMutableList().also { it[idx] = it[idx].copy(falseText = v.stripNewlines()) } },
                         label = { Text("Если не отмечено") },
                         singleLine = true,
                         keyboardOptions = if (idx == columns.lastIndex) TextKeyboardOptionsDone else TextKeyboardOptions,
                         keyboardActions = if (idx == columns.lastIndex) KeyboardActions(onDone = { focusManager.clearFocus() }) else KeyboardActions(),
                         modifier = Modifier.fillMaxWidth(),
                     )
                            }
                            val dropdownKeys = colKeys.filter { optByKey[it]?.type == "DROPDOWN" }
                            if (dropdownKeys.isNotEmpty()) {
                                val map = parseDropdownMap(col.dropdownMap)
                                Spacer(Modifier.height(6.dp))
                                Text("Интерпретация значений списка (пустое — как есть):", style = MaterialTheme.typography.labelMedium)
                                dropdownKeys.forEach { dKey ->
                                    val fo = optByKey[dKey]
                                    val opts = fo?.let { optList(it) } ?: emptyList()
                                    if (opts.isNotEmpty()) {
                                        if (dropdownKeys.size > 1) {
                                            Spacer(Modifier.height(4.dp))
                                            Text(fo?.label ?: dKey, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                        }
                                        opts.forEach { opt ->
                                            val raw = map["$dKey#$opt"]
                                            val isEmpty = raw == DROPDOWN_EMPTY_MARKER
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                OutlinedTextField(
                                                    value = if (isEmpty) "" else (raw ?: ""),
                                                    enabled = !isEmpty,
                                                    onValueChange = { v ->
                                                        val nv = v.stripNewlines()
                                                        val m = parseDropdownMap(col.dropdownMap).toMutableMap()
                                                        val entryKey = "$dKey#$opt"
                                                        if (nv.isEmpty()) m.remove(entryKey) else m[entryKey] = nv
                                                        columns = columns.toMutableList().also { it[idx] = it[idx].copy(dropdownMap = serializeDropdownMap(m)) }
                                                    },
                                                    label = { Text(opt) },
                                                    singleLine = true,
                                                    keyboardOptions = TextKeyboardOptions,
                                                    modifier = Modifier.weight(1f),
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                OutlinedButton(
                                                    onClick = {
                                                        val m = parseDropdownMap(col.dropdownMap).toMutableMap()
                                                        if (raw == DROPDOWN_EMPTY_MARKER) m.remove("$dKey#$opt")
                                                        else m["$dKey#$opt"] = DROPDOWN_EMPTY_MARKER
                                                        columns = columns.toMutableList().also { it[idx] = it[idx].copy(dropdownMap = serializeDropdownMap(m)) }
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                                    border = BorderStroke(1.dp, if (isEmpty) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                                                ) {
                                                    Text("Пустая", style = MaterialTheme.typography.labelSmall, color = if (isEmpty) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { editingColumn = -1; showColumnPicker = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.Add, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Добавить поле") }

            Spacer(Modifier.height(20.dp))
            Text("Фильтры (условия включения пациентов)", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (filters.isEmpty()) {
                Text("Без фильтров будут показаны все пациенты.", color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.labelSmall)
            } else {
                filters.forEachIndexed { idx, f ->
                    val fo = optByKey[f.fieldKey]
                    FilterRow(
                        filter = f,
                        showConnector = idx > 0,
                        type = fo?.type ?: "TEXT",
                        label = fo?.label ?: patientFieldByKey(f.fieldKey)?.label ?: f.fieldKey,
                        options = fo?.let { optList(it) } ?: emptyList(),
                        onConnectorChange = { c -> filters = filters.toMutableList().also { it[idx] = it[idx].copy(connector = c) } },
                        onValueChange = { v -> filters = filters.toMutableList().also { it[idx] = it[idx].copy(value = v) } },
                        onDelete = { filters = filters.toMutableList().also { it.removeAt(idx) } },
                        isLast = idx == filters.lastIndex,
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { showFilterPicker = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.Add, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Добавить фильтр") }
        }
    }

    if (showColumnPicker) {
        AddColumnDialog(
            title = if (editingColumn < 0) "Поле отчёта" else "Изменить поле",
            fieldOpts = fieldOpts,
            initial = if (editingColumn < 0) null else columns.getOrNull(editingColumn),
            onDismiss = { showColumnPicker = false; editingColumn = -1 },
            onConfirm = { col ->
                columns = if (editingColumn < 0) columns + col else columns.toMutableList().also { it[editingColumn] = col }
                showColumnPicker = false
                editingColumn = -1
            },
        )
    }

    if (showFilterPicker) {
        FieldPickerDialog(
            title = "Поле фильтра",
            fieldOpts = fieldOpts,
            onDismiss = { showFilterPicker = false },
            onConfirm = { key ->
                val fo = optByKey[key]!!
                val op = operatorOptions(fo.type).first().first
                val cond = if (fo.type == "CHECKBOX") "true" else optList(fo).firstOrNull() ?: ""
                filters = filters + ReportFilterEntity(
                    id = 0, reportId = 0,
                    position = filters.size,
                    connector = if (filters.isEmpty()) "AND" else "AND",
                    fieldKey = key, operator = op, value = cond,
                )
                showFilterPicker = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterRow(
    filter: ReportFilterEntity,
    showConnector: Boolean,
    type: String,
    label: String,
    options: List<String>,
    onConnectorChange: (String) -> Unit,
    onValueChange: (String) -> Unit,
    onDelete: () -> Unit,
    isLast: Boolean = false,
) {
    var operator by remember(filter.id) { mutableStateOf(filter.operator) }
    var value by remember(filter.id) { mutableStateOf(filter.value) }
    var opExpanded by remember { mutableStateOf(false) }
    var valExpanded by remember { mutableStateOf(false) }
    val ops = operatorOptions(type)
    val focusManager = LocalFocusManager.current

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            if (showConnector) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = filter.connector == "AND", onClick = { onConnectorChange("AND") }, label = { Text("И") })
                    FilterChip(selected = filter.connector == "OR", onClick = { onConnectorChange("OR") }, label = { Text("ИЛИ") })
                }
                Spacer(Modifier.height(6.dp))
            }
            Text(label, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(6.dp))
            ExposedDropdownMenuBox(expanded = opExpanded, onExpandedChange = { opExpanded = it }, modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = ops.firstOrNull { it.first == operator }?.second ?: operator,
                    onValueChange = {}, readOnly = true, label = { Text("Условие") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(opExpanded) }, modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable).fillMaxWidth(),
                )
                ExposedDropdownMenu(expanded = opExpanded, onDismissRequest = { opExpanded = false }) {
                    ops.forEach { o -> DropdownMenuItem(text = { Text(o.second) }, onClick = { operator = o.first; opExpanded = false }) }
                }
            }
            Spacer(Modifier.height(6.dp))
            when (type) {
                "TEXT", "NUMBER" -> OutlinedTextField(
                    value = value, onValueChange = { v -> value = v.stripNewlines(); onValueChange(v.stripNewlines()) }, label = { Text(if (type == "NUMBER") "Значение (число)" else "Значение") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = if (type == "NUMBER") androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number, imeAction = if (isLast) ImeAction.Done else ImeAction.Next) else (if (isLast) TextKeyboardOptionsDone else TextKeyboardOptions),
                    keyboardActions = if (isLast) KeyboardActions(onDone = { focusManager.clearFocus() }) else KeyboardActions(),
                )
                "DATE" -> com.example.entimate.ui.components.DateField(value = value, onValueChange = { onValueChange(it); value = it }, label = "Значение (дата)")
                "CHECKBOX" -> {
                    var chk by remember(value) { mutableStateOf(value == "true") }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = chk, onCheckedChange = { c -> chk = c; val nv = if (c) "true" else "false"; value = nv; onValueChange(nv) })
                        Spacer(Modifier.width(8.dp)); Text("Да")
                    }
                }
                else -> {
                    if (options.isNotEmpty()) {
                        ExposedDropdownMenuBox(expanded = valExpanded, onExpandedChange = { valExpanded = it }, modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = value, onValueChange = {}, readOnly = true, label = { Text("Значение") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(valExpanded) }, modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable).fillMaxWidth(),
                            )
                            ExposedDropdownMenu(expanded = valExpanded, onDismissRequest = { valExpanded = false }) {
                                options.forEach { o -> DropdownMenuItem(text = { Text(o) }, onClick = { value = o; valExpanded = false; onValueChange(o) }) }
                            }
                        }
                    } else {
                        OutlinedTextField(value = value, onValueChange = { v -> value = v.stripNewlines(); onValueChange(v.stripNewlines()) }, label = { Text("Значение") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = if (isLast) TextKeyboardOptionsDone else TextKeyboardOptions, keyboardActions = if (isLast) KeyboardActions(onDone = { focusManager.clearFocus() }) else KeyboardActions())
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Удалить", tint = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FieldPickerDialog(
    title: String,
    fieldOpts: List<FieldOpt>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var selected by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = fieldOpts.firstOrNull { it.key == selected }?.label ?: "Выберите поле",
                    onValueChange = {}, readOnly = true, label = { Text("Поле") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable).fillMaxWidth(),
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    fieldOpts.forEach { fo -> DropdownMenuItem(text = { Text(fo.label) }, onClick = { selected = fo.key; expanded = false }) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { selected?.let { onConfirm(it) } }) { Text("Выбрать") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddColumnDialog(
    title: String,
    fieldOpts: List<FieldOpt>,
    initial: ReportColumnEntity?,
    onDismiss: () -> Unit,
    onConfirm: (ReportColumnEntity) -> Unit,
) {
    val initialKeys = initial?.let { (it.sourceFieldKeys.ifBlank { it.fieldKey }).split(",").map { k -> k.trim() }.filter { it.isNotBlank() } } ?: emptyList()
    var selected by remember { mutableStateOf(initialKeys.toSet()) }
    var separator by remember { mutableStateOf(initial?.joinSeparator ?: " ") }
    var label by remember { mutableStateOf(initial?.label ?: "") }
    var align by remember { mutableStateOf(initial?.align ?: "LEFT") }
    val focusManager = LocalFocusManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).imePadding()) {
                OutlinedTextField(value = label, onValueChange = { label = it.stripNewlines() }, label = { Text("Заголовок (необязательно)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = TextKeyboardOptions)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = separator, onValueChange = { separator = it.stripNewlines() }, label = { Text("Разделитель") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = TextKeyboardOptionsDone, keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }))
                Spacer(Modifier.height(8.dp))
                var alignExpanded by remember { mutableStateOf(false) }
                val alignOptions = listOf("LEFT" to "По левому краю", "CENTER" to "По центру", "RIGHT" to "По правому краю")
                ExposedDropdownMenuBox(expanded = alignExpanded, onExpandedChange = { alignExpanded = it }, modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = alignOptions.firstOrNull { it.first == align }?.second ?: "По левому краю",
                        onValueChange = {}, readOnly = true, label = { Text("Выравнивание значений столбца") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = alignExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable).fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = alignExpanded, onDismissRequest = { alignExpanded = false }) {
                        alignOptions.forEach { o -> DropdownMenuItem(text = { Text(o.second) }, onClick = { align = o.first; alignExpanded = false }) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Поля (можно выбрать несколько для объединения):", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                fieldOpts.forEach { fo ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Checkbox(checked = selected.contains(fo.key), onCheckedChange = { on -> selected = if (on) selected + fo.key else selected - fo.key })
                        Spacer(Modifier.width(6.dp))
                        Text(fo.label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (selected.isNotEmpty()) {
                    val keys = selected.toList()
                    onConfirm(
                        ReportColumnEntity(
                            id = initial?.id ?: 0,
                            reportId = 0,
                            fieldKey = keys.first(),
                            sourceFieldKeys = keys.joinToString(","),
                            joinSeparator = separator.ifBlank { " " },
                            label = label.trim(),
                            trueText = initial?.trueText ?: "",
                            falseText = initial?.falseText ?: "",
                            align = align,
                            position = initial?.position ?: 0,
                        )
                    )
                }
            }) { Text("Добавить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
