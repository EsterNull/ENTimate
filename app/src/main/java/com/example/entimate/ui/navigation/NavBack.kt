package com.example.entimate.ui.navigation

import android.os.SystemClock
import androidx.navigation.NavController

private const val BACK_DEBOUNCE_MS = 300L
private var lastBackAt = 0L

/** Bottom-tab roots: the arrow-back must stop here and never cross between tabs. */
val TAB_ROUTES: Set<String> = setOf("documents", "patients", "reports", "settings")

/**
 * Guarded "arrow back" for the top bar: ignores rapid consecutive taps
 * (which would otherwise pop several screens at once) and never pops a
 * bottom-tab root, so back from a sub-screen stops at its own tab instead
 * of landing on the start tab / an empty back stack.
 */
fun NavController.navigateBack() {
    val now = SystemClock.uptimeMillis()
    if (now - lastBackAt < BACK_DEBOUNCE_MS) return
    lastBackAt = now
    val current = currentBackStackEntry?.destination?.route ?: return
    if (current in TAB_ROUTES) return
    if (previousBackStackEntry == null) return
    popBackStack()
}