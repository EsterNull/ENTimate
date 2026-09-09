package com.example.entimate.ui.folders

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.entimate.EntimateApplication
import com.example.entimate.data.repository.FolderSummary
import com.example.entimate.ui.components.tutorialAnchor

/** Height the list below the bar must scroll out of the way for. */
val FolderBarHeight = 52.dp

/**
 * The floating, transparent folder-switcher layer shown above the bottom bar on
 * the Documents / Patients / Reports tabs.
 */
@Composable
fun CurrentFolderBar(
    onOpenFolders: () -> Unit,
    onAdd: (() -> Unit)? = null,
    addTutorialAnchor: String? = null,
    modifier: Modifier = Modifier,
) {
    val app = LocalContext.current.applicationContext as EntimateApplication
    val repo = app.folderRepository
    val folders by repo.foldersFlow.collectAsStateWithLifecycle(emptyList())
    val currentId by repo.currentFolderIdFlow.collectAsStateWithLifecycle(0L)
    val summaries by repo.summariesFlow.collectAsStateWithLifecycle(emptyMap())
    val current = folders.firstOrNull { it.id == currentId } ?: folders.firstOrNull()
    val name = current?.name?.takeIf { it.isNotBlank() } ?: "Папки"
    val summary = if (current != null) summaries[current.id] ?: FolderSummary() else FolderSummary()
    val config = LocalConfiguration.current
    val sidePadding = (config.screenWidthDp * 0.07f).dp

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(FolderBarHeight)
            .background(Color.Transparent)
            .padding(horizontal = sidePadding, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(50))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(50))
                .clickable(onClick = onOpenFolders)
                .padding(horizontal = 10.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "Д${summary.docs} П${summary.patients}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Filled.SwapVert, contentDescription = "Выбрать папку", tint = MaterialTheme.colorScheme.primary)
        }
        if (onAdd != null) {
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = onAdd,
                shape = RoundedCornerShape(50),
                modifier = Modifier.fillMaxHeight().aspectRatio(1f).then(
                    if (addTutorialAnchor != null) Modifier.tutorialAnchor(addTutorialAnchor) else Modifier,
                ),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Добавить", tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}