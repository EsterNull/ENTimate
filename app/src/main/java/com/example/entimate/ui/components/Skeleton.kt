package com.example.entimate.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp

@Composable
fun Skeleton(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(6.dp),
) {
    val base = MaterialTheme.colorScheme.surfaceVariant
    val shimmer = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val transition = rememberInfiniteTransition()
    val x by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
    )
    val brush = remember(base, shimmer) {
        Brush.linearGradient(
            colorStops = arrayOf(0f to base, 0.45f to shimmer, 0.9f to base),
            start = Offset(x - 400f, 0f),
            end = Offset(x + 600f, 0f),
        )
    }
    Box(modifier.clip(shape).background(brush))
}
