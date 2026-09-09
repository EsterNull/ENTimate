package com.example.entimate.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.entimate.data.local.COMPUTED_TYPE
import com.example.entimate.data.local.DocumentEntity
import com.example.entimate.data.local.PatientCustomFieldEntity
import com.example.entimate.ui.stripNewlines

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCustomFieldDialog(
    initial: PatientCustomFieldEntity? = null,
    documents: List<DocumentEntity> = emptyList(),
    customFields: List<PatientCustomFieldEntity> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (label: String, type: String, options: String, default: String, formula: String) -> Unit,
) {
    var label by remember { mutableStateOf(initial?.label ?: "") }
    var type by remember { mutableStateOf(initial?.type ?: "TEXT") }
    var options by remember { mutableStateOf(initial?.options ?: "") }
    var default by remember { mutableStateOf(initial?.defaultValue ?: "") }
    var formula by remember { mutableStateOf(initial?.formula ?: "") }
    var typeExpanded by remember { mutableStateOf(false) }
    var defaultExpanded by remember { mutableStateOf(false) }
    var newOption by remember { mutableStateOf("") }
    val types = listOf(
        "TEXT" to "Текст",
        "NUMBER" to "Число",
        "DATE" to "Дата",
        "DROPDOWN" to "Список",
        "CHECKBOX" to "Чекбокс",
        "DOCUMENT" to "Документ",
        "COMPUTED" to "Вычисляемое",
    )

    val optionList = remember(options) {
        options.split(",").map { it.trim() }.filter { it.isNotBlank() }
    }
    val docNames = remember(documents) { documents.map { it.name } }

    fun applyOptions(list: List<String>) {
        options = list.joinToString(",")
        if (default.isNotBlank() && !list.contains(default)) default = ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Новое поле" else "Изменить поле") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = label, onValueChange = { label = it.stripNewlines() }, label = { Text("Название") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = TextKeyboardOptions)
                ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = it }, modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = types.first { it.first == type }.second, onValueChange = {}, readOnly = true, label = { Text("Тип") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) }, modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable).fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                        types.forEach { t -> DropdownMenuItem(text = { Text(t.second) }, onClick = { type = t.first; typeExpanded = false }) }
                    }
                }
                if (type == "DROPDOWN") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newOption, onValueChange = { newOption = it.stripNewlines() },
                            label = { Text("Новый вариант") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = TextKeyboardOptions,
                        )
                        Spacer(Modifier.width(6.dp))
                        FilledTonalButton(onClick = {
                            val trimmed = newOption.trim()
                            if (trimmed.isNotBlank() && !optionList.contains(trimmed)) {
                                applyOptions(optionList + trimmed)
                                newOption = ""
                            }
                        }) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    if (optionList.isEmpty()) {
                        Text("Вариантов пока нет. Добавьте хотя бы один.", color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.labelSmall)
                    } else {
                        optionList.forEachIndexed { idx, opt ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(opt, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                IconButton(onClick = { applyOptions(optionList.toMutableList().also { it.removeAt(idx) }) }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Удалить вариант", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    ExposedDropdownMenuBox(expanded = defaultExpanded, onExpandedChange = { defaultExpanded = it }, modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = if (optionList.contains(default)) default else "",
                            onValueChange = {}, readOnly = true,
                            label = { Text("Значение по умолчанию") },
                            placeholder = { Text(if (optionList.isEmpty()) "Сначала добавьте варианты" else "Не задано") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(defaultExpanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable).fillMaxWidth(),
                            enabled = optionList.isNotEmpty(),
                            leadingIcon = if (default.isNotBlank()) ({
                                IconButton(onClick = { default = "" }) { Icon(Icons.Filled.Close, contentDescription = "Сбросить", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }) else null,
                        )
                        ExposedDropdownMenu(expanded = defaultExpanded, onDismissRequest = { defaultExpanded = false }) {
                            optionList.forEach { o ->
                                DropdownMenuItem(text = { Text(o) }, onClick = { default = o; defaultExpanded = false })
                            }
                        }
                    }
                } else if (type == "DOCUMENT") {
                    ExposedDropdownMenuBox(expanded = defaultExpanded, onExpandedChange = { defaultExpanded = it }, modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = if (docNames.contains(default)) default else "",
                            onValueChange = {}, readOnly = true,
                            label = { Text("Значение по умолчанию") },
                            placeholder = { Text(if (docNames.isEmpty()) "Документов нет" else "Не задано") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(defaultExpanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable).fillMaxWidth(),
                            enabled = docNames.isNotEmpty(),
                            leadingIcon = if (default.isNotBlank()) ({
                                IconButton(onClick = { default = "" }) { Icon(Icons.Filled.Close, contentDescription = "Сбросить", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }) else null,
                        )
                        ExposedDropdownMenu(expanded = defaultExpanded, onDismissRequest = { defaultExpanded = false }) {
                            if (docNames.isEmpty()) DropdownMenuItem(text = { Text("Документов нет") }, enabled = false, onClick = {})
                            docNames.forEach { d ->
                                DropdownMenuItem(text = { Text(d) }, onClick = { default = d; defaultExpanded = false })
                            }
                        }
                    }
                } else if (type == "COMPUTED") {
                    Text(
                        "Значение считается по формуле из числовых полей (как в Excel). " +
                            "В карточке пациента будет показан только результат.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    Spacer(Modifier.height(6.dp))
                    FormulaBuilderField(formula, onFormulaChange = { formula = it }, customFields = customFields)
                    if (formula.isBlank()) {
                        Text("Введите формулу — поле обязательно.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    OutlinedTextField(value = default, onValueChange = { default = it.stripNewlines() }, label = { Text("Значение по умолчанию") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = TextKeyboardOptions)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (label.isNotBlank()) {
                    if (type == "DROPDOWN" && optionList.isEmpty()) return@TextButton
                    if (type == "COMPUTED" && formula.isBlank()) return@TextButton
                    onConfirm(label.trim(), type, options.trim(), default.trim(), formula.trim())
                }
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FormulaBuilderField(
    value: String,
    onFormulaChange: (String) -> Unit,
    customFields: List<PatientCustomFieldEntity>,
) {
    var tf by remember { mutableStateOf(TextFieldValue(value)) }
    LaunchedEffect(value) {
        if (tf.text != value) {
            val sel = tf.selection.start.coerceIn(0, value.length)
            tf = TextFieldValue(value, selection = TextRange(sel))
        }
    }

    fun insertToken(token: String) {
        val text = tf.text
        val start = tf.selection.start.coerceIn(0, text.length)
        val end = tf.selection.end.coerceIn(start, text.length)
        val newText = text.replaceRange(start, end, token)
        tf = TextFieldValue(newText, selection = TextRange(start + token.length))
        onFormulaChange(newText)
    }

    val insertable = buildList {
        add("Номер пациента" to "number")
        customFields.filter { it.type == "NUMBER" || it.type == COMPUTED_TYPE }
            .forEach { add(it.label to "custom:${it.id}") }
    }

    Column {
        Text("Вставьте поля в формулу (числовые поля и результат других вычисляемых полей):", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            insertable.forEach { (label, key) ->
                AssistChip(
                    onClick = { insertToken("{$key}") },
                    label = { Text(label) },
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = tf,
            onValueChange = {
                tf = it
                onFormulaChange(it.text)
            },
            label = { Text("Формула") },
            placeholder = { Text("Например: {Рост} + {Вес}") },
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Операторы: + − * / ( ) ^. Функции: abs, sqrt, cbrt, ln, log, exp, floor, ceil, round, sign, min(a, b), max(a, b).",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}
