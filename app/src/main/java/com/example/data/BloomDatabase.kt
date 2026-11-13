package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
  entities = [
    UserProfileEntity::class,
    DailyEntryEntity::class,
    CycleEntity::class,
    CustomOptionEntity::class
  ],
  version = 2,
  exportSchema = false
)
@TypeConverters(Converters::class)
abstract class BloomDatabase : RoomDatabase() {
  abstract fun userProfileDao(): UserProfileDao
  abstract fun dailyEntryDao(): DailyEntryDao
  abstract fun cycleDao(): CycleDao
  abstract fun customOptionDao(): CustomOptionDao

  companion object {
    @Volatile
    private var INSTANCE: BloomDatabase? = null

    fun getInstance(context: Context): BloomDatabase {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: Room.databaseBuilder(
          context.applicationContext,
          BloomDatabase::class.java,
          "bloomcycle_local.db"
        )
          .fallbackToDestructiveMigration(dropAllTables = true)
          .build()
          .also { INSTANCE = it }
      }
    }
  }
}
