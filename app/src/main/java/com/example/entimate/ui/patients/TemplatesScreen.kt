package com.example.entimate.ui.patients

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.entimate.data.local.PatientTemplateEntity
import com.example.entimate.ui.components.SwipeableRow
import com.example.entimate.ui.navigation.navigateBack
import com.example.entimate.viewmodel.PatientsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatesScreen(nav: NavController, vm: PatientsViewModel = viewModel()) {
    val templates by vm.templates.collectAsStateWithLifecycle()
    var deleteTarget by remember { mutableStateOf<PatientTemplateEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Шаблоны пациентов") },
                navigationIcon = {
                    IconButton(onClick = { nav.navigateBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
                },
                actions = {
                    IconButton(onClick = { nav.navigate("templates/edit") }) {
                        Icon(Icons.Filled.Add, contentDescription = "Добавить шаблон")
                    }
                },
            )
        },
    ) { padding ->
        if (templates.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Text("Шаблонов нет", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Нажмите «+» вверху, чтобы создать шаблон.",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(templates, key = { it.id }) { t ->
                    SwipeableRow(
                        onSwipeLeft = { deleteTarget = t },
                        onSwipeRight = { nav.navigate("templates/edit/${t.id}") },
                        backgroundLeft = {
                            Box(
                                Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 24.dp),
                                contentAlignment = Alignment.CenterEnd,
                            ) { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                        },
                        backgroundRight = {
                            Box(
                                Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 24.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) { Icon(Icons.Filled.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                        },
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(onClick = {}, onLongClick = { vm.duplicateTemplate(t) }),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(t.name, style = MaterialTheme.typography.titleMedium)
                                }
                                Spacer(Modifier.width(12.dp))
                                Button(onClick = { nav.navigate("patients/edit/0?templateId=${t.id}") }) { Text("Применить") }
                            }
                        }
                    }
                }
            }
        }
    }

    deleteTarget?.let { t ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Удалить шаблон?") },
            text = { Text("Шаблон «${t.name}» будет удалён. Существующие пациенты не изменятся.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteTemplate(t); deleteTarget = null }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Отмена") } },
        )
    }
}