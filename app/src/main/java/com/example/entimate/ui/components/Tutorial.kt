package com.example.entimate.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.example.entimate.data.local.DocumentEntity

data class TutorialStep(
    val anchorKey: String?,
    val title: String,
    val text: String,
    val navTarget: String? = null,
    val showSwipeDemo: Boolean = false,
)

class TutorialState(
    val steps: List<TutorialStep>,
    private val onSeen: () -> Unit = {},
) {
    var active by mutableStateOf(false)
        private set
    var step by mutableStateOf(0)
        private set
    val anchors = mutableStateMapOf<String, Rect>()

    val currentStep: TutorialStep? get() = steps.getOrNull(step)
    val isLast: Boolean get() = step >= steps.lastIndex

    fun start() { step = 0; active = true }
    fun next() { if (isLast) finish() else step++ }
    fun skip() = finish()
    fun finish() { active = false; onSeen() }
    fun setAnchor(key: String, rect: Rect) { anchors[key] = rect }
}

val LocalTutorial = compositionLocalOf<TutorialState?> { null }

@Composable
fun Modifier.tutorialAnchor(key: String): Modifier {
    val tutorial = LocalTutorial.current ?: return this
    return this.onGloballyPositioned { coordinates ->
        tutorial.setAnchor(key, coordinates.boundsInRoot())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TutorialOverlay(state: TutorialState) {
    if (!state.active) return
    val step = state.currentStep ?: return
    val anchor = step.anchorKey?.let { state.anchors[it] }
    val density = LocalDensity.current
    val showSwipeDemo = step.showSwipeDemo

    val demoDoc = remember {
        DocumentEntity(id = 0, name = "Документ", description = "Пример карточки", colorArgb = 0, quantity = 12)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    ) {
        val maxWPx = with(density) { maxWidth.roundToPx() }
        val maxHPx = with(density) { maxHeight.roundToPx() }
        var tooltipH by remember { mutableStateOf(240) }
        val tooltipWidthPx = with(density) { (300.dp).roundToPx() }.coerceAtMost(maxWPx - 16)
        val tooltipWidthDp = with(density) { tooltipWidthPx.toDp() }

        Canvas(Modifier.fillMaxSize()) {
            val c = Color.Black.copy(alpha = 0.72f)
            if (anchor != null) {
                val top = anchor.top
                val bottom = anchor.bottom
                val left = anchor.left
                val right = anchor.right
                val w = size.width
                val h = size.height
                drawRect(c, topLeft = Offset(0f, 0f), size = Size(w, top))
                drawRect(c, topLeft = Offset(0f, bottom), size = Size(w, h - bottom))
                drawRect(c, topLeft = Offset(0f, top), size = Size(left, bottom - top))
                drawRect(c, topLeft = Offset(right, top), size = Size(w - right, bottom - top))
            } else {
                drawRect(c)
            }
        }

        if (anchor != null) {
            Canvas(Modifier.fillMaxSize()) {
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.9f),
                    topLeft = Offset(anchor.left - 2f, anchor.top - 2f),
                    size = Size(anchor.width + 4f, anchor.height + 4f),
                    cornerRadius = CornerRadius(14.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
                )
            }
        }

        if (showSwipeDemo) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(top = 48.dp, bottom = 260.dp, start = 24.dp, end = 24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "← свайп влево: удалить",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                SwipeDemoCard(SwipeToDismissBoxValue.EndToStart, demoDoc)
                Spacer(Modifier.height(16.dp))
                SwipeDemoCard(SwipeToDismissBoxValue.StartToEnd, demoDoc)
                Spacer(Modifier.height(8.dp))
                Text(
                    "свайп вправо: редактировать →",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        val xPx = if (anchor != null) {
            (anchor.center.x - tooltipWidthPx / 2f).toInt()
                .coerceIn(8, (maxWPx - tooltipWidthPx - 8).coerceAtLeast(8))
        } else {
            ((maxWPx - tooltipWidthPx) / 2f).toInt().coerceAtLeast(8)
        }
        val yPx = if (showSwipeDemo) {
            (maxHPx - tooltipH - 16).toInt().coerceAtLeast(8)
        } else if (anchor != null) {
            val below = anchor.bottom + 8f
            if (below + tooltipH < maxHPx) below.toInt()
            else (anchor.top - 8f - tooltipH).coerceAtLeast(8f).toInt()
        } else {
            ((maxHPx - tooltipH) / 2f).toInt().coerceAtLeast(8)
        }

        Surface(
            modifier = Modifier
                .offset { IntOffset(xPx, yPx) }
                .width(tooltipWidthDp)
                .onGloballyPositioned { tooltipH = it.size.height },
            shape = RoundedCornerShape(12.dp),
            shadowElevation = 8.dp,
            tonalElevation = 6.dp,
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(step.title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(step.text, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = state::skip) { Text("Пропустить") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = state::next) { Text(if (state.isLast) "Готово" else "Далее") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeDemoCard(direction: SwipeToDismissBoxValue, doc: DocumentEntity) {
    val isDelete = direction == SwipeToDismissBoxValue.EndToStart
    val icon = if (isDelete) Icons.Filled.Delete else Icons.Filled.Edit
    val bg = if (isDelete) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
    val tint = if (isDelete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
    ) {
        Box(
            Modifier
                .matchParentSize()
                .background(bg)
                .padding(horizontal = 24.dp),
            contentAlignment = if (isDelete) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Icon(icon, contentDescription = null, tint = tint)
        }
        Box(
            Modifier.offset { IntOffset((if (isDelete) -150 else 150).dp.roundToPx(), 0) },
        ) {
            DocumentCard(doc = doc, onClick = {}, onLongClick = {}, onAdjust = {}, onCommit = {})
        }
    }
}
