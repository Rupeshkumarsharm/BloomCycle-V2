package com.example.data

import androidx.room.TypeConverter
import com.example.domain.model.ActivityLevel
import com.example.domain.model.CustomOptionType
import com.example.domain.model.FlowLevel
import com.example.domain.model.MoodSwingLevel
import com.example.domain.model.RelationshipStatus
import com.example.domain.model.TimingSlot
import com.example.util.CycleDate
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
  private val json = Json { ignoreUnknownKeys = true }

  @TypeConverter
  fun fromCycleDate(date: CycleDate?): String? = date?.toIsoString()

  @TypeConverter
  fun toCycleDate(value: String?): CycleDate? = CycleDate.parseOrNull(value)

  @TypeConverter
  fun fromStringList(list: List<String>?): String =
    json.encodeToString(list ?: emptyList())

  @TypeConverter
  fun toStringList(value: String?): List<String> =
    if (value.isNullOrBlank()) emptyList()
    else runCatching { json.decodeFromString<List<String>>(value) }.getOrDefault(emptyList())

  @TypeConverter
  fun fromStringIntMap(map: Map<String, Int>?): String =
    json.encodeToString(map ?: emptyMap())

  @TypeConverter
  fun toStringIntMap(value: String?): Map<String, Int> =
    if (value.isNullOrBlank()) emptyMap()
    else runCatching { json.decodeFromString<Map<String, Int>>(value) }.getOrDefault(emptyMap())

  @TypeConverter
  fun fromFlowLevel(level: FlowLevel?): String = (level ?: FlowLevel.NONE).name

  @TypeConverter
  fun toFlowLevel(value: String?): FlowLevel =
    FlowLevel.fromNameOrNull(value) ?: FlowLevel.NONE

  @TypeConverter
  fun fromMoodSwingLevel(level: MoodSwingLevel?): String = (level ?: MoodSwingLevel.NONE).name

  @TypeConverter
  fun toMoodSwingLevel(value: String?): MoodSwingLevel =
    MoodSwingLevel.fromNameOrNull(value) ?: MoodSwingLevel.NONE

  @TypeConverter
  fun fromActivityLevel(level: ActivityLevel?): String = (level ?: ActivityLevel.NONE).name

  @TypeConverter
  fun toActivityLevel(value: String?): ActivityLevel =
    ActivityLevel.fromNameOrNull(value) ?: ActivityLevel.NONE

  @TypeConverter
  fun fromTimingSlot(slot: TimingSlot?): String = (slot ?: TimingSlot.ANY).name

  @TypeConverter
  fun toTimingSlot(value: String?): TimingSlot =
    TimingSlot.fromNameOrNull(value) ?: TimingSlot.ANY

  @TypeConverter
  fun fromRelationshipStatus(status: RelationshipStatus?): String =
    (status ?: RelationshipStatus.PREFER_NOT_TO_SAY).name

  @TypeConverter
  fun toRelationshipStatus(value: String?): RelationshipStatus =
    RelationshipStatus.fromNameOrNull(value) ?: RelationshipStatus.PREFER_NOT_TO_SAY

  @TypeConverter
  fun fromCustomOptionType(type: CustomOptionType?): String =
    (type ?: CustomOptionType.SYMPTOM).name

  @TypeConverter
  fun toCustomOptionType(value: String?): CustomOptionType =
    runCatching { CustomOptionType.valueOf(value.orEmpty()) }.getOrDefault(CustomOptionType.SYMPTOM)
}
