package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.ActivityLevel
import com.example.domain.model.CustomOption
import com.example.domain.model.CustomOptionType
import com.example.domain.model.DailyEntry
import com.example.domain.model.DerivedCycle
import com.example.domain.model.FlowLevel
import com.example.domain.model.MoodSwingLevel
import com.example.domain.model.RelationshipStatus
import com.example.domain.model.TimingSlot
import com.example.domain.model.UserProfile
import com.example.util.CycleDate

@Entity(tableName = "user_profile")
data class UserProfileEntity(
  @PrimaryKey val id: String,
  val name: String,
  val dob: CycleDate,
  val relationshipStatus: RelationshipStatus,
  val customRelationshipLabel: String,
  val defaultCycleLength: Int,
  val createdAtMillis: Long
) {
  fun toDomain(): UserProfile = UserProfile(
    id = id,
    name = name,
    dob = dob,
    relationshipStatus = relationshipStatus,
    customRelationshipLabel = customRelationshipLabel,
    defaultCycleLength = defaultCycleLength,
    createdAtMillis = createdAtMillis
  )

  companion object {
    fun fromDomain(profile: UserProfile): UserProfileEntity = UserProfileEntity(
      id = profile.id,
      name = profile.name,
      dob = profile.dob,
      relationshipStatus = profile.relationshipStatus,
      customRelationshipLabel = profile.customRelationshipLabel,
      defaultCycleLength = profile.defaultCycleLength,
      createdAtMillis = profile.createdAtMillis
    )
  }
}

@Entity(
  tableName = "daily_entries",
  indices = [Index(value = ["isPeriod"])]
)
data class DailyEntryEntity(
  @PrimaryKey val date: CycleDate,
  val isPeriod: Boolean,
  val flowLevel: FlowLevel,
  val spotting: Boolean,
  val isPeriodStartOverride: Boolean,
  val isPeriodEndOverride: Boolean,
  val flowChangesNote: String,
  val crampScore: Int,
  val painTiming: TimingSlot,
  val moods: List<String>,
  val moodSwingLevel: MoodSwingLevel,
  val symptoms: List<String>,
  val symptomTiming: TimingSlot,
  val sleepDurationHours: Float?,
  val sleepQuality: Int?,
  val sleepTimingNote: String,
  val energyLevel: Int?,
  val stressLevel: Int?,
  val stressTriggers: String,
  val activityLevel: ActivityLevel,
  val exerciseMinutes: Int?,
  val waterCups: Int?,
  val caffeineCups: Int?,
  val medicationTaken: Boolean,
  val medicationNote: String,
  val customTags: List<String>,
  val notes: String,
  val updatedAtMillis: Long
) {
  fun toDomain(): DailyEntry = DailyEntry(
    date = date,
    isPeriod = isPeriod,
    flowLevel = flowLevel,
    spotting = spotting,
    isPeriodStartOverride = isPeriodStartOverride,
    isPeriodEndOverride = isPeriodEndOverride,
    flowChangesNote = flowChangesNote,
    crampScore = crampScore.coerceIn(0, 5),
    painTiming = painTiming,
    moods = moods,
    moodSwingLevel = moodSwingLevel,
    symptoms = symptoms,
    symptomTiming = symptomTiming,
    sleepDurationHours = sleepDurationHours,
    sleepQuality = sleepQuality,
    sleepTimingNote = sleepTimingNote,
    energyLevel = energyLevel,
    stressLevel = stressLevel,
    stressTriggers = stressTriggers,
    activityLevel = activityLevel,
    exerciseMinutes = exerciseMinutes,
    waterCups = waterCups,
    caffeineCups = caffeineCups,
    medicationTaken = medicationTaken,
    medicationNote = medicationNote,
    customTags = customTags,
    notes = notes,
    updatedAtMillis = updatedAtMillis
  )

  companion object {
    fun fromDomain(entry: DailyEntry): DailyEntryEntity = DailyEntryEntity(
      date = entry.date,
      isPeriod = entry.isPeriod,
      flowLevel = entry.flowLevel,
      spotting = entry.spotting,
      isPeriodStartOverride = entry.isPeriodStartOverride,
      isPeriodEndOverride = entry.isPeriodEndOverride,
      flowChangesNote = entry.flowChangesNote,
      crampScore = entry.crampScore.coerceIn(0, 5),
      painTiming = entry.painTiming,
      moods = entry.moods,
      moodSwingLevel = entry.moodSwingLevel,
      symptoms = entry.symptoms,
      symptomTiming = entry.symptomTiming,
      sleepDurationHours = entry.sleepDurationHours,
      sleepQuality = entry.sleepQuality,
      sleepTimingNote = entry.sleepTimingNote,
      energyLevel = entry.energyLevel,
      stressLevel = entry.stressLevel,
      stressTriggers = entry.stressTriggers,
      activityLevel = entry.activityLevel,
      exerciseMinutes = entry.exerciseMinutes,
      waterCups = entry.waterCups,
      caffeineCups = entry.caffeineCups,
      medicationTaken = entry.medicationTaken,
      medicationNote = entry.medicationNote,
      customTags = entry.customTags,
      notes = entry.notes,
      updatedAtMillis = entry.updatedAtMillis
    )
  }
}

@Entity(
  tableName = "cycles",
  indices = [Index(value = ["startDate"], unique = true)]
)
data class CycleEntity(
  @PrimaryKey val cycleIndex: Int,
  val startDate: CycleDate,
  val endDate: CycleDate,
  val periodDurationDays: Int,
  val cycleLengthDays: Int?,
  val averageCrampScore: Float,
  val maxCrampScore: Int,
  val totalSymptomLogs: Int,
  val associatedSymptoms: List<String>,
  val symptomCounts: Map<String, Int>,
  val moodCounts: Map<String, Int>,
  val dominantMood: String?,
  val userAgeAtStart: Int,
  val updatedAtMillis: Long = System.currentTimeMillis()
) {
  fun toDomain(): DerivedCycle = DerivedCycle(
    cycleIndex = cycleIndex,
    startDate = startDate,
    endDate = endDate,
    periodDurationDays = periodDurationDays,
    cycleLengthDays = cycleLengthDays,
    averageCrampScore = averageCrampScore,
    maxCrampScore = maxCrampScore,
    totalSymptomLogs = totalSymptomLogs,
    dominantMood = dominantMood,
    userAgeAtStart = userAgeAtStart,
    associatedSymptoms = associatedSymptoms,
    symptomCounts = symptomCounts,
    moodCounts = moodCounts
  )

  companion object {
    fun fromDomain(cycle: DerivedCycle): CycleEntity = CycleEntity(
      cycleIndex = cycle.cycleIndex,
      startDate = cycle.startDate,
      endDate = cycle.endDate,
      periodDurationDays = cycle.periodDurationDays,
      cycleLengthDays = cycle.cycleLengthDays,
      averageCrampScore = cycle.averageCrampScore,
      maxCrampScore = cycle.maxCrampScore,
      totalSymptomLogs = cycle.totalSymptomLogs,
      associatedSymptoms = cycle.associatedSymptoms,
      symptomCounts = cycle.symptomCounts,
      moodCounts = cycle.moodCounts,
      dominantMood = cycle.dominantMood,
      userAgeAtStart = cycle.userAgeAtStart
    )
  }
}

@Entity(tableName = "custom_options")
data class CustomOptionEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val type: CustomOptionType,
  val label: String,
  val createdAtMillis: Long
) {
  fun toDomain(): CustomOption = CustomOption(
    id = id,
    type = type,
    label = label,
    createdAtMillis = createdAtMillis
  )

  companion object {
    fun fromDomain(option: CustomOption): CustomOptionEntity = CustomOptionEntity(
      id = option.id,
      type = option.type,
      label = option.label,
      createdAtMillis = option.createdAtMillis
    )
  }
}
