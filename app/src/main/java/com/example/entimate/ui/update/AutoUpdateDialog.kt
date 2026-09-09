package com.example.entimate.ui.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.entimate.data.update.AppUpdater
import com.example.entimate.data.update.UpdateChecker
import com.example.entimate.data.update.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private sealed interface AutoUpdateState {
    data object Idle : AutoUpdateState
    data object Checking : AutoUpdateState
    data class Found(val info: UpdateInfo) : AutoUpdateState
    data class Downloading(val progress: Float) : AutoUpdateState
    data class Ready(val file: File) : AutoUpdateState
}

@Composable
fun AutoUpdateDialog() {
    val context = LocalContext.current
    var state by remember { mutableStateOf<AutoUpdateState>(AutoUpdateState.Checking) }
    val scope = rememberCoroutineScope()

    val installPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val st = state
        if (st is AutoUpdateState.Ready && AppUpdater.canRequestInstalls(context)) {
            AppUpdater.install(context, st.file)
            state = AutoUpdateState.Idle
        }
    }

    LaunchedEffect(Unit) {
        state = try {
            val info = withContext(Dispatchers.IO) { UpdateChecker.check() }
            if (UpdateChecker.isNewer(info.version, currentVersionName(context))) {
                AutoUpdateState.Found(info)
            } else {
                AutoUpdateState.Idle
            }
        } catch (_: Exception) {
            AutoUpdateState.Idle
        }
    }

    when (val st = state) {
        is AutoUpdateState.Found -> AlertDialog(
            onDismissRequest = { state = AutoUpdateState.Idle },
            title = { Text("Доступна новая версия ${st.info.version}") },
            text = { Text(st.info.body.ifBlank { "Выпущена новая версия приложения." }) },
            confirmButton = {
                TextButton(onClick = {
                    state = AutoUpdateState.Downloading(-1f)
                    scope.launch {
                        try {
                            val file = AppUpdater.download(context, st.info.downloadUrl) { done, total ->
                                state = AutoUpdateState.Downloading(if (total > 0) done.toFloat() / total else -1f)
                            }
                            state = AutoUpdateState.Ready(file)
                        } catch (_: Exception) {
                            state = AutoUpdateState.Idle
                        }
                    }
                }) { Text("Скачать") }
            },
            dismissButton = {
                TextButton(onClick = { state = AutoUpdateState.Idle }) { Text("Позже") }
            },
        )
        is AutoUpdateState.Downloading -> AlertDialog(
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
        is AutoUpdateState.Ready -> AlertDialog(
            onDismissRequest = { state = AutoUpdateState.Idle },
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
                        state = AutoUpdateState.Idle
                    } else {
                        AppUpdater.installPermissionIntent(context)?.let { installPermLauncher.launch(it) }
                    }
                }) { Text("Установить") }
            },
            dismissButton = {
                TextButton(onClick = { state = AutoUpdateState.Idle }) { Text("Позже") }
            },
        )
        else -> Unit
    }
}

private fun currentVersionName(context: Context): String = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0)).versionName.orEmpty()
    } else {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }
} catch (_: Exception) {
    ""
}