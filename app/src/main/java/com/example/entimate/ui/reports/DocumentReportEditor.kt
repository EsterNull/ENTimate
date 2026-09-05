package com.example.entimate.ui.reports

import com.example.entimate.ui.navigation.navigateBack

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignJustify
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.BorderOuter
import androidx.compose.material.icons.filled.BorderClear
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.example.entimate.ui.components.ColorPickerDialog
import com.example.entimate.ui.components.ColorRow
import com.example.entimate.ui.components.colorLuminance
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
    val paragraphs = remember { mutableStateListOf<ParagraphDraft>() }
    var loaded by remember { mutableStateOf(reportId == 0L) }
    var tableReports by remember { mutableStateOf(listOf<ReportEntity>()) }
    var nextId by remember { mutableStateOf(-1L) }
    val collapsedParas = remember { mutableStateMapOf<Long, Boolean>() }
    val collapsedEls = remember { mutableStateMapOf<Long, Boolean>() }
    var pendingAddPara by remember { mutableStateOf<Long?>(null) }

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
                paragraphs.clear()
                paragraphs.addAll(d.paragraphs.map { pw ->
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
                })
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
                navigationIcon = { IconButton(onClick = { nav.navigateBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") } },
                actions = {
                    IconButton(onClick = { saveAnd { id -> nav.navigate("reports/docpreview/$id/0/${System.currentTimeMillis()}") } }) {
                        Icon(Icons.Filled.TableChart, contentDescription = "Предпросмотр")
                    }
                    IconButton(onClick = { saveAnd { nav.navigateBack() } }) {
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
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
            paragraphs.forEachIndexed { pi, pd ->
                val paraCollapsed = collapsedParas[pd.id] ?: true
                item(key = "para_${pd.id}") {
                    Box(Modifier.animateItem()) {
                        ParagraphCard(
                            paragraph = pd,
                            index = pi,
                            total = paragraphs.size,
                            tableReports = tableReports,
                            vm = vm,
                            isCollapsed = paraCollapsed,
                            onToggleCollapsed = { collapsedParas[pd.id] = !paraCollapsed },
                            onUpdate = { np -> paragraphs[pi] = np },
                            onDelete = { paragraphs.removeAt(pi) },
                            onMove = { dir ->
                                val j = pi + dir
                                if (j in paragraphs.indices) {
                                    val tmp = paragraphs[pi]; paragraphs[pi] = paragraphs[j]; paragraphs[j] = tmp
                                }
                            },
                            onAddElement = { pendingAddPara = pd.id },
                            collapsedEls = collapsedEls,
                            onToggleEl = { id, collapsed -> collapsedEls[id] = collapsed },
                            onMoveEl = { ei, dir ->
                                val els = pd.elements.toMutableList()
                                val j = ei + dir
                                if (j in els.indices) {
                                    val tmp = els[ei]; els[ei] = els[j]; els[j] = tmp
                                    paragraphs[pi] = pd.copy(elements = els)
                                }
                            },
                            onDeleteEl = { ei -> paragraphs[pi] = pd.copy(elements = pd.elements.toMutableList().also { it.removeAt(ei) }) },
                        )
                    }
                }
            }

            item {
                Box(Modifier.animateItem()) {
                    Button(onClick = { val id = nid(); paragraphs.add(ParagraphDraft(id = id)); collapsedParas[id] = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Add, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Добавить абзац")
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    if (pendingAddPara != null) {
        AddElementDialog(
            onDismiss = { pendingAddPara = null },
            onSelect = { type ->
                val pi = paragraphs.indexOfFirst { it.id == pendingAddPara }
                if (pi >= 0) {
                    val pd2 = paragraphs[pi]
                    val ne = when (type) {
                        "TEXT" -> ElementDraft(id = nid(), type = "TEXT")
                        "TABLE" -> ElementDraft(id = nid(), type = if (tableReports.isNotEmpty()) "REPORT" else "MANUAL", embeddedReportId = tableReports.firstOrNull()?.id ?: 0)
                        "TAB" -> ElementDraft(id = nid(), type = "TAB")
                        else -> ElementDraft(id = nid(), type = "PAGEBREAK")
                    }
                    paragraphs[pi] = pd2.copy(elements = pd2.elements + ne)
                }
                pendingAddPara = null
            },
        )
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
    isCollapsed: Boolean,
    onToggleCollapsed: () -> Unit,
    onUpdate: (ParagraphDraft) -> Unit,
    onDelete: () -> Unit,
    onMove: (Int) -> Unit,
    onAddElement: () -> Unit,
    collapsedEls: MutableMap<Long, Boolean>,
    onToggleEl: (Long, Boolean) -> Unit,
    onMoveEl: (Int, Int) -> Unit,
    onDeleteEl: (Int) -> Unit,
) {
    var showFormat by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column {
                    if (index > 0) IconButton(onClick = { onMove(-1) }, modifier = Modifier.size(34.dp)) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Вверх") }
                    if (index < total - 1) IconButton(onClick = { onMove(1) }, modifier = Modifier.size(34.dp)) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Вниз") }
                }
                Text("Абзац ${index + 1}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = onToggleCollapsed) {
                    AnimatedChevron(
                        isExpanded = !isCollapsed,
                        contentDescription = if (isCollapsed) "Развернуть абзац" else "Свернуть абзац",
                    )
                }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Удалить абзац", tint = MaterialTheme.colorScheme.error) }
            }
            if (!isCollapsed) {
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
                Spacer(Modifier.height(10.dp))
                Text("Элементы абзаца", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.height(6.dp))
                paragraph.elements.forEachIndexed { ei, el ->
                    val elCollapsed = collapsedEls[el.id] ?: true
                    val isFirstEl = ei == 0
                    val isLastEl = ei == paragraph.elements.lastIndex
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column {
                            ElementHeaderRow(
                                el = el,
                                isFirst = isFirstEl,
                                isLast = isLastEl,
                                isExpanded = !elCollapsed,
                                onMove = { dir -> onMoveEl(ei, dir) },
                                onToggle = { onToggleEl(el.id, !elCollapsed) },
                                onDelete = { onDeleteEl(ei) },
                            )
                            AnimatedVisibility(
                                visible = !elCollapsed,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut(),
                            ) {
                                ElementEditorRow(
                                    el = el,
                                    tableReports = tableReports,
                                    loadReportColumns = { id -> vm.reportColumns(id).size },
                                    onUpdate = { ne -> onUpdate(paragraph.copy(elements = paragraph.elements.toMutableList().also { it[ei] = ne })) },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                if (paragraph.elements.isEmpty()) {
                    Text("Нет элементов.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedButton(onClick = onAddElement, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.Add, contentDescription = null); Spacer(Modifier.width(4.dp)); Text("Добавить элемент") }
            }
        }
    }
}

@Composable
private fun ElementEditorRow(
    el: ElementDraft,
    tableReports: List<ReportEntity>,
    loadReportColumns: suspend (Long) -> Int,
    onUpdate: (ElementDraft) -> Unit,
) {
    when (el.type) {
        "REPORT", "MANUAL" -> TableElementEditor(
            el = el,
            tableReports = tableReports,
            loadReportColumns = loadReportColumns,
            onUpdate = onUpdate,
        )
        "TAB" -> ElementHintCard("Табуляция")
        "PAGEBREAK" -> ElementHintCard("Разрыв страницы")
        else -> TextElementEditor(el = el, onUpdate = onUpdate)
    }
}

@Composable
private fun ElementHeaderRow(el: ElementDraft, isFirst: Boolean, isLast: Boolean, isExpanded: Boolean, onMove: (Int) -> Unit, onToggle: () -> Unit, onDelete: () -> Unit) {
    val label = when (el.type) {
        "REPORT" -> "Таблица (из отчёта)"
        "MANUAL" -> "Таблица (ручная)"
        "TAB" -> "Табуляция"
        "PAGEBREAK" -> "Разрыв страницы"
        else -> "Текстовый элемент"
    }
    Row(Modifier.padding(6.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        MoveColumn(isFirst = isFirst, isLast = isLast, onMove = onMove)
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
        IconButton(onClick = onToggle) {
            AnimatedChevron(isExpanded = isExpanded, contentDescription = if (isExpanded) "Свернуть элемент" else "Развернуть элемент")
        }
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Удалить", tint = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun AnimatedChevron(isExpanded: Boolean, contentDescription: String) {
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 0f else -90f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "chevron",
    )
    Icon(
        Icons.Filled.KeyboardArrowDown,
        contentDescription = contentDescription,
        modifier = Modifier.graphicsLayer { rotationZ = rotation },
    )
}

@Composable
private fun MoveColumn(isFirst: Boolean, isLast: Boolean, onMove: (Int) -> Unit) {
    Column {
        if (!isFirst) IconButton(onClick = { onMove(-1) }, modifier = Modifier.size(34.dp)) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Вверх") }
        if (!isLast) IconButton(onClick = { onMove(1) }, modifier = Modifier.size(34.dp)) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Вниз") }
    }
}

@Composable
private fun ElementHintCard(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddElementDialog(onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить элемент") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = { onSelect("TEXT") }, modifier = Modifier.fillMaxWidth()) { Text("T", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Spacer(Modifier.width(8.dp)); Text("Текст") }
                OutlinedButton(onClick = { onSelect("TABLE") }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.TableChart, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Таблица") }
                OutlinedButton(onClick = { onSelect("TAB") }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.AutoMirrored.Filled.FormatAlignLeft, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Табуляция") }
                OutlinedButton(onClick = { onSelect("PAGEBREAK") }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.Add, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Разрыв страницы") }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun TextElementEditor(el: ElementDraft, onUpdate: (ElementDraft) -> Unit) {
    var text by remember(el.id) { mutableStateOf(el.text) }
    var size by remember(el.id) { mutableStateOf(el.size) }
    LaunchedEffect(el.text) { if (el.text != text) text = el.text }
    LaunchedEffect(el.size) { if (el.size != size) size = el.size }
    Column(Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp)) {
        OutlinedTextField(value = text, onValueChange = { text = it; onUpdate(el.copy(text = it)) }, label = { Text("Текст") }, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp), maxLines = 6)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ToggleChip(label = "B", selected = el.bold) { onUpdate(el.copy(bold = it)) }
            ToggleChip(label = "I", selected = el.italic) { onUpdate(el.copy(italic = it)) }
            ToggleChip(label = "U", selected = el.underline) { onUpdate(el.copy(underline = it)) }
        }
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = size,
            onValueChange = { if (it.isEmpty() || it.all { c -> c.isDigit() }) { size = it; onUpdate(el.copy(size = it)) } },
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

@Composable
private fun TableElementEditor(el: ElementDraft, tableReports: List<ReportEntity>, loadReportColumns: suspend (Long) -> Int, onUpdate: (ElementDraft) -> Unit) {
    var source by remember(el.type) { mutableStateOf(if (el.type == "REPORT") "REPORT" else "MANUAL") }
    Column(Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp)) {
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
                Text("Выравнивание таблицы", style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(4.dp))
                IconAlignPicker(current = el.align, options = ALIGN_OPTIONS, onPick = { onUpdate(el.copy(align = it)) })
            }
            Spacer(Modifier.height(6.dp))
            Text("Обводка таблицы", style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(4.dp))
            IconBorderPicker(current = el.border, onPick = { onUpdate(el.copy(border = it)) })
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

@Composable
private fun ReportFields(el: ElementDraft, tableReports: List<ReportEntity>, onUpdate: (ElementDraft) -> Unit) {
    ReportDropdown(value = el.embeddedReportId, reports = tableReports, onValueChange = { onUpdate(el.copy(embeddedReportId = it)) })
    Spacer(Modifier.height(6.dp))
    OutlinedTextField(value = el.embeddedTitle, onValueChange = { onUpdate(el.copy(embeddedTitle = it.stripNewlines())) }, label = { Text("Заголовок (необязательно)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManualTableEditor(table: ManualTable, onChange: (ManualTable) -> Unit) {
    val scroll = rememberScrollState()
    var editingCol by remember { mutableStateOf<Int?>(null) }
    var confirmCol by remember { mutableStateOf<Int?>(null) }

    fun moveCol(ci: Int, dir: Int) {
        val j = ci + dir
        if (j !in table.headers.indices) return
        val headers = table.headers.toMutableList()
        val tmp = headers[ci]; headers[ci] = headers[j]; headers[j] = tmp
        val rows = table.rows.map { r ->
            val rr = r.toMutableList()
            if (ci in rr.indices && j in rr.indices) {
                val t = rr[ci]; rr[ci] = rr[j]; rr[j] = t
            }
            rr
        }
        onChange(table.copy(headers = headers, rows = rows))
    }
    Column(Modifier.fillMaxWidth()) {
    OutlinedTextField(value = table.title, onValueChange = { onChange(table.copy(title = it.stripNewlines())) }, label = { Text("Заголовок таблицы") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            table.headers.forEachIndexed { ci, cell ->
                val label = if (cell.text.isNotBlank()) cell.text else "Столбец ${ci + 1}"
                Card(onClick = { editingCol = ci }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { editingCol = ci }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Настроить столбец", tint = MaterialTheme.colorScheme.primary)
                            }
                            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                            if (ci > 0) IconButton(onClick = { moveCol(ci, -1) }) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Переместить вверх") }
                            if (ci < table.headers.lastIndex) IconButton(onClick = { moveCol(ci, 1) }) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Переместить вниз") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(
                                onClick = { confirmCol = ci },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            }
                            Spacer(Modifier.weight(1f))
                            val alignLabel = when (cell.align) {
                                "LEFT" -> "по левому краю"
                                "CENTER" -> "по центру"
                                "RIGHT" -> "по правому краю"
                                else -> "авто"
                            }
                            Text("Ширина: ${if (cell.weight >= 0.01f) formatNum(cell.weight) else "авто"} см · $alignLabel", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        OutlinedButton(onClick = { onChange(table.copy(headers = table.headers + TableCell(""))) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.Add, contentDescription = null); Spacer(Modifier.width(4.dp)); Text("Добавить столбец") }
        Spacer(Modifier.height(6.dp))
        Text("Строки", style = MaterialTheme.typography.labelSmall)
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(table.rows.size) { ri ->
                ManualTableRowEditor(
                    row = table.rows[ri],
                    headers = table.headers,
                    scroll = scroll,
                    onRowChange = { nr ->
                        val rows = table.rows.toMutableList()
                        rows[ri] = nr
                        onChange(table.copy(rows = rows))
                    },
                    onDelete = {
                        val rows = table.rows.toMutableList()
                        rows.removeAt(ri)
                        onChange(table.copy(rows = rows))
                    },
                )
            }
        }
        OutlinedButton(onClick = { val nr = table.rows.toMutableList().also { it.add(List(table.headers.size) { TableCell("") }) }; onChange(table.copy(rows = nr)) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.Add, contentDescription = null); Spacer(Modifier.width(4.dp)); Text("Добавить строку") }
    }

    editingCol?.let { ci ->
        val cell = table.headers.getOrNull(ci) ?: return@let
        ColumnSettingsDialog(
            cell = cell,
            title = "Столбец ${ci + 1}",
            onDismiss = { editingCol = null },
            onSave = { nc ->
                val headers = table.headers.toMutableList()
                headers[ci] = nc
                onChange(table.copy(headers = headers))
                editingCol = null
            },
        )
    }

    confirmCol?.let { ci ->
        AlertDialog(
            onDismissRequest = { confirmCol = null },
            title = { Text("Удалить столбец?") },
            text = { Text("Столбец «${if (table.headers.getOrNull(ci)?.text?.isNotBlank() == true) table.headers[ci].text else "Столбец ${ci + 1}"}» и его данные во всех строках будут удалены.") },
            confirmButton = { TextButton(onClick = {
                if (ci in table.headers.indices) {
                    val headers = table.headers.toMutableList().also { it.removeAt(ci) }
                    val rows = table.rows.map { r -> r.toMutableList().also { if (ci in it.indices) it.removeAt(ci) } }
                    onChange(table.copy(headers = headers, rows = rows))
                }
                confirmCol = null
            }) { Text("Удалить", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmCol = null }) { Text("Отмена") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnSettingsDialog(
    cell: TableCell,
    title: String,
    onDismiss: () -> Unit,
    onSave: (TableCell) -> Unit,
) {
    var text by remember { mutableStateOf(cell.text) }
    var colSpan by remember { mutableStateOf(if (cell.colSpan <= 1) "" else cell.colSpan.toString()) }
    var weight by remember { mutableStateOf(if (cell.weight >= 0.01f) formatNum(cell.weight) else "") }
    var align by remember { mutableStateOf(cell.align) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).imePadding()) {
                OutlinedTextField(value = text, onValueChange = { text = it.stripNewlines() }, label = { Text("Заголовок") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = colSpan,
                    onValueChange = { v -> colSpan = v.filter { ch -> ch.isDigit() } },
                    label = { Text("Столбцы") },
                    placeholder = { Text("1") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = weight,
                    onValueChange = { v ->
                        // Accept '.' and ',' as the fractional separator, keep the raw typed
                        // text locally so typing a decimal point never clears the field.
                        val t = v.replace(',', '.').filter { ch -> ch.isDigit() || ch == '.' }
                        weight = t
                    },
                    label = { Text("Размер (см)") },
                    placeholder = { Text("авто") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text("Выравнивание", style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(4.dp))
                CellAlignPicker(align = align, onPick = { align = it })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val s = colSpan.filter { ch -> ch.isDigit() }.toIntOrNull() ?: 1
                val w = weight.replace(',', '.').toFloatOrNull()?.coerceAtLeast(0f) ?: 0f
                onSave(cell.copy(text = text.trim(), colSpan = s.coerceAtLeast(1), weight = w, align = align))
            }) { Text("Готово") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun ManualTableRowEditor(
    row: List<TableCell>,
    headers: List<TableCell>,
    scroll: ScrollState,
    onRowChange: (List<TableCell>) -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.horizontalScroll(scroll),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        headers.indices.forEach { ci ->
            val cell = row.getOrElse(ci) { TableCell("") }
            Column {
                var cellText by remember { mutableStateOf(cell.text) }
                var colSpan by remember { mutableStateOf(if (cell.colSpan <= 1) "" else cell.colSpan.toString()) }
                LaunchedEffect(cell.text) { if (cellText != cell.text) cellText = cell.text }
                OutlinedTextField(
                    value = cellText,
                    onValueChange = { nv ->
                        cellText = nv
                        val nr = row.toMutableList().also { list -> while (list.size <= ci) list.add(TableCell("")); list[ci] = cell.copy(text = nv) }
                        onRowChange(nr)
                    },
                    placeholder = { Text("Текст") },
                    singleLine = true,
                    modifier = Modifier.width(110.dp),
                )
                OutlinedTextField(
                    value = colSpan,
                    onValueChange = { v ->
                        val digits = v.filter { ch -> ch.isDigit() }
                        colSpan = digits
                        val s = digits.toIntOrNull() ?: 1
                        val nr = row.toMutableList().also { list -> while (list.size <= ci) list.add(TableCell("")); list[ci] = cell.copy(colSpan = s.coerceAtLeast(1)) }
                        onRowChange(nr)
                    },
                    label = { Text("Столбцы") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    placeholder = { Text("1") },
                    singleLine = true,
                    modifier = Modifier.width(110.dp).heightIn(min = 44.dp),
                )
                CellAlignPicker(align = cell.align, onPick = { a ->
                    val nr = row.toMutableList().also { list -> while (list.size <= ci) list.add(TableCell("")); list[ci] = cell.copy(align = a) }
                    onRowChange(nr)
                })
            }
        }
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Удалить строку") }
    }
}

@Composable
private fun CellAlignPicker(align: String, onPick: (String) -> Unit) {
    Column(Modifier.padding(top = 2.dp)) {
        Text("Выравнивание", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(
                selected = align == "",
                onClick = { onPick("") },
                label = { Text("авто", style = MaterialTheme.typography.labelSmall) },
            )
            listOf(
                "LEFT" to Icons.AutoMirrored.Filled.FormatAlignLeft,
                "CENTER" to Icons.Filled.FormatAlignCenter,
                "RIGHT" to Icons.AutoMirrored.Filled.FormatAlignRight,
            ).forEach { (value, icon) ->
                IconButton(
                    onClick = { onPick(value) },
                    modifier = Modifier
                        .size(34.dp)
                        .border(1.dp, if (align == value) MaterialTheme.colorScheme.primary else Color.Gray, CircleShape)
                        .clip(CircleShape),
                ) {
                    Icon(icon, contentDescription = value, tint = if (align == value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun IconAlignPicker(current: String, options: List<Pair<String, ImageVector>>, onPick: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (value, icon) ->
            IconButton(
                onClick = { onPick(value) },
                modifier = Modifier
                    .size(40.dp)
                    .border(1.dp, if (current == value) MaterialTheme.colorScheme.primary else Color.Gray, CircleShape)
                    .clip(CircleShape),
            ) {
                Icon(icon, contentDescription = value, tint = if (current == value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun IconBorderPicker(current: Int, onPick: (Int) -> Unit) {
    val options = listOf(
        1 to Icons.Filled.GridOn,
        2 to Icons.Filled.BorderOuter,
        3 to Icons.Filled.HorizontalRule,
        0 to Icons.Filled.BorderClear,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (value, icon) ->
            IconButton(
                onClick = { onPick(value) },
                modifier = Modifier
                    .size(40.dp)
                    .border(1.dp, if (current == value) MaterialTheme.colorScheme.primary else Color.Gray, CircleShape)
                    .clip(CircleShape),
            ) {
                Icon(icon, contentDescription = "Обводка $value", tint = if (current == value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }
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
    var showPicker by remember { mutableStateOf(false) }
    val isCustom = selected != AUTO_COLOR && PRESET_COLORS.none { it.first == selected }
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
        if (isCustom) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(selected))
                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    .clickable { showPicker = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.ColorLens, contentDescription = "Свой цвет", tint = if (colorLuminance(Color(selected)) > 0.5f) Color.Black else Color.White)
            }
        } else {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    .clickable { showPicker = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.ColorLens, contentDescription = "Свой цвет", tint = Color.Black)
            }
        }
    }
    if (showPicker) {
        ColorPickerDialog(
            initialColor = if (selected != AUTO_COLOR) selected else 0xFF000000.toInt(),
            onDismiss = { showPicker = false },
            onColorSelected = { onPick(it); showPicker = false },
        )
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
