package com.example.entimate.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.entimate.data.local.DocumentEntity
import com.example.entimate.data.local.PatientCustomFieldEntity
import com.example.entimate.ui.stripNewlines

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCustomFieldDialog(
    initial: PatientCustomFieldEntity? = null,
    documents: List<DocumentEntity> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (label: String, type: String, options: String, default: String) -> Unit,
) {
    var label by remember { mutableStateOf(initial?.label ?: "") }
    var type by remember { mutableStateOf(initial?.type ?: "TEXT") }
    var options by remember { mutableStateOf(initial?.options ?: "") }
    var default by remember { mutableStateOf(initial?.defaultValue ?: "") }
    var typeExpanded by remember { mutableStateOf(false) }
    var defaultExpanded by remember { mutableStateOf(false) }
    var newOption by remember { mutableStateOf("") }
    val types = listOf("TEXT" to "Текст", "NUMBER" to "Число", "DATE" to "Дата", "DROPDOWN" to "Список", "CHECKBOX" to "Чекбокс", "DOCUMENT" to "Документ")

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
                } else {
                    OutlinedTextField(value = default, onValueChange = { default = it.stripNewlines() }, label = { Text("Значение по умолчанию") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = TextKeyboardOptions)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (label.isNotBlank()) {
                    if (type == "DROPDOWN" && optionList.isEmpty()) return@TextButton
                    onConfirm(label.trim(), type, options.trim(), default.trim())
                }
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
