package com.example.entimate.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun ColorRow(color: Int, onColorChange: (Int) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    val isCustom = color != 0 && color !in QUICK_COLORS

    FlowRow(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            Modifier
                .size(36.dp)
                .border(
                    2.dp,
                    if (color == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    RoundedCornerShape(8.dp),
                )
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                .clickable { onColorChange(0) },
            contentAlignment = Alignment.Center,
        ) {
            Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        QUICK_COLORS.forEach { c ->
            Box(
                Modifier
                    .size(36.dp)
                    .border(
                        2.dp,
                        if (color == c) MaterialTheme.colorScheme.primary else Color.Transparent,
                        RoundedCornerShape(8.dp),
                    )
                    .background(Color(c), RoundedCornerShape(8.dp))
                    .clickable { onColorChange(c) },
            )
        }
        IconButton(onClick = { showPicker = true }) {
            if (isCustom) {
                Box(
                    Modifier
                        .size(36.dp)
                        .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                        .background(Color(color), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.ColorLens,
                        contentDescription = "Палитра",
                        tint = if (colorLuminance(Color(color)) > 0.5f) Color.Black else Color.White,
                    )
                }
            } else {
                Icon(Icons.Filled.ColorLens, contentDescription = "Палитра")
            }
        }
    }

    if (showPicker) {
        ColorPickerDialog(
            initialColor = if (color != 0) color else QUICK_COLORS.first(),
            onDismiss = { showPicker = false },
            onColorSelected = { onColorChange(it); showPicker = false },
        )
    }
}
