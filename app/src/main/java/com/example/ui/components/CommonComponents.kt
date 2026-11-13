package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.util.CycleDate

@Composable
fun PrivacyBadgePill(
  text: String = "Stored only on this device",
  modifier: Modifier = Modifier
) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(50),
    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f))
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      Icon(
        imageVector = Icons.Filled.Lock,
        contentDescription = "Local privacy indicator",
        tint = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.size(14.dp)
      )
      Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSecondaryContainer
      )
    }
  }
}

@Composable
fun BloomCard(
  modifier: Modifier = Modifier,
  containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
  borderColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f),
  onClick: (() -> Unit)? = null,
  content: @Composable ColumnScope.() -> Unit
) {
  val shape = RoundedCornerShape(22.dp)
  if (onClick != null) {
    Card(
      onClick = onClick,
      modifier = modifier,
      shape = shape,
      colors = CardDefaults.cardColors(containerColor = containerColor),
      border = BorderStroke(1.dp, borderColor),
      elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
      Column(modifier = Modifier.padding(16.dp), content = content)
    }
  } else {
    Card(
      modifier = modifier,
      shape = shape,
      colors = CardDefaults.cardColors(containerColor = containerColor),
      border = BorderStroke(1.dp, borderColor),
      elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
      Column(modifier = Modifier.padding(16.dp), content = content)
    }
  }
}

@Composable
fun CycleDatePickerDialog(
  title: String,
  initialDate: CycleDate,
  minYear: Int = 1940,
  maxYear: Int = CycleDate.today().year + 1,
  maxDate: CycleDate? = null,
  onDismiss: () -> Unit,
  onDateSelected: (CycleDate) -> Unit
) {
  var selectedYear by remember { mutableIntStateOf(initialDate.year.coerceIn(minYear, maxYear)) }
  var selectedMonth by remember { mutableIntStateOf(initialDate.month) }
  var selectedDay by remember { mutableIntStateOf(initialDate.day) }
  var showYearGrid by remember { mutableStateOf(false) }

  val maxDays = CycleDate.daysInMonth(selectedYear, selectedMonth)
  if (selectedDay > maxDays) selectedDay = maxDays

  val candidate = CycleDate(selectedYear, selectedMonth, selectedDay)
  val isAllowed = maxDate == null || candidate <= maxDate

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(text = title, style = MaterialTheme.typography.headlineSmall)
        TextButton(onClick = { showYearGrid = !showYearGrid }) {
          Text(if (showYearGrid) "Calendar" else "$selectedYear")
        }
      }
    },
    text = {
      if (showYearGrid) {
        val years = (maxYear downTo minYear).toList()
        LazyColumn(
          modifier = Modifier
            .fillMaxWidth()
            .height(260.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          items(years) { yr ->
            val selected = yr == selectedYear
            Surface(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                  selectedYear = yr
                  showYearGrid = false
                },
              color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
            ) {
              Text(
                text = yr.toString(),
                modifier = Modifier.padding(vertical = 10.dp, horizontal = 14.dp),
                style = MaterialTheme.typography.titleMedium,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
              )
            }
          }
        }
      } else {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            IconButton(
              onClick = {
                if (selectedMonth == 1) {
                  if (selectedYear > minYear) {
                    selectedYear--
                    selectedMonth = 12
                  }
                } else {
                  selectedMonth--
                }
              }
            ) {
              Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous month")
            }
            Text(
              text = "${CycleDate.monthFullName(selectedMonth)} $selectedYear",
              style = MaterialTheme.typography.titleMedium
            )
            IconButton(
              onClick = {
                if (selectedMonth == 12) {
                  if (selectedYear < maxYear) {
                    selectedYear++
                    selectedMonth = 1
                  }
                } else {
                  selectedMonth++
                }
              }
            ) {
              Icon(Icons.Filled.ChevronRight, contentDescription = "Next month")
            }
          }

          val firstDow = CycleDate(selectedYear, selectedMonth, 1).dayOfWeekIso() // 1..7
          val leadingBlanks = firstDow - 1
          val cells = buildList<Int?> {
            repeat(leadingBlanks) { add(null) }
            for (d in 1..maxDays) add(d)
            while (size % 7 != 0) add(null)
          }

          Row(modifier = Modifier.fillMaxWidth()) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { dow ->
              Text(
                text = dow,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }

          cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
              week.forEach { d ->
                if (d == null) {
                  Spacer(modifier = Modifier.weight(1f).height(38.dp))
                } else {
                  val cellDate = CycleDate(selectedYear, selectedMonth, d)
                  val disabled = maxDate != null && cellDate > maxDate
                  val isSel = d == selectedDay
                  Box(
                    modifier = Modifier
                      .weight(1f)
                      .height(38.dp)
                      .padding(2.dp)
                      .clip(CircleShape)
                      .background(
                        when {
                          isSel -> MaterialTheme.colorScheme.primary
                          else -> Color.Transparent
                        }
                      )
                      .clickable(enabled = !disabled) { selectedDay = d },
                    contentAlignment = Alignment.Center
                  ) {
                    Text(
                      text = d.toString(),
                      style = MaterialTheme.typography.bodyMedium,
                      fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                      color = when {
                        isSel -> MaterialTheme.colorScheme.onPrimary
                        disabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                        else -> MaterialTheme.colorScheme.onSurface
                      }
                    )
                  }
                }
              }
            }
          }

          if (!isAllowed && maxDate != null) {
            Text(
              text = "Please choose a date on or before ${maxDate.mediumLabel()}.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.error
            )
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = { onDateSelected(candidate) },
        enabled = isAllowed,
        modifier = Modifier.testTag("date_picker_confirm")
      ) {
        Text("Select")
      }
    },
    dismissButton = {
      OutlinedButton(onClick = onDismiss) {
        Text("Cancel")
      }
    }
  )
}
