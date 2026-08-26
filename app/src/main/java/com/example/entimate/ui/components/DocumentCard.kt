package com.example.entimate.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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

@Composable
fun DocumentCard(
    doc: DocumentEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onAdjust: (Int) -> Unit = {},
    onCommit: (Int) -> Unit = {},
) {
    var displayQty by remember(doc.id) { mutableStateOf(doc.quantity) }
    LaunchedEffect(doc.quantity) { displayQty = doc.quantity }

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
                    onAdjust = { sign ->
                        displayQty += sign * doc.step
                        onAdjust(sign)
                    },
                    onCommit = onCommit,
                    onBg = onBg,
                )
                Text(
                    text = displayQty.toString(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = onBg,
                )
                HoldIconButton(
                    sign = 1,
                    doc = doc,
                    onAdjust = { sign ->
                        displayQty += sign * doc.step
                        onAdjust(sign)
                    },
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
    IconButton(
        onClick = {
            onAdjust(sign)
            onCommit(sign)
        },
    ) {
        Icon(
            imageVector = if (sign > 0) Icons.Filled.Add else Icons.Filled.Remove,
            contentDescription = if (sign > 0) "Увеличить" else "Уменьшить",
            tint = onBg,
        )
    }
}
