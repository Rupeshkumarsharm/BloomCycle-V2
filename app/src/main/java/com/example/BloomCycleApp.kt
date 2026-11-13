package com.example

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.ImportMode
import com.example.ui.components.PinLockOverlay
import com.example.ui.navigation.BottomNavDestination
import com.example.ui.navigation.CalendarRoute
import com.example.ui.navigation.InsightsRoute
import com.example.ui.navigation.LogRoute
import com.example.ui.navigation.TodayRoute
import com.example.ui.navigation.YouRoute
import com.example.ui.screens.CalendarScreen
import com.example.ui.screens.InsightsScreen
import com.example.ui.screens.LogScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.TodayDashboardScreen
import com.example.ui.screens.YouSettingsScreen
import com.example.ui.theme.BloomCycleTheme
import com.example.ui.viewmodel.BloomUiState
import com.example.ui.viewmodel.MainViewModel
import com.example.util.CycleDate

private const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

object ScreenSecurityPolicy {
  fun isSensitiveScreen(uiState: BloomUiState, onboardingStep: Int): Boolean {
    if (!uiState.isLoaded) return true
    if (uiState.onboardingCompleted) return true
    if (uiState.pendingImportPreview != null) return true
    return onboardingStep >= 1
  }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
  is Activity -> this
  is ContextWrapper -> baseContext.findActivity()
  else -> null
}

@Composable
fun BloomCycleApp(viewModel: MainViewModel) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val context = LocalContext.current
  var onboardingStep by remember { mutableIntStateOf(0) }

  val isSensitive = ScreenSecurityPolicy.isSensitiveScreen(uiState, onboardingStep)
  SideEffect {
    val window = context.findActivity()?.window ?: return@SideEffect
    if (isSensitive) {
      window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    } else {
      window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
  }

  val exportLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument(XLSX_MIME)
  ) { uri ->
    if (uri != null) {
      viewModel.exportToExcelUri(context, uri)
    } else {
      viewModel.onSafPickerCancelled()
    }
  }

  val importLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument()
  ) { uri ->
    if (uri != null) {
      viewModel.previewExcelImportUri(context, uri)
    } else {
      viewModel.onSafPickerCancelled()
    }
  }

  BloomCycleTheme(darkThemePreference = uiState.preferences.darkThemePreference) {
    if (!uiState.isLoaded) {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
      }
      return@BloomCycleTheme
    }

    if (uiState.isPinLocked && uiState.onboardingCompleted) {
      PinLockOverlay(
        userName = uiState.profile?.name,
        errorMessage = uiState.pinError,
        lockoutRemainingSeconds = uiState.pinLockoutRemainingSeconds,
        isVerifyingPin = uiState.isVerifyingPin,
        hasPendingBackupAction = uiState.hasPendingBackupActionAfterUnlock,
        onPinSubmitted = viewModel::unlockWithPin,
        onClearError = viewModel::clearPinError
      )
      return@BloomCycleTheme
    }

    if (!uiState.onboardingCompleted) {
      OnboardingScreen(
        onCompleteOnboarding = viewModel::completeOnboarding,
        onTriggerExcelRestore = {
          viewModel.onLaunchingSafPicker()
          importLauncher.launch(arrayOf(XLSX_MIME, "application/octet-stream"))
        },
        onStepChanged = { onboardingStep = it }
      )
    } else {
      MainAuthenticatedShell(
        uiState = uiState,
        viewModel = viewModel,
        onLaunchExport = {
          val filename = "BloomCycle_Backup_${CycleDate.today().toIsoString()}.xlsx"
          viewModel.onLaunchingSafPicker()
          exportLauncher.launch(filename)
        },
        onLaunchImport = {
          viewModel.onLaunchingSafPicker()
          importLauncher.launch(arrayOf(XLSX_MIME, "application/octet-stream"))
        }
      )
    }

    // Excel Import Preview & Mode Selection Dialog (available both in Onboarding and Settings)
    val preview = uiState.pendingImportPreview
    if (preview != null) {
      AlertDialog(
        onDismissRequest = viewModel::dismissImportPreview,
        title = { Text("Restore Excel Backup") },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
              text = "Found ${preview.entries.size} daily log(s)" +
                (preview.earliestDate?.let { e ->
                  preview.latestDate?.let { l -> " (${e.shortLabel()} – ${l.shortLabel()})" }
                } ?: "") + ".",
              style = MaterialTheme.typography.bodyMedium
            )
            if (preview.profile != null) {
              Text(
                text = "Profile included: ${preview.profile.name}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
              )
            }
            Text(
              text = "Choose Merge to keep existing entries and update matching dates, or Replace to restore strictly from this file.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        },
        confirmButton = {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
              onClick = { viewModel.confirmImport(ImportMode.REPLACE) },
              modifier = Modifier.testTag("import_replace_btn")
            ) {
              Text("Replace")
            }
            Button(
              onClick = { viewModel.confirmImport(ImportMode.MERGE) },
              modifier = Modifier.testTag("import_merge_btn")
            ) {
              Text("Merge")
            }
          }
        },
        dismissButton = {
          TextButton(onClick = viewModel::dismissImportPreview) {
            Text("Cancel")
          }
        }
      )
    }
  }
}

@Composable
private fun MainAuthenticatedShell(
  uiState: BloomUiState,
  viewModel: MainViewModel,
  onLaunchExport: () -> Unit,
  onLaunchImport: () -> Unit
) {
  val navController = rememberNavController()
  val backStackEntry by navController.currentBackStackEntryAsState()
  val currentDest = backStackEntry?.destination

  fun navigateTab(dest: BottomNavDestination) {
    val routeObj: Any = when (dest) {
      BottomNavDestination.TODAY -> TodayRoute
      BottomNavDestination.CALENDAR -> CalendarRoute
      BottomNavDestination.LOG -> LogRoute
      BottomNavDestination.INSIGHTS -> InsightsRoute
      BottomNavDestination.YOU -> YouRoute
    }
    navController.navigate(routeObj) {
      popUpTo(navController.graph.findStartDestination().id) { saveState = true }
      launchSingleTop = true
      restoreState = true
    }
  }

  fun isSelected(dest: BottomNavDestination): Boolean = when (dest) {
    BottomNavDestination.TODAY -> currentDest?.hasRoute<TodayRoute>() == true
    BottomNavDestination.CALENDAR -> currentDest?.hasRoute<CalendarRoute>() == true
    BottomNavDestination.LOG -> currentDest?.hasRoute<LogRoute>() == true
    BottomNavDestination.INSIGHTS -> currentDest?.hasRoute<InsightsRoute>() == true
    BottomNavDestination.YOU -> currentDest?.hasRoute<YouRoute>() == true
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useRail = maxWidth >= 680.dp

    Scaffold(
      containerColor = MaterialTheme.colorScheme.background,
      contentWindowInsets = WindowInsets.safeDrawing,
      bottomBar = {
        if (!useRail) {
          NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            windowInsets = WindowInsets.navigationBars
          ) {
            BottomNavDestination.entries.forEach { dest ->
              val selected = isSelected(dest)
              NavigationBarItem(
                selected = selected,
                onClick = { navigateTab(dest) },
                icon = {
                  Icon(
                    imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                    contentDescription = dest.label
                  )
                },
                label = { Text(dest.label) },
                modifier = Modifier.testTag(dest.testTag)
              )
            }
          }
        }
      }
    ) { innerPadding ->
      Row(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding)
      ) {
        if (useRail) {
          NavigationRail(
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxHeight().statusBarsPadding()
          ) {
            BottomNavDestination.entries.forEach { dest ->
              val selected = isSelected(dest)
              NavigationRailItem(
                selected = selected,
                onClick = { navigateTab(dest) },
                icon = {
                  Icon(
                    imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                    contentDescription = dest.label
                  )
                },
                label = { Text(dest.label) },
                modifier = Modifier.testTag(dest.testTag)
              )
            }
          }
        }

        NavHost(
          navController = navController,
          startDestination = TodayRoute,
          modifier = Modifier.weight(1f).fillMaxHeight()
        ) {
          composable<TodayRoute> {
            TodayDashboardScreen(
              uiState = uiState,
              onQuickTogglePeriodToday = viewModel::quickTogglePeriodToday,
              onOpenTodayLog = {
                viewModel.selectDate(CycleDate.today())
                navigateTab(BottomNavDestination.LOG)
              },
              onOpenCalendar = { navigateTab(BottomNavDestination.CALENDAR) },
              onOpenInsights = { navigateTab(BottomNavDestination.INSIGHTS) }
            )
          }
          composable<CalendarRoute> {
            CalendarScreen(
              uiState = uiState,
              onSelectDate = viewModel::selectDate,
              onEditSelectedDay = { date ->
                viewModel.selectDate(date)
                navigateTab(BottomNavDestination.LOG)
              }
            )
          }
          composable<LogRoute> {
            LogScreen(
              uiState = uiState,
              onSelectDate = viewModel::selectDate,
              onSaveEntry = viewModel::saveDailyEntry,
              onAddCustomOption = viewModel::addCustomOption
            )
          }
          composable<InsightsRoute> {
            InsightsScreen(
              uiState = uiState,
              onSelectWindow = viewModel::setAnalyticsWindow
            )
          }
          composable<YouRoute> {
            YouSettingsScreen(
              uiState = uiState,
              onSaveProfile = viewModel::updateProfile,
              onToggleFertileWindow = viewModel::setShowFertileWindow,
              onSelectThemeMode = viewModel::setDarkThemePreference,
              onSetPin = viewModel::setPin,
              onDisablePin = viewModel::disablePin,
              onLockNow = { viewModel.lockAppNow(fromLifecycleStop = false) },
              onExportExcel = onLaunchExport,
              onImportExcel = onLaunchImport,
              onAddCustomOption = viewModel::addCustomOption,
              onDeleteCustomOption = viewModel::deleteCustomOption,
              onClearAllData = viewModel::clearAllLocalData
            )
          }
        }
      }
    }
  }
}
