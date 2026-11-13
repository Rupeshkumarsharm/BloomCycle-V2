package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.analytics.CycleAnalyticsEngine
import com.example.domain.model.AnalyticsSnapshot
import com.example.domain.model.AnalyticsWindow
import com.example.ui.components.BloomCard
import com.example.ui.components.CrampByCycleDayChart
import com.example.ui.components.CycleLengthTrendChart
import com.example.ui.components.FrequencyBarRow
import com.example.ui.viewmodel.BloomUiState
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InsightsScreen(
  uiState: BloomUiState,
  onSelectWindow: (AnalyticsWindow) -> Unit
) {
  val snapshot = uiState.analytics
  var selectedTab by remember { mutableIntStateOf(0) }
  val tabs = listOf("Cycles", "Symptoms & Mood", "Patterns", "Age & Alerts")

  Box(
    modifier = Modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter
  ) {
    Column(
      modifier = Modifier
        .widthIn(max = 780.dp)
        .fillMaxSize()
    ) {
      Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text("Personal Insights", style = MaterialTheme.typography.headlineMedium)
        Text(
          text = "Past → Patterns → Future • Computed locally from your data",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          AnalyticsWindow.entries.forEach { window ->
            FilterChip(
              selected = uiState.analyticsWindow == window,
              onClick = { onSelectWindow(window) },
              label = { Text(window.label) },
              modifier = Modifier.testTag("window_${window.name.lowercase()}")
            )
          }
        }
      }

      ScrollableTabRow(
        selectedTabIndex = selectedTab,
        containerColor = MaterialTheme.colorScheme.background,
        edgePadding = 16.dp
      ) {
        tabs.forEachIndexed { idx, title ->
          Tab(
            selected = selectedTab == idx,
            onClick = { selectedTab = idx },
            text = { Text(title) },
            modifier = Modifier.testTag("insights_tab_$idx")
          )
        }
      }

      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        when (selectedTab) {
          0 -> {
            item { CycleOverviewMetricsCard(snapshot) }
            item {
              BloomCard {
                Text("Cycle length history", style = MaterialTheme.typography.titleMedium)
                Text(
                  text = "Tap any cycle point to inspect start date and period duration",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                CycleLengthTrendChart(
                  cycles = snapshot.cycles,
                  averageLength = snapshot.averageCycleLength
                )
              }
            }
            item { PredictionDetailsCard(snapshot, uiState.preferences.showFertileWindow) }
          }

          1 -> {
            item {
              BloomCard {
                Text("Cramp intensity by cycle day", style = MaterialTheme.typography.titleMedium)
                Text(
                  text = "Average cramp score (0–5) across logged cycle days",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                CrampByCycleDayChart(crampByDay = snapshot.crampByCycleDay)
              }
            }
            item { SymptomAnalyticsCard(snapshot) }
            item { MoodAnalyticsCard(snapshot) }
          }

          2 -> {
            item { PersonalInsightsEngineSection(snapshot) }
            item { CrossFactorSection(snapshot) }
          }

          else -> {
            item { AgeTrendsSection(snapshot) }
            item { AnomalyDetectionSection(snapshot) }
          }
        }

        item {
          Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              Icons.Filled.Info,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(16.dp)
            )
            Text(
              text = "All summaries are personal observations from your logged entries, not medical diagnoses.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }
    }
  }
}

@Composable
private fun CycleOverviewMetricsCard(snapshot: AnalyticsSnapshot) {
  BloomCard {
    Text("Cycle & period statistics", style = MaterialTheme.typography.titleMedium)
    Spacer(modifier = Modifier.height(10.dp))

    if (snapshot.totalCyclesCount == 0) {
      Text(
        text = "No cycles derived yet. Log period days in Calendar or Log to unlock descriptive cycle statistics.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      return@BloomCard
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      StatCell(
        label = "Average cycle",
        value = snapshot.averageCycleLength?.let { "${CycleAnalyticsEngine.format1(it)}d" } ?: "—",
        modifier = Modifier.weight(1f)
      )
      StatCell(
        label = "Median cycle",
        value = snapshot.medianCycleLength?.let { "${CycleAnalyticsEngine.format1(it)}d" } ?: "—",
        modifier = Modifier.weight(1f)
      )
      StatCell(
        label = "Variability",
        value = snapshot.cycleVariabilityDays?.let { "±${CycleAnalyticsEngine.format1(it)}d" } ?: "—",
        modifier = Modifier.weight(1f)
      )
    }

    Spacer(modifier = Modifier.height(12.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      StatCell(
        label = "Shortest / Longest",
        value = if (snapshot.shortestCycleLength != null && snapshot.longestCycleLength != null) {
          "${snapshot.shortestCycleLength}–${snapshot.longestCycleLength}d"
        } else "—",
        modifier = Modifier.weight(1f)
      )
      StatCell(
        label = "Avg period",
        value = snapshot.averagePeriodDuration?.let { "${CycleAnalyticsEngine.format1(it)}d" } ?: "—",
        modifier = Modifier.weight(1f)
      )
      StatCell(
        label = "Period range",
        value = if (snapshot.shortestPeriodDuration != null && snapshot.longestPeriodDuration != null) {
          "${snapshot.shortestPeriodDuration}–${snapshot.longestPeriodDuration}d"
        } else "—",
        modifier = Modifier.weight(1f)
      )
    }

    if (snapshot.variabilityTrendNote != null) {
      Spacer(modifier = Modifier.height(12.dp))
      Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
      ) {
        Text(
          text = snapshot.variabilityTrendNote,
          modifier = Modifier.fillMaxWidth().padding(12.dp),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onPrimaryContainer
        )
      }
    }
  }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(14.dp),
    color = MaterialTheme.colorScheme.surface
  ) {
    Column(modifier = Modifier.padding(10.dp)) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      Spacer(modifier = Modifier.height(3.dp))
      Text(
        text = value,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
      )
    }
  }
}

@Composable
private fun PredictionDetailsCard(snapshot: AnalyticsSnapshot, showFertile: Boolean) {
  val pred = snapshot.prediction
  BloomCard {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
      Text("Prediction Engine (Estimated)", style = MaterialTheme.typography.titleMedium)
    }
    Spacer(modifier = Modifier.height(8.dp))
    if (pred == null) {
      Text(
        text = "Log at least one period to generate estimated cycle windows.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    } else {
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          text = "Estimated next period: ${pred.nextPeriodStart.mediumLabel()}",
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.primary
        )
        Text(
          text = "Approximate cycle window: ${pred.nextPeriodWindowStart.shortLabel()} – ${pred.nextPeriodWindowEnd.shortLabel()}",
          style = MaterialTheme.typography.bodyMedium
        )
        if (showFertile && pred.fertileWindowStart != null && pred.fertileWindowEnd != null) {
          Text(
            text = "Estimated fertile window: ${pred.fertileWindowStart.shortLabel()} – ${pred.fertileWindowEnd.shortLabel()} (Est. ovulation ${pred.estimatedOvulationDay?.shortLabel()})",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary
          )
        }
        Text(
          text = "${pred.confidenceNote}. All dates are estimated, not guaranteed.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}

@Composable
private fun SymptomAnalyticsCard(snapshot: AnalyticsSnapshot) {
  BloomCard {
    Text("Recurring physical symptoms", style = MaterialTheme.typography.titleMedium)
    Spacer(modifier = Modifier.height(10.dp))
    if (snapshot.symptomFrequencies.isEmpty()) {
      Text(
        text = "No physical symptoms logged yet. Track headaches, bloating, fatigue, or custom symptoms to spot patterns.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    } else {
      val maxLogs = snapshot.symptomFrequencies.maxOf { it.totalLogs }.coerceAtLeast(1)
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        snapshot.symptomFrequencies.take(8).forEach { stat ->
          val subtitle = if (stat.cycleCount > 0) {
            "${stat.cycleCount} of ${stat.totalCyclesEvaluated} cycle(s) • ${stat.primaryTimingSummary}"
          } else {
            stat.primaryTimingSummary
          }
          FrequencyBarRow(
            label = stat.symptom,
            subtitle = subtitle,
            fraction = stat.totalLogs.toFloat() / maxLogs,
            valueText = "${stat.totalLogs}x"
          )
        }
      }
    }
  }
}

@Composable
private fun MoodAnalyticsCard(snapshot: AnalyticsSnapshot) {
  BloomCard {
    Text("Mood & energy patterns", style = MaterialTheme.typography.titleMedium)
    Spacer(modifier = Modifier.height(8.dp))
    if (snapshot.moodDistribution.isEmpty()) {
      Text(
        text = "Log your moods and energy levels to uncover emotional rhythms across your cycle.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    } else {
      Text(
        text = "Mood swings were logged on ${snapshot.moodSwingRatePercent.roundToInt()}% of mood check-in days.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      Spacer(modifier = Modifier.height(10.dp))
      val maxCount = snapshot.moodDistribution.maxOf { it.count }.coerceAtLeast(1)
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        snapshot.moodDistribution.take(6).forEach { m ->
          FrequencyBarRow(
            label = m.mood,
            subtitle = "${m.percentage.roundToInt()}% of logged moods",
            fraction = m.count.toFloat() / maxCount,
            valueText = "${m.count}d",
            barColor = MaterialTheme.colorScheme.secondary
          )
        }
      }

      if (snapshot.energyByPhase.isNotEmpty()) {
        Spacer(modifier = Modifier.height(14.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(10.dp))
        Text("Average energy by cycle phase", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(6.dp))
        snapshot.energyByPhase.entries.forEach { (phase, avgEnergy) ->
          Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text(phase.label, style = MaterialTheme.typography.bodyMedium)
            Text(
              text = "${CycleAnalyticsEngine.format1(avgEnergy)} / 5",
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.primary
            )
          }
        }
      }
    }
  }
}

@Composable
private fun PersonalInsightsEngineSection(snapshot: AnalyticsSnapshot) {
  BloomCard {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
      Text("Personal Insights Engine", style = MaterialTheme.typography.titleMedium)
    }
    Spacer(modifier = Modifier.height(10.dp))
    if (snapshot.personalInsights.isEmpty()) {
      Text(
        text = "Log a few more days and cycles so BloomCycle can translate your records into personalized statements.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    } else {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        snapshot.personalInsights.forEach { card ->
          Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
          ) {
            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
              Text(
                text = card.category.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
              )
              Spacer(modifier = Modifier.height(2.dp))
              Text(text = card.title, style = MaterialTheme.typography.titleSmall)
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = card.statement,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun CrossFactorSection(snapshot: AnalyticsSnapshot) {
  BloomCard {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Icon(Icons.Filled.Psychology, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
      Column {
        Text("Cross-Factor Patterns", style = MaterialTheme.typography.titleMedium)
        Text(
          text = "Co-occurrence in your logs (correlation ≠ causation)",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
    Spacer(modifier = Modifier.height(10.dp))
    if (snapshot.crossFactorInsights.isEmpty()) {
      Text(
        text = "Log sleep duration, stress (0–5), or physical activity in \"More details\" on at least 4–6 days to compare how lifestyle factors appear alongside cramps and moods.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    } else {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        snapshot.crossFactorInsights.forEach { cf ->
          Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
          ) {
            Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Text(text = cf.title, style = MaterialTheme.typography.titleSmall)
              Text(text = cf.summary, style = MaterialTheme.typography.bodyMedium)
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Surface(
                  shape = RoundedCornerShape(10.dp),
                  color = MaterialTheme.colorScheme.surface,
                  modifier = Modifier.weight(1f)
                ) {
                  Text(
                    text = cf.comparisonMetricA,
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.labelSmall
                  )
                }
                Surface(
                  shape = RoundedCornerShape(10.dp),
                  color = MaterialTheme.colorScheme.surface,
                  modifier = Modifier.weight(1f)
                ) {
                  Text(
                    text = cf.comparisonMetricB,
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.labelSmall
                  )
                }
              }
              Text(
                text = "Based on ${cf.sampleDays} paired days",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun AgeTrendsSection(snapshot: AnalyticsSnapshot) {
  BloomCard {
    Text("Age-based personal history", style = MaterialTheme.typography.titleMedium)
    Text(
      text = "Contextual summary of your own logged cycles grouped by your age at each cycle start.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(10.dp))
    if (snapshot.ageTrends.isEmpty()) {
      Text(
        text = "No historical cycles available yet for age-grouped comparison.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    } else {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        snapshot.ageTrends.forEach { bucket ->
          Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
          ) {
            Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Text("Age ${bucket.ageYears}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Text("${bucket.cycleCount} cycle(s)", style = MaterialTheme.typography.labelMedium)
              }
              Text(
                text = buildString {
                  append("Avg period: ${CycleAnalyticsEngine.format1(bucket.averagePeriodDuration)}d")
                  bucket.averageCycleLength?.let { append(" • Avg cycle: ${CycleAnalyticsEngine.format1(it)}d") }
                  if (bucket.averageCrampScore > 0f) append(" • Avg cramps: ${CycleAnalyticsEngine.format1(bucket.averageCrampScore)}/5")
                },
                style = MaterialTheme.typography.bodySmall
              )
              bucket.topSymptom?.let {
                Text(
                  text = "Most logged symptom: $it",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun AnomalyDetectionSection(snapshot: AnalyticsSnapshot) {
  BloomCard {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
      Text("Anomaly & Pattern Watch", style = MaterialTheme.typography.titleMedium)
    }
    Spacer(modifier = Modifier.height(8.dp))
    if (snapshot.anomalies.isEmpty()) {
      Text(
        text = "No unusual deviations detected compared with your personal history.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    } else {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        snapshot.anomalies.forEach { alert ->
          Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f)
          ) {
            Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text(
                text = alert.severityLabel.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
              )
              Text(
                text = alert.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
              )
              Text(
                text = alert.detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
              )
            }
          }
        }
      }
    }
  }
}
