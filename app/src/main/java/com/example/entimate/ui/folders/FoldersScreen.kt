package com.example.entimate.ui.folders

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.entimate.EntimateApplication
import com.example.entimate.data.local.FolderEntity
import com.example.entimate.data.repository.FolderSummary
import com.example.entimate.ui.components.colorLuminance
import com.example.entimate.ui.navigation.navigateBack
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FoldersScreen(nav: NavController) {
    val app = LocalContext.current.applicationContext as EntimateApplication
    val repo = app.folderRepository
    val scope = rememberCoroutineScope()
    val folders by repo.foldersFlow.collectAsStateWithLifecycle(emptyList())
    val currentId by repo.currentFolderIdFlow.collectAsStateWithLifecycle(1L)
    val summaries by repo.summariesFlow.collectAsStateWithLifecycle(emptyMap())
    val listState = rememberLazyListState()

    var reordering by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<FolderEntity?>(null) }
    var pendingDup by remember { mutableStateOf<FolderEntity?>(null) }

    fun reorderFolders(from: Int, to: Int) {
        val ids = folders.map { it.id }.toMutableList()
        if (from !in ids.indices || to !in ids.indices) return
        val id = ids.removeAt(from)
        ids.add(to, id)
        scope.launch { repo.assignOrders(ids) }
    }

    LaunchedEffect(folders, currentId) {
        if (folders.isNotEmpty() && folders.none { it.id == currentId }) {
            repo.selectFolder(folders.first().id)
        }
    }

    if (pendingDelete != null) {
        val f = pendingDelete!!
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Удалить папку?") },
            text = {
                Text(
                    "Папка «${f.name}» будет удалена вместе со всеми её документами, пациентами, отчётами и пользовательскими полями. Это действие нельзя отменить.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = pendingDelete!!.id
                    pendingDelete = null
                    scope.launch { repo.deleteFolder(id) }
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Отмена") } },
        )
    }

    if (pendingDup != null) {
        val f = pendingDup!!
        AlertDialog(
            onDismissRequest = { pendingDup = null },
            title = { Text("Дублировать папку?") },
            text = {
                Text(
                    "Будет создана полная копия папки «${f.name}» со всеми документами, пациентами, отчётами и пользовательскими полями. Копия получит название «${f.name} (копия)».",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = pendingDup!!.id
                    pendingDup = null
                    scope.launch { repo.duplicateFolder(id) }
                }) { Text("Дублировать") }
            },
            dismissButton = { TextButton(onClick = { pendingDup = null }) { Text("Отмена") } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Папки") },
                navigationIcon = {
                    IconButton(onClick = { nav.navigateBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (reordering) {
                        IconButton(onClick = { reordering = false }) {
                            Icon(Icons.Filled.Check, contentDescription = "Завершить изменение порядка")
                        }
                    } else {
                        IconButton(onClick = { reordering = true }) {
                            Icon(Icons.Filled.DragHandle, contentDescription = "Изменить порядок")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { nav.navigate("folders/edit/0") }) {
                Icon(Icons.Filled.CreateNewFolder, contentDescription = "Новая папка")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(folders, key = { _, f -> f.id }) { index, folder ->
                if (reordering) {
                    Row(
                        modifier = Modifier.fillMaxWidth().animateItem(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            IconButton(
                                onClick = { if (index > 0) reorderFolders(index, index - 1) },
                                enabled = index > 0,
                            ) {
                                Icon(Icons.Filled.ArrowDropUp, contentDescription = "Вверх")
                            }
                            IconButton(
                                onClick = { if (index < folders.lastIndex) reorderFolders(index, index + 1) },
                                enabled = index < folders.lastIndex,
                            ) {
                                Icon(Icons.Filled.ArrowDropDown, contentDescription = "Вниз")
                            }
                        }
                        Box(Modifier.weight(1f)) {
                            FolderCard(
                                folder = folder,
                                summary = summaries[folder.id] ?: FolderSummary(),
                                selected = folder.id == currentId,
                                onClick = {},
                                onLongClick = {},
                                onEdit = {},
                                onDelete = {},
                                deleteEnabled = false,
                                showDelete = false,
                            )
                        }
                    }
                } else {
                    FolderCard(
                        folder = folder,
                        summary = summaries[folder.id] ?: FolderSummary(),
                        selected = folder.id == currentId,
                        onClick = {
                            scope.launch {
                                repo.selectFolder(folder.id)
                                nav.navigateBack()
                            }
                        },
                        onLongClick = { pendingDup = folder },
                        onEdit = { nav.navigate("folders/edit/${folder.id}") },
                        onDelete = { pendingDelete = folder },
                        deleteEnabled = folders.size > 1,
                        showDelete = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderCard(
    folder: FolderEntity,
    summary: FolderSummary,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    deleteEnabled: Boolean,
    showDelete: Boolean,
) {
    val colorArgb = folder.colorArgb
    val hasColor = colorArgb != 0
    val bg = if (hasColor) Color(colorArgb) else MaterialTheme.colorScheme.surfaceVariant
    val onBg = if (hasColor && colorLuminance(Color(colorArgb)) > 0.5f) Color.Black else if (hasColor) Color.White else MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, enabled = showDelete),
        colors = CardDefaults.cardColors(containerColor = bg, contentColor = onBg),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Folder,
                contentDescription = null,
                tint = if (hasColor) onBg.copy(alpha = 0.8f) else MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(folder.name.ifBlank { "Без названия" }, style = MaterialTheme.typography.titleMedium, color = onBg)
                Text("Д${summary.docs} П${summary.patients}", style = MaterialTheme.typography.labelSmall, color = onBg.copy(alpha = 0.7f))
            }
            if (showDelete) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = "Изменить папку", tint = MaterialTheme.colorScheme.primary)
                }
                if (deleteEnabled) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = "Удалить папку", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            if (selected) {
                Icon(Icons.Filled.Check, contentDescription = "Выбрана", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}