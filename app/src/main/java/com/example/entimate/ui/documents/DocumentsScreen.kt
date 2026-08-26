package com.example.entimate.ui.documents

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Help
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.entimate.data.local.DocumentEntity
import com.example.entimate.ui.components.LocalTutorial
import com.example.entimate.ui.components.DocumentCard
import com.example.entimate.ui.components.rememberReorderState
import com.example.entimate.ui.components.tutorialAnchor
import com.example.entimate.viewmodel.DocumentsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DocumentsScreen(nav: NavController, vm: DocumentsViewModel = viewModel()) {
    val docs by vm.documents.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<DocumentEntity?>(null) }
    var pendingDup by remember { mutableStateOf<DocumentEntity?>(null) }
    var reordering by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val tutorial = LocalTutorial.current

    val listState = rememberLazyListState()
    val reorderState = rememberReorderState(
        lazyListState = listState,
        items = docs,
        keyOf = { it.id },
        onReorder = { from, to -> vm.reorder(from, to) },
    )

    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Удалить документ?") },
            text = { Text("Документ «${pendingDelete!!.name}» будет удалён безвозвратно.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(pendingDelete!!)
                    pendingDelete = null
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Отмена") }
            },
        )
    }

    if (pendingDup != null) {
        AlertDialog(
            onDismissRequest = { pendingDup = null },
            title = { Text("Дублировать документ?") },
            text = { Text("Будет создана копия документа «${pendingDup!!.name}».") },
            confirmButton = {
                TextButton(onClick = {
                    val d = pendingDup!!
                    scope.launch {
                        val newId = vm.duplicate(d)
                        pendingDup = null
                        nav.navigate("documents/edit/$newId")
                    }
                }) { Text("Дублировать") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDup = null }) { Text("Отмена") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Документы") },
                actions = {
                    if (reordering) {
                        IconButton(onClick = { reordering = false }) {
                            Icon(Icons.Filled.Close, contentDescription = "Завершить изменение порядка")
                        }
                    } else {
                        IconButton(
                            onClick = { tutorial?.start() },
                            modifier = Modifier.tutorialAnchor("doc_help"),
                        ) {
                            Icon(Icons.Filled.Help, contentDescription = "Обучение")
                        }
                        IconButton(
                            onClick = { nav.navigate("documents/edit/0") },
                            modifier = Modifier.tutorialAnchor("doc_add"),
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Добавить документ")
                        }
                        IconButton(
                            onClick = { reordering = true },
                            modifier = Modifier.tutorialAnchor("doc_reorder"),
                        ) {
                            Icon(Icons.Filled.DragHandle, contentDescription = "Изменить порядок")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (docs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Нет документов.\nНажмите + вверху, чтобы создать.",
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(docs, key = { _, doc -> doc.id }) { index, doc ->
                    if (reordering) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (reorderState.draggingKey != doc.id) Modifier.animateItem() else Modifier)
                                .then(reorderState.draggedItemModifier(doc)),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.DragHandle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(36.dp)
                                    .padding(6.dp)
                                    .then(reorderState.handleModifier(doc)),
                            )
                            Box(Modifier.weight(1f)) {
                                DocumentCard(
                                    doc = doc,
                                    onClick = {},
                                    onLongClick = {},
                                    onAdjust = {},
                                    onCommit = {},
                                )
                            }
                        }
                    } else {
                        val dismissState = rememberSwipeToDismissBoxState(
                            positionalThreshold = { it * 0.85f },
                            confirmValueChange = { value ->
                                when (value) {
                                    SwipeToDismissBoxValue.EndToStart -> { pendingDelete = doc; false }
                                    SwipeToDismissBoxValue.StartToEnd -> { nav.navigate("documents/edit/${doc.id}"); false }
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
                            DocumentCard(
                                doc = doc,
                                onClick = { nav.navigate("documents/stats/${doc.id}") },
                                onLongClick = { pendingDup = doc },
                                onAdjust = { vm.adjust(doc.id, it) },
                                onCommit = { vm.recordChange(doc.id, it) },
                            )
                        }
                    }
                }
            }
        }
    }
}
