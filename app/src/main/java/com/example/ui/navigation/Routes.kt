package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

@Serializable
object OnboardingRoute

@Serializable
object TodayRoute

@Serializable
object CalendarRoute

@Serializable
object LogRoute

@Serializable
object InsightsRoute

@Serializable
object YouRoute

enum class BottomNavDestination(
  val label: String,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
  val testTag: String
) {
  TODAY("Today", Icons.Filled.Home, Icons.Outlined.Home, "nav_today"),
  CALENDAR("Calendar", Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth, "nav_calendar"),
  LOG("Log", Icons.Filled.EditNote, Icons.Outlined.EditNote, "nav_log"),
  INSIGHTS("Insights", Icons.Filled.Analytics, Icons.Outlined.Analytics, "nav_insights"),
  YOU("You", Icons.Filled.Person, Icons.Outlined.Person, "nav_you")
}
