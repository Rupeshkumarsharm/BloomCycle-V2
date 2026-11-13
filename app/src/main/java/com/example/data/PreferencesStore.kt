package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.util.PinSecurity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "bloomcycle_prefs")

data class AppPreferences(
  val onboardingCompleted: Boolean = false,
  val showFertileWindow: Boolean = true,
  val pinEnabled: Boolean = false,
  val pinHash: String = "",
  val pinSalt: String = "",
  val darkThemePreference: String = "SYSTEM" // SYSTEM, LIGHT, DARK
)

class PreferencesStore(private val context: Context) {
  private object Keys {
    val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    val SHOW_FERTILE_WINDOW = booleanPreferencesKey("show_fertile_window")
    val PIN_ENABLED = booleanPreferencesKey("pin_enabled")
    val PIN_HASH = stringPreferencesKey("pin_hash")
    val PIN_SALT = stringPreferencesKey("pin_salt")
    val DARK_THEME_PREF = stringPreferencesKey("dark_theme_pref")
  }

  val preferencesFlow: Flow<AppPreferences> = context.dataStore.data.map { prefs ->
    AppPreferences(
      onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
      showFertileWindow = prefs[Keys.SHOW_FERTILE_WINDOW] ?: true,
      pinEnabled = prefs[Keys.PIN_ENABLED] ?: false,
      pinHash = prefs[Keys.PIN_HASH] ?: "",
      pinSalt = prefs[Keys.PIN_SALT] ?: "",
      darkThemePreference = prefs[Keys.DARK_THEME_PREF] ?: "SYSTEM"
    )
  }

  suspend fun setOnboardingCompleted(completed: Boolean) {
    context.dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
  }

  suspend fun setShowFertileWindow(show: Boolean) {
    context.dataStore.edit { it[Keys.SHOW_FERTILE_WINDOW] = show }
  }

  suspend fun setDarkThemePreference(mode: String) {
    context.dataStore.edit { it[Keys.DARK_THEME_PREF] = mode }
  }

  suspend fun setPin(pin: String) {
    val salt = PinSecurity.generateSalt()
    val hash = PinSecurity.hashPin(pin, salt)
    context.dataStore.edit {
      it[Keys.PIN_ENABLED] = true
      it[Keys.PIN_SALT] = salt
      it[Keys.PIN_HASH] = hash
    }
  }

  suspend fun disablePin() {
    context.dataStore.edit {
      it[Keys.PIN_ENABLED] = false
      it[Keys.PIN_SALT] = ""
      it[Keys.PIN_HASH] = ""
    }
  }

  suspend fun clearAllPreferences() {
    context.dataStore.edit { it.clear() }
  }
}
