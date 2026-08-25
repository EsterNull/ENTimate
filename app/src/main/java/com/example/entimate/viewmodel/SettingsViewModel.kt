package com.example.entimate.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.entimate.EntimateApplication
import com.example.entimate.settings.SettingsDataStore
import com.example.entimate.settings.ThemeSettings
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val ds = SettingsDataStore(application)

    val settings: StateFlow<ThemeSettings> = ds.settingsFlow
        .catch { emit(ThemeSettings()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeSettings())

    fun update(
        preset: String? = null,
        darkMode: String? = null,
        customColor: Long? = null,
        customBg: Long? = null,
        customSecondary: Long? = null,
        dateFormat: String? = null,
    ) = viewModelScope.launch {
        ds.update(preset, darkMode, customColor, customBg, customSecondary, dateFormat)
    }
}
