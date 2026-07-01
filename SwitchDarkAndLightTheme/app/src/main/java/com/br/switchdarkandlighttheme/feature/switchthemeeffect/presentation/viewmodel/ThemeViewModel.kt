package com.br.switchdarkandlighttheme.feature.switchthemeeffect.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.br.switchdarkandlighttheme.feature.switchthemeeffect.data.preference.ThemePreferenceManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/*
    https://share.google/aimode/qyqbtlr3gfbRo12LT
 */
class ThemeViewModel(private val themePreferenceManager: ThemePreferenceManager) : ViewModel() {

    val isDarkTheme: StateFlow<Boolean?> = themePreferenceManager.isDarkTheme
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    fun toggleTheme() {
        viewModelScope.launch {
            isDarkTheme.value?.let { themePreferenceManager.saveThemePreference(!it) }
        }
    }

    companion object {
        /**
         * Cria uma Factory utilizando a DSL moderna do Android (viewModelFactory).
         * Passamos a instância do manager como parâmetro para construir o escopo.
         */
        fun provideFactory(themePreferenceManager: ThemePreferenceManager): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    ThemeViewModel(themePreferenceManager = themePreferenceManager)
                }
            }
    }
}