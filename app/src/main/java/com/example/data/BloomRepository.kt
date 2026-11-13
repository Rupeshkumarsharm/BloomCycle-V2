package com.example.data

import androidx.room.withTransaction
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
  private val preferencesStore: PreferencesStore,
  private val database: BloomDatabase? = null
) {
  private val writeMutex = Mutex()

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

  private suspend fun <T> withDbTransaction(block: suspend () -> T): T {
    val db = database
    return if (db != null) db.withTransaction { block() } else block()
  }

  suspend fun completeOnboarding(
    profile: UserProfile,
    pastCycles: List<PastCycleRange>,
    optionalPin: String?
  ) {
    writeMutex.withLock {
      withDbTransaction {
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
        syncDerivedCyclesLocked()
      }
      preferencesStore.setOnboardingCompletedWithOptionalPin(optionalPin)
    }
  }

  suspend fun saveProfile(profile: UserProfile) {
    writeMutex.withLock {
      withDbTransaction {
        profileDao.upsertProfile(UserProfileEntity.fromDomain(profile))
        syncDerivedCyclesLocked()
      }
    }
  }

  suspend fun saveDailyEntry(entry: DailyEntry) {
    writeMutex.withLock {
      withDbTransaction {
        if (!entry.hasAnyDetails) {
          entryDao.deleteByDate(entry.date)
        } else {
          entryDao.upsertEntry(DailyEntryEntity.fromDomain(entry.copy(updatedAtMillis = System.currentTimeMillis())))
        }
        syncDerivedCyclesLocked()
      }
    }
  }

  suspend fun addCustomOption(type: CustomOptionType, label: String) {
    val clean = label.trim()
    if (clean.isEmpty()) return
    writeMutex.withLock {
      withDbTransaction {
        val existing = customOptionDao.getAllOptionsOnce()
        if (existing.none { it.type == type && it.label.equals(clean, ignoreCase = true) }) {
          customOptionDao.insertOption(
            CustomOptionEntity.fromDomain(CustomOption(type = type, label = clean))
          )
        }
      }
    }
  }

  suspend fun deleteCustomOption(id: Long) {
    writeMutex.withLock {
      customOptionDao.deleteById(id)
    }
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

  suspend fun upgradeLegacyPinHashAndClearFailures(newV2Hash: String) {
    preferencesStore.upgradeLegacyPinHashAndClearFailures(newV2Hash)
  }

  suspend fun recordFailedPinAttempt(
    failedAttempts: Int,
    lockoutUntilEpochMs: Long,
    lockoutSetEpochMs: Long,
    lockoutDurationMs: Long
  ) {
    preferencesStore.recordFailedPinAttempt(
      failedAttempts = failedAttempts,
      lockoutUntilEpochMs = lockoutUntilEpochMs,
      lockoutSetEpochMs = lockoutSetEpochMs,
      lockoutDurationMs = lockoutDurationMs
    )
  }

  suspend fun clearPinFailureState() {
    preferencesStore.clearPinFailureState()
  }

  suspend fun disablePin() {
    preferencesStore.disablePin()
  }

  suspend fun applyWorkbookImport(preview: ParsedWorkbookPreview, mode: ImportMode) {
    writeMutex.withLock {
      var importedProfile = false
      withDbTransaction {
        if (mode == ImportMode.REPLACE) {
          entryDao.clearAllEntries()
          customOptionDao.clearAllOptions()
        }
        if (preview.profile != null && (mode == ImportMode.REPLACE || profileDao.getProfileOnce() == null)) {
          profileDao.upsertProfile(UserProfileEntity.fromDomain(preview.profile))
          importedProfile = true
        }
        if (preview.entries.isNotEmpty()) {
          entryDao.upsertEntries(preview.entries.map { DailyEntryEntity.fromDomain(it) })
        }
        if (preview.discoveredCustomOptions.isNotEmpty()) {
          val existing = customOptionDao.getAllOptionsOnce()
          val seen = existing.map { it.type to it.label.trim().lowercase() }.toMutableSet()
          val newOnes = mutableListOf<CustomOptionEntity>()
          for (candidate in preview.discoveredCustomOptions) {
            val clean = candidate.label.trim()
            if (clean.isNotEmpty() && seen.add(candidate.type to clean.lowercase())) {
              newOnes.add(CustomOptionEntity.fromDomain(candidate.copy(label = clean)))
            }
          }
          if (newOnes.isNotEmpty()) {
            customOptionDao.insertOptions(newOnes)
          }
        }
        syncDerivedCyclesLocked()
      }
      if (importedProfile) {
        preferencesStore.setOnboardingCompleted(true)
      }
    }
  }

  suspend fun clearAllLocalData() {
    writeMutex.withLock {
      withDbTransaction {
        entryDao.clearAllEntries()
        cycleDao.clearAllCycles()
        customOptionDao.clearAllOptions()
        profileDao.clearProfile()
      }
      preferencesStore.clearAllPreferences()
    }
  }

  suspend fun syncDerivedCycles() {
    writeMutex.withLock {
      withDbTransaction {
        syncDerivedCyclesLocked()
      }
    }
  }

  private suspend fun syncDerivedCyclesLocked() {
    val profile = profileDao.getProfileOnce()?.toDomain()
    val entries = entryDao.getAllEntriesOnce().map { it.toDomain() }
    val derived = CycleAnalyticsEngine.deriveCycles(entries, profile)
    cycleDao.replaceAllCycles(derived.map { CycleEntity.fromDomain(it) })
  }
}
