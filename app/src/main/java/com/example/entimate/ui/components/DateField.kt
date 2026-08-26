package com.example.entimate.ui.components

import android.app.DatePickerDialog
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import java.text.SimpleDateFormat
import java.util.*

private val isoFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

val LocalDatePattern = compositionLocalOf { "dd.MM.yyyy" }

fun formatIsoDate(iso: String, pattern: String): String {
    if (iso.isBlank()) return ""
    return try {
        val fmt = SimpleDateFormat(pattern, Locale.getDefault())
        fmt.format(isoFmt.parse(iso) ?: return iso)
    } catch (_: Exception) {
        iso
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    maxDate: Long? = null,
    minDate: Long? = null,
) {
    val context = LocalContext.current
    val pattern = LocalDatePattern.current
    val display = formatIsoDate(value, pattern)
    val cal = remember(value, maxDate, minDate) {
        Calendar.getInstance().apply {
            if (value.isNotBlank()) {
                try { time = isoFmt.parse(value) ?: Date() } catch (_: Exception) { }
            } else if (maxDate != null) {
                timeInMillis = maxDate
            } else if (minDate != null) {
                timeInMillis = minDate
            }
        }
    }
    var show by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    DisposableEffect(show) {
        if (show) {
            val picker = DatePickerDialog(
                context,
                { _: android.widget.DatePicker, y, m, d ->
                    cal.set(y, m, d)
                    onValueChange(isoFmt.format(cal.time))
                },
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH),
            )
            if (maxDate != null) picker.datePicker.maxDate = maxDate
            if (minDate != null) picker.datePicker.minDate = minDate
            picker.setOnDismissListener { show = false }
            picker.show()
        }
        onDispose { }
    }

    LaunchedEffect(interactionSource) {
        var pressed = false
        interactionSource.interactions.collect { i ->
            when (i) {
                is PressInteraction.Press -> pressed = true
                is PressInteraction.Release -> if (pressed) { pressed = false; show = true }
                is PressInteraction.Cancel -> pressed = false
            }
        }
    }

    OutlinedTextField(
        value = display,
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        modifier = modifier,
        interactionSource = interactionSource,
        trailingIcon = { Text(if (value.isBlank()) "выбрать" else display) },
    )
}
