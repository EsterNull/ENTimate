package com.example.entimate.ui.reports

import com.example.entimate.ui.navigation.navigateBack

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.app.DatePickerDialog
import android.content.Intent
import android.widget.DatePicker
import androidx.compose.foundation.background
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import java.io.File

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
    var addNumber by remember { mutableStateOf(true) }
    var addBorder by remember { mutableStateOf(true) }

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
                "PDF" -> os.write(ReportExporter.renderPdf(context, t.headers, t.rows, t.colAligns))
                else -> os.write(ReportExporter.buildXlsx(t.headers, t.rows, withBorder = addBorder))
            }
        }
    }

    val shareLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    LaunchedEffect(reportId, periodFrom, periodTo, addNumber) {
        val report = repo.getReportWithColumns(reportId)
        reportName = report?.report?.name?.ifBlank { "report" } ?: "report"
        table = repo.buildTable(reportId, periodFrom, periodTo, settings.dateFormat, withNumber = addNumber)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Предпросмотр") },
                navigationIcon = {
                    IconButton(onClick = { nav.navigateBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val t = table ?: return@IconButton
                        val ext = when (format) {
                            "CSV" -> "csv"
                            "PDF" -> "pdf"
                            else -> "xlsx"
                        }
                        val mime = when (format) {
                            "CSV" -> "text/csv"
                            "PDF" -> "application/pdf"
                            else -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                        }
                        val dateStr = SimpleDateFormat(settings.dateFormat, Locale.getDefault()).format(Date()).replace(Regex("[.\\-/]"), "_")
                        val fileName = "${reportName}_$dateStr.$ext"
                        val bytes = when (format) {
                            "CSV" -> ReportExporter.buildCsv(t.headers, t.rows).toByteArray(Charsets.UTF_8)
                            "PDF" -> ReportExporter.renderPdf(context, t.headers, t.rows, t.colAligns)
                            else -> ReportExporter.buildXlsx(t.headers, t.rows, withBorder = addBorder)
                        }
                        val cacheFile = File(context.cacheDir, fileName)
                        cacheFile.writeBytes(bytes)
                        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cacheFile)
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = mime
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        shareLauncher.launch(Intent.createChooser(shareIntent, "Поделиться отчётом"))
                    }) { Icon(Icons.Filled.Share, contentDescription = "Поделиться") }
                    IconButton(onClick = {
                        val ext = when (format) {
                            "CSV" -> "csv"
                            "PDF" -> "pdf"
                            else -> "xlsx"
                        }
                        val dateStr = SimpleDateFormat(settings.dateFormat, Locale.getDefault()).format(Date()).replace(Regex("[.\\-/]"), "_")
                        launcher.launch("${reportName}_$dateStr.$ext")
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = addNumber, onCheckedChange = { addNumber = it })
                Spacer(Modifier.width(6.dp))
                Text("Добавить столбец № (нумерация)", style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = addBorder, onCheckedChange = { addBorder = it }, enabled = format == "XLSX")
                Spacer(Modifier.width(6.dp))
                Text("Добавить обводку таблицы", style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.height(4.dp))
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
