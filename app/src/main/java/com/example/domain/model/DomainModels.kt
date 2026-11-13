package com.example.domain.model

import com.example.util.CycleDate

enum class FlowLevel(val label: String, val weight: Int) {
  NONE("None", 0),
  LIGHT("Light", 1),
  MEDIUM("Medium", 2),
  HEAVY("Heavy", 3),
  VERY_HEAVY("Very heavy", 4);

  companion object {
    fun fromNameOrNull(name: String?): FlowLevel? =
      entries.firstOrNull { it.name.equals(name?.trim(), ignoreCase = true) || it.label.equals(name?.trim(), ignoreCase = true) }
  }
}

enum class MoodSwingLevel(val label: String, val severity: Int) {
  NONE("None", 0),
  MILD("Mild", 1),
  MODERATE("Moderate", 2),
  SEVERE("Severe", 3);

  companion object {
    fun fromNameOrNull(name: String?): MoodSwingLevel? =
      entries.firstOrNull { it.name.equals(name?.trim(), ignoreCase = true) || it.label.equals(name?.trim(), ignoreCase = true) }
  }
}

enum class ActivityLevel(val label: String, val score: Int) {
  NONE("None", 0),
  LIGHT("Light", 1),
  MODERATE("Moderate", 2),
  HIGH("High", 3);

  companion object {
    fun fromNameOrNull(name: String?): ActivityLevel? =
      entries.firstOrNull { it.name.equals(name?.trim(), ignoreCase = true) || it.label.equals(name?.trim(), ignoreCase = true) }
  }
}

enum class TimingSlot(val label: String) {
  ANY("Unspecified"),
  MORNING("Morning"),
  AFTERNOON("Afternoon"),
  EVENING("Evening"),
  NIGHT("Night"),
  ALL_DAY("All day");

  companion object {
    fun fromNameOrNull(name: String?): TimingSlot? =
      entries.firstOrNull { it.name.equals(name?.trim(), ignoreCase = true) || it.label.equals(name?.trim(), ignoreCase = true) }
  }
}

enum class RelationshipStatus(val label: String) {
  PREFER_NOT_TO_SAY("Prefer not to say"),
  SINGLE("Single"),
  IN_RELATIONSHIP("In a relationship"),
  MARRIED("Married"),
  COMPLICATED("It's complicated 😂"),
  CUSTOM("Other / custom");

  companion object {
    fun fromNameOrNull(name: String?): RelationshipStatus? =
      entries.firstOrNull { it.name.equals(name?.trim(), ignoreCase = true) || it.label.equals(name?.trim(), ignoreCase = true) }
  }
}

enum class CustomOptionType {
  MOOD,
  SYMPTOM,
  TAG
}

enum class CyclePhase(val label: String, val description: String) {
  MENSTRUAL("Menstrual phase", "Period days currently logged"),
  FOLLICULAR("Follicular phase (Estimated)", "Days following your period"),
  FERTILE_WINDOW("Fertile window (Estimated)", "Estimated higher-fertility days"),
  LUTEAL("Luteal phase (Estimated)", "Days leading up to your next estimated period"),
  UNKNOWN("Building baseline", "Log a period to see cycle phase estimates")
}

object DefaultCatalog {
  val defaultMoods = listOf(
    "Happy",
    "Calm",
    "Sad",
    "Irritated",
    "Angry",
    "Anxious",
    "Emotional",
    "Tired",
    "Energetic",
    "Low",
    "Excited",
    "Neutral"
  )

  val defaultSymptoms = listOf(
    "Headache",
    "Migraine",
    "Back pain",
    "Abdominal pain",
    "Bloating",
    "Nausea",
    "Breast tenderness",
    "Acne",
    "Fatigue",
    "Dizziness",
    "Constipation",
    "Diarrhea",
    "Food cravings",
    "Increased appetite",
    "Decreased appetite"
  )

  val negativeMoods = setOf("Sad", "Irritated", "Angry", "Anxious", "Emotional", "Tired", "Low")
  val positiveMoods = setOf("Happy", "Calm", "Energetic", "Excited")
}

data class UserProfile(
  val id: String,
  val name: String,
  val dob: CycleDate,
  val relationshipStatus: RelationshipStatus = RelationshipStatus.PREFER_NOT_TO_SAY,
  val customRelationshipLabel: String = "",
  val defaultCycleLength: Int = 28,
  val createdAtMillis: Long = System.currentTimeMillis()
) {
  fun ageOn(date: CycleDate = CycleDate.today()): Int = dob.ageOn(date)

  fun relationshipDisplay(): String =
    if (relationshipStatus == RelationshipStatus.CUSTOM && customRelationshipLabel.isNotBlank()) {
      customRelationshipLabel
    } else {
      relationshipStatus.label
    }
}

data class DailyEntry(
  val date: CycleDate,
  val isPeriod: Boolean = false,
  val flowLevel: FlowLevel = FlowLevel.NONE,
  val spotting: Boolean = false,
  val isPeriodStartOverride: Boolean = false,
  val isPeriodEndOverride: Boolean = false,
  val flowChangesNote: String = "",
  val crampScore: Int = 0, // 0..5
  val painTiming: TimingSlot = TimingSlot.ANY,
  val moods: List<String> = emptyList(),
  val moodSwingLevel: MoodSwingLevel = MoodSwingLevel.NONE,
  val symptoms: List<String> = emptyList(),
  val symptomTiming: TimingSlot = TimingSlot.ANY,
  val sleepDurationHours: Float? = null,
  val sleepQuality: Int? = null, // 1..5
  val sleepTimingNote: String = "",
  val energyLevel: Int? = null, // 0..5
  val stressLevel: Int? = null, // 0..5
  val stressTriggers: String = "",
  val activityLevel: ActivityLevel = ActivityLevel.NONE,
  val exerciseMinutes: Int? = null,
  val waterCups: Int? = null,
  val caffeineCups: Int? = null,
  val medicationTaken: Boolean = false,
  val medicationNote: String = "",
  val customTags: List<String> = emptyList(),
  val notes: String = "",
  val updatedAtMillis: Long = System.currentTimeMillis()
) {
  val hasAnyDetails: Boolean
    get() = isPeriod || flowLevel != FlowLevel.NONE || spotting || crampScore > 0 ||
      moods.isNotEmpty() || moodSwingLevel != MoodSwingLevel.NONE || symptoms.isNotEmpty() ||
      sleepDurationHours != null || sleepQuality != null || energyLevel != null ||
      stressLevel != null || activityLevel != ActivityLevel.NONE || exerciseMinutes != null ||
      waterCups != null || caffeineCups != null || medicationTaken || medicationNote.isNotBlank() ||
      customTags.isNotEmpty() || notes.isNotBlank() || flowChangesNote.isNotBlank() ||
      stressTriggers.isNotBlank() || sleepTimingNote.isNotBlank() ||
      isPeriodStartOverride || isPeriodEndOverride
}

data class CustomOption(
  val id: Long = 0,
  val type: CustomOptionType,
  val label: String,
  val createdAtMillis: Long = System.currentTimeMillis()
)

data class DerivedCycle(
  val cycleIndex: Int,
  val startDate: CycleDate,
  val endDate: CycleDate, // Last day of period bleeding
  val periodDurationDays: Int,
  val cycleLengthDays: Int?, // Null for currently active / ongoing cycle
  val averageCrampScore: Float,
  val maxCrampScore: Int,
  val totalSymptomLogs: Int,
  val dominantMood: String?,
  val userAgeAtStart: Int,
  val associatedSymptoms: List<String> = emptyList(),
  val symptomCounts: Map<String, Int> = emptyMap(),
  val moodCounts: Map<String, Int> = emptyMap()
)

data class CyclePrediction(
  val nextPeriodStart: CycleDate,
  val nextPeriodWindowStart: CycleDate,
  val nextPeriodWindowEnd: CycleDate,
  val estimatedPeriodEnd: CycleDate,
  val estimatedOvulationDay: CycleDate?,
  val fertileWindowStart: CycleDate?,
  val fertileWindowEnd: CycleDate?,
  val confidenceNote: String,
  val daysUntilNextPeriod: Int
)

data class SymptomFrequencyStat(
  val symptom: String,
  val totalLogs: Int,
  val cycleCount: Int,
  val totalCyclesEvaluated: Int,
  val primaryTimingSummary: String
)

data class MoodStat(
  val mood: String,
  val count: Int,
  val percentage: Float
)

data class CrossFactorInsight(
  val title: String,
  val summary: String,
  val comparisonMetricA: String,
  val comparisonMetricB: String,
  val sampleDays: Int,
  val category: String
)

data class AgeTrendBucket(
  val ageYears: Int,
  val cycleCount: Int,
  val averageCycleLength: Float?,
  val averagePeriodDuration: Float,
  val averageCrampScore: Float,
  val topSymptom: String?
)

data class CycleAnomaly(
  val title: String,
  val detail: String,
  val severityLabel: String
)

data class PersonalInsightCard(
  val category: String,
  val title: String,
  val statement: String
)

enum class AnalyticsWindow(val label: String, val maxCycles: Int?) {
  LAST_6("Last 6 cycles", 6),
  LAST_12("Last 12 cycles", 12),
  ALL("All history", null)
}

data class AnalyticsSnapshot(
  val currentCycleDay: Int?,
  val currentPeriodDay: Int?,
  val currentPhase: CyclePhase,
  val completedCyclesCount: Int,
  val totalCyclesCount: Int,
  val averageCycleLength: Float?,
  val medianCycleLength: Float?,
  val shortestCycleLength: Int?,
  val longestCycleLength: Int?,
  val cycleVariabilityDays: Float?,
  val variabilityTrendNote: String?,
  val averagePeriodDuration: Float?,
  val shortestPeriodDuration: Int?,
  val longestPeriodDuration: Int?,
  val recentCrampAverage: Float?,
  val topLoggedMood: String?,
  val averageSleepHours: Float?,
  val prediction: CyclePrediction?,
  val cycles: List<DerivedCycle>,
  val crampByCycleDay: List<Pair<Int, Float>>,
  val symptomFrequencies: List<SymptomFrequencyStat>,
  val moodDistribution: List<MoodStat>,
  val moodSwingRatePercent: Float,
  val energyByPhase: Map<CyclePhase, Float>,
  val crossFactorInsights: List<CrossFactorInsight>,
  val ageTrends: List<AgeTrendBucket>,
  val anomalies: List<CycleAnomaly>,
  val personalInsights: List<PersonalInsightCard>
) {
  companion object {
    val Empty = AnalyticsSnapshot(
      currentCycleDay = null,
      currentPeriodDay = null,
      currentPhase = CyclePhase.UNKNOWN,
      completedCyclesCount = 0,
      totalCyclesCount = 0,
      averageCycleLength = null,
      medianCycleLength = null,
      shortestCycleLength = null,
      longestCycleLength = null,
      cycleVariabilityDays = null,
      variabilityTrendNote = null,
      averagePeriodDuration = null,
      shortestPeriodDuration = null,
      longestPeriodDuration = null,
      recentCrampAverage = null,
      topLoggedMood = null,
      averageSleepHours = null,
      prediction = null,
      cycles = emptyList(),
      crampByCycleDay = emptyList(),
      symptomFrequencies = emptyList(),
      moodDistribution = emptyList(),
      moodSwingRatePercent = 0f,
      energyByPhase = emptyMap(),
      crossFactorInsights = emptyList(),
      ageTrends = emptyList(),
      anomalies = emptyList(),
      personalInsights = emptyList()
    )
  }
}
