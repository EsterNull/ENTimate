package com.example.entimate.ui.reports

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.entimate.data.local.DocDocument
import com.example.entimate.viewmodel.ReportsViewModel
import com.example.entimate.viewmodel.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentPreviewScreen(reportId: Long, from: Long = 0L, to: Long = System.currentTimeMillis(), nav: NavController, vm: ReportsViewModel = viewModel(), settingsVm: SettingsViewModel = viewModel()) {
    val context = LocalContext.current
    val settings by settingsVm.settings.collectAsStateWithLifecycle()
    val dateFmt = remember(settings.dateFormat) { SimpleDateFormat(settings.dateFormat, Locale.getDefault()) }
    var title by remember { mutableStateOf("document") }
    var docState by remember { mutableStateOf<DocDocument?>(null) }
    var busy by remember { mutableStateOf(true) }
    var format by remember { mutableStateOf("DOCX") }
    var loadError by remember { mutableStateOf<String?>(null) }
    val renderLock = remember { Mutex() }

    var periodFrom by remember { mutableStateOf(from) }
    var periodTo by remember { mutableStateOf(to) }

    var renderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var pageCount by remember { mutableStateOf(0) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val doc = docState ?: return@rememberLauncherForActivityResult
        uri ?: return@rememberLauncherForActivityResult
        val (bytes, ext) = when (format) {
            "DOCX" -> vm.buildDocx(doc) to "docx"
            else -> vm.buildPdf(doc) to "pdf"
        }
        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
    }

    val shareLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    fun showDatePicker(initialMillis: Long, onPicked: (Long) -> Unit) {
        val cal = Calendar.getInstance().apply { timeInMillis = initialMillis }
        android.app.DatePickerDialog(
            context,
            { _: android.widget.DatePicker, y, m, d ->
                val picked = Calendar.getInstance().apply { set(y, m, d, 0, 0, 0); set(Calendar.MILLISECOND, 0) }
                onPicked(picked.timeInMillis)
            },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    LaunchedEffect(reportId, periodFrom, periodTo) {
        busy = true
        loadError = null
        renderer?.close()
        renderer = null
        pageCount = 0
        try {
            val doc = vm.buildDocModel(reportId, settings.dateFormat, periodFrom, periodTo)
            docState = doc
            title = doc.title.ifBlank { "document" }
            withContext(Dispatchers.IO) {
                val bytes = vm.buildPdf(doc)
                val file = File(context.cacheDir, "doc_preview_$reportId.pdf")
                file.writeBytes(bytes)
                val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val r = PdfRenderer(fd)
                pageCount = r.pageCount
                renderer = r
                busy = false
            }
        } catch (e: Throwable) {
            loadError = e.message ?: e.toString()
            busy = false
            pageCount = 0
        }
    }

    DisposableEffect(reportId) {
        onDispose {
            renderer?.close()
            File(context.cacheDir, "doc_preview_$reportId.pdf").delete()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Предпросмотр") },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") } },
                actions = {
                    IconButton(onClick = {
                        val doc = docState ?: return@IconButton
                        val ext = when (format) {
                            "DOCX" -> "docx"
                            else -> "pdf"
                        }
                        val mime = when (format) {
                            "DOCX" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                            else -> "application/pdf"
                        }
                        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                        val fileName = "${title}_$dateStr.$ext"
                        val bytes = when (format) {
                            "DOCX" -> vm.buildDocx(doc)
                            else -> vm.buildPdf(doc)
                        }
                        val cacheFile = File(context.cacheDir, fileName)
                        cacheFile.writeBytes(bytes)
                        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cacheFile)
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = mime
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        shareLauncher.launch(Intent.createChooser(shareIntent, "Поделиться документом"))
                    }) { Icon(Icons.Filled.Share, contentDescription = "Поделиться") }
                    IconButton(onClick = {
                        val ext = when (format) {
                            "DOCX" -> "docx"
                            else -> "pdf"
                        }
                        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                        launcher.launch("${title}_$dateStr.$ext")
                    }) { Icon(Icons.Filled.Save, contentDescription = "Сохранить") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
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
            Text("Формат сохранения", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = format == "DOCX", onClick = { format = "DOCX" }, label = { Text("DOCX") })
                FilterChip(selected = format == "PDF", onClick = { format = "PDF" }, label = { Text("PDF") })
            }
            Spacer(Modifier.height(12.dp))
            if (busy) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (loadError != null) {
                Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text("Не удалось сформировать документ:\n${loadError}", color = MaterialTheme.colorScheme.error)
                }
            } else if (pageCount == 0) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Нет данных для предпросмотра.") }
            } else {
                LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Top) {
                    items(count = pageCount) { i ->
                        PagePreview(renderer = renderer, index = i, renderLock = renderLock)
                    }
                }
            }
        }
    }
}

@Composable
private fun PagePreview(renderer: PdfRenderer?, index: Int, renderLock: Mutex) {
    var bmp by remember(index) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(renderer, index) {
        bmp = null
        val r = renderer ?: return@LaunchedEffect
        val bitmap = withContext(Dispatchers.IO) {
            try {
                renderLock.withLock {
                    val page = r.openPage(index)
                    try {
                        val pageW = page.width
                        val pageH = page.height
                        val maxDim = maxOf(pageW, pageH).coerceAtLeast(1)
                        val bScale = minOf(2.5f, 4096f / maxDim).coerceAtLeast(0.1f)
                        val outW = (pageW * bScale).toInt().coerceAtLeast(1)
                        val outH = (pageH * bScale).toInt().coerceAtLeast(1)
                        val bmp2 = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
                        bmp2.eraseColor(android.graphics.Color.WHITE)
                        val matrix = android.graphics.Matrix().apply { setScale(bScale, bScale) }
                        page.render(bmp2, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bmp2
                    } finally {
                        page.close()
                    }
                }
            } catch (e: Exception) {
                null
            }
        }
        bmp = bitmap?.asImageBitmap()
    }
    Box(Modifier.fillMaxWidth().background(Color.White)) {
        if (bmp != null) {
            Image(bitmap = bmp!!, contentDescription = null, modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
        } else {
            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
    }
}
