package com.example.entimate.ui.settings

import com.example.entimate.ui.navigation.navigateBack

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.entimate.settings.ThemeSettings
import com.example.entimate.settings.getColorScheme
import com.example.entimate.ui.components.ColorPickerDialog
import com.example.entimate.ui.components.Skeleton
import com.example.entimate.viewmodel.SettingsViewModel

private val PRESETS = listOf(
    "tokyonight" to "Tokyo Night",
    "nord" to "Nord",
    "gruvbox" to "Gruvbox",
    "catppuccin" to "Catppuccin",
    "custom" to "Кастомная",
)

private val DARK_MODES = listOf(
    "system" to "Системная",
    "light" to "Светлая",
    "dark" to "Тёмная",
)

@Composable
private fun isDark(s: ThemeSettings): Boolean = when (s.darkMode) {
    "dark" -> true
    "light" -> false
    else -> isSystemInDarkTheme()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSettingsScreen(nav: NavController, vm: SettingsViewModel = viewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var pickerTarget by remember { mutableStateOf<String?>(null) }

    if (pickerTarget != null) {
        val isBg = pickerTarget == "bg"
        val isSecondary = pickerTarget == "secondary"
        ColorPickerDialog(
            initialColor = when (pickerTarget) {
                "bg" -> if (settings.customBg != 0L) settings.customBg.toInt() else 0xFF1B1B22.toInt()
                "secondary" -> if (settings.customSecondary != 0L) settings.customSecondary.toInt() else 0xFF9854F1.toInt()
                else -> settings.customColor.toInt()
            },
            onDismiss = { pickerTarget = null },
            onColorSelected = {
                when (pickerTarget) {
                    "bg" -> vm.update(customBg = it.toLong())
                    "secondary" -> vm.update(customSecondary = it.toLong())
                    else -> vm.update(customColor = it.toLong())
                }
                pickerTarget = null
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Тема оформления") },
                navigationIcon = {
                    IconButton(onClick = { nav.navigateBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text("Тема (светлая/тёмная)", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            DARK_MODES.forEach { (value, label) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = settings.darkMode == value, onClick = { vm.update(darkMode = value) })
                        .padding(vertical = 4.dp),
                ) {
                    RadioButton(selected = settings.darkMode == value, onClick = { vm.update(darkMode = value) })
                    Spacer(Modifier.width(8.dp))
                    Text(label)
                }
            }

            Spacer(Modifier.height(24.dp))
            Text("Оформление", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            PRESETS.forEach { (value, label) ->
                val scheme = getColorScheme(value, isDark(settings), settings.customColor, settings.customBg, settings.customSecondary)
                ThemePreviewCard(
                    selected = settings.preset == value,
                    scheme = scheme,
                    title = label,
                    onClick = { vm.update(preset = value) },
                )
                Spacer(Modifier.height(12.dp))
            }

            if (settings.preset == "custom") {
                Spacer(Modifier.height(8.dp))
                Button(onClick = { pickerTarget = "accent" }, modifier = Modifier.fillMaxWidth()) {
                    Text("Выбрать цвет акцента")
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = { pickerTarget = "secondary" }, modifier = Modifier.fillMaxWidth()) {
                    Text("Выбрать вторичный цвет")
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = { pickerTarget = "bg" }, modifier = Modifier.fillMaxWidth()) {
                    Text("Выбрать цвет фона")
                }
            }
        }
    }
}

@Composable
private fun ThemePreviewCard(
    selected: Boolean,
    scheme: androidx.compose.material3.ColorScheme,
    title: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        border = if (selected) BorderStroke(2.dp, scheme.primary) else null,
    ) {
        MaterialTheme(colorScheme = scheme) {
            Column(Modifier.padding(16.dp)) {
                ThemePreviewSample()
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (selected) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = scheme.primary)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(title, style = MaterialTheme.typography.labelLarge, color = scheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun ThemePreviewSample() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            color = MaterialTheme.colorScheme.primary,
            shape = RoundedCornerShape(50),
            modifier = Modifier.size(36.dp),
        ) { }
        Spacer(Modifier.width(10.dp))
        Column {
            Text("Заголовок", style = MaterialTheme.typography.titleMedium)
            Text("Пример интерфейса", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.weight(1f))
        Surface(
            color = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary,
            shape = RoundedCornerShape(8.dp),
        ) {
            Text("ВТОР", modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.width(8.dp))
        AssistChip(onClick = { }, label = { Text("ЛОР") })
    }
    Spacer(Modifier.height(14.dp))
    Text("Загрузка данных:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(6.dp))
    Skeleton(modifier = Modifier.fillMaxWidth().height(14.dp))
    Spacer(Modifier.height(6.dp))
    Skeleton(modifier = Modifier.fillMaxWidth(0.7f).height(14.dp))
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
        value = "Значение",
        onValueChange = { },
        label = { Text("Поле") },
        enabled = false,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(10.dp))
    var sw by remember { mutableStateOf(true) }
    var cb by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Переключатель", color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(8.dp))
        Switch(checked = sw, onCheckedChange = { sw = it })
        Spacer(Modifier.width(16.dp))
        Checkbox(checked = cb, onCheckedChange = { cb = it })
        Text("Флажок", color = MaterialTheme.colorScheme.onSurface)
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { }, modifier = Modifier.weight(1f)) { Text("Основная") }
        FilledTonalButton(onClick = { }, modifier = Modifier.weight(1f)) { Text("Вторичная") }
    }
}
