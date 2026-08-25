package com.example.entimate.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.entimate.data.local.DocumentEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DocumentCard(
    doc: DocumentEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onAdjust: (Int) -> Unit = {},
    onCommit: (Int) -> Unit = {},
) {
    val hasColor = doc.colorArgb != 0
    val bg = if (hasColor) Color(doc.colorArgb) else MaterialTheme.colorScheme.surface
    val onBg = if (hasColor) {
        if (colorLuminance(bg) > 0.5f) Color.Black else Color.White
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(
            containerColor = bg,
            contentColor = onBg,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = doc.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = onBg,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (doc.description.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = doc.description,
                        color = onBg.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                HoldIconButton(
                    sign = -1,
                    doc = doc,
                    onAdjust = onAdjust,
                    onCommit = onCommit,
                    onBg = onBg,
                )
                Text(
                    text = doc.quantity.toString(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = onBg,
                )
                HoldIconButton(
                    sign = 1,
                    doc = doc,
                    onAdjust = onAdjust,
                    onCommit = onCommit,
                    onBg = onBg,
                )
            }
        }
    }
}

@Composable
private fun HoldIconButton(
    sign: Int,
    doc: DocumentEntity,
    onAdjust: (Int) -> Unit,
    onCommit: (Int) -> Unit,
    onBg: Color,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val heldDelta = remember { mutableIntStateOf(0) }
    val pressJob = remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    heldDelta.intValue = 0
                    onAdjust(sign)
                    heldDelta.intValue += sign
                    val job = launch {
                        delay(400)
                        while (true) {
                            onAdjust(sign)
                            heldDelta.intValue += sign
                            delay(130)
                        }
                    }
                    pressJob.value = job
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    pressJob.value?.cancel()
                    pressJob.value = null
                    if (heldDelta.intValue != 0) onCommit(heldDelta.intValue)
                    heldDelta.intValue = 0
                }
            }
        }
    }
    IconButton(
        onClick = {},
        interactionSource = interactionSource,
    ) {
        Icon(
            imageVector = if (sign > 0) Icons.Filled.Add else Icons.Filled.Remove,
            contentDescription = if (sign > 0) "Увеличить" else "Уменьшить",
            tint = onBg,
        )
    }
}
