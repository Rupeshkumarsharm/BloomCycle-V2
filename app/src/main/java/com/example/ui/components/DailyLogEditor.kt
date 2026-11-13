package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.domain.model.ActivityLevel
import com.example.domain.model.CustomOption
import com.example.domain.model.CustomOptionType
import com.example.domain.model.DailyEntry
import com.example.domain.model.DefaultCatalog
import com.example.domain.model.FlowLevel
import com.example.domain.model.MoodSwingLevel
import com.example.domain.model.TimingSlot
import com.example.util.CycleDate
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private const val TEXT_SAVE_DEBOUNCE_MS = 400L
private const val MAX_EXERCISE_MINUTES_PER_DAY = 1440

private fun DailyEntry.normalizedForComparison(): DailyEntry = copy(updatedAtMillis = 0L)

@Stable
private class EditorDraftHolder(
  val date: CycleDate,
  initialEntry: DailyEntry,
  initialExternalNormalized: DailyEntry?
) {
  var draft by mutableStateOf(initialEntry)
  var exerciseMinutesText by mutableStateOf(initialEntry.exerciseMinutes?.toString().orEmpty())
  var exerciseMinutesError by mutableStateOf<String?>(null)
  var hasPendingTextWrite by mutableStateOf(false)
  var textRevision by mutableStateOf(0)
  val inFlightSaves = ArrayDeque<DailyEntry>()
  var lastSyncedExternal: DailyEntry? = initialExternalNormalized

  fun dispatchSave(stamped: DailyEntry, onSave: (DailyEntry) -> Unit) {
    inFlightSaves.addLast(stamped.normalizedForComparison())
    while (inFlightSaves.size > 20) {
      inFlightSaves.removeFirst()
    }
    onSave(stamped)
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DailyLogEditor(
  date: CycleDate,
  existingEntry: DailyEntry?,
  periodDayNumber: Int?,
  customOptions: List<CustomOption>,
  onSaveEntry: (DailyEntry) -> Unit,
  onAddCustomOption: (CustomOptionType, String) -> Unit,
  modifier: Modifier = Modifier
) {
  val currentOnSave by rememberUpdatedState(onSaveEntry)
  val holder = remember(date) {
    EditorDraftHolder(
      date = date,
      initialEntry = existingEntry ?: DailyEntry(date = date),
      initialExternalNormalized = existingEntry?.normalizedForComparison()
    )
  }
  val draft = holder.draft
  var showMoreDetails by remember(date) { mutableStateOf(false) }
  var addOptionDialogType by remember { mutableStateOf<CustomOptionType?>(null) }

  // Sync only genuine external DB changes; never clobber active drafts or Room echoes of our own writes.
  LaunchedEffect(date, existingEntry) {
    val existingNorm = existingEntry?.normalizedForComparison()
    val draftNorm = holder.draft.normalizedForComparison()

    if (existingNorm == draftNorm || (existingNorm == null && !holder.draft.hasAnyDetails)) {
      holder.lastSyncedExternal = existingNorm
      if (existingNorm != null) {
        while (holder.inFlightSaves.isNotEmpty()) {
          val head = holder.inFlightSaves.removeFirst()
          if (head == existingNorm) break
        }
      } else {
        holder.inFlightSaves.clear()
      }
      return@LaunchedEffect
    }

    if (holder.hasPendingTextWrite) {
      return@LaunchedEffect
    }

    if (existingNorm != null && existingNorm in holder.inFlightSaves) {
      while (holder.inFlightSaves.isNotEmpty()) {
        val head = holder.inFlightSaves.removeFirst()
        if (head == existingNorm) break
      }
      holder.lastSyncedExternal = existingNorm
      return@LaunchedEffect
    }

    if (existingNorm != holder.lastSyncedExternal) {
      val nextEntry = existingEntry ?: DailyEntry(date = date)
      holder.draft = nextEntry
      holder.exerciseMinutesText = nextEntry.exerciseMinutes?.toString().orEmpty()
      holder.exerciseMinutesError = null
      holder.lastSyncedExternal = existingNorm
    }
  }

  // Debounce free-text edits so rapid typing does not trigger concurrent saves or cursor jumps.
  LaunchedEffect(holder, holder.textRevision) {
    if (holder.hasPendingTextWrite) {
      delay(TEXT_SAVE_DEBOUNCE_MS)
      if (holder.hasPendingTextWrite) {
        val stamped = holder.draft.copy(updatedAtMillis = System.currentTimeMillis())
        holder.draft = stamped
        holder.hasPendingTextWrite = false
        holder.dispatchSave(stamped, currentOnSave)
      }
    }
  }

  // Flush any pending text write for this exact date when switching dates or leaving the editor.
  DisposableEffect(holder) {
    onDispose {
      if (holder.hasPendingTextWrite) {
        val stamped = holder.draft.copy(updatedAtMillis = System.currentTimeMillis())
        holder.draft = stamped
        holder.hasPendingTextWrite = false
        holder.dispatchSave(stamped, currentOnSave)
      }
    }
  }

  fun commit(updated: DailyEntry) {
    val stamped = updated.copy(updatedAtMillis = System.currentTimeMillis())
    holder.draft = stamped
    holder.hasPendingTextWrite = false
    holder.dispatchSave(stamped, currentOnSave)
  }

  fun scheduleTextUpdate(updated: DailyEntry) {
    holder.draft = updated
    holder.hasPendingTextWrite = true
    holder.textRevision++
  }

  val customMoods = remember(customOptions) {
    customOptions.filter { it.type == CustomOptionType.MOOD }.map { it.label }
  }
  val customSymptoms = remember(customOptions) {
    customOptions.filter { it.type == CustomOptionType.SYMPTOM }.map { it.label }
  }
  val customTags = remember(customOptions) {
    customOptions.filter { it.type == CustomOptionType.TAG }.map { it.label }
  }

  val allMoods = remember(customMoods) { (DefaultCatalog.defaultMoods + customMoods).distinct() }
  val allSymptoms = remember(customSymptoms) { (DefaultCatalog.defaultSymptoms + customSymptoms).distinct() }

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    // Level 1: Period & Flow
    BloomCard {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Icon(
            imageVector = Icons.Filled.WaterDrop,
            contentDescription = "Menstrual flow",
            tint = MaterialTheme.colorScheme.primary
          )
          Column {
            Text("Period today", style = MaterialTheme.typography.titleMedium)
            if (periodDayNumber != null) {
              Text(
                text = "Period Day $periodDayNumber",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
              )
            }
          }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          val yesSelected = draft.isPeriod
          FilterChip(
            selected = yesSelected,
            onClick = {
              val flow = if (draft.flowLevel == FlowLevel.NONE) FlowLevel.MEDIUM else draft.flowLevel
              commit(draft.copy(isPeriod = true, flowLevel = flow))
            },
            label = { Text("Yes") },
            modifier = Modifier.testTag("period_yes_chip")
          )
          FilterChip(
            selected = !yesSelected,
            onClick = {
              commit(draft.copy(isPeriod = false, flowLevel = FlowLevel.NONE))
            },
            label = { Text("No") },
            modifier = Modifier.testTag("period_no_chip")
          )
        }
      }

      if (draft.isPeriod) {
        Spacer(modifier = Modifier.height(12.dp))
        Text("Flow intensity", style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          FlowLevel.entries.forEach { level ->
            FilterChip(
              selected = draft.flowLevel == level,
              onClick = {
                commit(
                  draft.copy(
                    flowLevel = level,
                    isPeriod = level != FlowLevel.NONE
                  )
                )
              },
              label = { Text(level.label) },
              modifier = Modifier.testTag("flow_${level.name.lowercase()}")
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text("Spotting", style = MaterialTheme.typography.titleSmall)
          Text(
            text = "Light spotting outside or around period",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        Switch(
          checked = draft.spotting,
          onCheckedChange = { commit(draft.copy(spotting = it)) },
          modifier = Modifier.testTag("spotting_switch")
        )
      }
    }

    // Level 1: Cramp Score 0-5
    BloomCard {
      val crampLabels = listOf("None", "Very mild", "Mild", "Moderate", "Severe", "Very severe")
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text("Cramps", style = MaterialTheme.typography.titleMedium)
        Text(
          text = "${draft.crampScore} — ${crampLabels[draft.crampScore.coerceIn(0, 5)]}",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.primary
        )
      }
      Spacer(modifier = Modifier.height(10.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        for (score in 0..5) {
          val selected = draft.crampScore == score
          Surface(
            modifier = Modifier
              .weight(1f)
              .height(48.dp)
              .clip(RoundedCornerShape(14.dp))
              .clickable { commit(draft.copy(crampScore = score)) }
              .testTag("cramp_score_$score"),
            shape = RoundedCornerShape(14.dp),
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
            border = BorderStroke(
              1.dp,
              if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
          ) {
            Box(contentAlignment = Alignment.Center) {
              Text(
                text = score.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
              )
            }
          }
        }
      }
    }

    // Level 1: Moods & Separate Mood Swings
    BloomCard {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text("Mood", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(
          onClick = { addOptionDialogType = CustomOptionType.MOOD },
          modifier = Modifier.testTag("add_custom_mood_btn")
        ) {
          Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Custom")
        }
      }
      Spacer(modifier = Modifier.height(8.dp))
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        allMoods.forEach { mood ->
          val selected = mood in draft.moods
          FilterChip(
            selected = selected,
            onClick = {
              val next = if (selected) draft.moods - mood else draft.moods + mood
              commit(draft.copy(moods = next))
            },
            leadingIcon = if (selected) {
              { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(15.dp)) }
            } else null,
            label = { Text(mood) },
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
              selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
          )
        }
      }

      Spacer(modifier = Modifier.height(14.dp))
      HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
      Spacer(modifier = Modifier.height(12.dp))

      Text("Mood swings (separate from mood)", style = MaterialTheme.typography.titleSmall)
      Spacer(modifier = Modifier.height(8.dp))
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        MoodSwingLevel.entries.forEach { level ->
          FilterChip(
            selected = draft.moodSwingLevel == level,
            onClick = { commit(draft.copy(moodSwingLevel = level)) },
            label = { Text(level.label) },
            modifier = Modifier.testTag("mood_swing_${level.name.lowercase()}")
          )
        }
      }
    }

    // Level 1: Physical Symptoms
    BloomCard {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text("Physical symptoms", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(
          onClick = { addOptionDialogType = CustomOptionType.SYMPTOM },
          modifier = Modifier.testTag("add_custom_symptom_btn")
        ) {
          Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Custom")
        }
      }
      Spacer(modifier = Modifier.height(8.dp))
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        allSymptoms.forEach { symptom ->
          val selected = symptom in draft.symptoms
          FilterChip(
            selected = selected,
            onClick = {
              val next = if (selected) draft.symptoms - symptom else draft.symptoms + symptom
              commit(draft.copy(symptoms = next))
            },
            leadingIcon = if (selected) {
              { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(15.dp)) }
            } else null,
            label = { Text(symptom) }
          )
        }
      }
    }

    // Level 2: Expandable "More details"
    BloomCard(
      containerColor = MaterialTheme.colorScheme.surface,
      onClick = { showMoreDetails = !showMoreDetails }
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .testTag("more_details_toggle"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text("More details (optional)", style = MaterialTheme.typography.titleMedium)
          Text(
            text = "Sleep, energy, stress, exercise, water, caffeine, medication & tags",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        Icon(
          imageVector = if (showMoreDetails) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
          contentDescription = "Toggle more details"
        )
      }
    }

    AnimatedVisibility(
      visible = showMoreDetails,
      enter = expandVertically(spring()),
      exit = shrinkVertically(spring())
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Sleep, Energy, Stress
        BloomCard {
          Text("Sleep & Rest", style = MaterialTheme.typography.titleMedium)
          Spacer(modifier = Modifier.height(8.dp))
          val sleepVal = draft.sleepDurationHours ?: 7.0f
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text("Sleep duration", style = MaterialTheme.typography.bodyMedium)
            Text(
              text = if (draft.sleepDurationHours != null) "${"%.1f".format(sleepVal)} hrs" else "Not logged",
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.primary
            )
          }
          Slider(
            value = sleepVal,
            onValueChange = { raw ->
              val snapped = (raw * 2f).roundToInt() / 2f
              holder.draft = holder.draft.copy(sleepDurationHours = snapped)
            },
            onValueChangeFinished = { commit(holder.draft) },
            valueRange = 2f..12f
          )

          Text("Sleep quality (1–5)", style = MaterialTheme.typography.labelLarge)
          Spacer(modifier = Modifier.height(6.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (q in 1..5) {
              FilterChip(
                selected = draft.sleepQuality == q,
                onClick = { commit(draft.copy(sleepQuality = if (draft.sleepQuality == q) null else q)) },
                label = { Text("$q") }
              )
            }
          }

          Spacer(modifier = Modifier.height(8.dp))
          OutlinedTextField(
            value = draft.sleepTimingNote,
            onValueChange = { scheduleTextUpdate(draft.copy(sleepTimingNote = it)) },
            label = { Text("Sleep timing (e.g., 11 PM – 7 AM)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
          )

          Spacer(modifier = Modifier.height(14.dp))
          Text("Energy (0–5)", style = MaterialTheme.typography.labelLarge)
          Spacer(modifier = Modifier.height(6.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (e in 0..5) {
              FilterChip(
                selected = draft.energyLevel == e,
                onClick = { commit(draft.copy(energyLevel = if (draft.energyLevel == e) null else e)) },
                label = { Text("$e") }
              )
            }
          }

          Spacer(modifier = Modifier.height(10.dp))
          Text("Stress (0–5)", style = MaterialTheme.typography.labelLarge)
          Spacer(modifier = Modifier.height(6.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (s in 0..5) {
              FilterChip(
                selected = draft.stressLevel == s,
                onClick = { commit(draft.copy(stressLevel = if (draft.stressLevel == s) null else s)) },
                label = { Text("$s") }
              )
            }
          }
          Spacer(modifier = Modifier.height(8.dp))
          OutlinedTextField(
            value = draft.stressTriggers,
            onValueChange = { scheduleTextUpdate(draft.copy(stressTriggers = it)) },
            label = { Text("Stress triggers (optional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
          )
        }

        // Physical Activity, Hydration, Caffeine
        BloomCard {
          Text("Activity & Lifestyle", style = MaterialTheme.typography.titleMedium)
          Spacer(modifier = Modifier.height(8.dp))
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActivityLevel.entries.forEach { level ->
              FilterChip(
                selected = draft.activityLevel == level,
                onClick = { commit(draft.copy(activityLevel = level)) },
                label = { Text(level.label) }
              )
            }
          }
          Spacer(modifier = Modifier.height(8.dp))
          OutlinedTextField(
            value = holder.exerciseMinutesText,
            onValueChange = { raw ->
              val digitsOnly = raw.filter { it.isDigit() }.take(5)
              if (digitsOnly == holder.exerciseMinutesText && raw != digitsOnly) {
                return@OutlinedTextField
              }
              holder.exerciseMinutesText = digitsOnly
              if (digitsOnly.isEmpty()) {
                holder.exerciseMinutesError = null
                scheduleTextUpdate(draft.copy(exerciseMinutes = null))
              } else {
                val parsed = digitsOnly.toIntOrNull()
                if (parsed != null && parsed in 0..MAX_EXERCISE_MINUTES_PER_DAY) {
                  holder.exerciseMinutesError = null
                  scheduleTextUpdate(draft.copy(exerciseMinutes = parsed))
                } else {
                  holder.exerciseMinutesError = "Enter 0 to $MAX_EXERCISE_MINUTES_PER_DAY minutes (24 hours max)"
                }
              }
            },
            label = { Text("Exercise duration (minutes)") },
            isError = holder.exerciseMinutesError != null,
            supportingText = holder.exerciseMinutesError?.let { err -> { Text(err) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("exercise_minutes_input"),
            singleLine = true
          )

          Spacer(modifier = Modifier.height(12.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              Icon(Icons.Filled.LocalDrink, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
              Text("Water intake (cups)", style = MaterialTheme.typography.bodyMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
              OutlinedButton(onClick = {
                val cur = draft.waterCups ?: 0
                if (cur > 0) commit(draft.copy(waterCups = cur - 1))
              }) { Text("-") }
              Text(
                text = "${draft.waterCups ?: 0}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 12.dp)
              )
              OutlinedButton(onClick = {
                commit(draft.copy(waterCups = (draft.waterCups ?: 0) + 1))
              }) { Text("+") }
            }
          }

          Spacer(modifier = Modifier.height(8.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("Caffeine (cups)", style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
              OutlinedButton(onClick = {
                val cur = draft.caffeineCups ?: 0
                if (cur > 0) commit(draft.copy(caffeineCups = cur - 1))
              }) { Text("-") }
              Text(
                text = "${draft.caffeineCups ?: 0}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 12.dp)
              )
              OutlinedButton(onClick = {
                commit(draft.copy(caffeineCups = (draft.caffeineCups ?: 0) + 1))
              }) { Text("+") }
            }
          }
        }

        // Exact Cycle Overrides, Timing, Medication, Tags & Notes
        BloomCard {
          Text("Timing, Medication & Notes", style = MaterialTheme.typography.titleMedium)
          Spacer(modifier = Modifier.height(8.dp))

          Text("Pain timing", style = MaterialTheme.typography.labelLarge)
          Spacer(modifier = Modifier.height(4.dp))
          FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TimingSlot.entries.forEach { slot ->
              FilterChip(
                selected = draft.painTiming == slot,
                onClick = { commit(draft.copy(painTiming = slot)) },
                label = { Text(slot.label) }
              )
            }
          }

          Spacer(modifier = Modifier.height(10.dp))
          Text("Symptom timing", style = MaterialTheme.typography.labelLarge)
          Spacer(modifier = Modifier.height(4.dp))
          FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TimingSlot.entries.forEach { slot ->
              FilterChip(
                selected = draft.symptomTiming == slot,
                onClick = { commit(draft.copy(symptomTiming = slot)) },
                label = { Text(slot.label) }
              )
            }
          }

          Spacer(modifier = Modifier.height(10.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("Mark exact period start", style = MaterialTheme.typography.bodyMedium)
            Switch(
              checked = draft.isPeriodStartOverride,
              onCheckedChange = { commit(draft.copy(isPeriodStartOverride = it, isPeriod = it || draft.isPeriod)) }
            )
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("Mark exact period end", style = MaterialTheme.typography.bodyMedium)
            Switch(
              checked = draft.isPeriodEndOverride,
              onCheckedChange = { commit(draft.copy(isPeriodEndOverride = it)) }
            )
          }

          OutlinedTextField(
            value = draft.flowChangesNote,
            onValueChange = { scheduleTextUpdate(draft.copy(flowChangesNote = it)) },
            label = { Text("Flow changes note (optional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
          )

          Spacer(modifier = Modifier.height(10.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              Icon(Icons.Filled.Medication, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
              Column {
                Text("Medication taken", style = MaterialTheme.typography.bodyMedium)
                Text(
                  text = "Logged for your records only",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
            Switch(
              checked = draft.medicationTaken,
              onCheckedChange = { commit(draft.copy(medicationTaken = it)) }
            )
          }
          if (draft.medicationTaken || draft.medicationNote.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
              value = draft.medicationNote,
              onValueChange = { scheduleTextUpdate(draft.copy(medicationNote = it)) },
              label = { Text("Medication / relief logged") },
              modifier = Modifier.fillMaxWidth(),
              singleLine = true
            )
          }

          Spacer(modifier = Modifier.height(10.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("Custom tags", style = MaterialTheme.typography.labelLarge)
            OutlinedButton(onClick = { addOptionDialogType = CustomOptionType.TAG }) {
              Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Add tag")
            }
          }
          val allTags = (customTags + draft.customTags).distinct()
          if (allTags.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              allTags.forEach { tag ->
                val selected = tag in draft.customTags
                FilterChip(
                  selected = selected,
                  onClick = {
                    val next = if (selected) draft.customTags - tag else draft.customTags + tag
                    commit(draft.copy(customTags = next))
                  },
                  label = { Text("#$tag") }
                )
              }
            }
          }
        }
      }
    }

    // Personal Notes (always visible at bottom of Level 1/2)
    BloomCard {
      Text("Personal notes", style = MaterialTheme.typography.titleMedium)
      Spacer(modifier = Modifier.height(8.dp))
      OutlinedTextField(
        value = draft.notes,
        onValueChange = { scheduleTextUpdate(draft.copy(notes = it)) },
        placeholder = { Text("Anything you'd like to remember about today...") },
        modifier = Modifier
          .fillMaxWidth()
          .testTag("daily_notes_input"),
        minLines = 2,
        maxLines = 5
      )
    }
  }

  val dialogType = addOptionDialogType
  if (dialogType != null) {
    var inputLabel by remember { mutableStateOf("") }
    val typeLabel = when (dialogType) {
      CustomOptionType.MOOD -> "custom mood"
      CustomOptionType.SYMPTOM -> "custom symptom"
      CustomOptionType.TAG -> "custom tag"
    }
    AlertDialog(
      onDismissRequest = { addOptionDialogType = null },
      title = { Text("Add $typeLabel") },
      text = {
        OutlinedTextField(
          value = inputLabel,
          onValueChange = { inputLabel = it },
          label = { Text("Name") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("custom_option_input")
        )
      },
      confirmButton = {
        Button(
          onClick = {
            val clean = inputLabel.trim()
            if (clean.isNotEmpty()) {
              onAddCustomOption(dialogType, clean)
              val updated = when (dialogType) {
                CustomOptionType.MOOD -> draft.copy(moods = (draft.moods + clean).distinct())
                CustomOptionType.SYMPTOM -> draft.copy(symptoms = (draft.symptoms + clean).distinct())
                CustomOptionType.TAG -> draft.copy(customTags = (draft.customTags + clean).distinct())
              }
              commit(updated)
            }
            addOptionDialogType = null
          },
          enabled = inputLabel.trim().isNotEmpty(),
          modifier = Modifier.testTag("custom_option_save")
        ) {
          Text("Add")
        }
      },
      dismissButton = {
        OutlinedButton(onClick = { addOptionDialogType = null }) {
          Text("Cancel")
        }
      }
    )
  }
}
