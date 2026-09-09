package com.example.entimate.ui.folders

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.entimate.EntimateApplication
import com.example.entimate.data.local.FolderEntity
import com.example.entimate.ui.components.ColorRow
import com.example.entimate.ui.navigation.navigateBack
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderEditScreen(folderId: Long, nav: NavController) {
    val app = LocalContext.current.applicationContext as EntimateApplication
    val repo = app.folderRepository
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(0) }
    var nameError by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(folderId == 0L) }

    LaunchedEffect(Unit) {
        if (folderId != 0L) {
            val f = repo.getById(folderId)
            if (f != null) {
                name = f.name
                description = f.description
                color = f.colorArgb
            }
            loaded = true
        }
    }

    fun saveAnd(action: () -> Unit) {
        if (name.isBlank()) { nameError = true; return }
        scope.launch {
            if (repo.hasDuplicateName(name, folderId)) { nameError = true; return@launch }
            repo.save(FolderEntity(id = folderId, name = name.trim(), description = description.trim(), colorArgb = color))
            action()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (folderId == 0L) "Новая папка" else "Изменить папку") },
                navigationIcon = {
                    IconButton(onClick = { nav.navigateBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { saveAnd { nav.navigateBack() } }) {
                        Icon(Icons.Filled.Check, contentDescription = "Сохранить")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
                .imePadding(),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; nameError = false },
                label = { Text("Название") },
                isError = nameError,
                supportingText = if (nameError) {
                    { Text(if (name.isBlank()) "Введите название" else "Папка с таким названием уже есть") }
                } else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Описание") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Text("Цвет", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            ColorRow(color = color, onColorChange = { color = it })
        }
    }
}