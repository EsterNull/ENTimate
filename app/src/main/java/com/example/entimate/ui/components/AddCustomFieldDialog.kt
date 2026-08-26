package com.example.entimate.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.entimate.data.local.PatientCustomFieldEntity
import com.example.entimate.ui.stripNewlines

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCustomFieldDialog(
    initial: PatientCustomFieldEntity? = null,
    onDismiss: () -> Unit,
    onConfirm: (label: String, type: String, options: String, default: String) -> Unit,
) {
    var label by remember { mutableStateOf(initial?.label ?: "") }
    var type by remember { mutableStateOf(initial?.type ?: "TEXT") }
    var options by remember { mutableStateOf(initial?.options ?: "") }
    var default by remember { mutableStateOf(initial?.defaultValue ?: "") }
    var typeExpanded by remember { mutableStateOf(false) }
    val types = listOf("TEXT" to "Текст", "NUMBER" to "Число", "DATE" to "Дата", "DROPDOWN" to "Список", "CHECKBOX" to "Чекбокс")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Новое поле" else "Изменить поле") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = label, onValueChange = { label = it.stripNewlines() }, label = { Text("Название") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = TextKeyboardOptions)
                ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = it }, modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = types.first { it.first == type }.second, onValueChange = {}, readOnly = true, label = { Text("Тип") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) }, modifier = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                        types.forEach { t -> DropdownMenuItem(text = { Text(t.second) }, onClick = { type = t.first; typeExpanded = false }) }
                    }
                }
                if (type == "DROPDOWN") {
                    OutlinedTextField(
                        value = options, onValueChange = { options = it.stripNewlines() },
                        label = { Text("Варианты (через запятую)") }, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = TextKeyboardOptions,
                    )
                }
                OutlinedTextField(value = default, onValueChange = { default = it.stripNewlines() }, label = { Text("Значение по умолчанию") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = TextKeyboardOptions)
            }
        },
        confirmButton = {
            TextButton(onClick = { if (label.isNotBlank()) onConfirm(label.trim(), type, options.trim(), default.trim()) }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
