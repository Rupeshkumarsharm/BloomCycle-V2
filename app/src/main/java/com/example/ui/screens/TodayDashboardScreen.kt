package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.analytics.CycleAnalyticsEngine
import com.example.domain.model.CyclePhase
import com.example.ui.components.BloomCard
import com.example.ui.components.PrivacyBadgePill
import com.example.ui.viewmodel.BloomUiState
import com.example.util.CycleDate

@Composable
fun TodayDashboardScreen(
  uiState: BloomUiState,
  onQuickTogglePeriodToday: () -> Unit,
  onOpenTodayLog: () -> Unit,
  onOpenCalendar: () -> Unit,
  onOpenInsights: () -> Unit
) {
  val today = CycleDate.today()
  val todayEntry = uiState.entriesByDate[today]
  val snapshot = uiState.analytics
  val profile = uiState.profile
  val greeting = if (!profile?.name.isNullOrBlank()) "Hello, ${profile!!.name}" else "Your Today"

  Box(
    modifier = Modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter
  ) {
    LazyColumn(
      modifier = Modifier
        .widthIn(max = 760.dp)
        .fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = greeting,
              style = MaterialTheme.typography.headlineMedium,
              color = MaterialTheme.colorScheme.onBackground
            )
            Text(
              text = today.mediumLabel(),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          PrivacyBadgePill()
        }
      }

      // Hero Cycle Overview Banner
      item {
        Surface(
          shape = RoundedCornerShape(26.dp),
          color = Color.Transparent,
          modifier = Modifier.fillMaxWidth()
        ) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .background(
                Brush.linearGradient(
                  listOf(
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.85f)
                  )
                )
              )
              .padding(20.dp)
          ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = if (snapshot.currentCycleDay != null) {
                      "Cycle Day ${snapshot.currentCycleDay}"
                    } else {
                      "Start your cycle log"
                    },
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onPrimary
                  )
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(
                    text = snapshot.currentPhase.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.92f)
                  )
                  Text(
                    text = snapshot.currentPhase.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f)
                  )
                }

                Surface(
                  shape = RoundedCornerShape(16.dp),
                  color = Color.White.copy(alpha = 0.2f)
                ) {
                  Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                  ) {
                    val pred = snapshot.prediction
                    Text(
                      text = if (pred != null) {
                        when {
                          pred.daysUntilNextPeriod > 0 -> "${pred.daysUntilNextPeriod}d"
                          pred.daysUntilNextPeriod == 0 -> "Today"
                          else -> "Window"
                        }
                      } else "—",
                      style = MaterialTheme.typography.headlineSmall,
                      fontWeight = FontWeight.Bold,
                      color = Color.White
                    )
                    Text(
                      text = "Est. next",
                      style = MaterialTheme.typography.labelSmall,
                      color = Color.White.copy(alpha = 0.9f)
                    )
                  }
                }
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                Button(
                  onClick = onOpenTodayLog,
                  colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = MaterialTheme.colorScheme.primary
                  ),
                  modifier = Modifier.weight(1f).testTag("dashboard_log_today_btn")
                ) {
                  Icon(Icons.Filled.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(if (todayEntry?.hasAnyDetails == true) "Update Today" else "Log Today")
                }

                OutlinedButton(
                  onClick = onQuickTogglePeriodToday,
                  colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                  border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.65f)),
                  modifier = Modifier.testTag("dashboard_quick_period_btn")
                ) {
                  Icon(Icons.Filled.WaterDrop, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(if (todayEntry?.isPeriod == true) "Period: Yes" else "Period?")
                }
              }
            }
          }
        }
      }

      // Anomaly Alerts (if any)
      if (snapshot.anomalies.isNotEmpty()) {
        item {
          val alert = snapshot.anomalies.first()
          BloomCard(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
            borderColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f),
            onClick = onOpenInsights
          ) {
            Row(
              verticalAlignment = Alignment.Top,
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Icon(
                Icons.Filled.WarningAmber,
                contentDescription = "Cycle notice",
                tint = MaterialTheme.colorScheme.onTertiaryContainer
              )
              Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                  text = alert.title,
                  style = MaterialTheme.typography.titleMedium,
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

      // 6 Overview Cards (2-column layout)
      item {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            MetricTile(
              icon = Icons.Filled.WaterDrop,
              title = "Current cycle",
              value = snapshot.currentCycleDay?.let { "Day $it" } ?: "Not started",
              subtitle = snapshot.currentPeriodDay?.let { "Period Day $it" }
                ?: if (snapshot.currentPhase == CyclePhase.FERTILE_WINDOW) "Estimated fertile window"
                else "Tap calendar for view",
              accent = MaterialTheme.colorScheme.primary,
              modifier = Modifier.weight(1f),
              onClick = onOpenCalendar
            )
            MetricTile(
              icon = Icons.Filled.CalendarMonth,
              title = "Next period",
              value = snapshot.prediction?.let { p ->
                when {
                  p.daysUntilNextPeriod > 0 -> "In ~${p.daysUntilNextPeriod} days"
                  p.daysUntilNextPeriod == 0 -> "Estimated today"
                  else -> "Est. window"
                }
              } ?: "Need 1+ period",
              subtitle = snapshot.prediction?.let { "Est. ${it.nextPeriodStart.shortLabel()}" }
                ?: "Estimated once logged",
              accent = MaterialTheme.colorScheme.secondary,
              modifier = Modifier.weight(1f),
              onClick = onOpenCalendar
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            MetricTile(
              icon = Icons.Filled.Timeline,
              title = "Average cycle",
              value = snapshot.averageCycleLength?.let { "${CycleAnalyticsEngine.format1(it)} days" }
                ?: "${profile?.defaultCycleLength ?: 28}d base",
              subtitle = if (snapshot.completedCyclesCount > 0) {
                "${snapshot.completedCyclesCount} completed cycle(s)"
              } else "Building cycle history",
              accent = MaterialTheme.colorScheme.primary,
              modifier = Modifier.weight(1f),
              onClick = onOpenInsights
            )
            MetricTile(
              icon = Icons.Filled.Favorite,
              title = "Recent cramps",
              value = snapshot.recentCrampAverage?.let { "${CycleAnalyticsEngine.format1(it)} / 5" } ?: "No data",
              subtitle = "30-day logged average",
              accent = MaterialTheme.colorScheme.tertiary,
              modifier = Modifier.weight(1f),
              onClick = onOpenTodayLog
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            MetricTile(
              icon = Icons.Filled.Mood,
              title = "Top mood",
              value = snapshot.topLoggedMood ?: "No mood yet",
              subtitle = if (snapshot.moodSwingRatePercent > 0f) {
                "Swings on ${snapshot.moodSwingRatePercent.toInt()}% of logs"
              } else "Log mood in seconds",
              accent = MaterialTheme.colorScheme.secondary,
              modifier = Modifier.weight(1f),
              onClick = onOpenTodayLog
            )
            MetricTile(
              icon = Icons.Filled.Bedtime,
              title = "Sleep average",
              value = snapshot.averageSleepHours?.let { "${CycleAnalyticsEngine.format1(it)} hrs" } ?: "Optional",
              subtitle = "In More details",
              accent = MaterialTheme.colorScheme.tertiary,
              modifier = Modifier.weight(1f),
              onClick = onOpenTodayLog
            )
          }
        }
      }

      // Personal Insight Spotlight or Botanical Empty State
      item {
        val topInsight = snapshot.personalInsights.firstOrNull()
        if (topInsight != null) {
          BloomCard(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
            onClick = onOpenInsights
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Icon(
                Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
              )
              Text(
                text = "Personal Insight • ${topInsight.category}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
              )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = topInsight.title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = topInsight.statement,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurface
            )
          }
        } else {
          BloomCard {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
              Image(
                painter = painterResource(id = R.drawable.img_botanical_hero),
                contentDescription = "Botanical cycle wellness",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                  .size(72.dp)
                  .clip(RoundedCornerShape(16.dp))
              )
              Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Personal insights unlock as you log", style = MaterialTheme.typography.titleMedium)
                Text(
                  text = "Tap Log Today or tap any date in Calendar to record flow, cramps, moods, and symptoms. Every insight is computed locally from your own history.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          }
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
            text = "Predictions are estimated from your logs and are observational, not medical diagnosis.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }
  }
}

@Composable
private fun MetricTile(
  icon: ImageVector,
  title: String,
  value: String,
  subtitle: String,
  accent: Color,
  modifier: Modifier = Modifier,
  onClick: () -> Unit
) {
  BloomCard(
    modifier = modifier,
    onClick = onClick
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
      Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
    Spacer(modifier = Modifier.height(8.dp))
    Text(
      text = value,
      style = MaterialTheme.typography.titleLarge,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(modifier = Modifier.height(2.dp))
    Text(
      text = subtitle,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}
