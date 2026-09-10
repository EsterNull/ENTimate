package com.example.entimate.ui.patients

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.entimate.data.local.PatientEntity
import com.example.entimate.data.local.PatientWithValues
import com.example.entimate.data.local.PATIENT_FIELDS
import com.example.entimate.data.local.patientValue
import com.example.entimate.ui.components.DateField
import com.example.entimate.ui.components.LocalTutorial
import com.example.entimate.ui.components.SwipeableRow
import com.example.entimate.ui.components.tutorialAnchor
import com.example.entimate.ui.folders.CurrentFolderBar
import com.example.entimate.viewmodel.PatientsViewModel
import com.example.entimate.viewmodel.SettingsViewModel
import com.example.entimate.util.normalKey
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PatientsScreen(nav: NavController, vm: PatientsViewModel = viewModel()) {
    val patients by vm.patients.collectAsStateWithLifecycle()
    val settingsVm: SettingsViewModel = viewModel()
    val settings by settingsVm.settings.collectAsStateWithLifecycle()
    val customFields by vm.customFields.collectAsStateWithLifecycle()
    val activePatients = patients.filter { it.patient.discharged != 1 }
    val dischargedPatients = patients.filter { it.patient.discharged == 1 }
    val visiblePatients = if (settings.showDischarged) activePatients + dischargedPatients else activePatients
    var showSearch by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val searchQuery = query.trim()
    val filteredPatients = if (searchQuery.isBlank()) {
        visiblePatients
    } else {
        visiblePatients.filter { pw ->
            listOf(pw.patient.lastName, pw.patient.firstName, pw.patient.middleName).any { it.normalKey().contains(searchQuery.normalKey()) }
        }
    }
    var pendingDelete by remember { mutableStateOf<PatientWithValues?>(null) }
    var pendingDischarge by remember { mutableStateOf<PatientWithValues?>(null) }
    var pendingReregister by remember { mutableStateOf<PatientWithValues?>(null) }
    var dossierPatient by remember { mutableStateOf<PatientWithValues?>(null) }
    var reordering by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val tutorial = LocalTutorial.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.effectEvents.collect { snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Long) }
    }

    val listState = rememberLazyListState()

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
            text = { Text("Пациент «${pendingDischarge!!.patient.lastName} ${pendingDischarge!!.patient.firstName}» будет отмечен как выписанный. Данные останутся в отчётах. Количество документов при этом не изменится.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.dischargePatient(pendingDischarge!!.patient)
                    pendingDischarge = null
                }) { Text("Выписать", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDischarge = null }) { Text("Отмена") } },
        )
    }

    if (pendingReregister != null) {
        val todayStart = remember {
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }
        val todayIso = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
        var reDate by remember(pendingReregister) { mutableStateOf(todayIso) }
        var reNumber by remember(pendingReregister) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pendingReregister = null },
            title = { Text("Переоформление") },
            text = {
                Column {
                    Text("Переоформить пациента «${pendingReregister!!.patient.lastName} ${pendingReregister!!.patient.firstName}»? Старая карточка будет отмечена как выписанная, а создана новая с той же информацией, кроме номера, даты поступления, начала заболевания/травмы (приравнивается к дате поступления), поля «Кем направлен больной» (очищается) и пользовательских полей (не заполняются).")
                    Spacer(Modifier.height(12.dp))
                    DateField(
                        value = reDate,
                        onValueChange = { reDate = it },
                        label = "Дата поступления",
                        maxDate = todayStart,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = reNumber,
                        onValueChange = { v -> reNumber = v.filter { it.isDigit() } },
                        label = { Text("Номер (необязательно)") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = reDate.isNotBlank(),
                    onClick = {
                        val newNumber = reNumber.toIntOrNull()
                        vm.reregisterPatient(pendingReregister!!.patient, reDate, newNumber)
                        pendingReregister = null
                    },
                ) { Text("Переоформить") }
            },
            dismissButton = { TextButton(onClick = { pendingReregister = null }) { Text("Отмена") } },
        )
    }

    if (dossierPatient != null) {
        ModalBottomSheet(onDismissRequest = { dossierPatient = null }) {
            PatientDossierSheet(
                pw = dossierPatient!!,
                dateFormat = settings.dateFormat,
                customFields = customFields,
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Пациенты") },
                actions = {
                    if (reordering) {
                        IconButton(onClick = { reordering = false }) {
                            Icon(Icons.Filled.Check, contentDescription = "Завершить изменение порядка")
                        }
                    } else {
                        IconButton(onClick = { tutorial?.start() }) {
                            Icon(Icons.Filled.Help, contentDescription = "Обучение")
                        }
                        IconButton(onClick = { showSearch = !showSearch; if (!showSearch) query = "" }) {
                            Icon(Icons.Filled.Search, contentDescription = "Поиск")
                        }
                        IconButton(onClick = { nav.navigate("patients/links") }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Настройки связей с документами")
                        }
                        IconButton(onClick = { nav.navigate("patienttemplates") }) {
                            Icon(Icons.Filled.Layers, contentDescription = "Шаблоны пациентов")
                        }
                        IconButton(onClick = { reordering = true }, enabled = searchQuery.isBlank()) {
                            Icon(Icons.Filled.DragHandle, contentDescription = "Изменить порядок")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (showSearch) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Поиск по имени") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    )
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Filled.Close, contentDescription = "Очистить")
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                if (filteredPatients.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (searchQuery.isBlank()) "Нет пациентов.\nНажмите «+», чтобы добавить." else "Ничего не найдено.",
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        itemsIndexed(filteredPatients, key = { _, pw -> pw.patient.id }) { index, pw ->
                            if (reordering) {
                                if (pw.patient.discharged == 1) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .animateItem(),
                                    ) {
                                        Box(Modifier.weight(1f)) {
                                            PatientCard(
                                                p = pw.patient,
                                                dateFormat = settings.dateFormat,
                                                onClick = {},
                                                onDischarge = {},
                                                onReregister = {},
                                            )
                                        }
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .animateItem(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column {
                                            IconButton(
                                                onClick = { if (index > 0) scope.launch { vm.reorder(index, index - 1) } },
                                                enabled = index > 0,
                                            ) { Icon(Icons.Filled.ArrowDropUp, contentDescription = "Вверх") }
                                            IconButton(
                                                onClick = { if (index < activePatients.lastIndex) scope.launch { vm.reorder(index, index + 1) } },
                                                enabled = index < activePatients.lastIndex,
                                            ) { Icon(Icons.Filled.ArrowDropDown, contentDescription = "Вниз") }
                                        }
                                        Box(Modifier.weight(1f)) {
                                            PatientCard(
                                                p = pw.patient,
                                                dateFormat = settings.dateFormat,
                                                onClick = {},
                                                onDischarge = {},
                                                onReregister = {},
                                            )
                                        }
                                    }
                                }
                            } else {
                            SwipeableRow(
                                onSwipeLeft = { pendingDelete = pw },
                                onSwipeRight = { nav.navigate("patients/edit/${pw.patient.id}") },
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
                                PatientCard(
                                    p = pw.patient,
                                    dateFormat = settings.dateFormat,
                                    onClick = { dossierPatient = pw },
                                    onDischarge = { pendingDischarge = pw },
                                    onReregister = { pendingReregister = pw },
                                )
                            }
                        }
                    }
                }
            }
            CurrentFolderBar(
                modifier = Modifier,
                onOpenFolders = { nav.navigate("folders") },
                onAdd = { nav.navigate("patients/edit/0") },
            )
        }
    }
}
}

@Composable
private fun PatientCard(p: PatientEntity, dateFormat: String = "dd.MM.yyyy", onClick: () -> Unit, onDischarge: () -> Unit, onReregister: () -> Unit = {}) {
    val fio = listOf(p.lastName, p.firstName, p.middleName).filter { it.isNotBlank() }.joinToString(" ")
    val formattedAdmission = remember(p.admissionDate, dateFormat) {
        if (p.admissionDate.isNotBlank()) {
            try {
                val isoFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val displayFmt = SimpleDateFormat(dateFormat, Locale.getDefault())
                isoFmt.parse(p.admissionDate)?.let { displayFmt.format(it) } ?: p.admissionDate
            } catch (_: Exception) { p.admissionDate }
        } else ""
    }
    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onReregister),
        colors = CardDefaults.cardColors(containerColor = if (p.colorArgb != 0) Color(p.colorArgb) else MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (p.number != 0) {
                    Text("№${p.number}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                if (p.soch == 1) {
                    Spacer(Modifier.width(8.dp))
                    Text("СОЧ", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
                if (p.discharged == 1) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "ВЫПИСАН",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(fio.ifBlank { "Без имени" }, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            val details = listOf(p.rank, p.unit).filter { it.isNotBlank() }.joinToString(" · ")
            if (details.isNotBlank()) {
                Text(details, style = MaterialTheme.typography.bodyMedium)
            }
            if (formattedAdmission.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text("Поступление: $formattedAdmission", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            }
            if (p.discharged == 1 && p.dischargeDate.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text("Выписан: ${formatDischargeDate(p.dischargeDate, dateFormat)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            }
            Spacer(Modifier.height(8.dp))
            if (p.discharged != 1) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(
                        onClick = onDischarge,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                    ) { Text("Выписать") }
                }
            }
        }
    }
}

private fun formatDischargeDate(iso: String, dateFormat: String): String = try {
    val isoFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val displayFmt = SimpleDateFormat(dateFormat, Locale.getDefault())
    isoFmt.parse(iso)?.let { displayFmt.format(it) } ?: iso
} catch (_: Exception) {
    iso
}

@Composable
private fun PatientDossierSheet(pw: PatientWithValues, dateFormat: String, customFields: List<com.example.entimate.data.local.PatientCustomFieldEntity>) {
    val p = pw.patient
    val fio = listOf(p.lastName, p.firstName, p.middleName).filter { it.isNotBlank() }.joinToString(" ")
    val dateKeys = setOf("birthDate", "serviceDate", "admissionDate", "illnessStart", "dischargeDate")
    val displayFmt = remember(dateFormat) { SimpleDateFormat(dateFormat, Locale.getDefault()) }
    val isoFmt = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val customMap = remember(pw.customValues) { pw.customValues.associateBy { it.fieldId } }

    fun formatVal(key: String, raw: String): String {
        if (raw.isBlank()) return ""
        if (key in dateKeys) {
            return try { isoFmt.parse(raw)?.let { displayFmt.format(it) } ?: raw } catch (_: Exception) { raw }
        }
        if (key == "svo" || key == "soch") return if (raw == "true") "Да" else "Нет"
        return raw
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
    ) {
        if (fio.isNotBlank()) {
            Text(fio, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
        }
        val mainNumber = listOf(p.rank, p.unit).filter { it.isNotBlank() }.joinToString(" · ")
        if (mainNumber.isNotBlank()) {
            Text(mainNumber, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {
            PATIENT_FIELDS.forEach { def ->
                val raw = patientValue(p, def.key)
                val display = formatVal(def.key, raw)
                DossierRow(label = def.label, value = display.ifBlank { "—" })
            }

            customFields.forEach { cf ->
                val raw = customMap[cf.id]?.value ?: ""
                DossierRow(label = cf.label, value = raw.ifBlank { "—" })
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DossierRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
