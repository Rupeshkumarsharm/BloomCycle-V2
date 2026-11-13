package com.example.data

import com.example.data.excel.ParsedWorkbookPreview
import com.example.domain.analytics.CycleAnalyticsEngine
import com.example.domain.model.CustomOption
import com.example.domain.model.CustomOptionType
import com.example.domain.model.DailyEntry
import com.example.domain.model.DerivedCycle
import com.example.domain.model.FlowLevel
import com.example.domain.model.UserProfile
import com.example.util.CycleDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ImportMode {
  MERGE,
  REPLACE
}

data class PastCycleRange(
  val startDate: CycleDate,
  val endDate: CycleDate
)

class BloomRepository(
  private val profileDao: UserProfileDao,
  private val entryDao: DailyEntryDao,
  private val cycleDao: CycleDao,
  private val customOptionDao: CustomOptionDao,
  private val preferencesStore: PreferencesStore
) {
  val profileFlow: Flow<UserProfile?> = profileDao.observeProfile().map { it?.toDomain() }
  val entriesFlow: Flow<List<DailyEntry>> = entryDao.observeAllEntries().map { list ->
    list.map { it.toDomain() }
  }
  val cyclesFlow: Flow<List<DerivedCycle>> = cycleDao.observeAllCycles().map { list ->
    list.map { it.toDomain() }
  }
  val customOptionsFlow: Flow<List<CustomOption>> = customOptionDao.observeAllOptions().map { list ->
    list.map { it.toDomain() }
  }
  val preferencesFlow: Flow<AppPreferences> = preferencesStore.preferencesFlow

  suspend fun completeOnboarding(
    profile: UserProfile,
    pastCycles: List<PastCycleRange>,
    optionalPin: String?
  ) {
    profileDao.upsertProfile(UserProfileEntity.fromDomain(profile))
    if (pastCycles.isNotEmpty()) {
      val initialEntries = mutableListOf<DailyEntryEntity>()
      for (range in pastCycles) {
        val start = minOf(range.startDate, range.endDate)
        val end = maxOf(range.startDate, range.endDate)
        val spanDays = start.daysUntil(end).coerceIn(0, 14)
        for (offset in 0..spanDays) {
          val date = start.plusDays(offset)
          val flow = when {
            offset == 0 -> FlowLevel.MEDIUM
            offset == spanDays -> FlowLevel.LIGHT
            else -> FlowLevel.MEDIUM
          }
          initialEntries.add(
            DailyEntryEntity.fromDomain(
              DailyEntry(
                date = date,
                isPeriod = true,
                flowLevel = flow,
                isPeriodStartOverride = offset == 0,
                isPeriodEndOverride = offset == spanDays
              )
            )
          )
        }
      }
      entryDao.upsertEntries(initialEntries)
    }
    syncDerivedCycles()
    if (!optionalPin.isNullOrBlank()) {
      preferencesStore.setPin(optionalPin)
    }
    preferencesStore.setOnboardingCompleted(true)
  }

  suspend fun saveProfile(profile: UserProfile) {
    profileDao.upsertProfile(UserProfileEntity.fromDomain(profile))
    syncDerivedCycles()
  }

  suspend fun saveDailyEntry(entry: DailyEntry) {
    if (!entry.hasAnyDetails) {
      entryDao.deleteByDate(entry.date)
    } else {
      entryDao.upsertEntry(DailyEntryEntity.fromDomain(entry.copy(updatedAtMillis = System.currentTimeMillis())))
    }
    syncDerivedCycles()
  }

  suspend fun addCustomOption(type: CustomOptionType, label: String) {
    val clean = label.trim()
    if (clean.isEmpty()) return
    val existing = customOptionDao.getAllOptionsOnce()
    if (existing.none { it.type == type && it.label.equals(clean, ignoreCase = true) }) {
      customOptionDao.insertOption(
        CustomOptionEntity.fromDomain(CustomOption(type = type, label = clean))
      )
    }
  }

  suspend fun deleteCustomOption(id: Long) {
    customOptionDao.deleteById(id)
  }

  suspend fun setShowFertileWindow(show: Boolean) {
    preferencesStore.setShowFertileWindow(show)
  }

  suspend fun setDarkThemePreference(mode: String) {
    preferencesStore.setDarkThemePreference(mode)
  }

  suspend fun setPin(pin: String) {
    preferencesStore.setPin(pin)
  }

  suspend fun disablePin() {
    preferencesStore.disablePin()
  }

  suspend fun applyWorkbookImport(preview: ParsedWorkbookPreview, mode: ImportMode) {
    if (mode == ImportMode.REPLACE) {
      entryDao.clearAllEntries()
      customOptionDao.clearAllOptions()
    }
    if (preview.profile != null && (mode == ImportMode.REPLACE || profileDao.getProfileOnce() == null)) {
      profileDao.upsertProfile(UserProfileEntity.fromDomain(preview.profile))
      preferencesStore.setOnboardingCompleted(true)
    }
    if (preview.entries.isNotEmpty()) {
      entryDao.upsertEntries(preview.entries.map { DailyEntryEntity.fromDomain(it) })
    }
    if (preview.discoveredCustomOptions.isNotEmpty()) {
      val existing = customOptionDao.getAllOptionsOnce()
      val newOnes = preview.discoveredCustomOptions.filter { candidate ->
        existing.none { it.type == candidate.type && it.label.equals(candidate.label, ignoreCase = true) }
      }
      if (newOnes.isNotEmpty()) {
        customOptionDao.insertOptions(newOnes.map { CustomOptionEntity.fromDomain(it) })
      }
    }
    syncDerivedCycles()
  }

  suspend fun clearAllLocalData() {
    entryDao.clearAllEntries()
    cycleDao.clearAllCycles()
    customOptionDao.clearAllOptions()
    profileDao.clearProfile()
    preferencesStore.clearAllPreferences()
  }

  suspend fun syncDerivedCycles() {
    val profile = profileDao.getProfileOnce()?.toDomain()
    val entries = entryDao.getAllEntriesOnce().map { it.toDomain() }
    val derived = CycleAnalyticsEngine.deriveCycles(entries, profile)
    cycleDao.replaceAllCycles(derived.map { CycleEntity.fromDomain(it) })
  }
}
