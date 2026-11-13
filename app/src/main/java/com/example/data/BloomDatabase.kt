package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
  entities = [
    UserProfileEntity::class,
    DailyEntryEntity::class,
    CycleEntity::class,
    CustomOptionEntity::class
  ],
  version = 3,
  exportSchema = true
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

    /**
     * Migrates v2 -> v3: rebuilds custom_options with COLLATE NOCASE on label,
     * deduplicates any pre-existing case-insensitive duplicates, and adds the
     * unique index on (type, label) while preserving all user data.
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS `custom_options_new` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `type` TEXT NOT NULL,
            `label` TEXT NOT NULL COLLATE NOCASE,
            `createdAtMillis` INTEGER NOT NULL
          )
          """.trimIndent()
        )
        db.execSQL(
          """
          INSERT INTO `custom_options_new` (`id`, `type`, `label`, `createdAtMillis`)
          SELECT MIN(`id`), `type`, TRIM(`label`), MIN(`createdAtMillis`)
          FROM `custom_options`
          WHERE TRIM(`label`) != ''
          GROUP BY `type`, LOWER(TRIM(`label`))
          """.trimIndent()
        )
        db.execSQL("DROP TABLE `custom_options`")
        db.execSQL("ALTER TABLE `custom_options_new` RENAME TO `custom_options`")
        db.execSQL(
          "CREATE UNIQUE INDEX IF NOT EXISTS `index_custom_options_type_label` ON `custom_options` (`type`, `label`)"
        )
      }
    }

    fun getInstance(context: Context): BloomDatabase {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: Room.databaseBuilder(
          context.applicationContext,
          BloomDatabase::class.java,
          "bloomcycle_local.db"
        )
          .addMigrations(MIGRATION_2_3)
          .build()
          .also { INSTANCE = it }
      }
    }
  }
}
