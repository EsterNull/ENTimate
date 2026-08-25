package com.example.entimate.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorPickerDialog(
    initialColor: Int,
    onDismiss: () -> Unit,
    onColorSelected: (Int) -> Unit,
) {
    val (ih, is_, iv) = rgbToHsv(initialColor)
    var hue by remember { mutableStateOf(ih) }
    var sat by remember { mutableStateOf(is_) }
    var value by remember { mutableStateOf(iv) }
    val colorInt = hsvToColorInt(hue, sat, value)

    val presets = listOf(
        0xFF6750A4.toInt(), 0xFF7AA2F7.toInt(), 0xFFBB9AF7.toInt(), 0xFF7DCFFF.toInt(),
        0xFFF7768E.toInt(), 0xFF9ECE6A.toInt(), 0xFFE0AF68.toInt(), 0xFFE06C75.toInt(),
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Выберите цвет") },
        text = {
            Column {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .background(Color(colorInt), RoundedCornerShape(8.dp)),
                )
                Spacer(Modifier.height(12.dp))
                Text("Оттенок", style = MaterialTheme.typography.labelMedium)
                Slider(value = hue, onValueChange = { hue = it }, valueRange = 0f..360f)
                Text("Насыщенность", style = MaterialTheme.typography.labelMedium)
                Slider(value = sat, onValueChange = { sat = it }, valueRange = 0f..1f)
                Text("Яркость", style = MaterialTheme.typography.labelMedium)
                Slider(value = value, onValueChange = { value = it }, valueRange = 0f..1f)

                Spacer(Modifier.height(12.dp))
                Text("Быстрые цвета:", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { c ->
                        Box(
                            Modifier
                                .size(32.dp)
                                .background(Color(c), RoundedCornerShape(6.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                                .clickable {
                                    val (h, s, v) = rgbToHsv(c)
                                    hue = h
                                    sat = s
                                    value = v
                                },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onColorSelected(colorInt) }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}
