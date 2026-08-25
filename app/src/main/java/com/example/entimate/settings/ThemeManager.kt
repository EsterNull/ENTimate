package com.example.entimate.settings

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.example.entimate.ui.components.shiftHue
import com.example.entimate.ui.components.colorLuminance

private val tokioLight = lightColorScheme(
    primary = Color(0xFF3D59FA),
    secondary = Color(0xFF9854F1),
    tertiary = Color(0xFF007ACC),
    background = Color(0xFFD5D6DB),
    surface = Color(0xFFEDEEF2),
    onBackground = Color(0xFF343B58),
    onSurface = Color(0xFF343B58),
    error = Color(0xFFE0264D),
)

private val tokioDark = darkColorScheme(
    primary = Color(0xFF7AA2F7),
    secondary = Color(0xFFBB9AF7),
    tertiary = Color(0xFF7DCFFF),
    background = Color(0xFF1A1B26),
    surface = Color(0xFF24283B),
    onBackground = Color(0xFFC0CAF5),
    onSurface = Color(0xFFC0CAF5),
    error = Color(0xFFF7768E),
)

private val nordLight = lightColorScheme(
    primary = Color(0xFF5E81AC),
    secondary = Color(0xFFB48EAD),
    tertiary = Color(0xFFA3BE8C),
    background = Color(0xFFECEFF4),
    surface = Color(0xFFF7F9FC),
    onBackground = Color(0xFF2E3440),
    onSurface = Color(0xFF2E3440),
    error = Color(0xFFBF616A),
)

private val nordDark = darkColorScheme(
    primary = Color(0xFF88C0D0),
    secondary = Color(0xFFB48EAD),
    tertiary = Color(0xFFA3BE8C),
    background = Color(0xFF2E3440),
    surface = Color(0xFF3B4252),
    onBackground = Color(0xFFECEFF4),
    onSurface = Color(0xFFECEFF4),
    error = Color(0xFFBF616A),
)

private val gruvboxLight = lightColorScheme(
    primary = Color(0xFFAF3A03),
    secondary = Color(0xFF8F3F71),
    tertiary = Color(0xFF79740E),
    background = Color(0xFFFBF1C7),
    surface = Color(0xFFF9F5D7),
    onBackground = Color(0xFF282828),
    onSurface = Color(0xFF282828),
    error = Color(0xFF9D0006),
)

private val gruvboxDark = darkColorScheme(
    primary = Color(0xFFD79921),
    secondary = Color(0xFFB16286),
    tertiary = Color(0xFF98971A),
    background = Color(0xFF282828),
    surface = Color(0xFF3C3836),
    onBackground = Color(0xFFEBDBB2),
    onSurface = Color(0xFFEBDBB2),
    error = Color(0xFFCC241D),
)

private val catppuccinLight = lightColorScheme(
    primary = Color(0xFF8839EF),
    secondary = Color(0xFFDDB6F2),
    tertiary = Color(0xFF179299),
    background = Color(0xFFEFF1F5),
    surface = Color(0xFFF8FAFC),
    onBackground = Color(0xFF4C4F69),
    onSurface = Color(0xFF4C4F69),
    error = Color(0xFFD20F39),
)

private val catppuccinDark = darkColorScheme(
    primary = Color(0xFFCBA6F7),
    secondary = Color(0xFFF5C2E7),
    tertiary = Color(0xFF94E2D5),
    background = Color(0xFF1E1E2E),
    surface = Color(0xFF313244),
    onBackground = Color(0xFFCDD6F4),
    onSurface = Color(0xFFCDD6F4),
    error = Color(0xFFF38BA8),
)

private fun adjust(c: Int, factor: Float): Int {
    val r = (c shr 16) and 0xFF
    val g = (c shr 8) and 0xFF
    val b = c and 0xFF
    val nr = (r + (255 - r) * factor).toInt().coerceIn(0, 255)
    val ng = (g + (255 - g) * factor).toInt().coerceIn(0, 255)
    val nb = (b + (255 - b) * factor).toInt().coerceIn(0, 255)
    return (0xFF shl 24) or (nr shl 16) or (ng shl 8) or nb
}

private fun customScheme(dark: Boolean, color: Long, bg: Long, secondaryColor: Long = 0L): ColorScheme {
    val base = Color(color.toInt())
    val secondary = if (secondaryColor != 0L) Color(secondaryColor.toInt()) else Color(shiftHue(color.toInt(), 40f))
    val tertiary = Color(shiftHue(color.toInt(), 200f))
    val bgInt = if (bg != 0L) bg.toInt() else (if (dark) 0xFF15151A.toInt() else 0xFFF4F4F8.toInt())
    val surfaceInt = if (bg != 0L) adjust(bgInt, if (dark) 0.06f else -0.03f) else (if (dark) 0xFF1F1F25.toInt() else 0xFFFFFFFF.toInt())
    val surfaceVariantInt = if (bg != 0L) adjust(bgInt, if (dark) 0.12f else -0.06f) else (if (dark) 0xFF2A2A32.toInt() else 0xFFE6E6EE.toInt())
    val onBg = if (colorLuminance(Color(bgInt)) > 0.5f) Color.Black else Color.White
    return if (dark) {
        darkColorScheme(
            primary = base,
            onPrimary = Color.White,
            primaryContainer = base.copy(alpha = 0.25f),
            onPrimaryContainer = base,
            secondary = secondary,
            onSecondary = Color.White,
            tertiary = tertiary,
            onTertiary = Color.Black,
            background = Color(bgInt),
            onBackground = onBg,
            surface = Color(surfaceInt),
            onSurface = onBg,
            surfaceVariant = Color(surfaceVariantInt),
            onSurfaceVariant = if (bg != 0L) onBg.copy(alpha = 0.7f) else Color(0xFFB6B6C0),
            outline = Color(adjust(bgInt, -0.2f)),
            outlineVariant = Color(surfaceVariantInt),
            error = Color(0xFFF7768E),
            onError = Color.Black,
        )
    } else {
        lightColorScheme(
            primary = base,
            onPrimary = Color.White,
            primaryContainer = base.copy(alpha = 0.15f),
            onPrimaryContainer = base,
            secondary = secondary,
            onSecondary = Color.White,
            tertiary = tertiary,
            onTertiary = Color.Black,
            background = Color(bgInt),
            onBackground = onBg,
            surface = Color(surfaceInt),
            onSurface = onBg,
            surfaceVariant = Color(surfaceVariantInt),
            onSurfaceVariant = if (bg != 0L) onBg.copy(alpha = 0.7f) else Color(0xFF5A5A66),
            outline = Color(adjust(bgInt, 0.2f)),
            outlineVariant = Color(surfaceVariantInt),
            error = Color(0xFFD72660),
            onError = Color.White,
        )
    }
}

fun getColorScheme(preset: String, dark: Boolean, customColor: Long, customBg: Long = 0L, customSecondary: Long = 0L): ColorScheme {
    return when (preset) {
        "nord" -> if (dark) nordDark else nordLight
        "gruvbox" -> if (dark) gruvboxDark else gruvboxLight
        "catppuccin" -> if (dark) catppuccinDark else catppuccinLight
        "custom" -> customScheme(dark, customColor, customBg, customSecondary)
        else -> if (dark) tokioDark else tokioLight
    }
}
