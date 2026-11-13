package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.domain.model.CustomOptionType
import com.example.util.CycleDate
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {
  @Query("SELECT * FROM user_profile LIMIT 1")
  fun observeProfile(): Flow<UserProfileEntity?>

  @Query("SELECT * FROM user_profile LIMIT 1")
  suspend fun getProfileOnce(): UserProfileEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertProfile(profile: UserProfileEntity)

  @Query("DELETE FROM user_profile")
  suspend fun clearProfile()
}

@Dao
interface DailyEntryDao {
  @Query("SELECT * FROM daily_entries ORDER BY date ASC")
  fun observeAllEntries(): Flow<List<DailyEntryEntity>>

  @Query("SELECT * FROM daily_entries WHERE date BETWEEN :startDate AND :endDate ORDER BY date ASC")
  fun observeEntriesInRange(startDate: CycleDate, endDate: CycleDate): Flow<List<DailyEntryEntity>>

  @Query("SELECT * FROM daily_entries WHERE isPeriod = 1 ORDER BY date ASC")
  fun observePeriodEntries(): Flow<List<DailyEntryEntity>>

  @Query("SELECT * FROM daily_entries ORDER BY date ASC")
  suspend fun getAllEntriesOnce(): List<DailyEntryEntity>

  @Query("SELECT * FROM daily_entries WHERE date = :date LIMIT 1")
  suspend fun getEntryByDate(date: CycleDate): DailyEntryEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertEntry(entry: DailyEntryEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertEntries(entries: List<DailyEntryEntity>)

  @Query("DELETE FROM daily_entries WHERE date = :date")
  suspend fun deleteByDate(date: CycleDate)

  @Query("DELETE FROM daily_entries")
  suspend fun clearAllEntries()
}

@Dao
interface CycleDao {
  @Query("SELECT * FROM cycles ORDER BY cycleIndex ASC")
  fun observeAllCycles(): Flow<List<CycleEntity>>

  @Query("SELECT * FROM cycles ORDER BY cycleIndex DESC LIMIT :limit")
  fun observeRecentCycles(limit: Int): Flow<List<CycleEntity>>

  @Query("SELECT * FROM cycles ORDER BY cycleIndex ASC")
  suspend fun getAllCyclesOnce(): List<CycleEntity>

  @Query("SELECT * FROM cycles ORDER BY cycleIndex DESC LIMIT 1")
  suspend fun getLatestCycle(): CycleEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertCycles(cycles: List<CycleEntity>)

  @Query("DELETE FROM cycles")
  suspend fun clearAllCycles()

  @Transaction
  suspend fun replaceAllCycles(cycles: List<CycleEntity>) {
    clearAllCycles()
    if (cycles.isNotEmpty()) {
      upsertCycles(cycles)
    }
  }
}

@Dao
interface CustomOptionDao {
  @Query("SELECT * FROM custom_options ORDER BY createdAtMillis ASC")
  fun observeAllOptions(): Flow<List<CustomOptionEntity>>

  @Query("SELECT * FROM custom_options WHERE type = :type ORDER BY createdAtMillis ASC")
  fun observeOptionsByType(type: CustomOptionType): Flow<List<CustomOptionEntity>>

  @Query("SELECT * FROM custom_options ORDER BY createdAtMillis ASC")
  suspend fun getAllOptionsOnce(): List<CustomOptionEntity>

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertOption(option: CustomOptionEntity)

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertOptions(options: List<CustomOptionEntity>)

  @Query("DELETE FROM custom_options WHERE id = :id")
  suspend fun deleteById(id: Long)

  @Query("DELETE FROM custom_options")
  suspend fun clearAllOptions()
}
