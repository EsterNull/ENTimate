package com.example.entimate.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.entimate.data.local.FormFieldEntity
import com.example.entimate.ui.stripNewlines

private val NUMBER_OPERATORS = listOf(
    "GT" to "больше (>)",
    "LT" to "меньше (<)",
    "EQ" to "равно (=)",
    "GTE" to "больше или равно (≥)",
    "LTE" to "меньше или равно (≤)",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConditionEditor(
    candidates: List<FormFieldEntity>,
    conditionFieldId: Long,
    conditionOperator: String,
    conditionValue: String,
    onFieldIdChange: (Long) -> Unit,
    onOperatorChange: (String) -> Unit,
    onValueChange: (String) -> Unit,
) {
    val target = candidates.firstOrNull { it.id == conditionFieldId }

    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = if (conditionFieldId != 0L) (target?.label?.ifBlank { typeLabel(target.type) } ?: "—") else "Без условия",
            onValueChange = {},
            readOnly = true,
            label = { Text("Поле условия") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Без условия") }, onClick = { onFieldIdChange(0L); expanded = false })
            candidates.forEach { f ->
                DropdownMenuItem(text = { Text(f.label.ifBlank { typeLabel(f.type) }) }, onClick = {
                    onFieldIdChange(f.id); expanded = false
                })
            }
        }
    }

    if (target != null) {
        Spacer(Modifier.height(6.dp))
        when (target.type) {
            "SWITCH" -> {
                var wantTrue by remember(conditionFieldId, conditionValue) { mutableStateOf(conditionValue == "true") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Должно быть: ${if (wantTrue) "Да" else "Нет"}")
                    Spacer(Modifier.width(8.dp))
                    Switch(checked = wantTrue, onCheckedChange = { wantTrue = it; onValueChange(if (it) "true" else "false") })
                }
            }
            "DROPDOWN" -> {
                val options = target.options.split(",").map { it.trim() }.filter { it.isNotBlank() }
                var optExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = optExpanded, onExpandedChange = { optExpanded = it }, modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = conditionValue,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Выбранное значение") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(optExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = optExpanded, onDismissRequest = { optExpanded = false }) {
                        options.forEach { opt ->
                            DropdownMenuItem(text = { Text(opt) }, onClick = { onValueChange(opt); optExpanded = false })
                        }
                    }
                }
            }
            "CHECKBOX_LIST" -> {
                val options = target.options.split(",").map { it.trim() }.filter { it.isNotBlank() }
                val selected = remember(conditionValue) {
                    conditionValue.split(",").map { it.trim() }.filter { it.isNotBlank() }.toMutableStateList()
                }
                Text("Выбранные чекбоксы:", style = MaterialTheme.typography.labelSmall)
                options.forEach { opt ->
                    val isOn = selected.contains(opt)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isOn, onCheckedChange = {
                            if (it) selected.add(opt) else selected.remove(opt)
                            onValueChange(selected.joinToString(","))
                        })
                        Text(opt)
                    }
                }
            }
            "NUMBER" -> {
                var opExpanded by remember { mutableStateOf(false) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ExposedDropdownMenuBox(expanded = opExpanded, onExpandedChange = { opExpanded = it }, modifier = Modifier.width(200.dp)) {
                        OutlinedTextField(
                            value = NUMBER_OPERATORS.firstOrNull { it.first == conditionOperator }?.second ?: "оператор",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Условие") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(opExpanded) },
                            modifier = Modifier.menuAnchor(),
                        )
                        ExposedDropdownMenu(expanded = opExpanded, onDismissRequest = { opExpanded = false }) {
                            NUMBER_OPERATORS.forEach { (op, label) ->
                                DropdownMenuItem(text = { Text(label) }, onClick = { onOperatorChange(op); opExpanded = false })
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = conditionValue,
                        onValueChange = { onValueChange(it.stripNewlines()) },
                        label = { Text("Значение") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            else -> {
                Text("Для этого типа условие недоступно.", color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

private val FIELD_LABELS = listOf(
    "TEXT" to "Текст",
    "SWITCH" to "Переключатель",
    "DROPDOWN" to "Выпадающий список",
    "CHECKBOX_LIST" to "Список чекбоксов",
    "NUMBER" to "Число",
)

private fun typeLabel(type: String) = FIELD_LABELS.firstOrNull { it.first == type }?.second ?: type
