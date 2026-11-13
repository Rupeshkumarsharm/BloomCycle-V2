package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.BloomDatabase
import com.example.data.BloomRepository
import com.example.data.CustomOptionEntity
import com.example.data.DailyEntryEntity
import com.example.data.PastCycleRange
import com.example.data.PreferencesStore
import com.example.data.UserProfileEntity
import com.example.domain.model.ActivityLevel
import com.example.domain.model.CustomOptionType
import com.example.domain.model.DailyEntry
import com.example.domain.model.FlowLevel
import com.example.domain.model.MoodSwingLevel
import com.example.domain.model.RelationshipStatus
import com.example.domain.model.TimingSlot
import com.example.domain.model.UserProfile
import com.example.util.CycleDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
      preferencesStore = PreferencesStore(context)
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
}
