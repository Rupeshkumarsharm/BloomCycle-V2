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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.model.CustomOptionType
import com.example.domain.model.RelationshipStatus
import com.example.domain.model.UserProfile
import com.example.ui.components.BloomCard
import com.example.ui.components.CycleDatePickerDialog
import com.example.ui.components.PinSetupDialog
import com.example.ui.components.PrivacyBadgePill
import com.example.ui.viewmodel.BloomUiState
import com.example.util.CycleDate
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun YouSettingsScreen(
  uiState: BloomUiState,
  onSaveProfile: (UserProfile) -> Unit,
  onToggleFertileWindow: (Boolean) -> Unit,
  onSelectThemeMode: (String) -> Unit,
  onSetPin: (String) -> Unit,
  onDisablePin: () -> Unit,
  onLockNow: () -> Unit,
  onExportExcel: () -> Unit,
  onImportExcel: () -> Unit,
  onAddCustomOption: (CustomOptionType, String) -> Unit,
  onDeleteCustomOption: (Long) -> Unit,
  onClearAllData: () -> Unit
) {
  val profile = uiState.profile
  var editProfileDialog by remember { mutableStateOf(false) }
  var showPinDialog by remember { mutableStateOf(false) }
  var showResetDialog by remember { mutableStateOf(false) }
  var addCustomType by remember { mutableStateOf<CustomOptionType?>(null) }

  Box(
    modifier = Modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter
  ) {
    LazyColumn(
      modifier = Modifier
        .widthIn(max = 720.dp)
        .fillMaxSize(),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text("You & Privacy", style = MaterialTheme.typography.headlineMedium)
          PrivacyBadgePill()
        }
      }

      if (!uiState.backupMessage.isNullOrBlank()) {
        item {
          Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.secondaryContainer
          ) {
            Text(
              text = uiState.backupMessage,
              modifier = Modifier.fillMaxWidth().padding(14.dp),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSecondaryContainer
            )
          }
        }
      }

      if (!uiState.backupError.isNullOrBlank()) {
        item {
          Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.errorContainer
          ) {
            Text(
              text = uiState.backupError,
              modifier = Modifier.fillMaxWidth().padding(14.dp),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onErrorContainer
            )
          }
        }
      }

      // Profile Summary Card
      item {
        BloomCard {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
          ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text(
                text = profile?.name ?: "Local Profile",
                style = MaterialTheme.typography.headlineSmall
              )
              if (profile != null) {
                Text(
                  text = "Age ${profile.ageOn()} • Born ${profile.dob.mediumLabel()}",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.primary
                )
                Text(
                  text = "Relationship status: ${profile.relationshipDisplay()}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                  text = "Logged days: ${uiState.entries.size} • Cycles derived: ${uiState.analytics.totalCyclesCount}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
            OutlinedButton(
              onClick = { editProfileDialog = true },
              modifier = Modifier.testTag("edit_profile_btn")
            ) {
              Text("Edit")
            }
          }
        }
      }

      // Excel Export & Import / Restore
      item {
        BloomCard {
          Text("Excel (.xlsx) Backup & Restore", style = MaterialTheme.typography.titleMedium)
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "Export your entire history as a multi-sheet .xlsx file or restore from an existing BloomCycle backup without any cloud account.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Spacer(modifier = Modifier.height(12.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Button(
              onClick = onExportExcel,
              modifier = Modifier.weight(1f).testTag("export_excel_btn")
            ) {
              Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Export .xlsx")
            }
            OutlinedButton(
              onClick = onImportExcel,
              modifier = Modifier.weight(1f).testTag("import_excel_btn")
            ) {
              Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Import .xlsx")
            }
          }
        }
      }

      // Privacy PIN Lock
      item {
        BloomCard {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              modifier = Modifier.weight(1f)
            ) {
              Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
              Column {
                Text("Local PIN privacy lock", style = MaterialTheme.typography.titleMedium)
                Text(
                  text = if (uiState.preferences.pinEnabled) "Enabled — locks on launch & background" else "Off — tap to protect your journal",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
            Switch(
              checked = uiState.preferences.pinEnabled,
              onCheckedChange = { enabled ->
                if (enabled) showPinDialog = true else onDisablePin()
              },
              modifier = Modifier.testTag("pin_toggle_switch")
            )
          }

          if (uiState.preferences.pinEnabled) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              OutlinedButton(onClick = { showPinDialog = true }) {
                Text("Change PIN")
              }
              OutlinedButton(
                onClick = onLockNow,
                modifier = Modifier.testTag("lock_now_btn")
              ) {
                Text("Lock now")
              }
            }
          }
        }
      }

      // Preferences: Fertile Window & Theme
      item {
        BloomCard {
          Text("Preferences", style = MaterialTheme.typography.titleMedium)
          Spacer(modifier = Modifier.height(8.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Show estimated fertile window", style = MaterialTheme.typography.bodyMedium)
              Text(
                text = "Displays estimated fertile days & ovulation marker on calendar",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            Switch(
              checked = uiState.preferences.showFertileWindow,
              onCheckedChange = onToggleFertileWindow,
              modifier = Modifier.testTag("fertile_toggle_switch")
            )
          }

          Spacer(modifier = Modifier.height(12.dp))
          Text("Theme", style = MaterialTheme.typography.labelLarge)
          Spacer(modifier = Modifier.height(6.dp))
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("SYSTEM" to "System", "LIGHT" to "Warm Cream", "DARK" to "Evening Cocoa").forEach { (key, label) ->
              FilterChip(
                selected = uiState.preferences.darkThemePreference == key,
                onClick = { onSelectThemeMode(key) },
                label = { Text(label) }
              )
            }
          }
        }
      }

      // Custom Moods, Symptoms, and Tags Manager
      item {
        BloomCard {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("Custom moods, symptoms & tags", style = MaterialTheme.typography.titleMedium)
          }
          Spacer(modifier = Modifier.height(8.dp))
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { addCustomType = CustomOptionType.MOOD }) {
              Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Mood")
            }
            OutlinedButton(onClick = { addCustomType = CustomOptionType.SYMPTOM }) {
              Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Symptom")
            }
            OutlinedButton(onClick = { addCustomType = CustomOptionType.TAG }) {
              Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Tag")
            }
          }

          if (uiState.customOptions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              uiState.customOptions.forEach { opt ->
                InputChip(
                  selected = false,
                  onClick = { onDeleteCustomOption(opt.id) },
                  label = { Text("${opt.label} (${opt.type.name.lowercase()})") },
                  trailingIcon = {
                    Icon(Icons.Filled.Close, contentDescription = "Remove ${opt.label}", modifier = Modifier.size(16.dp))
                  }
                )
              }
            }
          } else {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = "No custom items added yet.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }

      // Privacy Transparency & Reset
      item {
        BloomCard {
          Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Filled.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text("Privacy-first architecture", style = MaterialTheme.typography.titleMedium)
              Text(
                text = "BloomCycle operates 100% offline with zero network permissions, zero cloud storage, and zero third-party analytics. Your data belongs exclusively to you.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
          Spacer(modifier = Modifier.height(12.dp))
          OutlinedButton(
            onClick = { showResetDialog = true },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth().testTag("clear_all_data_btn")
          ) {
            Icon(Icons.Filled.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Delete all local data")
          }
        }
      }
    }
  }

  if (editProfileDialog) {
    EditProfileDialog(
      initialProfile = profile,
      onDismiss = { editProfileDialog = false },
      onSave = {
        onSaveProfile(it)
        editProfileDialog = false
      }
    )
  }

  if (showPinDialog) {
    PinSetupDialog(
      onDismiss = { showPinDialog = false },
      onPinConfirmed = {
        onSetPin(it)
        showPinDialog = false
      }
    )
  }

  val customDialogType = addCustomType
  if (customDialogType != null) {
    var label by remember { mutableStateOf("") }
    AlertDialog(
      onDismissRequest = { addCustomType = null },
      title = { Text("Add custom ${customDialogType.name.lowercase()}") },
      text = {
        OutlinedTextField(
          value = label,
          onValueChange = { label = it },
          label = { Text("Label") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
      },
      confirmButton = {
        Button(
          onClick = {
            if (label.trim().isNotEmpty()) onAddCustomOption(customDialogType, label.trim())
            addCustomType = null
          },
          enabled = label.trim().isNotEmpty()
        ) { Text("Add") }
      },
      dismissButton = {
        OutlinedButton(onClick = { addCustomType = null }) { Text("Cancel") }
      }
    )
  }

  if (showResetDialog) {
    AlertDialog(
      onDismissRequest = { showResetDialog = false },
      title = { Text("Delete all local data?") },
      text = {
        Text("This permanently erases your profile, logged entries, custom tags, and PIN from this device. Export an Excel (.xlsx) backup first if you want to keep a copy.")
      },
      confirmButton = {
        Button(
          onClick = {
            showResetDialog = false
            onClearAllData()
          },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
          Text("Erase Everything")
        }
      },
      dismissButton = {
        OutlinedButton(onClick = { showResetDialog = false }) { Text("Cancel") }
      }
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditProfileDialog(
  initialProfile: UserProfile?,
  onDismiss: () -> Unit,
  onSave: (UserProfile) -> Unit
) {
  val today = CycleDate.today()
  var name by remember { mutableStateOf(initialProfile?.name.orEmpty()) }
  var dob by remember { mutableStateOf(initialProfile?.dob ?: CycleDate(1998, 1, 1)) }
  var relStatus by remember { mutableStateOf(initialProfile?.relationshipStatus ?: RelationshipStatus.PREFER_NOT_TO_SAY) }
  var customRel by remember { mutableStateOf(initialProfile?.customRelationshipLabel.orEmpty()) }
  var cycleLen by remember { mutableIntStateOf(initialProfile?.defaultCycleLength ?: 28) }
  var showDobPicker by remember { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Edit local profile") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("Name") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text("DOB: ${dob.mediumLabel()}", style = MaterialTheme.typography.bodyMedium)
            Text("Age: ${dob.ageOn(today)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
          }
          TextButton(onClick = { showDobPicker = true }) {
            Icon(Icons.Filled.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Change")
          }
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text("Default cycle: ${cycleLen}d", style = MaterialTheme.typography.bodyMedium)
          Row {
            OutlinedButton(onClick = { if (cycleLen > 20) cycleLen-- }) { Text("-") }
            Spacer(modifier = Modifier.width(6.dp))
            OutlinedButton(onClick = { if (cycleLen < 45) cycleLen++ }) { Text("+") }
          }
        }
        Text("Relationship status (optional)", style = MaterialTheme.typography.labelLarge)
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          RelationshipStatus.entries.forEach { status ->
            FilterChip(
              selected = relStatus == status,
              onClick = { relStatus = status },
              label = { Text(status.label) }
            )
          }
        }
        if (relStatus == RelationshipStatus.CUSTOM) {
          OutlinedTextField(
            value = customRel,
            onValueChange = { customRel = it },
            label = { Text("Custom status") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          onSave(
            UserProfile(
              id = initialProfile?.id ?: UUID.randomUUID().toString(),
              name = name.trim().ifEmpty { "Bloom User" },
              dob = dob,
              relationshipStatus = relStatus,
              customRelationshipLabel = customRel.trim(),
              defaultCycleLength = cycleLen,
              createdAtMillis = initialProfile?.createdAtMillis ?: System.currentTimeMillis()
            )
          )
        }
      ) { Text("Save") }
    },
    dismissButton = {
      OutlinedButton(onClick = onDismiss) { Text("Cancel") }
    }
  )

  if (showDobPicker) {
    CycleDatePickerDialog(
      title = "Date of Birth",
      initialDate = dob,
      minYear = 1945,
      maxYear = today.year - 10,
      maxDate = today.minusDays(365 * 10),
      onDismiss = { showDobPicker = false },
      onDateSelected = {
        dob = it
        showDobPicker = false
      }
    )
  }
}
