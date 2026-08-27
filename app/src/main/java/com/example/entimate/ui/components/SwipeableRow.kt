package com.example.entimate.ui.components

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun SwipeableRow(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onSwipeLeft: (() -> Unit)? = null,
    onSwipeRight: (() -> Unit)? = null,
    backgroundLeft: @Composable BoxScope.() -> Unit = {},
    backgroundRight: @Composable BoxScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    var width by remember { mutableStateOf(1) }

    Box(modifier.fillMaxWidth().onSizeChanged { width = it.width }) {
        Box(Modifier.matchParentSize(), contentAlignment = Alignment.CenterEnd) {
            if (offsetX < -30 && onSwipeLeft != null) backgroundLeft()
            else if (offsetX > 30 && onSwipeRight != null) backgroundRight()
        }
        Box(
            Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .pointerInput(enabled, onSwipeLeft, onSwipeRight) {
                    detectHorizontalDragGestures(
                        onDragStart = { offsetX = 0f },
                        onDragEnd = { runSwipe(offsetX, width, onSwipeLeft, onSwipeRight); offsetX = 0f },
                        onDragCancel = { runSwipe(offsetX, width, onSwipeLeft, onSwipeRight); offsetX = 0f },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            offsetX += dragAmount
                        },
                    )
                },
        ) { content() }
    }
}

private fun runSwipe(
    offsetX: Float,
    width: Int,
    onSwipeLeft: (() -> Unit)?,
    onSwipeRight: (() -> Unit)?,
) {
    if (abs(offsetX) < width * 0.2f) return
    if (offsetX < 0 && onSwipeLeft != null) onSwipeLeft.invoke()
    else if (offsetX > 0 && onSwipeRight != null) onSwipeRight.invoke()
}
