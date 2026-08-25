package com.example.entimate.ui.reports

import android.app.DatePickerDialog
import android.widget.DatePicker
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.entimate.ui.components.colorLuminance
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.entimate.data.local.ReportWithColumns
import com.example.entimate.viewmodel.ReportsViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(nav: NavController, vm: ReportsViewModel = viewModel()) {
    val reports by vm.reports.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = context.applicationContext as com.example.entimate.EntimateApplication
    val repo = app.reportRepository

    var pendingDup by remember { mutableStateOf<ReportWithColumns?>(null) }
    var generateTarget by remember { mutableStateOf<ReportWithColumns?>(null) }
    var fromMillis by remember { mutableStateOf(System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000) }
    var toMillis by remember { mutableStateOf(System.currentTimeMillis()) }

    if (generateTarget != null) {
        val fmt = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        AlertDialog(
            onDismissRequest = { generateTarget = null },
            title = { Text("Сформировать отчёт") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Период")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        val now = System.currentTimeMillis()
                        OutlinedButton(onClick = { fromMillis = now - 7L * 24 * 60 * 60 * 1000; toMillis = now }, modifier = Modifier.weight(1f)) { Text("За неделю") }
                        OutlinedButton(onClick = { fromMillis = now - 30L * 24 * 60 * 60 * 1000; toMillis = now }, modifier = Modifier.weight(1f)) { Text("За месяц") }
                        OutlinedButton(onClick = { fromMillis = 0L; toMillis = now }, modifier = Modifier.weight(1f)) { Text("За всё время") }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = {
                            val cal = Calendar.getInstance().apply { timeInMillis = fromMillis }
                            DatePickerDialog(
                                context,
                                { _: DatePicker, y, m, d -> cal.set(y, m, d); fromMillis = cal.timeInMillis },
                                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH),
                            ).show()
                        }, modifier = Modifier.fillMaxWidth()) { Text("С: ${fmt.format(Date(fromMillis))}") }
                        OutlinedButton(onClick = {
                            val cal = Calendar.getInstance().apply { timeInMillis = toMillis }
                            DatePickerDialog(
                                context,
                                { _: DatePicker, y, m, d -> cal.set(y, m, d, 23, 59, 59); toMillis = cal.timeInMillis },
                                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH),
                            ).show()
                        }, modifier = Modifier.fillMaxWidth()) { Text("По: ${fmt.format(Date(toMillis))}") }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = generateTarget!!
                    generateTarget = null
                    nav.navigate("reports/preview/${target.report.id}/$fromMillis/$toMillis")
                }) { Text("Сформировать") }
            },
            dismissButton = { TextButton(onClick = { generateTarget = null }) { Text("Отмена") } },
        )
    }

    if (pendingDup != null) {
        AlertDialog(
            onDismissRequest = { pendingDup = null },
            title = { Text("Дублировать отчёт?") },
            text = { Text("Будет создана копия отчёта «${pendingDup!!.report.name}».") },
            confirmButton = {
                TextButton(onClick = {
                    val r = pendingDup!!
                    scope.launch {
                        val newId = vm.duplicateReport(r.report.id)
                        pendingDup = null
                        nav.navigate("reports/edit/$newId")
                    }
                }) { Text("Дублировать") }
            },
            dismissButton = { TextButton(onClick = { pendingDup = null }) { Text("Отмена") } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Отчёты") },
                actions = {
                    IconButton(onClick = { nav.navigate("reports/edit/0") }) {
                        Icon(Icons.Filled.Add, contentDescription = "Добавить отчёт")
                    }
                },
            )
        },
    ) { padding ->
        if (reports.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { Text("Нет отчётов.\nНажмите + чтобы создать.", textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(reports, key = { it.report.id }) { r ->
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            when (value) {
                                SwipeToDismissBoxValue.EndToStart -> { vm.delete(r.report); false }
                                SwipeToDismissBoxValue.StartToEnd -> { nav.navigate("reports/edit/${r.report.id}"); false }
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
                                Modifier
                                    .fillMaxSize()
                                    .clip(MaterialTheme.shapes.medium)
                                    .background(bg)
                                    .padding(horizontal = 24.dp),
                                contentAlignment = if (direction == SwipeToDismissBoxValue.EndToStart) Alignment.CenterEnd else Alignment.CenterStart,
                            ) {
                                icon?.let {
                                    Icon(
                                        it,
                                        contentDescription = null,
                                        tint = if (direction == SwipeToDismissBoxValue.EndToStart) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        },
                    ) {
                        ReportCard(
                            report = r,
                            onGenerate = { fromMillis = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000; toMillis = System.currentTimeMillis(); generateTarget = r },
                            onLongClick = { pendingDup = r },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportCard(
    report: ReportWithColumns,
    onGenerate: () -> Unit,
    onLongClick: () -> Unit = {},
) {
    val colorArgb = report.report.colorArgb
    val hasColor = colorArgb != 0
    val bg = if (hasColor) Color(colorArgb) else MaterialTheme.colorScheme.surfaceVariant
    val onBg = if (hasColor) {
        if (colorLuminance(Color(colorArgb)) > 0.5f) Color.Black else Color.White
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(modifier = Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = onLongClick), colors = CardDefaults.cardColors(containerColor = bg, contentColor = onBg)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.TableChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(report.report.name.ifBlank { "Без названия" }, style = MaterialTheme.typography.titleMedium, color = onBg)
            }
            if (report.report.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(report.report.description, color = onBg.copy(alpha = 0.7f))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onGenerate,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) { Text("Сформировать") }
            }
        }
    }
}
