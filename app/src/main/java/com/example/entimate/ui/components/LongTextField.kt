package com.example.entimate.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun LongTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = TextKeyboardOptions,
    keyboardActions: KeyboardActions = KeyboardActions(),
) {
    var fieldValue by remember { mutableStateOf(TextFieldValue(value)) }
    LaunchedEffect(value) {
        if (fieldValue.text != value) {
            fieldValue = TextFieldValue(value, TextRange(value.length))
        }
    }

    val scheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    val borderColor = when {
        isError -> scheme.error
        focused -> scheme.primary
        else -> scheme.outline
    }
    val labelColor = when {
        isError -> scheme.error
        focused -> scheme.primary
        else -> scheme.onSurfaceVariant
    }

    val floating = focused || fieldValue.text.isNotEmpty()
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var viewportWidth by remember { mutableStateOf(0) }

    suspend fun revealCaret() {
        val current = layout ?: return
        if (viewportWidth <= 0) return
        val offset = fieldValue.selection.end.coerceIn(0, fieldValue.text.length)
        val caret = current.getCursorRect(offset).left.toInt()
        val maxScroll = (current.size.width.toInt() - viewportWidth).coerceAtLeast(0)
        val position = scrollState.value
        val target = when {
            caret > position + viewportWidth -> caret - viewportWidth
            caret < position -> caret
            else -> position
        }
        val clamped = target.coerceIn(0, maxScroll)
        if (abs(clamped - position) > 1) {
            scrollState.scrollTo(clamped)
        }
    }

    LaunchedEffect(fieldValue.selection, layout, viewportWidth) { revealCaret() }

    Box(
        modifier = modifier
            .padding(end = 64.dp)
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .border(1.dp, borderColor, RoundedCornerShape(4.dp))
    ) {
        if (label.isNotBlank()) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = labelColor,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 12.dp)
                    .background(scheme.surface)
                    .padding(horizontal = 4.dp)
                    .offset(y = if (floating) (-8).dp else 8.dp),
            )
        }

        BasicTextField(
            value = fieldValue,
            onValueChange = { updated ->
                fieldValue = updated
                onValueChange(updated.text)
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
            cursorBrush = SolidColor(scheme.primary),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            interactionSource = interactionSource,
            onTextLayout = { result ->
                layout = result
                scope.launch { revealCaret() }
            },
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = if (floating) 20.dp else 8.dp, bottom = 8.dp),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clipToBounds()
                        .onSizeChanged { viewportWidth = it.width }
                ) {
                    Box(modifier = Modifier.horizontalScroll(scrollState, enabled = false)) {
                        innerTextField()
                    }
                }
            },
        )
    }
}
