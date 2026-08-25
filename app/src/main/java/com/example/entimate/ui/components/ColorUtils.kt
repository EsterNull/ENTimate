package com.example.entimate.ui.components

import androidx.compose.ui.graphics.Color
import kotlin.math.abs

val QUICK_COLORS = listOf(
    0xFFE5484D.toInt(),
    0xFFFF9800.toInt(),
    0xFFE0AF68.toInt(),
    0xFF9ECE6A.toInt(),
    0xFF7DCFFF.toInt(),
    0xFF7AA2F7.toInt(),
    0xFF6750A4.toInt(),
    0xFFBB9AF7.toInt(),
)

fun hsvToColorInt(hue: Float, saturation: Float, value: Float): Int {
    val h = (hue % 360f + 360f) % 360f
    val c = value * saturation
    val x = c * (1f - abs((h / 60f) % 2f - 1f))
    val m = value - c
    val (r, g, b) = when (h.toInt() / 60) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val ri = ((r + m) * 255f).toInt().coerceIn(0, 255)
    val gi = ((g + m) * 255f).toInt().coerceIn(0, 255)
    val bi = ((b + m) * 255f).toInt().coerceIn(0, 255)
    return (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
}

fun rgbToHsv(color: Int): Triple<Float, Float, Float> {
    val r = ((color shr 16) and 0xFF) / 255f
    val g = ((color shr 8) and 0xFF) / 255f
    val b = (color and 0xFF) / 255f
    val max = if (r >= g && r >= b) r else if (g >= b) g else b
    val min = if (r <= g && r <= b) r else if (g <= b) g else b
    val delta = max - min
    val hue = when {
        delta == 0f -> 0f
        max == r -> 60f * (((g - b) / delta) % 6f)
        max == g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }.let { if (it < 0f) it + 360f else it }
    val sat = if (max == 0f) 0f else delta / max
    return Triple(hue, sat, max)
}

fun colorLuminance(color: Color): Float {
    return 0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue
}

fun shiftHue(color: Int, degrees: Float): Int {
    val (h, s, v) = rgbToHsv(color)
    return hsvToColorInt(h + degrees, s, v)
}
