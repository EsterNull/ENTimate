package com.example.entimate.ui.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignJustify
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.entimate.EntimateApplication
import com.example.entimate.data.local.AUTO_COLOR
import com.example.entimate.data.local.ManualTable
import com.example.entimate.data.local.TableCell
import com.example.entimate.data.local.manualTableFromJson
import com.example.entimate.data.local.manualTableToJson
import com.example.entimate.data.local.ReportDocElementEntity
import com.example.entimate.data.local.ReportEntity
import com.example.entimate.data.local.ReportParagraphEntity
import com.example.entimate.ui.components.ColorRow
import com.example.entimate.ui.stripNewlines
import com.example.entimate.viewmodel.ReportsViewModel
import kotlinx.coroutines.launch

private val FONT_OPTIONS = listOf("Times New Roman", "Arial", "Calibri", "Courier New", "Georgia")
private val ALIGN_OPTIONS = listOf(
    "LEFT" to Icons.AutoMirrored.Filled.FormatAlignLeft,
    "CENTER" to Icons.Filled.FormatAlignCenter,
    "RIGHT" to Icons.AutoMirrored.Filled.FormatAlignRight,
    "JUSTIFY" to Icons.Filled.FormatAlignJustify,
)
private val LINE_SPACING_OPTIONS = listOf(
    1f to "Одинарный",
    1.15f to "1,15",
    1.5f to "Полуторный",
    2f to "Двойной",
)
private val FIRST_LINE_OPTIONS = listOf("Нет" to "Нет", "Отступ" to "Отступ", "Выступ" to "Выступ")
private val PRESET_COLORS = listOf(
    AUTO_COLOR to "Авто",
    0xFFFFFFFF.toInt() to "Белый",
    0xFF000000.toInt() to "Чёрный",
    0xFFB71C1C.toInt() to "Красный",
    0xFF1B5E20.toInt() to "Зелёный",
    0xFF0D47A1.toInt() to "Синий",
    0xFFF9A825.toInt() to "Жёлтый",
    0xFF4A148C.toInt() to "Фиолетовый",
    0xFFE65100.toInt() to "Оранжевый",
)

private fun colorLabel(argb: Int): String = PRESET_COLORS.firstOrNull { it.first == argb }?.second ?: "Цвет"

private data class ElementDraft(
    val id: Long,
    val type: String = "TEXT",
    val text: String = "",
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val colorArgb: Int = AUTO_COLOR,
    val bgArgb: Int = AUTO_COLOR,
    val size: String = "12",
    val align: String = "LEFT",
    val minRows: Int = 0,
    val numbering: Boolean = false,
    val joinPrevious: Boolean = false,
    val border: Int = 1,
    val colWeights: String = "",
    val embeddedReportId: Long = 0,
    val embeddedTitle: String = "",
    val manualTable: ManualTable = ManualTable("", emptyList(), emptyList()),
)

private data class ParagraphDraft(
    val id: Long,
    val font: String = "Times New Roman",
    val align: String = "LEFT",
    val indentLeftMm: Float = 0f,
    val indentRightMm: Float = 0f,
    val firstLineMm: Float = 0f,
    val lineSpacing: Float = 1f,
    val spaceBeforeMm: Float = 0f,
    val spaceAfterMm: Float = 0f,
    val elements: List<ElementDraft> = emptyList(),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentReportEditor(reportId: Long, nav: NavController, vm: ReportsViewModel = viewModel()) {
    val app = LocalContext.current.applicationContext as EntimateApplication
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(0) }
    var marginTopMm by remember { mutableStateOf(25.4f) }
    var marginRightMm by remember { mutableStateOf(25.4f) }
    var marginBottomMm by remember { mutableStateOf(25.4f) }
    var marginLeftMm by remember { mutableStateOf(25.4f) }
    var nameError by remember { mutableStateOf(false) }
    var paragraphs by remember { mutableStateOf(listOf<ParagraphDraft>()) }
    var loaded by remember { mutableStateOf(reportId == 0L) }
    var tableReports by remember { mutableStateOf(listOf<ReportEntity>()) }
    var nextId by remember { mutableStateOf(-1L) }

    fun nid(): Long { val v = nextId; nextId -= 1; return v }

    LaunchedEffect(Unit) {
        tableReports = vm.tableReports()
        if (reportId != 0L) {
            val d = vm.getReportWithDocument(reportId)
            if (d != null) {
                name = d.report.name
                description = d.report.description
                color = d.report.colorArgb
                marginTopMm = d.report.marginTopMm
                marginRightMm = d.report.marginRightMm
                marginBottomMm = d.report.marginBottomMm
                marginLeftMm = d.report.marginLeftMm
                paragraphs = d.paragraphs.map { pw ->
                    ParagraphDraft(
                        id = pw.paragraph.id,
                        font = pw.paragraph.font.ifBlank { "Times New Roman" },
                        align = pw.paragraph.align,
                        indentLeftMm = pw.paragraph.indentLeftMm,
                        indentRightMm = pw.paragraph.indentRightMm,
                        firstLineMm = pw.paragraph.firstLineMm,
                        lineSpacing = pw.paragraph.lineSpacing,
                        spaceBeforeMm = pw.paragraph.spaceBeforeMm,
                        spaceAfterMm = pw.paragraph.spaceAfterMm,
                        elements = pw.elements.map { e ->
                            ElementDraft(
                                id = e.id,
                                type = e.type,
                                text = e.text,
                                bold = e.bold == 1,
                                italic = e.italic == 1,
                                underline = e.underline == 1,
                                size = if (e.size > 0) e.size.toString() else "",
                                colorArgb = e.colorArgb,
                                bgArgb = e.bgArgb,
                                align = e.align,
                                minRows = e.minRows,
                                numbering = e.numberColumn == 1,
                                joinPrevious = e.joinPrevious == 1,
                                border = e.border,
                                colWeights = e.colWeights,
                                embeddedReportId = e.embeddedReportId,
                                embeddedTitle = e.embeddedTitle,
                                manualTable = if (e.type == "MANUAL") manualTableFromJson(e.text) else ManualTable("", emptyList(), emptyList()),
                            )
                        },
                    )
                }
            }
            loaded = true
        }
    }

    fun saveAnd(action: (Long) -> Unit) {
        if (name.isBlank()) { nameError = true; return }
        scope.launch {
            val dup = vm.reports.value.any { it.report.id != reportId && it.report.name.equals(name.trim(), ignoreCase = true) }
            if (dup) { nameError = true; return@launch }
            val blocks = paragraphs.mapIndexed { pi, pd ->
                val p = ReportParagraphEntity(
                    id = 0, reportId = 0, position = pi, font = pd.font, align = pd.align,
                    indentLeftMm = pd.indentLeftMm, indentRightMm = pd.indentRightMm,
                    firstLineMm = pd.firstLineMm, lineSpacing = pd.lineSpacing,
                    spaceBeforeMm = pd.spaceBeforeMm, spaceAfterMm = pd.spaceAfterMm,
                )
                val els = pd.elements.mapIndexed { ei, ed ->
                    ReportDocElementEntity(
                        id = 0, paragraphId = 0, position = ei,                         type = ed.type,
                        text = if (ed.type == "MANUAL") manualTableToJson(ed.manualTable) else ed.text,
                        bold = if (ed.bold) 1 else 0, italic = if (ed.italic) 1 else 0,
                        underline = if (ed.underline) 1 else 0, size = ed.size.toIntOrNull() ?: 0,
                        colorArgb = ed.colorArgb, bgArgb = ed.bgArgb,
                        align = ed.align,
                        minRows = ed.minRows,
                        numberColumn = if (ed.numbering) 1 else 0,
                        joinPrevious = if (ed.joinPrevious) 1 else 0,
                        border = ed.border,
                        colWeights = ed.colWeights,
                        embeddedReportId = ed.embeddedReportId, embeddedTitle = ed.embeddedTitle,
                    )
                }
                p to els
            }
            val id = vm.saveDocumentReportSuspended(
                ReportEntity(
                    id = reportId, name = name.trim(), description = description.trim(), kind = "DOCUMENT", colorArgb = color,
                    marginTopMm = marginTopMm, marginRightMm = marginRightMm, marginBottomMm = marginBottomMm, marginLeftMm = marginLeftMm,
                ),
                blocks,
            )
            action(id)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (reportId == 0L) "Новый документ" else "Редактировать документ") },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") } },
                actions = {
                    IconButton(onClick = { saveAnd { id -> nav.navigate("reports/docpreview/$id/0/${System.currentTimeMillis()}") } }) {
                        Icon(Icons.Filled.TableChart, contentDescription = "Предпросмотр")
                    }
                    IconButton(onClick = { saveAnd { nav.popBackStack() } }) {
                        Icon(Icons.Filled.Check, contentDescription = "Сохранить")
                    }
                },
            )
        },
    ) { padding ->
        if (!loaded) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).imePadding(),
        ) {
            item {
                OutlinedTextField(value = name, onValueChange = { name = it.stripNewlines(); nameError = false }, label = { Text("Название") }, isError = nameError, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = description, onValueChange = { description = it.stripNewlines() }, label = { Text("Описание") }, modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp), maxLines = 4)
                Spacer(Modifier.height(12.dp))
                Text("Цвет карточки", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
            ColorRow(color = color, onColorChange = { color = it })
            Spacer(Modifier.height(16.dp))
            Text("Поля страницы (мм)", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                listOf(
                    "Обычные" to listOf(25.4f, 25.4f, 25.4f, 25.4f),
                    "Узкие" to listOf(12.7f, 12.7f, 12.7f, 12.7f),
                    "Средние" to listOf(25.4f, 19.05f, 25.4f, 19.05f),
                    "Широкие" to listOf(25.4f, 50.8f, 25.4f, 50.8f),
                ).forEach { (label, m) ->
                    FilterChip(
                        selected = false,
                        onClick = {
                            marginTopMm = m[0]; marginRightMm = m[1]; marginBottomMm = m[2]; marginLeftMm = m[3]
                        },
                        label = { Text(label) },
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("Верх, см", marginTopMm / 10f, { v -> marginTopMm = v * 10f }, Modifier.weight(1f))
                NumberField("Правый, см", marginRightMm / 10f, { v -> marginRightMm = v * 10f }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("Низ, см", marginBottomMm / 10f, { v -> marginBottomMm = v * 10f }, Modifier.weight(1f))
                NumberField("Левый, см", marginLeftMm / 10f, { v -> marginLeftMm = v * 10f }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))
            Text("Содержимое документа", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
            }

            if (paragraphs.isEmpty()) {
                item { Text("Нет абзацев. Добавьте абзац, чтобы начать.", color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.labelSmall) }
            }
            paragraphs.forEachIndexed { idx, pd ->
                item(key = "para_${pd.id}") {
                    ParagraphCard(
                        paragraph = pd,
                        index = idx,
                        total = paragraphs.size,
                        tableReports = tableReports,
                        vm = vm,
                        onUpdate = { np -> paragraphs = paragraphs.toMutableList().also { it[idx] = np } },
                        onDelete = { paragraphs = paragraphs.toMutableList().also { it.removeAt(idx) } },
                        onMove = { dir ->
                            val j = idx + dir
                            if (j in paragraphs.indices) {
                                val list = paragraphs.toMutableList()
                                val tmp = list[idx]; list[idx] = list[j]; list[j] = tmp
                                paragraphs = list
                            }
                        },
                        onAddText = { paragraphs = paragraphs.toMutableList().also { it[idx] = pd.copy(elements = pd.elements + ElementDraft(id = nid())) } },
                        onAddTable = { paragraphs = paragraphs.toMutableList().also { it[idx] = pd.copy(elements = pd.elements + ElementDraft(id = nid(), type = if (tableReports.isNotEmpty()) "REPORT" else "MANUAL", embeddedReportId = tableReports.firstOrNull()?.id ?: 0)) } },
                        onAddTab = { paragraphs = paragraphs.toMutableList().also { it[idx] = pd.copy(elements = pd.elements + ElementDraft(id = nid(), type = "TAB")) } },
                        onAddPageBreak = { paragraphs = paragraphs.toMutableList().also { it[idx] = pd.copy(elements = pd.elements + ElementDraft(id = nid(), type = "PAGEBREAK")) } },
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }

            item {
                Button(onClick = { paragraphs = paragraphs + ParagraphDraft(id = nid()) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Добавить абзац")
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ParagraphCard(
    paragraph: ParagraphDraft,
    index: Int,
    total: Int,
    tableReports: List<ReportEntity>,
    vm: ReportsViewModel,
    onUpdate: (ParagraphDraft) -> Unit,
    onDelete: () -> Unit,
    onMove: (Int) -> Unit,
    onAddText: () -> Unit,
    onAddTable: () -> Unit,
    onAddTab: () -> Unit,
    onAddPageBreak: () -> Unit,
) {
    var showFormat by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Абзац ${index + 1}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                if (index > 0) IconButton(onClick = { onMove(-1) }) { Text("↑") }
                if (index < total - 1) IconButton(onClick = { onMove(1) }) { Text("↓") }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Удалить абзац", tint = MaterialTheme.colorScheme.error) }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                ALIGN_OPTIONS.forEach { (value, icon) ->
                    IconButton(
                        onClick = { onUpdate(paragraph.copy(align = value)) },
                        modifier = Modifier
                            .size(40.dp)
                            .border(1.dp, if (paragraph.align == value) MaterialTheme.colorScheme.primary else Color.Gray, CircleShape)
                            .clip(CircleShape),
                    ) {
                        Icon(icon, contentDescription = value, tint = if (paragraph.align == value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            FontDropdown(value = paragraph.font, onValueChange = { onUpdate(paragraph.copy(font = it)) })
            Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick = { showFormat = !showFormat }, modifier = Modifier.fillMaxWidth()) {
                Text(if (showFormat) "Скрыть параметры абзаца" else "Параметры абзаца")
            }
            if (showFormat) {
                Spacer(Modifier.height(8.dp))
                LabeledDropdown(
                    label = "Межстрочный интервал",
                    options = LINE_SPACING_OPTIONS,
                    value = paragraph.lineSpacing,
                    onValueChange = { onUpdate(paragraph.copy(lineSpacing = it)) },
                )
                Spacer(Modifier.height(6.dp))
                NumberField("Множитель (своё)", paragraph.lineSpacing, { onUpdate(paragraph.copy(lineSpacing = it)) })
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("Отступ слева, см", paragraph.indentLeftMm / 10f, { onUpdate(paragraph.copy(indentLeftMm = it * 10f)) }, Modifier.weight(1f))
                    NumberField("Отступ справа, см", paragraph.indentRightMm / 10f, { onUpdate(paragraph.copy(indentRightMm = it * 10f)) }, Modifier.weight(1f))
                }
                Spacer(Modifier.height(6.dp))
                val firstKind = if (paragraph.firstLineMm == 0f) "Нет" else if (paragraph.firstLineMm > 0) "Отступ" else "Выступ"
                val firstAbs = kotlin.math.abs(paragraph.firstLineMm)
                LabeledDropdown(
                    label = "Первая строка",
                    options = FIRST_LINE_OPTIONS,
                    value = firstKind,
                    onValueChange = { kind ->
                        val v = if (kind == "Нет") 0f else if (kind == "Отступ") kotlin.math.abs(paragraph.firstLineMm).let { if (it == 0f) 12.5f else it } else -kotlin.math.abs(paragraph.firstLineMm).let { if (it == 0f) 12.5f else it }
                        onUpdate(paragraph.copy(firstLineMm = v))
                    },
                )
                if (firstKind != "Нет") {
                    Spacer(Modifier.height(6.dp))
                    NumberField("Значение, см", firstAbs / 10f, { cm ->
                        val mm = cm * 10f
                        onUpdate(paragraph.copy(firstLineMm = if (firstKind == "Выступ") -mm else mm))
                    })
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("Интервал перед, см", paragraph.spaceBeforeMm / 10f, { onUpdate(paragraph.copy(spaceBeforeMm = it * 10f)) }, Modifier.weight(1f))
                    NumberField("Интервал после, см", paragraph.spaceAfterMm / 10f, { onUpdate(paragraph.copy(spaceAfterMm = it * 10f)) }, Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(8.dp))

            paragraph.elements.forEachIndexed { ei, el ->
                when (el.type) {
                    "REPORT", "MANUAL" -> TableElementEditor(
                        el = el,
                        tableReports = tableReports,
                        loadReportColumns = { id -> vm.reportColumns(id).size },
                        onUpdate = { ne -> onUpdate(paragraph.copy(elements = paragraph.elements.toMutableList().also { it[ei] = ne })) },
                        onDelete = { onUpdate(paragraph.copy(elements = paragraph.elements.toMutableList().also { it.removeAt(ei) })) },
                    )
                    "TAB" -> SimpleElementCard("Табуляция", onDelete = { onUpdate(paragraph.copy(elements = paragraph.elements.toMutableList().also { it.removeAt(ei) })) })
                    "PAGEBREAK" -> SimpleElementCard("Разрыв страницы", onDelete = { onUpdate(paragraph.copy(elements = paragraph.elements.toMutableList().also { it.removeAt(ei) })) })
                    else -> TextElementEditor(
                        el = el,
                        onUpdate = { ne -> onUpdate(paragraph.copy(elements = paragraph.elements.toMutableList().also { it[ei] = ne })) },
                        onDelete = { onUpdate(paragraph.copy(elements = paragraph.elements.toMutableList().also { it.removeAt(ei) })) },
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onAddText, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.Add, contentDescription = null); Spacer(Modifier.width(4.dp)); Text("Текст") }
                OutlinedButton(onClick = onAddTable, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.TableChart, contentDescription = null); Spacer(Modifier.width(4.dp)); Text("Таблица") }
                OutlinedButton(onClick = onAddTab, modifier = Modifier.fillMaxWidth()) { Icon(Icons.AutoMirrored.Filled.FormatAlignLeft, contentDescription = null); Spacer(Modifier.width(4.dp)); Text("Табуляция") }
                OutlinedButton(onClick = onAddPageBreak, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.Add, contentDescription = null); Spacer(Modifier.width(4.dp)); Text("Разрыв страницы") }
            }
        }
    }
}

@Composable
private fun SimpleElementCard(label: String, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.padding(10.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Удалить", tint = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun TextElementEditor(el: ElementDraft, onUpdate: (ElementDraft) -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Текстовый элемент", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Удалить", tint = MaterialTheme.colorScheme.error) }
            }
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(value = el.text, onValueChange = { onUpdate(el.copy(text = it)) }, label = { Text("Текст") }, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp), maxLines = 6)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ToggleChip(label = "B", selected = el.bold) { onUpdate(el.copy(bold = it)) }
                ToggleChip(label = "I", selected = el.italic) { onUpdate(el.copy(italic = it)) }
                ToggleChip(label = "U", selected = el.underline) { onUpdate(el.copy(underline = it)) }
            }
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = el.size,
                onValueChange = { if (it.isEmpty() || it.all { c -> c.isDigit() }) onUpdate(el.copy(size = it)) },
                label = { Text("Размер") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.width(120.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text("Цвет текста: ${colorLabel(el.colorArgb)}", style = MaterialTheme.typography.labelSmall)
            ColorSwatchRow(selected = el.colorArgb, onPick = { onUpdate(el.copy(colorArgb = it)) })
            Spacer(Modifier.height(4.dp))
            Text("Цвет фона: ${colorLabel(el.bgArgb)}", style = MaterialTheme.typography.labelSmall)
            ColorSwatchRow(selected = el.bgArgb, onPick = { onUpdate(el.copy(bgArgb = it)) })
        }
    }
}

@Composable
private fun TableElementEditor(el: ElementDraft, tableReports: List<ReportEntity>, loadReportColumns: suspend (Long) -> Int, onUpdate: (ElementDraft) -> Unit, onDelete: () -> Unit) {
    var source by remember(el.type) { mutableStateOf(if (el.type == "REPORT") "REPORT" else "MANUAL") }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Таблица", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Удалить", tint = MaterialTheme.colorScheme.error) }
            }
            Spacer(Modifier.height(4.dp))
            LabeledDropdown(
                label = "Источник",
                options = listOf("REPORT" to "Из отчёта", "MANUAL" to "Вручную"),
                value = source,
                onValueChange = { src -> source = src; onUpdate(el.copy(type = src)) },
            )
            Spacer(Modifier.height(8.dp))
            if (source == "REPORT") {
                NumberField("Минимальное количество строк", el.minRows.toFloat(), { onUpdate(el.copy(minRows = it.toInt().coerceAtLeast(0))) })
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = el.numbering, onCheckedChange = { onUpdate(el.copy(numbering = it)) })
                    Spacer(Modifier.width(6.dp))
                    Text("Добавить столбец № (нумерация)", style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.height(6.dp))
                var colCount by remember { mutableStateOf(0) }
                LaunchedEffect(el.embeddedReportId) {
                    colCount = if (el.embeddedReportId != 0L) loadReportColumns(el.embeddedReportId) else 0
                }
                val total = colCount + if (el.numbering) 1 else 0
                if (total > 0) {
                    Text("Ширина столбцов (см):", style = MaterialTheme.typography.labelSmall)
                    val raw = el.colWeights.split(',').toMutableList()
                    while (raw.size < total) raw.add("")
                    if (raw.size > total) raw.subList(total, raw.size).clear()
                    val hscroll = rememberScrollState()
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.horizontalScroll(hscroll)) {
                        raw.forEachIndexed { wi, wv ->
                            Column {
                                Text(if (el.numbering && wi == 0) "№" else "Ст. ${wi + if (el.numbering) 0 else 1}", style = MaterialTheme.typography.labelSmall)
                                OutlinedTextField(
                                    value = wv,
                                    onValueChange = { s ->
                                        // Accept both '.' and ',' as the fractional separator (a
                                        // locale keyboard may emit ','). Normalize to '.' for storage,
                                        // otherwise the comma was filtered out and the field cleared.
                                        val normalized = s.replace(',', '.')
                                        val t = normalized.filter { ch -> ch.isDigit() || ch == '.' }
                                        val processed = if (t.endsWith(".") && t.count { it == '.' } == 1) t + "0" else t
                                        val updated = raw.toMutableList().also { it[wi] = processed }
                                        onUpdate(el.copy(colWeights = updated.joinToString(",")))
                                    },
                                    singleLine = true,
                                    modifier = Modifier.width(72.dp).heightIn(min = 44.dp),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                )
                            }
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = el.joinPrevious, onCheckedChange = { onUpdate(el.copy(joinPrevious = it)) })
                Spacer(Modifier.width(6.dp))
                Text("Присоединить к предыдущей таблице", style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.height(6.dp))
            Text("Форматирование текста таблицы", style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ToggleChip(label = "B", selected = el.bold) { onUpdate(el.copy(bold = it)) }
                ToggleChip(label = "I", selected = el.italic) { onUpdate(el.copy(italic = it)) }
                ToggleChip(label = "U", selected = el.underline) { onUpdate(el.copy(underline = it)) }
            }
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = el.size,
                onValueChange = { if (it.isEmpty() || it.all { c -> c.isDigit() }) onUpdate(el.copy(size = it)) },
                label = { Text("Размер") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.width(120.dp),
            )
            if (source != "REPORT") {
                Spacer(Modifier.height(6.dp))
                LabeledDropdown(
                    label = "Выравнивание",
                    options = listOf("LEFT" to "По левому краю", "CENTER" to "По центру", "RIGHT" to "По правому краю", "JUSTIFY" to "По ширине"),
                    value = el.align,
                    onValueChange = { onUpdate(el.copy(align = it)) },
                )
            }
            Spacer(Modifier.height(6.dp))
            LabeledDropdown(
                label = "Обводка таблицы",
                options = listOf(1 to "Все линии (сетка)", 2 to "Только внешние", 3 to "Только горизонтальные", 0 to "Без обводки"),
                value = el.border,
                onValueChange = { onUpdate(el.copy(border = it)) },
            )
            Spacer(Modifier.height(6.dp))
            Text("Цвет текста: ${colorLabel(el.colorArgb)}", style = MaterialTheme.typography.labelSmall)
            ColorSwatchRow(selected = el.colorArgb, onPick = { onUpdate(el.copy(colorArgb = it)) })
            Spacer(Modifier.height(4.dp))
            Text("Цвет фона: ${colorLabel(el.bgArgb)}", style = MaterialTheme.typography.labelSmall)
            ColorSwatchRow(selected = el.bgArgb, onPick = { onUpdate(el.copy(bgArgb = it)) })
            Spacer(Modifier.height(6.dp))
            if (source == "REPORT") {
                ReportFields(el = el, tableReports = tableReports, onUpdate = onUpdate)
            } else {
                ManualTableEditor(table = el.manualTable, onChange = { onUpdate(el.copy(manualTable = it)) })
            }
        }
    }
}

@Composable
private fun ReportFields(el: ElementDraft, tableReports: List<ReportEntity>, onUpdate: (ElementDraft) -> Unit) {
    ReportDropdown(value = el.embeddedReportId, reports = tableReports, onValueChange = { onUpdate(el.copy(embeddedReportId = it)) })
    Spacer(Modifier.height(6.dp))
    OutlinedTextField(value = el.embeddedTitle, onValueChange = { onUpdate(el.copy(embeddedTitle = it.stripNewlines())) }, label = { Text("Заголовок (необязательно)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun ManualTableEditor(table: ManualTable, onChange: (ManualTable) -> Unit) {
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(value = table.title, onValueChange = { onChange(table.copy(title = it.stripNewlines())) }, label = { Text("Заголовок таблицы") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        Text("Заголовки: текст сверху, «Столбцы» — на сколько столбцов растянуть ячейку (1 = обычная).",
            style = MaterialTheme.typography.labelSmall)
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.horizontalScroll(scroll),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            table.headers.forEachIndexed { ci, cell ->
                Column {
                    OutlinedTextField(value = cell.text, onValueChange = { nv -> onChange(table.copy(headers = table.headers.toMutableList().also { it[ci] = cell.copy(text = nv) })) }, singleLine = true, placeholder = { Text("Текст") }, modifier = Modifier.width(110.dp))
                    OutlinedTextField(
                        value = if (cell.colSpan <= 1) "" else cell.colSpan.toString(),
                        onValueChange = { v -> val s = v.filter { ch -> ch.isDigit() }.toIntOrNull() ?: 1; onChange(table.copy(headers = table.headers.toMutableList().also { it[ci] = cell.copy(colSpan = s.coerceAtLeast(1)) })) },
                        label = { Text("Столбцы") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        placeholder = { Text("1") },
                        singleLine = true,
                        modifier = Modifier.width(110.dp).heightIn(min = 44.dp),
                    )
                        OutlinedTextField(
                            value = if (cell.weight < 0.01f) "" else cell.weight.toString(),
                            onValueChange = { v -> val s = v.filter { ch -> ch.isDigit() || ch == '.' }.let { if (it.endsWith(".")) it + "0" else it }; val num = s.toFloatOrNull() ?: 0f; onChange(table.copy(headers = table.headers.toMutableList().also { it[ci] = cell.copy(weight = num) })) },
                            label = { Text("см") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            placeholder = { Text("авто") },
                            singleLine = true,
                            modifier = Modifier.width(110.dp).heightIn(min = 44.dp),
                        )
                        CellAlignPicker(align = cell.align, onPick = { a -> onChange(table.copy(headers = table.headers.toMutableList().also { it[ci] = cell.copy(align = a) })) })
                    }
                }
            }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
            OutlinedButton(onClick = { onChange(table.copy(headers = table.headers + TableCell(""))) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.Add, contentDescription = null); Spacer(Modifier.width(4.dp)); Text("Добавить столбец") }
            OutlinedButton(onClick = { onChange(table.copy(headers = table.headers.dropLast(1))) }, enabled = table.headers.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.Delete, contentDescription = null); Spacer(Modifier.width(4.dp)); Text("Удалить столбец") }
        }
        Spacer(Modifier.height(6.dp))
        Text("Строки", style = MaterialTheme.typography.labelSmall)
        table.rows.forEachIndexed { ri, row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.horizontalScroll(scroll),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                table.headers.indices.forEach { ci ->
                    val cell = row.getOrElse(ci) { TableCell("") }
                    Column {
                        OutlinedTextField(
                            value = cell.text,
                            onValueChange = { nv ->
                                val nr = row.toMutableList().also { list -> while (list.size <= ci) list.add(TableCell("")); list[ci] = cell.copy(text = nv) }
                                onChange(table.copy(rows = table.rows.toMutableList().also { it[ri] = nr }))
                            },
                            placeholder = { Text("Текст") },
                            singleLine = true,
                            modifier = Modifier.width(110.dp),
                        )
                        OutlinedTextField(
                            value = if (cell.colSpan <= 1) "" else cell.colSpan.toString(),
                            onValueChange = { v ->
                                val s = v.filter { ch -> ch.isDigit() }.toIntOrNull() ?: 1
                                val nr = row.toMutableList().also { list -> while (list.size <= ci) list.add(TableCell("")); list[ci] = cell.copy(colSpan = s.coerceAtLeast(1)) }
                                onChange(table.copy(rows = table.rows.toMutableList().also { it[ri] = nr }))
                            },
                            label = { Text("Столбцы") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            placeholder = { Text("1") },
                            singleLine = true,
                            modifier = Modifier.width(110.dp).heightIn(min = 44.dp),
                        )
                        CellAlignPicker(align = cell.align, onPick = { a ->
                            val nr = row.toMutableList().also { list -> while (list.size <= ci) list.add(TableCell("")); list[ci] = cell.copy(align = a) }
                            onChange(table.copy(rows = table.rows.toMutableList().also { it[ri] = nr }))
                        })
                    }
                }
                IconButton(onClick = { onChange(table.copy(rows = table.rows.toMutableList().also { it.removeAt(ri) })) }) { Icon(Icons.Filled.Delete, contentDescription = "Удалить строку") }
            }
            Spacer(Modifier.height(4.dp))
        }
        OutlinedButton(onClick = { val nr = table.rows.toMutableList().also { it.add(List(table.headers.size) { TableCell("") }) }; onChange(table.copy(rows = nr)) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.Add, contentDescription = null); Spacer(Modifier.width(4.dp)); Text("Добавить строку") }
    }
}

@Composable
private fun CellAlignPicker(align: String, onPick: (String) -> Unit) {
    val options = listOf(
        "" to "Авто",
        "LEFT" to "Л",
        "CENTER" to "Ц",
        "RIGHT" to "П",
    )
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 2.dp)) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = align == value,
                onClick = { onPick(value) },
                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                    selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                    containerColor = MaterialTheme.colorScheme.surface,
                    labelColor = MaterialTheme.colorScheme.onSurface,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = align == value,
                    borderColor = MaterialTheme.colorScheme.outline,
                    selectedBorderColor = MaterialTheme.colorScheme.primary,
                    borderWidth = 1.dp,
                ),
            )
        }
    }
}

@Composable
private fun ToggleChip(label: String, selected: Boolean, onToggle: (Boolean) -> Unit) {    FilterChip(
        selected = selected,
        onClick = { onToggle(!selected) },
        label = { Text(label, fontWeight = FontWeight.Bold) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            containerColor = MaterialTheme.colorScheme.surface,
            labelColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.primary,
            selectedBorderColor = MaterialTheme.colorScheme.primary,
            borderWidth = 1.5.dp,
        ),
    )
}

@Composable
private fun ColorSwatchRow(selected: Int, onPick: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 2.dp)) {
        PRESET_COLORS.forEach { (argb, _) ->
            if (argb == AUTO_COLOR) {
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(2.dp, if (selected == AUTO_COLOR) MaterialTheme.colorScheme.primary else Color.Gray, CircleShape)
                        .clickable { onPick(AUTO_COLOR) },
                    contentAlignment = Alignment.Center,
                ) { Text("А", fontSize = 12.sp, color = Color.Black) }
            } else {
                val c = Color(argb)
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(c)
                        .border(2.dp, if (selected == argb) MaterialTheme.colorScheme.primary else Color.Gray, CircleShape)
                        .clickable { onPick(argb) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> LabeledDropdown(label: String, options: List<Pair<T, String>>, value: T, onValueChange: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.first == value }?.second ?: value.toString()
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(value = current, onValueChange = {}, readOnly = true, label = { Text(label) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable).fillMaxWidth())
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (v, l) ->
                DropdownMenuItem(text = { Text(l) }, onClick = { onValueChange(v); expanded = false })
            }
        }
    }
}

@Composable
private fun NumberField(label: String, value: Float, onValueChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf(if (value == 0f) "" else formatNum(value)) }
    var lastEmitted by remember { mutableStateOf(value) }
    LaunchedEffect(value) {
        if (kotlin.math.abs(value - lastEmitted) > 0.001f) {
            text = if (value == 0f) "" else formatNum(value)
            lastEmitted = value
        }
    }
    OutlinedTextField(
        value = text,
        onValueChange = { s ->
            if (s.isEmpty() || s.matches(Regex("^\\d*\\.?\\d*$"))) {
                text = s
                val v = if (s.isEmpty()) 0f else s.toFloatOrNull() ?: 0f
                lastEmitted = v
                onValueChange(v)
            }
        },
        label = { Text(label) },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
    )
}

private fun formatNum(v: Float): String {
    val s = v.toString()
    return s.trimEnd('0').trimEnd('.')
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FontDropdown(value: String, onValueChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(value = value, onValueChange = {}, readOnly = true, label = { Text("Шрифт абзаца") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable).fillMaxWidth())
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            FONT_OPTIONS.forEach { f ->
                DropdownMenuItem(text = { Text(f) }, onClick = { onValueChange(f); expanded = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportDropdown(value: Long, reports: List<ReportEntity>, onValueChange: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = reports.firstOrNull { it.id == value }?.name ?: "Выберите отчёт"
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(value = label, onValueChange = {}, readOnly = true, label = { Text("Отчёт (таблица)") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable).fillMaxWidth())
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            reports.forEach { r ->
                DropdownMenuItem(text = { Text(r.name.ifBlank { "Без названия" }) }, onClick = { onValueChange(r.id); expanded = false })
            }
        }
    }
}
