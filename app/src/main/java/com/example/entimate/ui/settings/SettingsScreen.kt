package com.example.entimate.ui.settings

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.result.contract.ActivityResultContract
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.entimate.EntimateApplication
import com.example.entimate.data.repository.BackupRepository
import com.example.entimate.data.update.AppUpdater
import com.example.entimate.data.update.UpdateChecker
import com.example.entimate.data.update.UpdateInfo
import com.example.entimate.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(nav: NavController, vm: SettingsViewModel = viewModel()) {
    val context = LocalContext.current
    val app = context.applicationContext as EntimateApplication
    val backup = app.backupRepository
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var status by remember { mutableStateOf("") }
    val settings by vm.settings.collectAsStateWithLifecycle()
    var updateState by remember { mutableStateOf<UpdateUiState>(UpdateUiState.Idle) }

    val installPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (AppUpdater.canRequestInstalls(context)) {
            val st = updateState
            if (st is UpdateUiState.Ready) {
                AppUpdater.install(context, st.file)
                updateState = UpdateUiState.Idle
            }
        }
    }

    suspend fun downloadUpdate(info: UpdateInfo) {
        try {
            val file = AppUpdater.download(context, info.downloadUrl) { done, total ->
                updateState = UpdateUiState.Downloading(if (total > 0) done.toFloat() / total else -1f)
            }
            updateState = UpdateUiState.Ready(file)
        } catch (e: Exception) {
            updateState = UpdateUiState.Idle
            snackbar.showSnackbar("Ошибка загрузки обновления: ${e.message ?: "неизвестная ошибка"}")
        }
    }

    val dateFormats = listOf(
        "dd.MM.yyyy" to "дд.мм.гггг (15.01.2024)",
        "dd/MM/yyyy" to "дд/мм/гггг (15/01/2024)",
        "yyyy-MM-dd" to "гггг-мм-дд (2024-01-15)",
        "dd MMM yyyy" to "дд ммм гггг (15 янв 2024)",
    )

    val exportLauncher = rememberLauncherForActivityResult(
        CreateBackupDocument()
    ) { uri ->
        if (uri != null) scope.launch {
            try {
                val json = backup.exportJson()
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                status = "Резервная копия сохранена"
            } catch (e: Exception) {
                status = "Ошибка экспорта: ${e.message}"
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) scope.launch {
            try {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
                backup.importJson(text)
                status = "Данные восстановлены из копии"
            } catch (e: Exception) {
                status = "Ошибка импорта: ${e.message}"
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Настройки") }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = { nav.navigate("theme") },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Тема оформления") }

            Button(
                onClick = { nav.navigate("customfields") },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Управление пользовательскими полями") }

            HorizontalDivider()

            Text("Формат даты", style = MaterialTheme.typography.titleMedium)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                dateFormats.forEach { (pattern, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { vm.update(dateFormat = pattern) }
                            .padding(vertical = 4.dp),
                    ) {
                        RadioButton(
                            selected = settings.dateFormat == pattern,
                            onClick = { vm.update(dateFormat = pattern) },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(label)
                    }
                }
            }

            HorizontalDivider()

            Text("Резервное копирование", style = MaterialTheme.typography.titleMedium)
            Button(
                onClick = {
                    val ts = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.getDefault()).format(Date())
                    exportLauncher.launch("ENTimate-backup-$ts.json")
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Экспорт (сохранить копию)") }
            Button(
                onClick = { importLauncher.launch("application/json") },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Импорт (восстановить)") }
            if (status.isNotBlank()) {
                Text(status, color = MaterialTheme.colorScheme.primary)
            }

            HorizontalDivider()

            val versionName = remember {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0)).versionName
                    } else {
                        @Suppress("DEPRECATION")
                        context.packageManager.getPackageInfo(context.packageName, 0).versionName
                    }
                } catch (e: Exception) { "" }
            }
            Text("О приложении", style = MaterialTheme.typography.titleMedium)
            Text(
                "ENTimate — приложение для учёта количества документов: карточки документов, пациенты, связи между ними и отчёты. Все данные хранятся локально на устройстве. Интернет используется только для проверки обновлений.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Разработчик: ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "EsterNull",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        context.startActivity(Intent(Intent.ACTION_VIEW, "https://github.com/EsterNull".toUri()))
                    },
                )
            }
            Spacer(Modifier.height(4.dp))
            Text("Версия: ${versionName ?: ""}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(
                onClick = {
                    updateState = UpdateUiState.Checking
                    scope.launch {
                        updateState = try {
                            val info = UpdateChecker.check()
                            if (UpdateChecker.isNewer(info.version, versionName.orEmpty())) {
                                UpdateUiState.Found(info)
                            } else {
                                snackbar.showSnackbar("Установлена актуальная версия")
                                UpdateUiState.Idle
                            }
                        } catch (e: Exception) {
                            snackbar.showSnackbar("Не удалось проверить обновления: ${e.message ?: "проверьте подключение к интернету"}")
                            UpdateUiState.Idle
                        }
                    }
                },
                enabled = updateState !is UpdateUiState.Checking && updateState !is UpdateUiState.Downloading,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (updateState is UpdateUiState.Checking) "Проверка..." else "Проверить обновления") }
        }
    }

    when (val st = updateState) {
        is UpdateUiState.Found -> AlertDialog(
            onDismissRequest = { updateState = UpdateUiState.Idle },
            title = { Text("Доступна новая версия ${st.info.version}") },
            text = { Text(st.info.body.ifBlank { "Выпущена новая версия приложения." }) },
            confirmButton = {
                TextButton(onClick = {
                    updateState = UpdateUiState.Downloading(-1f)
                    scope.launch { downloadUpdate(st.info) }
                }) { Text("Скачать") }
            },
            dismissButton = {
                TextButton(onClick = { updateState = UpdateUiState.Idle }) { Text("Позже") }
            },
        )
        is UpdateUiState.Downloading -> AlertDialog(
            onDismissRequest = { },
            title = { Text("Загрузка обновления") },
            text = {
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (st.progress >= 0f) {
                        LinearProgressIndicator(progress = { st.progress }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Text("${(st.progress * 100).toInt()} %")
                    } else {
                        CircularProgressIndicator()
                    }
                }
            },
            confirmButton = { },
        )
        is UpdateUiState.Ready -> AlertDialog(
            onDismissRequest = { updateState = UpdateUiState.Idle },
            title = { Text("Обновление загружено") },
            text = {
                Text(
                    if (AppUpdater.canRequestInstalls(context)) {
                        "Новая версия скачана. Нажмите «Установить», чтобы обновить приложение."
                    } else {
                        "Разрешите установку приложений из неизвестных источников, вернитесь и нажмите «Установить»."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (AppUpdater.canRequestInstalls(context)) {
                        AppUpdater.install(context, st.file)
                        updateState = UpdateUiState.Idle
                    } else {
                        AppUpdater.installPermissionIntent(context)?.let { installPermLauncher.launch(it) }
                    }
                }) { Text("Установить") }
            },
            dismissButton = {
                TextButton(onClick = { updateState = UpdateUiState.Idle }) { Text("Позже") }
            },
        )
        else -> {}
    }
}

private sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data class Found(val info: UpdateInfo) : UpdateUiState
    data class Downloading(val progress: Float) : UpdateUiState
    data class Ready(val file: File) : UpdateUiState
}

private class CreateBackupDocument : ActivityResultContract<String, Uri?>() {
    override fun createIntent(context: Context, input: String): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, input)
        }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? = intent?.data
}
