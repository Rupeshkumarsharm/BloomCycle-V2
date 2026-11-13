package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AppPreferences
import com.example.data.BloomDatabase
import com.example.data.BloomRepository
import com.example.data.ImportMode
import com.example.data.PastCycleRange
import com.example.data.PreferencesStore
import com.example.data.excel.ExcelXlsxEngine
import com.example.data.excel.ParsedWorkbookPreview
import com.example.domain.analytics.CycleAnalyticsEngine
import com.example.domain.model.AnalyticsSnapshot
import com.example.domain.model.AnalyticsWindow
import com.example.domain.model.CustomOption
import com.example.domain.model.CustomOptionType
import com.example.domain.model.DailyEntry
import com.example.domain.model.FlowLevel
import com.example.domain.model.UserProfile
import com.example.util.CycleDate
import com.example.util.PinSecurity
import kotlin.math.ceil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

interface SecurityClock {
  fun currentTimeMillis(): Long
  fun elapsedRealtimeMillis(): Long
}

object SystemSecurityClock : SecurityClock {
  override fun currentTimeMillis(): Long = System.currentTimeMillis()
  override fun elapsedRealtimeMillis(): Long = SystemClock.elapsedRealtime()
}

object PinThrottlePolicy {
  const val MAX_FREE_ATTEMPTS = 3
  const val MAX_LOCKOUT_MS = 900_000L // 15-minute ceiling; never causes permanent lockout

  fun lockoutDurationForAttempts(failedAttempts: Int): Long = when {
    failedAttempts <= MAX_FREE_ATTEMPTS -> 0L
    failedAttempts == 4 -> 15_000L
    failedAttempts == 5 -> 30_000L
    failedAttempts == 6 -> 60_000L
    failedAttempts == 7 -> 300_000L
    else -> MAX_LOCKOUT_MS
  }

  fun formatCooldown(remainingMs: Long): String {
    val totalSeconds = ceil(remainingMs.coerceAtLeast(0L) / 1000.0).toInt().coerceAtLeast(1)
    val mins = totalSeconds / 60
    val secs = totalSeconds % 60
    return if (mins > 0 && secs > 0) "${mins}m ${secs}s"
    else if (mins > 0) "${mins}m"
    else "${secs}s"
  }
}

private sealed interface PendingSafAction {
  data class Export(val appContext: Context, val uri: Uri) : PendingSafAction
  data class ImportPreview(val appContext: Context, val uri: Uri) : PendingSafAction
}

data class BloomUiState(
  val isLoaded: Boolean = false,
  val onboardingCompleted: Boolean = false,
  val profile: UserProfile? = null,
  val preferences: AppPreferences = AppPreferences(),
  val isPinLocked: Boolean = false,
  val pinError: String? = null,
  val pinLockoutRemainingSeconds: Int = 0,
  val isVerifyingPin: Boolean = false,
  val hasPendingBackupActionAfterUnlock: Boolean = false,
  val entries: List<DailyEntry> = emptyList(),
  val entriesByDate: Map<CycleDate, DailyEntry> = emptyMap(),
  val customOptions: List<CustomOption> = emptyList(),
  val selectedDate: CycleDate = CycleDate.today(),
  val analyticsWindow: AnalyticsWindow = AnalyticsWindow.ALL,
  val analytics: AnalyticsSnapshot = AnalyticsSnapshot.Empty,
  val saveStatusMessage: String? = null,
  val pendingImportPreview: ParsedWorkbookPreview? = null,
  val backupMessage: String? = null,
  val backupError: String? = null
)

class MainViewModel(
  private val repository: BloomRepository,
  private val clock: SecurityClock = SystemSecurityClock
) : ViewModel() {

  private val authMutex = Mutex()
  private val selectedDateFlow = MutableStateFlow(CycleDate.today())
  private val analyticsWindowFlow = MutableStateFlow(AnalyticsWindow.ALL)
  private val pinUnlockedThisSession = MutableStateFlow(false)
  private val pinErrorFlow = MutableStateFlow<String?>(null)
  private val pinLockoutSecondsFlow = MutableStateFlow(0)
  private val isVerifyingPinFlow = MutableStateFlow(false)
  private val hasPendingSafFlow = MutableStateFlow(false)
  private val saveStatusFlow = MutableStateFlow<String?>(null)
  private val pendingImportFlow = MutableStateFlow<ParsedWorkbookPreview?>(null)
  private val backupMessageFlow = MutableStateFlow<String?>(null)
  private val backupErrorFlow = MutableStateFlow<String?>(null)

  private var saveStatusJob: Job? = null
  private var lockoutTickerJob: Job? = null

  private var sessionLockoutUntilElapsedMs: Long = 0L
  private var hydratedPersistedLockout: Boolean = false

  @Volatile
  private var safPickerAwaitingResult: Boolean = false

  @Volatile
  private var pendingSafAction: PendingSafAction? = null

  private data class CoreDataBundle(
    val profile: UserProfile?,
    val entries: List<DailyEntry>,
    val customOptions: List<CustomOption>,
    val preferences: AppPreferences,
    val analyticsWindow: AnalyticsWindow,
    val analytics: AnalyticsSnapshot
  )

  private data class SecurityUiOverlay(
    val unlocked: Boolean,
    val pinError: String?,
    val lockoutRemainingSeconds: Int,
    val isVerifyingPin: Boolean,
    val hasPendingSafAction: Boolean
  )

  private val coreBundleFlow = combine(
    repository.profileFlow,
    repository.entriesFlow,
    repository.customOptionsFlow,
    repository.preferencesFlow,
    analyticsWindowFlow
  ) { profile, entries, customOptions, prefs, window ->
    val snapshot = CycleAnalyticsEngine.buildAnalyticsSnapshot(
      profile = profile,
      entries = entries,
      showFertileWindow = prefs.showFertileWindow,
      today = CycleDate.today(),
      window = window
    )
    CoreDataBundle(
      profile = profile,
      entries = entries,
      customOptions = customOptions,
      preferences = prefs,
      analyticsWindow = window,
      analytics = snapshot
    )
  }.flowOn(Dispatchers.Default)

  private val securityOverlayFlow = combine(
    pinUnlockedThisSession,
    pinErrorFlow,
    pinLockoutSecondsFlow,
    isVerifyingPinFlow,
    hasPendingSafFlow
  ) { unlocked, pinError, lockoutSeconds, isVerifying, hasPendingSaf ->
    SecurityUiOverlay(
      unlocked = unlocked,
      pinError = pinError,
      lockoutRemainingSeconds = lockoutSeconds,
      isVerifyingPin = isVerifying,
      hasPendingSafAction = hasPendingSaf
    )
  }

  val uiState: StateFlow<BloomUiState> = combine(
    coreBundleFlow,
    selectedDateFlow,
    securityOverlayFlow,
    saveStatusFlow,
    pendingImportFlow,
    backupMessageFlow,
    backupErrorFlow
  ) { values ->
    val bundle = values[0] as CoreDataBundle
    val selectedDate = values[1] as CycleDate
    val security = values[2] as SecurityUiOverlay
    val saveStatus = values[3] as String?
    val pendingImport = values[4] as ParsedWorkbookPreview?
    val backupMsg = values[5] as String?
    val backupErr = values[6] as String?

    val locked = bundle.preferences.pinEnabled && !security.unlocked
    BloomUiState(
      isLoaded = true,
      onboardingCompleted = bundle.preferences.onboardingCompleted,
      profile = bundle.profile,
      preferences = bundle.preferences,
      isPinLocked = locked,
      pinError = security.pinError,
      pinLockoutRemainingSeconds = security.lockoutRemainingSeconds,
      isVerifyingPin = security.isVerifyingPin,
      hasPendingBackupActionAfterUnlock = locked && security.hasPendingSafAction,
      entries = bundle.entries,
      entriesByDate = bundle.entries.associateBy { it.date },
      customOptions = bundle.customOptions,
      selectedDate = selectedDate,
      analyticsWindow = bundle.analyticsWindow,
      analytics = bundle.analytics,
      saveStatusMessage = saveStatus,
      pendingImportPreview = pendingImport,
      backupMessage = backupMsg,
      backupError = backupErr
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = BloomUiState()
  )

  init {
    viewModelScope.launch {
      authMutex.withLock {
        val prefs = repository.preferencesFlow.first()
        if (prefs.pinEnabled) {
          val remainingMs = computeRemainingLockoutMsLocked(prefs)
          if (remainingMs > 0L) {
            startLockoutTickerLocked(remainingMs)
            pinErrorFlow.value =
              "Too many incorrect attempts. Try again in ${PinThrottlePolicy.formatCooldown(remainingMs)}."
          }
        }
      }
    }
  }

  private fun computeRemainingLockoutMsLocked(prefs: AppPreferences): Long {
    val nowElapsed = clock.elapsedRealtimeMillis()
    if (sessionLockoutUntilElapsedMs > 0L) {
      val monoRemaining = sessionLockoutUntilElapsedMs - nowElapsed
      return if (monoRemaining > 0L) {
        monoRemaining.coerceAtMost(PinThrottlePolicy.MAX_LOCKOUT_MS)
      } else {
        sessionLockoutUntilElapsedMs = 0L
        0L
      }
    }

    if (!hydratedPersistedLockout) {
      hydratedPersistedLockout = true
      if (prefs.pinLockoutDurationMs > 0L && prefs.pinLockoutUntilEpochMs > 0L) {
        val cappedDuration = prefs.pinLockoutDurationMs.coerceIn(0L, PinThrottlePolicy.MAX_LOCKOUT_MS)
        val nowEpoch = clock.currentTimeMillis()
        val remaining = when {
          // Wall clock moved backward past lockout start -> enforce capped cooldown without permanent lockout
          nowEpoch < prefs.pinLockoutSetEpochMs -> cappedDuration
          nowEpoch >= prefs.pinLockoutUntilEpochMs -> 0L
          else -> (prefs.pinLockoutUntilEpochMs - nowEpoch).coerceIn(0L, cappedDuration)
        }
        if (remaining > 0L) {
          sessionLockoutUntilElapsedMs = nowElapsed + remaining
          return remaining
        }
      }
    }
    return 0L
  }

  private fun startLockoutTickerLocked(initialRemainingMs: Long) {
    lockoutTickerJob?.cancel()
    val deadlineElapsed = clock.elapsedRealtimeMillis() + initialRemainingMs.coerceAtLeast(0L)
    sessionLockoutUntilElapsedMs = deadlineElapsed
    pinLockoutSecondsFlow.value = ceil(initialRemainingMs / 1000.0).toInt().coerceAtLeast(1)

    lockoutTickerJob = viewModelScope.launch {
      while (true) {
        val remaining = deadlineElapsed - clock.elapsedRealtimeMillis()
        if (remaining <= 0L) {
          pinLockoutSecondsFlow.value = 0
          pinErrorFlow.value = null
          break
        }
        pinLockoutSecondsFlow.value = ceil(remaining / 1000.0).toInt().coerceAtLeast(1)
        pinErrorFlow.value =
          "Too many incorrect attempts. Try again in ${PinThrottlePolicy.formatCooldown(remaining)}."
        delay(500L)
      }
    }
  }

  fun unlockWithPin(pinAttempt: String) {
    viewModelScope.launch {
      var actionToRunAfterUnlock: PendingSafAction? = null
      authMutex.withLock {
        val prefs = repository.preferencesFlow.first()
        if (!prefs.pinEnabled) {
          pinUnlockedThisSession.value = true
          pinErrorFlow.value = null
          pinLockoutSecondsFlow.value = 0
          actionToRunAfterUnlock = popPendingSafAction()
          return@withLock
        }

        val activeRemainingMs = computeRemainingLockoutMsLocked(prefs)
        if (activeRemainingMs > 0L) {
          startLockoutTickerLocked(activeRemainingMs)
          pinErrorFlow.value =
            "Too many incorrect attempts. Try again in ${PinThrottlePolicy.formatCooldown(activeRemainingMs)}."
          return@withLock
        }

        isVerifyingPinFlow.value = true
        try {
          val outcome = withContext(Dispatchers.Default) {
            PinSecurity.verifyPinDetailed(
              pin = pinAttempt,
              encodedHash = prefs.pinHash,
              legacySalt = prefs.pinSalt
            )
          }

          when (outcome) {
            is PinSecurity.VerificationOutcome.Valid -> {
              if (outcome.needsMigrationToV2) {
                val upgradedHash = withContext(Dispatchers.Default) {
                  PinSecurity.hashPin(pinAttempt)
                }
                repository.upgradeLegacyPinHashAndClearFailures(upgradedHash)
              } else if (prefs.pinFailedAttempts > 0 || prefs.pinLockoutDurationMs > 0L || prefs.pinLockoutUntilEpochMs > 0L) {
                repository.clearPinFailureState()
              }

              lockoutTickerJob?.cancel()
              sessionLockoutUntilElapsedMs = 0L
              hydratedPersistedLockout = true
              pinLockoutSecondsFlow.value = 0
              pinErrorFlow.value = null
              pinUnlockedThisSession.value = true
              actionToRunAfterUnlock = popPendingSafAction()
            }

            PinSecurity.VerificationOutcome.Invalid,
            PinSecurity.VerificationOutcome.Malformed -> {
              val nextAttempts = prefs.pinFailedAttempts + 1
              val durationMs = PinThrottlePolicy.lockoutDurationForAttempts(nextAttempts)
              val nowEpoch = clock.currentTimeMillis()
              val untilEpoch = if (durationMs > 0L) nowEpoch + durationMs else 0L
              val setEpoch = if (durationMs > 0L) nowEpoch else 0L

              repository.recordFailedPinAttempt(
                failedAttempts = nextAttempts,
                lockoutUntilEpochMs = untilEpoch,
                lockoutSetEpochMs = setEpoch,
                lockoutDurationMs = durationMs
              )
              hydratedPersistedLockout = true

              if (durationMs > 0L) {
                startLockoutTickerLocked(durationMs)
                pinErrorFlow.value =
                  "Too many incorrect attempts. Try again in ${PinThrottlePolicy.formatCooldown(durationMs)}."
              } else {
                val remainingBeforeCooldown = PinThrottlePolicy.MAX_FREE_ATTEMPTS - nextAttempts
                pinErrorFlow.value = if (remainingBeforeCooldown == 1) {
                  "Incorrect PIN. 1 attempt remaining before cooldown."
                } else {
                  "Incorrect PIN. Please try again."
                }
              }
            }
          }
        } finally {
          isVerifyingPinFlow.value = false
        }
      }

      when (val deferred = actionToRunAfterUnlock) {
        is PendingSafAction.Export -> executeExportInternal(deferred.appContext, deferred.uri)
        is PendingSafAction.ImportPreview -> executeImportPreviewInternal(deferred.appContext, deferred.uri)
        null -> Unit
      }
    }
  }

  private fun popPendingSafAction(): PendingSafAction? {
    val action = pendingSafAction
    pendingSafAction = null
    hasPendingSafFlow.value = false
    return action
  }

  fun onLaunchingSafPicker() {
    safPickerAwaitingResult = true
  }

  fun onSafPickerCancelled() {
    safPickerAwaitingResult = false
    popPendingSafAction()
  }

  fun lockAppNow(fromLifecycleStop: Boolean = false) {
    pinUnlockedThisSession.value = false
    pendingImportFlow.value = null
    saveStatusFlow.value = null
    if (!fromLifecycleStop || !safPickerAwaitingResult) {
      safPickerAwaitingResult = false
      popPendingSafAction()
    }
    if (pinLockoutSecondsFlow.value <= 0) {
      pinErrorFlow.value = null
    }
  }

  fun clearPinError() {
    if (pinLockoutSecondsFlow.value <= 0) {
      pinErrorFlow.value = null
    }
  }

  private suspend fun isAuthenticatedSession(): Boolean {
    val prefs = repository.preferencesFlow.first()
    return !prefs.pinEnabled || pinUnlockedThisSession.value
  }

  private suspend fun canPerformImportRestore(): Boolean {
    val prefs = repository.preferencesFlow.first()
    if (!prefs.onboardingCompleted && !prefs.pinEnabled) return true
    return !prefs.pinEnabled || pinUnlockedThisSession.value
  }

  fun completeOnboarding(
    profile: UserProfile,
    pastCycles: List<PastCycleRange>,
    optionalPin: String?
  ) {
    viewModelScope.launch {
      authMutex.withLock {
        val currentPrefs = repository.preferencesFlow.first()
        if (currentPrefs.onboardingCompleted && currentPrefs.pinEnabled && !pinUnlockedThisSession.value) {
          return@withLock
        }
        repository.completeOnboarding(profile, pastCycles, optionalPin)
        if (!optionalPin.isNullOrBlank()) {
          pinUnlockedThisSession.value = true
          sessionLockoutUntilElapsedMs = 0L
          hydratedPersistedLockout = true
          pinLockoutSecondsFlow.value = 0
          pinErrorFlow.value = null
        }
      }
    }
  }

  fun selectDate(date: CycleDate) {
    selectedDateFlow.value = date
  }

  fun setAnalyticsWindow(window: AnalyticsWindow) {
    analyticsWindowFlow.value = window
  }

  fun saveDailyEntry(entry: DailyEntry) {
    viewModelScope.launch {
      if (!isAuthenticatedSession()) return@launch
      repository.saveDailyEntry(entry)
      showSavedStatus("Saved locally")
    }
  }

  fun quickTogglePeriodToday() {
    viewModelScope.launch {
      if (!isAuthenticatedSession()) return@launch
      val today = CycleDate.today()
      val currentEntries = repository.entriesFlow.first()
      val existing = currentEntries.firstOrNull { it.date == today } ?: DailyEntry(date = today)
      val nextIsPeriod = !existing.isPeriod
      val updated = existing.copy(
        isPeriod = nextIsPeriod,
        flowLevel = if (nextIsPeriod && existing.flowLevel == FlowLevel.NONE) {
          FlowLevel.MEDIUM
        } else if (!nextIsPeriod) {
          FlowLevel.NONE
        } else existing.flowLevel
      )
      repository.saveDailyEntry(updated)
      showSavedStatus("Saved locally")
    }
  }

  fun addCustomOption(type: CustomOptionType, label: String) {
    viewModelScope.launch {
      if (!isAuthenticatedSession()) return@launch
      repository.addCustomOption(type, label)
    }
  }

  fun deleteCustomOption(id: Long) {
    viewModelScope.launch {
      if (!isAuthenticatedSession()) return@launch
      repository.deleteCustomOption(id)
    }
  }

  fun updateProfile(profile: UserProfile) {
    viewModelScope.launch {
      if (!isAuthenticatedSession()) return@launch
      repository.saveProfile(profile)
      showSavedStatus("Profile updated locally")
    }
  }

  fun setShowFertileWindow(show: Boolean) {
    viewModelScope.launch {
      if (!isAuthenticatedSession()) return@launch
      repository.setShowFertileWindow(show)
    }
  }

  fun setDarkThemePreference(mode: String) {
    viewModelScope.launch {
      if (!isAuthenticatedSession()) return@launch
      repository.setDarkThemePreference(mode)
    }
  }

  fun setPin(pin: String) {
    viewModelScope.launch {
      authMutex.withLock {
        val prefs = repository.preferencesFlow.first()
        if (prefs.pinEnabled && !pinUnlockedThisSession.value) return@withLock
        if (!PinSecurity.isValidPinFormat(pin)) return@withLock
        repository.setPin(pin)
        sessionLockoutUntilElapsedMs = 0L
        hydratedPersistedLockout = true
        lockoutTickerJob?.cancel()
        pinLockoutSecondsFlow.value = 0
        pinErrorFlow.value = null
        pinUnlockedThisSession.value = true
        showSavedStatus("Local PIN lock enabled")
      }
    }
  }

  fun disablePin() {
    viewModelScope.launch {
      authMutex.withLock {
        val prefs = repository.preferencesFlow.first()
        if (prefs.pinEnabled && !pinUnlockedThisSession.value) return@withLock
        repository.disablePin()
        sessionLockoutUntilElapsedMs = 0L
        hydratedPersistedLockout = true
        lockoutTickerJob?.cancel()
        pinLockoutSecondsFlow.value = 0
        pinErrorFlow.value = null
        pinUnlockedThisSession.value = true
        showSavedStatus("PIN lock disabled")
      }
    }
  }

  fun exportToExcelUri(context: Context, uri: Uri) {
    val cameFromSafPicker = safPickerAwaitingResult
    safPickerAwaitingResult = false
    val appContext = context.applicationContext

    viewModelScope.launch {
      if (!isAuthenticatedSession()) {
        if (cameFromSafPicker) {
          pendingSafAction = PendingSafAction.Export(appContext, uri)
          hasPendingSafFlow.value = true
        } else {
          backupErrorFlow.value = "Unlock with your PIN before exporting backups."
        }
        return@launch
      }
      executeExportInternal(appContext, uri)
    }
  }

  private suspend fun executeExportInternal(context: Context, uri: Uri) {
    if (!isAuthenticatedSession()) return
    backupErrorFlow.value = null
    backupMessageFlow.value = null

    val prefs = repository.preferencesFlow.first()
    val profile = repository.profileFlow.first()
    val entries = repository.entriesFlow.first()
    val snapshot = withContext(Dispatchers.Default) {
      CycleAnalyticsEngine.buildAnalyticsSnapshot(
        profile = profile,
        entries = entries,
        showFertileWindow = prefs.showFertileWindow,
        today = CycleDate.today(),
        window = analyticsWindowFlow.value
      )
    }

    val result = withContext(Dispatchers.IO) {
      runCatching {
        context.contentResolver.openOutputStream(uri)?.use { out ->
          ExcelXlsxEngine.exportWorkbook(out, profile, entries, snapshot)
        } ?: error("Unable to open destination file for writing.")
      }
    }
    result.fold(
      onSuccess = {
        backupMessageFlow.value = "Exported ${entries.size} daily log(s) to Excel (.xlsx)."
      },
      onFailure = { err ->
        backupErrorFlow.value = "Export failed: ${err.message ?: "Unknown error"}"
      }
    )
  }

  fun previewExcelImportUri(context: Context, uri: Uri) {
    val cameFromSafPicker = safPickerAwaitingResult
    safPickerAwaitingResult = false
    val appContext = context.applicationContext

    viewModelScope.launch {
      if (!canPerformImportRestore()) {
        if (cameFromSafPicker) {
          pendingSafAction = PendingSafAction.ImportPreview(appContext, uri)
          hasPendingSafFlow.value = true
        } else {
          backupErrorFlow.value = "Unlock with your PIN before importing backups."
        }
        return@launch
      }
      executeImportPreviewInternal(appContext, uri)
    }
  }

  private suspend fun executeImportPreviewInternal(context: Context, uri: Uri) {
    if (!canPerformImportRestore()) return
    backupErrorFlow.value = null
    backupMessageFlow.value = null
    val result = withContext(Dispatchers.IO) {
      runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
          ExcelXlsxEngine.parseWorkbook(input)
        } ?: error("Unable to read selected file.")
      }
    }
    result.fold(
      onSuccess = { preview ->
        if (preview.entries.isEmpty() && preview.profile == null) {
          backupErrorFlow.value = "No valid BloomCycle entries were found in that workbook."
        } else {
          pendingImportFlow.value = preview
        }
      },
      onFailure = { err ->
        backupErrorFlow.value = "Import failed: ${err.message ?: "Unsupported workbook format"}"
      }
    )
  }

  fun dismissImportPreview() {
    pendingImportFlow.value = null
  }

  fun confirmImport(mode: ImportMode) {
    val preview = pendingImportFlow.value ?: return
    viewModelScope.launch {
      if (!canPerformImportRestore()) {
        pendingImportFlow.value = null
        backupErrorFlow.value = "Unlock with your PIN before restoring data."
        return@launch
      }
      repository.applyWorkbookImport(preview, mode)
      pendingImportFlow.value = null
      backupMessageFlow.value = "Restored ${preview.entries.size} day(s) from Excel (${mode.name.lowercase()})."
    }
  }

  fun clearBackupBanners() {
    backupMessageFlow.value = null
    backupErrorFlow.value = null
  }

  fun clearAllLocalData() {
    viewModelScope.launch {
      authMutex.withLock {
        val prefs = repository.preferencesFlow.first()
        if (prefs.pinEnabled && !pinUnlockedThisSession.value) return@withLock
        repository.clearAllLocalData()
        popPendingSafAction()
        safPickerAwaitingResult = false
        pendingImportFlow.value = null
        sessionLockoutUntilElapsedMs = 0L
        hydratedPersistedLockout = false
        lockoutTickerJob?.cancel()
        pinLockoutSecondsFlow.value = 0
        pinErrorFlow.value = null
        pinUnlockedThisSession.value = false
        selectedDateFlow.value = CycleDate.today()
      }
    }
  }

  private fun showSavedStatus(message: String) {
    saveStatusJob?.cancel()
    saveStatusFlow.value = message
    saveStatusJob = viewModelScope.launch {
      delay(2200)
      saveStatusFlow.value = null
    }
  }
}

class MainViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
  @Suppress("UNCHECKED_CAST")
  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    val db = BloomDatabase.getInstance(context)
    val prefs = PreferencesStore(context.applicationContext)
    val repo = BloomRepository(
      profileDao = db.userProfileDao(),
      entryDao = db.dailyEntryDao(),
      cycleDao = db.cycleDao(),
      customOptionDao = db.customOptionDao(),
      preferencesStore = prefs,
      database = db
    )
    return MainViewModel(repo) as T
  }
}
