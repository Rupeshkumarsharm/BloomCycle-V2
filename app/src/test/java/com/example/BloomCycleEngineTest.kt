package com.example

import com.example.data.excel.ExcelXlsxEngine
import com.example.domain.analytics.CycleAnalyticsEngine
import com.example.domain.model.ActivityLevel
import com.example.domain.model.DailyEntry
import com.example.domain.model.FlowLevel
import com.example.domain.model.MoodSwingLevel
import com.example.domain.model.RelationshipStatus
import com.example.domain.model.UserProfile
import com.example.util.CycleDate
import com.example.util.PinSecurity
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BloomCycleEngineTest {

  @Test
  fun `cycle date arithmetic handles leap years and age calculation`() {
    val leapDay = CycleDate(2024, 2, 29)
    assertEquals(CycleDate(2024, 3, 1), leapDay.plusDays(1))
    assertEquals(29, leapDay.daysUntil(CycleDate(2024, 3, 29)))

    val dob = CycleDate(2000, 11, 20)
    assertEquals(25, dob.ageOn(CycleDate(2026, 11, 12)))
    assertEquals(26, dob.ageOn(CycleDate(2026, 11, 20)))
  }

  @Test
  fun `pin hashing and verification works with salt`() {
    val salt = PinSecurity.generateSalt()
    val hash = PinSecurity.hashPin("4829", salt)
    assertTrue(PinSecurity.verifyPin("4829", salt, hash))
    assertFalse(PinSecurity.verifyPin("0000", salt, hash))
  }

  @Test
  fun `analytics engine derives cycles, predictions, cross-factors, and anomalies`() {
    val profile = UserProfile(
      id = "u1",
      name = "Maya",
      dob = CycleDate(1998, 4, 10),
      relationshipStatus = RelationshipStatus.IN_RELATIONSHIP,
      defaultCycleLength = 28
    )

    val entries = mutableListOf<DailyEntry>()
    // Cycle 1: Jan 1..5 (28 days)
    for (d in 0..4) {
      entries.add(
        DailyEntry(
          date = CycleDate(2026, 1, 1).plusDays(d),
          isPeriod = true,
          flowLevel = FlowLevel.MEDIUM,
          crampScore = if (d == 0) 4 else 2,
          moods = listOf("Tired", "Calm"),
          symptoms = listOf("Bloating"),
          sleepDurationHours = 5.5f,
          stressLevel = 4,
          activityLevel = ActivityLevel.NONE
        )
      )
    }
    // Cycle 2: Jan 29..Feb 2 (28 days)
    for (d in 0..4) {
      entries.add(
        DailyEntry(
          date = CycleDate(2026, 1, 29).plusDays(d),
          isPeriod = true,
          flowLevel = FlowLevel.HEAVY,
          crampScore = if (d == 0) 3 else 1,
          moods = listOf("Calm"),
          symptoms = listOf("Bloating", "Headache"),
          sleepDurationHours = 7.5f,
          stressLevel = 1,
          activityLevel = ActivityLevel.MODERATE
        )
      )
    }
    // Cycle 3 (longer anomaly): Feb 26..Mar 2 -> wait, next start Apr 5 (38 days)
    for (d in 0..4) {
      entries.add(
        DailyEntry(
          date = CycleDate(2026, 2, 26).plusDays(d),
          isPeriod = true,
          flowLevel = FlowLevel.MEDIUM,
          crampScore = 1,
          moods = listOf("Happy"),
          moodSwingLevel = MoodSwingLevel.MILD,
          sleepDurationHours = 8.0f,
          stressLevel = 1,
          activityLevel = ActivityLevel.LIGHT
        )
      )
    }
    // Cycle 4: Apr 5..9
    for (d in 0..4) {
      entries.add(
        DailyEntry(
          date = CycleDate(2026, 4, 5).plusDays(d),
          isPeriod = true,
          flowLevel = FlowLevel.MEDIUM,
          crampScore = 4,
          moods = listOf("Anxious"),
          moodSwingLevel = MoodSwingLevel.MODERATE,
          sleepDurationHours = 5.0f,
          stressLevel = 4,
          activityLevel = ActivityLevel.NONE
        )
      )
    }

    val snapshot = CycleAnalyticsEngine.buildAnalyticsSnapshot(
      profile = profile,
      entries = entries,
      showFertileWindow = true,
      today = CycleDate(2026, 4, 15)
    )

    assertEquals(4, snapshot.totalCyclesCount)
    assertEquals(3, snapshot.completedCyclesCount)
    assertNotNull(snapshot.averageCycleLength)
    assertNotNull(snapshot.prediction)
    assertNotNull(snapshot.prediction?.fertileWindowStart)
    assertTrue(snapshot.crossFactorInsights.isNotEmpty())
    assertTrue(snapshot.anomalies.isNotEmpty())
    assertTrue(snapshot.ageTrends.isNotEmpty())
  }

  @Test
  fun `excel export and import performs lossless round-trip`() {
    val profile = UserProfile(
      id = "local-1",
      name = "Aria",
      dob = CycleDate(1997, 8, 22),
      relationshipStatus = RelationshipStatus.COMPLICATED,
      defaultCycleLength = 29
    )
    val entries = listOf(
      DailyEntry(
        date = CycleDate(2026, 10, 1),
        isPeriod = true,
        flowLevel = FlowLevel.HEAVY,
        spotting = false,
        crampScore = 4,
        moods = listOf("Calm", "Tired"),
        moodSwingLevel = MoodSwingLevel.MILD,
        symptoms = listOf("Bloating", "Back pain"),
        sleepDurationHours = 7.5f,
        energyLevel = 2,
        stressLevel = 3,
        activityLevel = ActivityLevel.LIGHT,
        exerciseMinutes = 25,
        waterCups = 6,
        caffeineCups = 1,
        medicationTaken = true,
        medicationNote = "Warm compress & ibuprofen",
        customTags = listOf("travel"),
        notes = "Heavy morning & gentle walk."
      )
    )
    val snapshot = CycleAnalyticsEngine.buildAnalyticsSnapshot(profile, entries, true, CycleDate(2026, 10, 5))

    val out = ByteArrayOutputStream()
    ExcelXlsxEngine.exportWorkbook(out, profile, entries, snapshot)
    val parsed = ExcelXlsxEngine.parseWorkbook(ByteArrayInputStream(out.toByteArray()))

    assertNotNull(parsed.profile)
    assertEquals("Aria", parsed.profile?.name)
    assertEquals(RelationshipStatus.COMPLICATED, parsed.profile?.relationshipStatus)
    assertEquals(1, parsed.entries.size)
    val roundTripped = parsed.entries.first()
    assertEquals(CycleDate(2026, 10, 1), roundTripped.date)
    assertTrue(roundTripped.isPeriod)
    assertEquals(FlowLevel.HEAVY, roundTripped.flowLevel)
    assertEquals(4, roundTripped.crampScore)
    assertEquals(listOf("Calm", "Tired"), roundTripped.moods)
    assertEquals(listOf("Bloating", "Back pain"), roundTripped.symptoms)
    assertEquals(7.5f, roundTripped.sleepDurationHours)
    assertEquals("Heavy morning & gentle walk.", roundTripped.notes)
  }
}
