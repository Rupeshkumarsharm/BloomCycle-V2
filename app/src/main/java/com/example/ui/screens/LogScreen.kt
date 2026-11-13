package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.model.CustomOptionType
import com.example.domain.model.DailyEntry
import com.example.ui.components.DailyLogEditor
import com.example.ui.viewmodel.BloomUiState
import com.example.util.CycleDate

@Composable
fun LogScreen(
  uiState: BloomUiState,
  onSelectDate: (CycleDate) -> Unit,
  onSaveEntry: (DailyEntry) -> Unit,
  onAddCustomOption: (CustomOptionType, String) -> Unit
) {
  val date = uiState.selectedDate
  val today = CycleDate.today()
  val existing = uiState.entriesByDate[date]

  val cycleForDay = uiState.analytics.cycles.lastOrNull { it.startDate <= date }
  val periodDay = if (existing?.isPeriod == true && cycleForDay != null) {
    (cycleForDay.startDate.daysUntil(date) + 1).coerceAtLeast(1)
  } else null

  Box(
    modifier = Modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter
  ) {
    Column(
      modifier = Modifier
        .widthIn(max = 700.dp)
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        IconButton(
          onClick = { onSelectDate(date.minusDays(1)) },
          modifier = Modifier.testTag("log_prev_day")
        ) {
          Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous day")
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text(
            text = if (date == today) "Today • ${date.shortLabel()}" else date.mediumLabel(),
            style = MaterialTheme.typography.headlineSmall
          )
          if (cycleForDay != null) {
            val cycleDay = cycleForDay.startDate.daysUntil(date) + 1
            if (cycleDay in 1..90) {
              Text(
                text = "Cycle Day $cycleDay",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
              )
            }
          }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          if (date != today) {
            TextButton(onClick = { onSelectDate(today) }) {
              Text("Today")
            }
          }
          IconButton(
            onClick = { onSelectDate(date.plusDays(1)) },
            modifier = Modifier.testTag("log_next_day")
          ) {
            Icon(Icons.Filled.ChevronRight, contentDescription = "Next day")
          }
        }
      }

      AnimatedVisibility(visible = uiState.saveStatusMessage != null) {
        Surface(
          shape = RoundedCornerShape(50),
          color = MaterialTheme.colorScheme.secondaryContainer,
          modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Icon(
              Icons.Filled.CheckCircle,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSecondaryContainer,
              modifier = Modifier.size(16.dp)
            )
            Text(
              text = uiState.saveStatusMessage.orEmpty(),
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSecondaryContainer
            )
          }
        }
      }

      DailyLogEditor(
        date = date,
        existingEntry = existing,
        periodDayNumber = periodDay,
        customOptions = uiState.customOptions,
        onSaveEntry = onSaveEntry,
        onAddCustomOption = onAddCustomOption
      )

      Spacer(modifier = Modifier.height(12.dp))
    }
  }
}
