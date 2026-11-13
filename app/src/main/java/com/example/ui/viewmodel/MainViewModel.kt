package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.excel.ExcelXlsxEngine
import com.example.data.excel.ParsedWorkbookPreview
import com.example.data.AppPreferences
import com.example.data.BloomDatabase
import com.example.data.BloomRepository
import com.example.data.ImportMode
import com.example.data.PastCycleRange
import com.example.data.PreferencesStore
import com.example.domain.analytics.CycleAnalyticsEngine
import com.example.domain.model.AnalyticsSnapshot
import com.example.domain.model.AnalyticsWindow
import com.example.domain.model.CustomOption
import com.example.domain.model.CustomOptionType
import com.example.domain.model.DailyEntry
import com.example.domain.model.UserProfile
import com.example.util.CycleDate
import com.example.util.PinSecurity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BloomUiState(
  val isLoaded: Boolean = false,
  val onboardingCompleted: Boolean = false,
  val profile: UserProfile? = null,
  val preferences: AppPreferences = AppPreferences(),
  val isPinLocked: Boolean = false,
  val pinError: String? = null,
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
  private val repository: BloomRepository
) : ViewModel() {

  private val selectedDateFlow = MutableStateFlow(CycleDate.today())
  private val analyticsWindowFlow = MutableStateFlow(AnalyticsWindow.ALL)
  private val pinUnlockedThisSession = MutableStateFlow(false)
  private val pinErrorFlow = MutableStateFlow<String?>(null)
  private val saveStatusFlow = MutableStateFlow<String?>(null)
  private val pendingImportFlow = MutableStateFlow<ParsedWorkbookPreview?>(null)
  private val backupMessageFlow = MutableStateFlow<String?>(null)
  private val backupErrorFlow = MutableStateFlow<String?>(null)

  private var saveStatusJob: Job? = null

  private data class CoreDataBundle(
    val profile: UserProfile?,
    val entries: List<DailyEntry>,
    val customOptions: List<CustomOption>,
    val preferences: AppPreferences,
    val analyticsWindow: AnalyticsWindow,
    val analytics: AnalyticsSnapshot
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

  val uiState: StateFlow<BloomUiState> = combine(
    coreBundleFlow,
    selectedDateFlow,
    pinUnlockedThisSession,
    pinErrorFlow,
    saveStatusFlow,
    pendingImportFlow,
    backupMessageFlow,
    backupErrorFlow
  ) { values ->
    val bundle = values[0] as CoreDataBundle
    val selectedDate = values[1] as CycleDate
    val unlocked = values[2] as Boolean
    val pinError = values[3] as String?
    val saveStatus = values[4] as String?
    val pendingImport = values[5] as ParsedWorkbookPreview?
    val backupMsg = values[6] as String?
    val backupErr = values[7] as String?

    val locked = bundle.preferences.pinEnabled && !unlocked
    BloomUiState(
      isLoaded = true,
      onboardingCompleted = bundle.preferences.onboardingCompleted,
      profile = bundle.profile,
      preferences = bundle.preferences,
      isPinLocked = locked,
      pinError = pinError,
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

  fun unlockWithPin(pinAttempt: String) {
    val prefs = uiState.value.preferences
    if (!prefs.pinEnabled) {
      pinUnlockedThisSession.value = true
      return
    }
    if (PinSecurity.verifyPin(pinAttempt, prefs.pinSalt, prefs.pinHash)) {
      pinUnlockedThisSession.value = true
      pinErrorFlow.value = null
    } else {
      pinErrorFlow.value = "Incorrect PIN. Please try again."
    }
  }

  fun lockAppNow() {
    if (uiState.value.preferences.pinEnabled) {
      pinUnlockedThisSession.value = false
      pinErrorFlow.value = null
    }
  }

  fun clearPinError() {
    pinErrorFlow.value = null
  }

  fun completeOnboarding(
    profile: UserProfile,
    pastCycles: List<PastCycleRange>,
    optionalPin: String?
  ) {
    viewModelScope.launch {
      repository.completeOnboarding(profile, pastCycles, optionalPin)
      if (!optionalPin.isNullOrBlank()) {
        pinUnlockedThisSession.value = true
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
      repository.saveDailyEntry(entry)
      showSavedStatus("Saved locally")
    }
  }

  fun quickTogglePeriodToday() {
    val today = CycleDate.today()
    val existing = uiState.value.entriesByDate[today] ?: DailyEntry(date = today)
    val nextIsPeriod = !existing.isPeriod
    val updated = existing.copy(
      isPeriod = nextIsPeriod,
      flowLevel = if (nextIsPeriod && existing.flowLevel == com.example.domain.model.FlowLevel.NONE) {
        com.example.domain.model.FlowLevel.MEDIUM
      } else if (!nextIsPeriod) {
        com.example.domain.model.FlowLevel.NONE
      } else existing.flowLevel
    )
    saveDailyEntry(updated)
  }

  fun addCustomOption(type: CustomOptionType, label: String) {
    viewModelScope.launch {
      repository.addCustomOption(type, label)
    }
  }

  fun deleteCustomOption(id: Long) {
    viewModelScope.launch {
      repository.deleteCustomOption(id)
    }
  }

  fun updateProfile(profile: UserProfile) {
    viewModelScope.launch {
      repository.saveProfile(profile)
      showSavedStatus("Profile updated locally")
    }
  }

  fun setShowFertileWindow(show: Boolean) {
    viewModelScope.launch {
      repository.setShowFertileWindow(show)
    }
  }

  fun setDarkThemePreference(mode: String) {
    viewModelScope.launch {
      repository.setDarkThemePreference(mode)
    }
  }

  fun setPin(pin: String) {
    viewModelScope.launch {
      repository.setPin(pin)
      pinUnlockedThisSession.value = true
      showSavedStatus("Local PIN lock enabled")
    }
  }

  fun disablePin() {
    viewModelScope.launch {
      repository.disablePin()
      pinUnlockedThisSession.value = true
      showSavedStatus("PIN lock disabled")
    }
  }

  fun exportToExcelUri(context: Context, uri: Uri) {
    viewModelScope.launch {
      backupErrorFlow.value = null
      backupMessageFlow.value = null
      val snapshot = uiState.value.analytics
      val profile = uiState.value.profile
      val entries = uiState.value.entries
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
  }

  fun previewExcelImportUri(context: Context, uri: Uri) {
    viewModelScope.launch {
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
  }

  fun dismissImportPreview() {
    pendingImportFlow.value = null
  }

  fun confirmImport(mode: ImportMode) {
    val preview = pendingImportFlow.value ?: return
    viewModelScope.launch {
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
      repository.clearAllLocalData()
      pinUnlockedThisSession.value = false
      selectedDateFlow.value = CycleDate.today()
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
      preferencesStore = prefs
    )
    return MainViewModel(repo) as T
  }
}
