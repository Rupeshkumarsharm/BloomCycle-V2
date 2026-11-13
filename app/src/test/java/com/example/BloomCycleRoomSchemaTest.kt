package com.example

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.BloomDatabase
import com.example.data.BloomRepository
import com.example.data.CustomOptionDao
import com.example.data.CustomOptionEntity
import com.example.data.DailyEntryEntity
import com.example.data.ImportMode
import com.example.data.PastCycleRange
import com.example.data.PreferencesStore
import com.example.data.UserProfileEntity
import com.example.data.excel.ParsedWorkbookPreview
import com.example.domain.analytics.CycleAnalyticsEngine
import com.example.domain.model.ActivityLevel
import com.example.domain.model.CustomOption
import com.example.domain.model.CustomOptionType
import com.example.domain.model.DailyEntry
import com.example.domain.model.FlowLevel
import com.example.domain.model.MoodSwingLevel
import com.example.domain.model.RelationshipStatus
import com.example.domain.model.TimingSlot
import com.example.domain.model.UserProfile
import com.example.util.CycleDate
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BloomCycleRoomSchemaTest {

  private lateinit var db: BloomDatabase
  private lateinit var context: Context

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    db = Room.inMemoryDatabaseBuilder(context, BloomDatabase::class.java)
      .allowMainThreadQueries()
      .build()
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun `room database persists UserProfile DailyEntry CustomOption and syncs Cycle metrics`() = runBlocking {
    val profile = UserProfile(
      id = "local-u1",
      name = "Maya",
      dob = CycleDate(1999, 3, 12),
      relationshipStatus = RelationshipStatus.IN_RELATIONSHIP,
      defaultCycleLength = 28
    )
    db.userProfileDao().upsertProfile(UserProfileEntity.fromDomain(profile))

    val storedProfile = db.userProfileDao().observeProfile().first()?.toDomain()
    assertNotNull(storedProfile)
    assertEquals("Maya", storedProfile?.name)
    assertEquals(CycleDate(1999, 3, 12), storedProfile?.dob)

    val repo = BloomRepository(
      profileDao = db.userProfileDao(),
      entryDao = db.dailyEntryDao(),
      cycleDao = db.cycleDao(),
      customOptionDao = db.customOptionDao(),
      preferencesStore = PreferencesStore(context),
      database = db
    )

    repo.completeOnboarding(
      profile = profile,
      pastCycles = listOf(
        PastCycleRange(CycleDate(2026, 9, 1), CycleDate(2026, 9, 4)),
        PastCycleRange(CycleDate(2026, 9, 29), CycleDate(2026, 10, 3))
      ),
      optionalPin = null
    )

    repo.saveDailyEntry(
      DailyEntry(
        date = CycleDate(2026, 9, 2),
        isPeriod = true,
        flowLevel = FlowLevel.HEAVY,
        crampScore = 4,
        painTiming = TimingSlot.MORNING,
        moods = listOf("Calm", "Tired"),
        moodSwingLevel = MoodSwingLevel.MILD,
        symptoms = listOf("Bloating", "Headache"),
        symptomTiming = TimingSlot.AFTERNOON,
        activityLevel = ActivityLevel.LIGHT,
        customTags = listOf("hydration")
      )
    )

    db.customOptionDao().insertOption(
      CustomOptionEntity(
        type = CustomOptionType.SYMPTOM,
        label = "Lower leg ache",
        createdAtMillis = 1000L
      )
    )

    val persistedEntry = db.dailyEntryDao().getEntryByDate(CycleDate(2026, 9, 2))?.toDomain()
    assertNotNull(persistedEntry)
    assertEquals(FlowLevel.HEAVY, persistedEntry?.flowLevel)
    assertEquals(listOf("Bloating", "Headache"), persistedEntry?.symptoms)
    assertEquals(TimingSlot.MORNING, persistedEntry?.painTiming)

    val cycles = db.cycleDao().observeAllCycles().first()
    assertEquals(2, cycles.size)
    assertEquals(28, cycles[0].cycleLengthDays)
    assertEquals(listOf("Bloating", "Headache"), cycles[0].associatedSymptoms)
    assertEquals(mapOf("Bloating" to 1, "Headache" to 1), cycles[0].symptomCounts)
    assertEquals("Calm", cycles[0].dominantMood)
    assertEquals(1, db.customOptionDao().observeOptionsByType(CustomOptionType.SYMPTOM).first().size)
  }

  @Test
  fun `room migration v2 to v3 preserves user data and enforces unique custom_options`() = runBlocking {
    val dbFile = File(context.cacheDir, "migration_v2_v3_test.db")
    if (dbFile.exists()) dbFile.delete()

    val v2Config = SupportSQLiteOpenHelper.Configuration.builder(context)
      .name(dbFile.absolutePath)
      .callback(object : SupportSQLiteOpenHelper.Callback(2) {
        override fun onCreate(db: SupportSQLiteDatabase) {
          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `user_profile` (
              `id` TEXT NOT NULL, `name` TEXT NOT NULL, `dob` TEXT NOT NULL,
              `relationshipStatus` TEXT NOT NULL, `customRelationshipLabel` TEXT NOT NULL,
              `defaultCycleLength` INTEGER NOT NULL, `createdAtMillis` INTEGER NOT NULL,
              PRIMARY KEY(`id`)
            )
            """.trimIndent()
          )
          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `daily_entries` (
              `date` TEXT NOT NULL, `isPeriod` INTEGER NOT NULL, `flowLevel` TEXT NOT NULL,
              `spotting` INTEGER NOT NULL, `isPeriodStartOverride` INTEGER NOT NULL,
              `isPeriodEndOverride` INTEGER NOT NULL, `flowChangesNote` TEXT NOT NULL,
              `crampScore` INTEGER NOT NULL, `painTiming` TEXT NOT NULL,
              `moods` TEXT NOT NULL, `moodSwingLevel` TEXT NOT NULL,
              `symptoms` TEXT NOT NULL, `symptomTiming` TEXT NOT NULL,
              `sleepDurationHours` REAL, `sleepQuality` INTEGER, `sleepTimingNote` TEXT NOT NULL,
              `energyLevel` INTEGER, `stressLevel` INTEGER, `stressTriggers` TEXT NOT NULL,
              `activityLevel` TEXT NOT NULL, `exerciseMinutes` INTEGER, `waterCups` INTEGER,
              `caffeineCups` INTEGER, `medicationTaken` INTEGER NOT NULL, `medicationNote` TEXT NOT NULL,
              `customTags` TEXT NOT NULL, `notes` TEXT NOT NULL, `updatedAtMillis` INTEGER NOT NULL,
              PRIMARY KEY(`date`)
            )
            """.trimIndent()
          )
          db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_entries_isPeriod` ON `daily_entries` (`isPeriod`)")
          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `cycles` (
              `cycleIndex` INTEGER NOT NULL, `startDate` TEXT NOT NULL, `endDate` TEXT NOT NULL,
              `periodDurationDays` INTEGER NOT NULL, `cycleLengthDays` INTEGER,
              `averageCrampScore` REAL NOT NULL, `maxCrampScore` INTEGER NOT NULL,
              `totalSymptomLogs` INTEGER NOT NULL, `associatedSymptoms` TEXT NOT NULL,
              `symptomCounts` TEXT NOT NULL, `moodCounts` TEXT NOT NULL,
              `dominantMood` TEXT, `userAgeAtStart` INTEGER NOT NULL,
              `updatedAtMillis` INTEGER NOT NULL, PRIMARY KEY(`cycleIndex`)
            )
            """.trimIndent()
          )
          db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_cycles_startDate` ON `cycles` (`startDate`)")
          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `custom_options` (
              `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
              `type` TEXT NOT NULL, `label` TEXT NOT NULL, `createdAtMillis` INTEGER NOT NULL
            )
            """.trimIndent()
          )
        }

        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
      })
      .build()

    FrameworkSQLiteOpenHelperFactory().create(v2Config).use { helper ->
      val v2Db = helper.writableDatabase
      v2Db.execSQL(
        "INSERT INTO `user_profile` VALUES ('u-v2', 'Sienna', '1997-04-12', 'SINGLE', '', 28, 1000)"
      )
      v2Db.execSQL(
        """
        INSERT INTO `daily_entries` VALUES (
          '2026-09-10', 1, 'HEAVY', 0, 1, 0, '', 3, 'MORNING',
          '["Calm"]', 'NONE', '["Bloating"]', 'ANY',
          7.5, 4, '', 3, 2, '', 'LIGHT', 30, 5, 1, 0, '', '["yoga, morning"]', 'Saved note', 2000
        )
        """.trimIndent()
      )
      v2Db.execSQL(
        """
        INSERT INTO `cycles` VALUES (
          1, '2026-09-10', '2026-09-10', 1, NULL, 3.0, 3, 1,
          '["Bloating"]', '{"Bloating":1}', '{"Calm":1}', 'Calm', 29, 2000
        )
        """.trimIndent()
      )
      // Pre-existing duplicate case-insensitive custom options + blank entry
      v2Db.execSQL("INSERT INTO `custom_options` (`id`, `type`, `label`, `createdAtMillis`) VALUES (1, 'TAG', 'Hydration', 100)")
      v2Db.execSQL("INSERT INTO `custom_options` (`id`, `type`, `label`, `createdAtMillis`) VALUES (2, 'TAG', 'hydration ', 200)")
      v2Db.execSQL("INSERT INTO `custom_options` (`id`, `type`, `label`, `createdAtMillis`) VALUES (3, 'MOOD', 'Balanced, calm', 300)")
    }

    val migratedDb = Room.databaseBuilder(context, BloomDatabase::class.java, dbFile.name)
      .openHelperFactory { configuration ->
        FrameworkSQLiteOpenHelperFactory().create(
          SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbFile.absolutePath)
            .callback(configuration.callback)
            .build()
        )
      }
      .addMigrations(BloomDatabase.MIGRATION_2_3)
      .allowMainThreadQueries()
      .build()

    try {
      val profile = migratedDb.userProfileDao().getProfileOnce()?.toDomain()
      assertEquals("Sienna", profile?.name)

      val entry = migratedDb.dailyEntryDao().getEntryByDate(CycleDate(2026, 9, 10))?.toDomain()
      assertNotNull(entry)
      assertEquals("Saved note", entry?.notes)
      assertEquals(listOf("yoga, morning"), entry?.customTags)

      val cycles = migratedDb.cycleDao().getAllCyclesOnce()
      assertEquals(1, cycles.size)
      assertEquals(CycleDate(2026, 9, 10), cycles.single().startDate)

      val options = migratedDb.customOptionDao().getAllOptionsOnce()
      assertEquals(2, options.size)
      assertTrue(options.any { it.type == CustomOptionType.TAG && it.label.equals("Hydration", ignoreCase = true) })
      assertTrue(options.any { it.type == CustomOptionType.MOOD && it.label == "Balanced, calm" })

      // Inserting a case-insensitive duplicate in v3 must be ignored by the unique NOCASE index
      migratedDb.customOptionDao().insertOption(
        CustomOptionEntity(type = CustomOptionType.TAG, label = "HYDRATION", createdAtMillis = 999L)
      )
      assertEquals(2, migratedDb.customOptionDao().getAllOptionsOnce().size)
    } finally {
      migratedDb.close()
      dbFile.delete()
    }
  }

  @Test
  fun `concurrent repository writes serialize derived cycle synchronization accurately`() = runBlocking {
    val profile = UserProfile(
      id = "u-concurrent",
      name = "Clara",
      dob = CycleDate(1996, 2, 15),
      defaultCycleLength = 28
    )
    val repo = BloomRepository(
      profileDao = db.userProfileDao(),
      entryDao = db.dailyEntryDao(),
      cycleDao = db.cycleDao(),
      customOptionDao = db.customOptionDao(),
      preferencesStore = PreferencesStore(context),
      database = db
    )
    repo.saveProfile(profile)

    val entries = listOf(
      DailyEntry(date = CycleDate(2026, 8, 1), isPeriod = true, flowLevel = FlowLevel.MEDIUM, crampScore = 3),
      DailyEntry(date = CycleDate(2026, 8, 2), isPeriod = true, flowLevel = FlowLevel.HEAVY, crampScore = 4, symptoms = listOf("Bloating")),
      DailyEntry(date = CycleDate(2026, 8, 3), isPeriod = true, flowLevel = FlowLevel.LIGHT, crampScore = 1),
      DailyEntry(date = CycleDate(2026, 8, 29), isPeriod = true, flowLevel = FlowLevel.MEDIUM, crampScore = 2, moods = listOf("Calm")),
      DailyEntry(date = CycleDate(2026, 8, 30), isPeriod = true, flowLevel = FlowLevel.MEDIUM, crampScore = 2),
      DailyEntry(date = CycleDate(2026, 9, 15), isPeriod = false, sleepDurationHours = 7.5f, notes = "Mid-cycle check")
    )

    entries.map { entry ->
      async(Dispatchers.IO) {
        repo.saveDailyEntry(entry)
      }
    }.awaitAll()

    val savedEntries = db.dailyEntryDao().getAllEntriesOnce().map { it.toDomain() }
    assertEquals(entries.size, savedEntries.size)

    val expectedCycles = CycleAnalyticsEngine.deriveCycles(savedEntries, profile)
    val persistedCycles = db.cycleDao().getAllCyclesOnce().map { it.toDomain() }
    assertEquals(expectedCycles.size, persistedCycles.size)
    assertEquals(expectedCycles.map { it.startDate }, persistedCycles.map { it.startDate })
    assertEquals(expectedCycles.map { it.cycleLengthDays }, persistedCycles.map { it.cycleLengthDays })
    assertEquals(expectedCycles.map { it.periodDurationDays }, persistedCycles.map { it.periodDurationDays })
  }

  @Test
  fun `failed workbook import rolls back transaction atomically without losing existing data`() = runBlocking {
    val existingProfile = UserProfile(
      id = "u-existing",
      name = "Elena",
      dob = CycleDate(1994, 7, 1)
    )
    val repo = BloomRepository(
      profileDao = db.userProfileDao(),
      entryDao = db.dailyEntryDao(),
      cycleDao = db.cycleDao(),
      customOptionDao = db.customOptionDao(),
      preferencesStore = PreferencesStore(context),
      database = db
    )
    repo.saveProfile(existingProfile)
    repo.saveDailyEntry(
      DailyEntry(
        date = CycleDate(2026, 7, 10),
        isPeriod = true,
        flowLevel = FlowLevel.HEAVY,
        notes = "Original precious log"
      )
    )
    repo.addCustomOption(CustomOptionType.TAG, "existing_tag")

    val failingCustomOptionDao = object : CustomOptionDao by db.customOptionDao() {
      override suspend fun insertOptions(options: List<CustomOptionEntity>) {
        throw IllegalStateException("Simulated mid-transaction failure")
      }
    }
    val failingRepo = BloomRepository(
      profileDao = db.userProfileDao(),
      entryDao = db.dailyEntryDao(),
      cycleDao = db.cycleDao(),
      customOptionDao = failingCustomOptionDao,
      preferencesStore = PreferencesStore(context),
      database = db
    )

    val preview = ParsedWorkbookPreview(
      profile = UserProfile(id = "u-new", name = "Replacement", dob = CycleDate(2000, 1, 1)),
      entries = listOf(
        DailyEntry(date = CycleDate(2026, 10, 1), isPeriod = true, flowLevel = FlowLevel.LIGHT, notes = "Imported")
      ),
      discoveredCustomOptions = listOf(CustomOption(type = CustomOptionType.TAG, label = "imported_tag")),
      earliestDate = CycleDate(2026, 10, 1),
      latestDate = CycleDate(2026, 10, 1)
    )

    var failureCaught = false
    try {
      failingRepo.applyWorkbookImport(preview, ImportMode.REPLACE)
    } catch (e: IllegalStateException) {
      failureCaught = true
    }
    assertTrue("Expected simulated transaction failure", failureCaught)

    // Pre-existing profile, entries, cycles, and custom options must all remain intact after rollback
    assertEquals("Elena", db.userProfileDao().getProfileOnce()?.name)
    val remainingEntries = db.dailyEntryDao().getAllEntriesOnce().map { it.toDomain() }
    assertEquals(1, remainingEntries.size)
    assertEquals(CycleDate(2026, 7, 10), remainingEntries.single().date)
    assertEquals("Original precious log", remainingEntries.single().notes)
    val remainingOptions = db.customOptionDao().getAllOptionsOnce()
    assertEquals(1, remainingOptions.size)
    assertEquals("existing_tag", remainingOptions.single().label)
    assertEquals(1, db.cycleDao().getAllCyclesOnce().size)
  }
}
