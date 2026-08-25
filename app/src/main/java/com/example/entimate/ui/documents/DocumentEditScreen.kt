package com.example.entimate.ui.documents

import com.example.entimate.ui.stripNewlines
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.example.entimate.EntimateApplication
import com.example.entimate.data.local.DocumentEntity
import com.example.entimate.ui.components.ColorRow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentEditScreen(docId: Long, nav: NavController) {
    val app = LocalContext.current.applicationContext as EntimateApplication
    val repo = app.documentRepository

    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(0) }
    var quantity by remember { mutableStateOf("0") }
    var step by remember { mutableStateOf("1") }
    var nameError by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(docId == 0L) }
    var originalQuantity by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(docId) {
        if (docId != 0L) {
            repo.getById(docId)?.let {
                name = it.name
                description = it.description
                color = it.colorArgb
                quantity = it.quantity.toString()
                originalQuantity = it.quantity
                step = it.step.toString()
            }
            loaded = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (docId == 0L) "Новый документ" else "Редактировать документ") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(
                        enabled = name.isNotBlank() && !nameError,
                        onClick = {
                            scope.launch {
                                val dup = repo.getAll().any { it.id != docId && it.name.equals(name.trim(), ignoreCase = true) }
                                if (dup) { nameError = true; return@launch }
                                val doc = DocumentEntity(
                                    id = docId,
                                    name = name.trim(),
                                    description = description.trim(),
                                    colorArgb = color,
                                    quantity = quantity.toIntOrNull() ?: 0,
                                    step = step.toIntOrNull() ?: 1,
                                )
                                if (docId == 0L) {
                                    val newId = repo.insert(doc.copy(id = 0))
                                    repo.recordInitial(newId, quantity.toIntOrNull() ?: 0)
                                } else {
                                    repo.update(doc)
                                    val delta = (quantity.toIntOrNull() ?: 0) - originalQuantity
                                    if (delta != 0) repo.recordChange(docId, delta)
                                }
                                nav.popBackStack()
                            }
                        }
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = "Сохранить")
                    }
                },
            )
        },
    ) { padding ->
        if (!loaded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.stripNewlines(); nameError = false },
                label = { Text("Название") },
                isError = nameError,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (nameError) {
                Text("Документ с таким названием уже существует.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it.stripNewlines() },
                label = { Text("Описание") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp),
                maxLines = 5,
            )
            Spacer(Modifier.height(16.dp))
            Text("Цвет карточки", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            ColorRow(color = color, onColorChange = { color = it })
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = quantity,
                onValueChange = { quantity = it.stripNewlines() },
                label = { Text("Количество") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = step,
                onValueChange = { step = it.stripNewlines() },
                label = { Text("Шаг изменения (кнопки + / -)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
