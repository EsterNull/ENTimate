package com.example.entimate.util

import java.util.Locale

/**
 * Lower-cased matching key in which «ё» counts as «е».
 * The source string keeps its «ё» for display; only comparisons use this key.
 */
fun String.normalKey(): String = lowercase(Locale.ROOT).replace('ё', 'е')