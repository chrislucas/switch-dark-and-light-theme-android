package com.br.switchdarkandlighttheme.feature.switchthemeeffect.data.preference


import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val THEME_PREFERENCE_KEY = "theme_preference"
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = THEME_PREFERENCE_KEY)

class ThemePreferenceManager(private val context: Context) {
    companion object {
        val IS_DARK_THEME = booleanPreferencesKey("is_dark_theme")
        const val IS_DEFAULT_DARK = false
    }

    suspend fun saveThemePreference(isDarkMode: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_DARK_THEME] = isDarkMode
        }
    }

    val isDarkTheme: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[IS_DARK_THEME] ?: IS_DEFAULT_DARK
    }
}