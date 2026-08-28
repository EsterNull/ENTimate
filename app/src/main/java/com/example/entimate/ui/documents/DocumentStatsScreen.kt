package com.example.entimate.ui.documents

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.entimate.EntimateApplication
import com.example.entimate.data.local.DocumentChangeEntity
import com.example.entimate.data.local.DocumentEntity
import com.example.entimate.viewmodel.SettingsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentStatsScreen(nav: NavController, docId: Long, settingsVm: SettingsViewModel = viewModel()) {
    val app = (LocalContext.current.applicationContext as EntimateApplication)
    val settings by settingsVm.settings.collectAsStateWithLifecycle()
    val dateFmt = remember(settings.dateFormat) { SimpleDateFormat(settings.dateFormat + " HH:mm", Locale.getDefault()) }
    val changes by app.documentRepository.changesFlow(docId).collectAsStateWithLifecycle(emptyList())
    var doc by remember { mutableStateOf<DocumentEntity?>(null) }
    LaunchedEffect(docId) {
        doc = app.documentRepository.getById(docId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(doc?.name ?: "Статистика") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        if (changes.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("Нет данных об изменениях количества.")
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
            ) {
                val axisMax = remember(changes) {
                    val maxQ = changes.maxOfOrNull { it.qtyAfter.toFloat() } ?: 0f
                    niceCeil(maxQ)
                }

                var patientNames by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
                LaunchedEffect(changes) {
                    val ids = changes.mapNotNull { if (it.patientId != 0L) it.patientId else null }.distinct()
                    val map = mutableMapOf<Long, String>()
                    for (id in ids) {
                        app.patientRepository.getPatient(id)?.let { pw ->
                            val p = pw.patient
                            val name = listOf(p.lastName, p.firstName, p.middleName).filter { it.isNotBlank() }.joinToString(" ")
                            map[id] = name.ifBlank { if (p.number != 0) "Пациент №${p.number}" else "Пациент" }
                        }
                    }
                    patientNames = map
                }

                Text(
                    text = "График изменения количества",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(8.dp))
                QuantityChart(
                    changes = changes,
                    axisMax = axisMax,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "История изменений",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(8.dp))
                val orderedForList = remember(changes) { changes.reversed() }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(orderedForList) { change ->
                        ChangeRow(
                            change,
                            dateFmt = dateFmt,
                            patientName = patientNames[change.patientId],
                            onPatientClick = { id -> nav.navigate("patients/edit/$id") },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuantityChart(
    changes: List<DocumentChangeEntity>,
    axisMax: Float,
) {
    val axisColor = MaterialTheme.colorScheme.outline
    val lineColor = MaterialTheme.colorScheme.primary
    val ordered = remember(changes) { changes.sortedBy { it.timestamp } }
    val ticks = 4
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .width(44.dp)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End,
        ) {
            for (i in ticks downTo 0) {
                Text(
                    text = (axisMax * i / ticks).toInt().toString(),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Canvas(
            modifier = Modifier
                .fillMaxHeight()
                .weight(1f),
        ) {
            val w = size.width
            val h = size.height

            for (i in 0..ticks) {
                val y = h - (i.toFloat() / ticks) * h
                drawLine(
                    axisColor.copy(alpha = 0.4f),
                    Offset(0f, y),
                    Offset(w, y),
                    strokeWidth = 1f,
                )
            }
            drawLine(axisColor, Offset(0f, 0f), Offset(0f, h), strokeWidth = 1.5f)
            drawLine(axisColor, Offset(0f, h), Offset(w, h), strokeWidth = 1.5f)

            fun px(index: Int, qty: Float): Offset {
                val xFrac = if (ordered.size <= 1) 0f else index.toFloat() / (ordered.size - 1)
                val yFrac = (qty / axisMax).coerceIn(0f, 1f)
                return Offset(xFrac * w, h - yFrac * h)
            }

            for (i in 0 until ordered.size - 1) {
                drawLine(
                    lineColor,
                    px(i, ordered[i].qtyAfter.toFloat()),
                    px(i + 1, ordered[i + 1].qtyAfter.toFloat()),
                    strokeWidth = 3f,
                )
            }
            ordered.forEachIndexed { i, c ->
                drawCircle(lineColor, radius = 4.5f, center = px(i, c.qtyAfter.toFloat()))
            }
        }
    }
}

private fun niceCeil(value: Float): Float {
    if (value <= 0f) return 10f
    val exp = kotlin.math.floor(kotlin.math.log10(value)).toInt()
    val base = Math.pow(10.0, exp.toDouble()).toFloat()
    val f = value / base
    val nice = when {
        f <= 1f -> 1f
        f <= 2f -> 2f
        f <= 5f -> 5f
        else -> 10f
    }
    return nice * base
}

@Composable
private fun ChangeRow(change: DocumentChangeEntity, dateFmt: SimpleDateFormat, patientName: String? = null, onPatientClick: (Long) -> Unit = {}) {
    val positive = change.delta >= 0
    val deltaColor = if (positive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    val clickable = change.patientId != 0L
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (clickable) Modifier.clickable { onPatientClick(change.patientId) } else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                text = dateFmt.format(Date(change.timestamp)),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (patientName != null) {
                Text(
                    text = "Пациент: $patientName",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (clickable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (positive) "+${change.delta}" else "${change.delta}",
                color = deltaColor,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "→ ${change.qtyAfter}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}
