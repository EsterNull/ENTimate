package com.example.entimate.ui.patients

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.entimate.data.local.PatientEntity
import com.example.entimate.data.local.PatientWithValues
import com.example.entimate.ui.components.LocalTutorial
import com.example.entimate.ui.components.rememberReorderState
import com.example.entimate.ui.components.tutorialAnchor
import com.example.entimate.viewmodel.PatientsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PatientsScreen(nav: NavController, vm: PatientsViewModel = viewModel()) {
    val patients by vm.patients.collectAsStateWithLifecycle()
    val activePatients = patients.filter { it.patient.discharged != 1 }
    var pendingDelete by remember { mutableStateOf<PatientWithValues?>(null) }
    var pendingDischarge by remember { mutableStateOf<PatientWithValues?>(null) }
    var reordering by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val tutorial = LocalTutorial.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.effectEvents.collect { snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Long) }
    }

    val listState = rememberLazyListState()
    val reorderState = rememberReorderState(
        lazyListState = listState,
        items = activePatients,
        keyOf = { it.patient.id },
        onReorder = { from, to -> vm.reorder(from, to) },
    )

    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Удалить пациента?") },
            text = {
                Text(
                    "Пациент «${pendingDelete!!.patient.lastName} ${pendingDelete!!.patient.firstName}» будет удалён безвозвратно. " +
                        "Изменения количества документов, произошедшие из-за добавления этого пациента, будут отменены.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deletePatient(pendingDelete!!.patient)
                    pendingDelete = null
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Отмена") } },
        )
    }

    if (pendingDischarge != null) {
        AlertDialog(
            onDismissRequest = { pendingDischarge = null },
            title = { Text("Выписать пациента?") },
            text = { Text("Пациент «${pendingDischarge!!.patient.lastName} ${pendingDischarge!!.patient.firstName}» будет отмечен как выписанный. Карточка исчезнет из списка, но данные останутся в отчётах. Количество документов при этом не изменится.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.dischargePatient(pendingDischarge!!.patient)
                    pendingDischarge = null
                }) { Text("Выписать", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDischarge = null }) { Text("Отмена") } },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Пациенты") },
                actions = {
                    if (reordering) {
                        IconButton(onClick = { reordering = false }) {
                            Icon(Icons.Filled.Close, contentDescription = "Завершить изменение порядка")
                        }
                    } else {
                        IconButton(onClick = { tutorial?.start() }) {
                            Icon(Icons.Filled.Help, contentDescription = "Обучение")
                        }
                        IconButton(onClick = { nav.navigate("patients/links") }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Настройки связей с документами")
                        }
                        IconButton(onClick = { nav.navigate("patients/edit/0") }) {
                            Icon(Icons.Filled.Add, contentDescription = "Добавить пациента")
                        }
                        IconButton(onClick = { reordering = true }) {
                            Icon(Icons.Filled.DragHandle, contentDescription = "Изменить порядок")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (activePatients.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("Нет пациентов.\nНажмите + вверху, чтобы добавить.", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(activePatients, key = { _, pw -> pw.patient.id }) { index, pw ->
                    if (reordering) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (reorderState.draggingKey != pw.patient.id) Modifier.animateItem() else Modifier)
                                .then(reorderState.draggedItemModifier(pw)),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.DragHandle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(36.dp)
                                    .padding(6.dp)
                                    .then(reorderState.handleModifier(pw)),
                            )
                            Box(Modifier.weight(1f)) {
                                PatientCard(
                                    p = pw.patient,
                                    onClick = {},
                                    onDischarge = {},
                                )
                            }
                        }
                    } else {
                        val dismissState = rememberSwipeToDismissBoxState(
                            positionalThreshold = { it * 0.85f },
                            confirmValueChange = { value ->
                                when (value) {
                                    SwipeToDismissBoxValue.EndToStart -> { pendingDelete = pw; false }
                                    SwipeToDismissBoxValue.StartToEnd -> { nav.navigate("patients/edit/${pw.patient.id}"); false }
                                    else -> false
                                }
                            }
                        )
                        SwipeToDismissBox(
                            state = dismissState,
                            backgroundContent = {
                                val direction = dismissState.dismissDirection
                                val bg = when (direction) {
                                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                                    SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primaryContainer
                                    else -> Color.Transparent
                                }
                                val icon = when (direction) {
                                    SwipeToDismissBoxValue.EndToStart -> Icons.Filled.Delete
                                    SwipeToDismissBoxValue.StartToEnd -> Icons.Filled.Edit
                                    else -> null
                                }
                                Box(
                                    Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium).background(bg).padding(horizontal = 24.dp),
                                    contentAlignment = if (direction == SwipeToDismissBoxValue.EndToStart) Alignment.CenterEnd else Alignment.CenterStart,
                                ) {
                                    icon?.let { Icon(it, contentDescription = null, tint = if (direction == SwipeToDismissBoxValue.EndToStart) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
                                }
                            },
                        ) {
                            PatientCard(
                                p = pw.patient,
                                onClick = { },
                                onDischarge = { pendingDischarge = pw },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PatientCard(p: PatientEntity, onClick: () -> Unit, onDischarge: () -> Unit) {
    val fio = listOf(p.lastName, p.firstName, p.middleName).filter { it.isNotBlank() }.joinToString(" ")
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = if (p.colorArgb != 0) Color(p.colorArgb) else MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("№${p.number}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                if (p.soch == 1) {
                    Spacer(Modifier.width(8.dp))
                    Text("СОЧ", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(fio.ifBlank { "Без имени" }, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            val details = listOf(p.rank, p.unit).filter { it.isNotBlank() }.joinToString(" · ")
            if (details.isNotBlank()) {
                Text(details, style = MaterialTheme.typography.bodyMedium)
            }
            if (p.admissionDate.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text("Поступление: ${p.admissionDate}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(
                    onClick = onDischarge,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                ) { Text("Выписать") }
            }
        }
    }
}
