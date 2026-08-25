package com.example.entimate.ui.reports

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.app.DatePickerDialog
import android.widget.DatePicker
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.entimate.EntimateApplication
import com.example.entimate.data.repository.ReportRepository
import com.example.entimate.viewmodel.ReportsViewModel
import com.example.entimate.viewmodel.SettingsViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportPreviewScreen(
    reportId: Long,
    from: Long,
    to: Long,
    nav: NavController,
    vm: ReportsViewModel = viewModel(),
    settingsVm: SettingsViewModel = viewModel(),
) {
    val app = LocalContext.current.applicationContext as EntimateApplication
    val repo = app.reportRepository
    val context = LocalContext.current
    val settings by settingsVm.settings.collectAsStateWithLifecycle()
    val dateFmt = remember(settings.dateFormat) { SimpleDateFormat(settings.dateFormat, Locale.getDefault()) }

    var periodFrom by remember { mutableStateOf(from) }
    var periodTo by remember { mutableStateOf(to) }
    var table by remember { mutableStateOf<ReportRepository.Table?>(null) }
    var format by remember { mutableStateOf("XLSX") }
    var reportName by remember { mutableStateOf("report") }

    fun showDatePicker(initialMillis: Long, onPicked: (Long) -> Unit) {
        val cal = Calendar.getInstance().apply { timeInMillis = initialMillis }
        DatePickerDialog(
            context,
            { _: DatePicker, y, m, d ->
                val picked = Calendar.getInstance().apply { set(y, m, d, 0, 0, 0); set(Calendar.MILLISECOND, 0) }
                onPicked(picked.timeInMillis)
            },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*"),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val t = table ?: return@rememberLauncherForActivityResult
        context.contentResolver.openOutputStream(uri)?.use { os ->
            when (format) {
                "CSV" -> os.write(ReportExporter.buildCsv(t.headers, t.rows).toByteArray(Charsets.UTF_8))
                "PDF" -> os.write(ReportExporter.renderPdf(context, t.headers, t.rows))
                else -> os.write(ReportExporter.buildXlsx(t.headers, t.rows))
            }
        }
    }

    LaunchedEffect(reportId, periodFrom, periodTo) {
        val report = repo.getReportWithColumns(reportId)
        reportName = report?.report?.name?.ifBlank { "report" } ?: "report"
        table = repo.buildTable(reportId, periodFrom, periodTo, settings.dateFormat)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Предпросмотр отчёта") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val ext = when (format) {
                            "CSV" -> "csv"
                            "PDF" -> "pdf"
                            else -> "xlsx"
                        }
                        launcher.launch("${reportName}_отчёт.$ext")
                    }) { Icon(Icons.Filled.Save, contentDescription = "Сохранить") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            Text("Период формирования", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showDatePicker(periodFrom) { periodFrom = it; periodTo = maxOf(periodTo, it) } }) {
                    Text("С: ${dateFmt.format(Date(periodFrom))}")
                }
                OutlinedButton(onClick = { showDatePicker(periodTo) { periodTo = it; periodFrom = minOf(periodFrom, it) } }) {
                    Text("По: ${dateFmt.format(Date(periodTo))}")
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = format == "XLSX", onClick = { format = "XLSX" }, label = { Text("XLSX") })
                FilterChip(selected = format == "CSV", onClick = { format = "CSV" }, label = { Text("CSV") })
                FilterChip(selected = format == "PDF", onClick = { format = "PDF" }, label = { Text("PDF") })
            }
            Spacer(Modifier.height(8.dp))
            when {
                table == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                table!!.rows.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Нет данных за выбранный период.")
                    }
                }
                else -> {
                    LazyColumn(Modifier.fillMaxSize()) {
                        item { TableHeaderRow(table!!.headers) }
                        items(table!!.rows) { TableDataRow(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TableHeaderRow(headers: List<String>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(vertical = 4.dp),
    ) {
        headers.forEach { h ->
            Text(
                h,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun TableDataRow(row: List<String>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        row.forEach { cell ->
            Text(
                cell,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}
