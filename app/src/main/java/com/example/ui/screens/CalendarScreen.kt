package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.domain.model.DailyEntry
import com.example.domain.model.FlowLevel
import com.example.ui.components.BloomCard
import com.example.ui.theme.FertileAccentColor
import com.example.ui.theme.FlowHeavyColor
import com.example.ui.theme.FlowLightColor
import com.example.ui.theme.FlowMediumColor
import com.example.ui.theme.FlowVeryHeavyColor
import com.example.ui.theme.MoodIndicatorColor
import com.example.ui.theme.SpottingDotColor
import com.example.ui.viewmodel.BloomUiState
import com.example.util.CycleDate

@Composable
fun CalendarScreen(
  uiState: BloomUiState,
  onSelectDate: (CycleDate) -> Unit,
  onEditSelectedDay: (CycleDate) -> Unit
) {
  val today = CycleDate.today()
  var displayedMonth by remember { mutableStateOf(uiState.selectedDate.atDay(1)) }
  val selectedDate = uiState.selectedDate
  val prediction = uiState.analytics.prediction
  val showFertile = uiState.preferences.showFertileWindow

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val isWide = maxWidth >= 720.dp

    if (isWide) {
      Row(
        modifier = Modifier
          .fillMaxSize()
          .padding(18.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp)
      ) {
        Column(
          modifier = Modifier
            .weight(1.25f)
            .verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          CalendarMonthCard(
            displayedMonth = displayedMonth,
            selectedDate = selectedDate,
            today = today,
            uiState = uiState,
            showFertile = showFertile,
            onPrevMonth = { displayedMonth = displayedMonth.previousMonth() },
            onNextMonth = { displayedMonth = displayedMonth.nextMonth() },
            onJumpToday = {
              displayedMonth = today.atDay(1)
              onSelectDate(today)
            },
            onSelectDay = onSelectDate
          )
          CalendarLegendCard(showFertile = showFertile)
        }
        Column(
          modifier = Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
        ) {
          SelectedDayInspectorCard(
            date = selectedDate,
            entry = uiState.entriesByDate[selectedDate],
            uiState = uiState,
            prediction = prediction,
            showFertile = showFertile,
            onEditDay = { onEditSelectedDay(selectedDate) }
          )
        }
      }
    } else {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        CalendarMonthCard(
          displayedMonth = displayedMonth,
          selectedDate = selectedDate,
          today = today,
          uiState = uiState,
          showFertile = showFertile,
          onPrevMonth = { displayedMonth = displayedMonth.previousMonth() },
          onNextMonth = { displayedMonth = displayedMonth.nextMonth() },
          onJumpToday = {
            displayedMonth = today.atDay(1)
            onSelectDate(today)
          },
          onSelectDay = onSelectDate
        )
        CalendarLegendCard(showFertile = showFertile)
        SelectedDayInspectorCard(
          date = selectedDate,
          entry = uiState.entriesByDate[selectedDate],
          uiState = uiState,
          prediction = prediction,
          showFertile = showFertile,
          onEditDay = { onEditSelectedDay(selectedDate) }
        )
      }
    }
  }
}

@Composable
private fun CalendarMonthCard(
  displayedMonth: CycleDate,
  selectedDate: CycleDate,
  today: CycleDate,
  uiState: BloomUiState,
  showFertile: Boolean,
  onPrevMonth: () -> Unit,
  onNextMonth: () -> Unit,
  onJumpToday: () -> Unit,
  onSelectDay: (CycleDate) -> Unit
) {
  val prediction = uiState.analytics.prediction
  val cycles = uiState.analytics.cycles

  BloomCard {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      IconButton(onClick = onPrevMonth, modifier = Modifier.testTag("calendar_prev_month")) {
        Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous month")
      }
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
          text = displayedMonth.monthYearLabel(),
          style = MaterialTheme.typography.headlineSmall
        )
      }
      Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onJumpToday, modifier = Modifier.testTag("calendar_today_btn")) {
          Text("Today")
        }
        IconButton(onClick = onNextMonth, modifier = Modifier.testTag("calendar_next_month")) {
          Icon(Icons.Filled.ChevronRight, contentDescription = "Next month")
        }
      }
    }

    Spacer(modifier = Modifier.height(8.dp))
    Row(modifier = Modifier.fillMaxWidth()) {
      listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { dow ->
        Text(
          text = dow,
          modifier = Modifier.weight(1f),
          textAlign = TextAlign.Center,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
    Spacer(modifier = Modifier.height(6.dp))

    val daysInMonth = CycleDate.daysInMonth(displayedMonth.year, displayedMonth.month)
    val leadingBlanks = displayedMonth.atDay(1).dayOfWeekIso() - 1
    val cells = buildList<CycleDate?> {
      repeat(leadingBlanks) { add(null) }
      for (d in 1..daysInMonth) add(displayedMonth.atDay(d))
      while (size % 7 != 0) add(null)
    }

    cells.chunked(7).forEach { week ->
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        week.forEach { date ->
          if (date == null) {
            Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
          } else {
            val entry = uiState.entriesByDate[date]
            val isPeriod = entry?.isPeriod == true
            val isPredictedPeriod = !isPeriod && prediction != null &&
              date >= prediction.nextPeriodStart && date <= prediction.estimatedPeriodEnd
            val isFertile = showFertile && !isPeriod && prediction?.fertileWindowStart != null &&
              prediction.fertileWindowEnd != null &&
              date >= prediction.fertileWindowStart && date <= prediction.fertileWindowEnd
            val isOvulation = showFertile && prediction?.estimatedOvulationDay == date
            val isSelected = date == selectedDate
            val isToday = date == today

            // Cycle day calculation
            val cycleForDay = cycles.lastOrNull { it.startDate <= date }
            val cycleDay = cycleForDay?.let {
              val d = it.startDate.daysUntil(date) + 1
              if (d in 1..60) d else null
            }

            val periodBg = when (entry?.flowLevel) {
              FlowLevel.VERY_HEAVY -> FlowVeryHeavyColor
              FlowLevel.HEAVY -> FlowHeavyColor
              FlowLevel.MEDIUM -> FlowMediumColor
              FlowLevel.LIGHT -> FlowLightColor
              else -> if (isPeriod) FlowMediumColor else Color.Transparent
            }
            val primaryColor = MaterialTheme.colorScheme.primary

            Box(
              modifier = Modifier
                .weight(1f)
                .aspectRatio(0.95f)
                .padding(vertical = 2.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                  when {
                    isPeriod -> periodBg
                    isFertile -> FertileAccentColor.copy(alpha = 0.16f)
                    else -> Color.Transparent
                  }
                )
                .drawBehind {
                  if (isPredictedPeriod) {
                    drawRoundRect(
                      color = primaryColor,
                      style = Stroke(
                        width = 1.6.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                      ),
                      cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx())
                    )
                  }
                }
                .then(
                  if (isSelected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.onBackground, RoundedCornerShape(14.dp))
                  } else if (isToday) {
                    Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
                  } else Modifier
                )
                .clickable { onSelectDay(date) }
                .testTag("cal_day_${date.toIsoString()}"),
              contentAlignment = Alignment.Center
            ) {
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
              ) {
                Text(
                  text = date.day.toString(),
                  style = MaterialTheme.typography.bodyMedium,
                  fontWeight = if (isSelected || isToday || isPeriod) FontWeight.Bold else FontWeight.Medium,
                  color = if (isPeriod) Color.White else MaterialTheme.colorScheme.onSurface
                )

                // Subtle visual indicator dots (spotting, cramps, mood, ovulation, notes)
                Row(
                  horizontalArrangement = Arrangement.spacedBy(2.5.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.height(8.dp)
                ) {
                  if (entry?.spotting == true) {
                    Box(Modifier.size(4.5.dp).clip(CircleShape).background(SpottingDotColor))
                  }
                  if ((entry?.crampScore ?: 0) > 0) {
                    Box(
                      Modifier
                        .size(4.5.dp)
                        .clip(CircleShape)
                        .background(if (isPeriod) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.tertiary)
                    )
                  }
                  if (entry?.moods?.isNotEmpty() == true || entry?.symptoms?.isNotEmpty() == true) {
                    Box(
                      Modifier
                        .size(4.5.dp)
                        .clip(CircleShape)
                        .background(if (isPeriod) Color.White.copy(alpha = 0.7f) else MoodIndicatorColor)
                    )
                  }
                  if (isOvulation) {
                    Box(Modifier.size(5.dp).clip(CircleShape).background(FertileAccentColor))
                  }
                }

                if (cycleDay != null && (isPeriod || isSelected || isToday)) {
                  Text(
                    text = "D$cycleDay",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isPeriod) Color.White.copy(alpha = 0.88f) else MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }

              if (!entry?.notes.isNullOrBlank()) {
                Icon(
                  imageVector = Icons.Filled.Bookmark,
                  contentDescription = "Has note",
                  tint = if (isPeriod) Color.White else MaterialTheme.colorScheme.primary,
                  modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .size(9.dp)
                )
              }
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CalendarLegendCard(showFertile: Boolean) {
  BloomCard {
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      LegendDot("Period", FlowMediumColor, isDashed = false)
      LegendDot("Est. period", MaterialTheme.colorScheme.primary, isDashed = true)
      if (showFertile) {
        LegendDot("Est. fertile", FertileAccentColor, isDashed = false)
      }
      LegendDot("Spotting", SpottingDotColor, isDashed = false)
      LegendDot("Mood / Symptoms", MoodIndicatorColor, isDashed = false)
    }
  }
}

@Composable
private fun LegendDot(label: String, color: Color, isDashed: Boolean) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(5.dp)
  ) {
    Box(
      modifier = Modifier
        .size(11.dp)
        .clip(CircleShape)
        .background(if (isDashed) Color.Transparent else color)
        .then(if (isDashed) Modifier.border(1.5.dp, color, CircleShape) else Modifier)
    )
    Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectedDayInspectorCard(
  date: CycleDate,
  entry: DailyEntry?,
  uiState: BloomUiState,
  prediction: com.example.domain.model.CyclePrediction?,
  showFertile: Boolean,
  onEditDay: () -> Unit
) {
  val cycle = uiState.analytics.cycles.lastOrNull { it.startDate <= date }
  val cycleDay = cycle?.let {
    val diff = it.startDate.daysUntil(date) + 1
    if (diff in 1..90) diff else null
  }
  val isPredicted = entry?.isPeriod != true && prediction != null &&
    date >= prediction.nextPeriodWindowStart && date <= prediction.nextPeriodWindowEnd
  val isEstFertile = showFertile && prediction?.fertileWindowStart != null &&
    prediction.fertileWindowEnd != null &&
    date >= prediction.fertileWindowStart && date <= prediction.fertileWindowEnd

  BloomCard {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column {
        Text(text = date.mediumLabel(), style = MaterialTheme.typography.headlineSmall)
        if (cycleDay != null) {
          Text(
            text = "Cycle Day $cycleDay",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
          )
        }
      }
      Button(
        onClick = onEditDay,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        modifier = Modifier.testTag("calendar_edit_day_btn")
      ) {
        Icon(Icons.Filled.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(if (entry?.hasAnyDetails == true) "Edit log" else "Log day")
      }
    }

    if (isPredicted || isEstFertile) {
      Spacer(modifier = Modifier.height(10.dp))
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (isPredicted) {
          Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.primaryContainer
          ) {
            Text(
              text = "Estimated period window",
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onPrimaryContainer
            )
          }
        }
        if (isEstFertile) {
          Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.secondaryContainer
          ) {
            Text(
              text = if (prediction?.estimatedOvulationDay == date) "Estimated ovulation day" else "Estimated fertile window",
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSecondaryContainer
            )
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(12.dp))
    if (entry == null || !entry.hasAnyDetails) {
      Text(
        text = "No tracking details logged for this date yet.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    } else {
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (entry.isPeriod) {
          Text("• Period: Yes (${entry.flowLevel.label} flow)", style = MaterialTheme.typography.bodyMedium)
        }
        if (entry.spotting) {
          Text("• Spotting: Logged", style = MaterialTheme.typography.bodyMedium)
        }
        if (entry.crampScore > 0) {
          Text("• Cramps: ${entry.crampScore} / 5", style = MaterialTheme.typography.bodyMedium)
        }
        if (entry.moods.isNotEmpty()) {
          Text("• Mood: ${entry.moods.joinToString(", ")}", style = MaterialTheme.typography.bodyMedium)
        }
        if (entry.moodSwingLevel != com.example.domain.model.MoodSwingLevel.NONE) {
          Text("• Mood swings: ${entry.moodSwingLevel.label}", style = MaterialTheme.typography.bodyMedium)
        }
        if (entry.symptoms.isNotEmpty()) {
          Text("• Symptoms: ${entry.symptoms.joinToString(", ")}", style = MaterialTheme.typography.bodyMedium)
        }
        if (entry.sleepDurationHours != null) {
          Text("• Sleep: ${entry.sleepDurationHours}h", style = MaterialTheme.typography.bodyMedium)
        }
        if (entry.notes.isNotBlank()) {
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
          ) {
            Text(
              text = entry.notes,
              modifier = Modifier.fillMaxWidth().padding(10.dp),
              style = MaterialTheme.typography.bodySmall
            )
          }
        }
      }
    }
  }
}
