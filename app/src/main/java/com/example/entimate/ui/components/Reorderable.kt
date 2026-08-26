package com.example.entimate.ui.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

class ReorderState<T : Any>(
    val lazyListState: LazyListState,
    private val itemsProvider: () -> List<T>,
    private val keyOf: (T) -> Any,
    private val onReorder: (from: Int, to: Int) -> Unit,
) {
    var draggingKey by mutableStateOf<Any?>(null)
        private set
    var dragOffsetPx by mutableStateOf(0f)
        private set

    fun handleModifier(item: T): Modifier = Modifier.pointerInput(keyOf(item)) {
        detectDragGestures(
            onDragStart = {
                draggingKey = keyOf(item)
                dragOffsetPx = 0f
            },
            onDrag = { change, dragAmount ->
                change.consume()
                if (draggingKey != keyOf(item)) return@detectDragGestures
                dragOffsetPx += dragAmount.y
                val list = itemsProvider()
                val from = list.indexOfFirst { keyOf(it) == draggingKey }
                if (from < 0) return@detectDragGestures
                val h = lazyListState.layoutInfo.visibleItemsInfo
                    .firstOrNull { it.key == draggingKey }
                    ?.size?.toFloat()
                    ?: 80.dp.toPx()
                if (dragOffsetPx <= -h) {
                    val to = (from - 1).coerceAtLeast(0)
                    if (to != from) { onReorder(from, to); dragOffsetPx += h }
                } else if (dragOffsetPx >= h) {
                    val to = (from + 1).coerceIn(0, list.lastIndex)
                    if (to != from) { onReorder(from, to); dragOffsetPx -= h }
                }
            },
            onDragEnd = { draggingKey = null; dragOffsetPx = 0f },
            onDragCancel = { draggingKey = null; dragOffsetPx = 0f },
        )
    }

    fun draggedItemModifier(item: T): Modifier =
        if (draggingKey == keyOf(item)) {
            Modifier.offset { IntOffset(0, dragOffsetPx.roundToInt()) }.zIndex(1f)
        } else Modifier
}

@Composable
fun <T : Any> rememberReorderState(
    lazyListState: LazyListState,
    items: List<T>,
    keyOf: (T) -> Any,
    onReorder: (from: Int, to: Int) -> Unit,
): ReorderState<T> {
    val itemsState = rememberUpdatedState(items)
    val onReorderState = rememberUpdatedState(onReorder)
    return remember(lazyListState) {
        ReorderState(
            lazyListState = lazyListState,
            itemsProvider = { itemsState.value },
            keyOf = keyOf,
            onReorder = { from, to -> onReorderState.value(from, to) },
        )
    }
}
