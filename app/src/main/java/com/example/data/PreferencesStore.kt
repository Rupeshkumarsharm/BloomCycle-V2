package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.util.PinSecurity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private val Context.dataStore by preferencesDataStore(name = "bloomcycle_prefs")

data class AppPreferences(
  val onboardingCompleted: Boolean = false,
  val showFertileWindow: Boolean = true,
  val pinEnabled: Boolean = false,
  val pinHash: String = "",
  val pinSalt: String = "",
  val pinFailedAttempts: Int = 0,
  val pinLockoutUntilEpochMs: Long = 0L,
  val pinLockoutSetEpochMs: Long = 0L,
  val pinLockoutDurationMs: Long = 0L,
  val darkThemePreference: String = "SYSTEM" // SYSTEM, LIGHT, DARK
)

class PreferencesStore(private val context: Context) {
  private object Keys {
    val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    val SHOW_FERTILE_WINDOW = booleanPreferencesKey("show_fertile_window")
    val PIN_ENABLED = booleanPreferencesKey("pin_enabled")
    val PIN_HASH = stringPreferencesKey("pin_hash")
    val PIN_SALT = stringPreferencesKey("pin_salt")
    val PIN_FAILED_ATTEMPTS = intPreferencesKey("pin_failed_attempts")
    val PIN_LOCKOUT_UNTIL_EPOCH_MS = longPreferencesKey("pin_lockout_until_epoch_ms")
    val PIN_LOCKOUT_SET_EPOCH_MS = longPreferencesKey("pin_lockout_set_epoch_ms")
    val PIN_LOCKOUT_DURATION_MS = longPreferencesKey("pin_lockout_duration_ms")
    val DARK_THEME_PREF = stringPreferencesKey("dark_theme_pref")
  }

  val preferencesFlow: Flow<AppPreferences> = context.dataStore.data.map { prefs ->
    AppPreferences(
      onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
      showFertileWindow = prefs[Keys.SHOW_FERTILE_WINDOW] ?: true,
      pinEnabled = prefs[Keys.PIN_ENABLED] ?: false,
      pinHash = prefs[Keys.PIN_HASH] ?: "",
      pinSalt = prefs[Keys.PIN_SALT] ?: "",
      pinFailedAttempts = (prefs[Keys.PIN_FAILED_ATTEMPTS] ?: 0).coerceAtLeast(0),
      pinLockoutUntilEpochMs = (prefs[Keys.PIN_LOCKOUT_UNTIL_EPOCH_MS] ?: 0L).coerceAtLeast(0L),
      pinLockoutSetEpochMs = (prefs[Keys.PIN_LOCKOUT_SET_EPOCH_MS] ?: 0L).coerceAtLeast(0L),
      pinLockoutDurationMs = (prefs[Keys.PIN_LOCKOUT_DURATION_MS] ?: 0L).coerceAtLeast(0L),
      darkThemePreference = prefs[Keys.DARK_THEME_PREF] ?: "SYSTEM"
    )
  }

  suspend fun setOnboardingCompleted(completed: Boolean) {
    context.dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
  }

  suspend fun setOnboardingCompletedWithOptionalPin(optionalPin: String?) {
    val encodedHash = if (!optionalPin.isNullOrBlank()) {
      withContext(Dispatchers.Default) { PinSecurity.hashPin(optionalPin) }
    } else null

    context.dataStore.edit { prefs ->
      if (encodedHash != null) {
        prefs[Keys.PIN_ENABLED] = true
        prefs[Keys.PIN_SALT] = ""
        prefs[Keys.PIN_HASH] = encodedHash
        prefs[Keys.PIN_FAILED_ATTEMPTS] = 0
        prefs[Keys.PIN_LOCKOUT_UNTIL_EPOCH_MS] = 0L
        prefs[Keys.PIN_LOCKOUT_SET_EPOCH_MS] = 0L
        prefs[Keys.PIN_LOCKOUT_DURATION_MS] = 0L
      }
      prefs[Keys.ONBOARDING_COMPLETED] = true
    }
  }

  suspend fun setShowFertileWindow(show: Boolean) {
    context.dataStore.edit { it[Keys.SHOW_FERTILE_WINDOW] = show }
  }

  suspend fun setDarkThemePreference(mode: String) {
    context.dataStore.edit { it[Keys.DARK_THEME_PREF] = mode }
  }

  suspend fun setPin(pin: String) {
    val encodedHash = withContext(Dispatchers.Default) { PinSecurity.hashPin(pin) }
    context.dataStore.edit { prefs ->
      prefs[Keys.PIN_ENABLED] = true
      prefs[Keys.PIN_SALT] = ""
      prefs[Keys.PIN_HASH] = encodedHash
      prefs[Keys.PIN_FAILED_ATTEMPTS] = 0
      prefs[Keys.PIN_LOCKOUT_UNTIL_EPOCH_MS] = 0L
      prefs[Keys.PIN_LOCKOUT_SET_EPOCH_MS] = 0L
      prefs[Keys.PIN_LOCKOUT_DURATION_MS] = 0L
    }
  }

  suspend fun upgradeLegacyPinHashAndClearFailures(newV2Hash: String) {
    context.dataStore.edit { prefs ->
      if (prefs[Keys.PIN_ENABLED] == true) {
        prefs[Keys.PIN_HASH] = newV2Hash
        prefs[Keys.PIN_SALT] = ""
      }
      prefs[Keys.PIN_FAILED_ATTEMPTS] = 0
      prefs[Keys.PIN_LOCKOUT_UNTIL_EPOCH_MS] = 0L
      prefs[Keys.PIN_LOCKOUT_SET_EPOCH_MS] = 0L
      prefs[Keys.PIN_LOCKOUT_DURATION_MS] = 0L
    }
  }

  suspend fun recordFailedPinAttempt(
    failedAttempts: Int,
    lockoutUntilEpochMs: Long,
    lockoutSetEpochMs: Long,
    lockoutDurationMs: Long
  ) {
    context.dataStore.edit { prefs ->
      prefs[Keys.PIN_FAILED_ATTEMPTS] = failedAttempts.coerceAtLeast(0)
      prefs[Keys.PIN_LOCKOUT_UNTIL_EPOCH_MS] = lockoutUntilEpochMs.coerceAtLeast(0L)
      prefs[Keys.PIN_LOCKOUT_SET_EPOCH_MS] = lockoutSetEpochMs.coerceAtLeast(0L)
      prefs[Keys.PIN_LOCKOUT_DURATION_MS] = lockoutDurationMs.coerceAtLeast(0L)
    }
  }

  suspend fun clearPinFailureState() {
    context.dataStore.edit { prefs ->
      prefs[Keys.PIN_FAILED_ATTEMPTS] = 0
      prefs[Keys.PIN_LOCKOUT_UNTIL_EPOCH_MS] = 0L
      prefs[Keys.PIN_LOCKOUT_SET_EPOCH_MS] = 0L
      prefs[Keys.PIN_LOCKOUT_DURATION_MS] = 0L
    }
  }

  suspend fun disablePin() {
    context.dataStore.edit { prefs ->
      prefs[Keys.PIN_ENABLED] = false
      prefs[Keys.PIN_SALT] = ""
      prefs[Keys.PIN_HASH] = ""
      prefs[Keys.PIN_FAILED_ATTEMPTS] = 0
      prefs[Keys.PIN_LOCKOUT_UNTIL_EPOCH_MS] = 0L
      prefs[Keys.PIN_LOCKOUT_SET_EPOCH_MS] = 0L
      prefs[Keys.PIN_LOCKOUT_DURATION_MS] = 0L
    }
  }

  suspend fun clearAllPreferences() {
    context.dataStore.edit { it.clear() }
  }

  internal suspend fun storeRawPinStateForTest(
    enabled: Boolean,
    hash: String,
    salt: String = "",
    onboardingCompleted: Boolean = true
  ) {
    context.dataStore.edit { prefs ->
      prefs[Keys.ONBOARDING_COMPLETED] = onboardingCompleted
      prefs[Keys.PIN_ENABLED] = enabled
      prefs[Keys.PIN_HASH] = hash
      prefs[Keys.PIN_SALT] = salt
      prefs[Keys.PIN_FAILED_ATTEMPTS] = 0
      prefs[Keys.PIN_LOCKOUT_UNTIL_EPOCH_MS] = 0L
      prefs[Keys.PIN_LOCKOUT_SET_EPOCH_MS] = 0L
      prefs[Keys.PIN_LOCKOUT_DURATION_MS] = 0L
    }
  }
}
