package com.example.entimate.ui.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.entimate.data.local.FormWithDetails

@Composable
fun FormCard(
    form: FormWithDetails,
    onFill: () -> Unit,
    onLongClick: () -> Unit = {},
) {
    val colorArgb = form.form.colorArgb
    val hasColor = colorArgb != 0
    val bg = if (hasColor) Color(colorArgb) else MaterialTheme.colorScheme.surface
    val onBg = if (hasColor) {
        if (colorLuminance(bg) > 0.5f) Color.Black else Color.White
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = onLongClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = bg, contentColor = onBg),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = form.form.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = onBg,
                )
                if (form.form.description.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = form.form.description,
                        fontSize = 13.sp,
                        color = onBg.copy(alpha = 0.7f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onFill) {
                Text("Заполнить", color = onBg)
            }
        }
    }
}
