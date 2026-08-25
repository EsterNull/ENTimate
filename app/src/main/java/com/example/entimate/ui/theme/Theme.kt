package com.example.entimate.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import com.example.entimate.settings.ThemeSettings
import com.example.entimate.settings.getColorScheme

@Composable
fun AppTheme(
    settings: ThemeSettings,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (settings.darkMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }

    val colorScheme = getColorScheme(settings.preset, darkTheme, settings.customColor, settings.customBg, settings.customSecondary)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content,
    )
}
