package com.example.entimate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.entimate.settings.SettingsDataStore
import com.example.entimate.settings.ThemeSettings
import com.example.entimate.ui.components.LocalDatePattern
import com.example.entimate.ui.navigation.AppNavigation
import com.example.entimate.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settingsDataStore = SettingsDataStore(this)
        setContent {
            val settings by settingsDataStore.settingsFlow.collectAsStateWithLifecycle(ThemeSettings())
            AppTheme(settings) {
                CompositionLocalProvider(LocalDatePattern provides settings.dateFormat) {
                    AppNavigation()
                }
            }
        }
    }
}
