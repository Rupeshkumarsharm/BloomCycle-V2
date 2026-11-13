package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.PastCycleRange
import com.example.domain.model.RelationshipStatus
import com.example.domain.model.UserProfile
import com.example.ui.components.BloomCard
import com.example.ui.components.CycleDatePickerDialog
import com.example.ui.components.PinSetupDialog
import com.example.ui.components.PrivacyBadgePill
import com.example.util.CycleDate
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(
  onCompleteOnboarding: (UserProfile, List<PastCycleRange>, String?) -> Unit,
  onTriggerExcelRestore: () -> Unit
) {
  var step by remember { mutableIntStateOf(0) }
  var name by remember { mutableStateOf("") }
  var dob by remember { mutableStateOf(CycleDate(1999, 5, 15)) }
  var relationshipStatus by remember { mutableStateOf(RelationshipStatus.PREFER_NOT_TO_SAY) }
  var customRelationship by remember { mutableStateOf("") }
  var defaultCycleLen by remember { mutableIntStateOf(28) }
  val pastCycles = remember { mutableStateListOf<PastCycleRange>() }
  var configuredPin by remember { mutableStateOf<String?>(null) }

  var showDobPicker by remember { mutableStateOf(false) }
  var editingCycleStart by remember { mutableStateOf(false) }
  var editingCycleEnd by remember { mutableStateOf(false) }
  var draftCycleStart by remember { mutableStateOf(CycleDate.today().minusDays(28)) }
  var draftCycleEnd by remember { mutableStateOf(CycleDate.today().minusDays(24)) }
  var showPinSetup by remember { mutableStateOf(false) }

  val today = CycleDate.today()
  val calculatedAge = dob.ageOn(today)

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(
        Brush.verticalGradient(
          listOf(
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.surfaceContainer
          )
        )
      )
      .systemBarsPadding(),
    contentAlignment = Alignment.TopCenter
  ) {
    Column(
      modifier = Modifier
        .widthIn(max = 620.dp)
        .fillMaxSize()
        .padding(horizontal = 20.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.SpaceBetween
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          PrivacyBadgePill()
          Text(
            text = "Step ${step + 1} of 4",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        LinearProgressIndicator(
          progress = { (step + 1) / 4f },
          modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(50))
        )
      }

      AnimatedContent(
        targetState = step,
        modifier = Modifier
          .weight(1f)
          .verticalScroll(rememberScrollState())
          .padding(vertical = 16.dp),
        label = "onboarding_step"
      ) { currentStep ->
        when (currentStep) {
          0 -> {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
              Image(
                painter = painterResource(id = R.drawable.img_botanical_hero),
                contentDescription = "BloomCycle botanical illustration",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                  .fillMaxWidth()
                  .height(210.dp)
                  .clip(RoundedCornerShape(24.dp))
              )
              Text(
                text = "Understand your cycle, privately.",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground
              )
              Text(
                text = "BloomCycle transforms effortless daily check-ins into personal insights about your cycle rhythm, symptoms, mood, and lifestyle patterns.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              BloomCard {
                Row(
                  verticalAlignment = Alignment.Top,
                  horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                  Icon(
                    Icons.Filled.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                  )
                  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Your data belongs to you", style = MaterialTheme.typography.titleMedium)
                    Text(
                      text = "Everything stays locally on this device. No cloud accounts, no trackers, and no external servers. Export or restore anytime in Excel (.xlsx).",
                      style = MaterialTheme.typography.bodyMedium,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                }
              }
              OutlinedButton(
                onClick = onTriggerExcelRestore,
                modifier = Modifier.fillMaxWidth().testTag("onboarding_restore_excel")
              ) {
                Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Restore from existing Excel (.xlsx) backup")
              }
            }
          }

          1 -> {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
              Text(
                text = "Your local profile",
                style = MaterialTheme.typography.headlineMedium
              )
              Text(
                text = "Used only on your device for personalized greetings and age-aware cycle trend views.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )

              BloomCard {
                OutlinedTextField(
                  value = name,
                  onValueChange = { name = it },
                  label = { Text("Your name") },
                  placeholder = { Text("e.g., Maya") },
                  singleLine = true,
                  modifier = Modifier.fillMaxWidth().testTag("onboarding_name_input")
                )

                Spacer(modifier = Modifier.height(14.dp))
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Column {
                    Text("Date of birth", style = MaterialTheme.typography.titleSmall)
                    Text(
                      text = "${dob.mediumLabel()} • Age $calculatedAge",
                      style = MaterialTheme.typography.bodyMedium,
                      color = MaterialTheme.colorScheme.primary
                    )
                  }
                  OutlinedButton(
                    onClick = { showDobPicker = true },
                    modifier = Modifier.testTag("onboarding_dob_btn")
                  ) {
                    Icon(Icons.Filled.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Change")
                  }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text("Typical cycle length", style = MaterialTheme.typography.titleSmall)
                    Text(
                      text = "Used only until you log 2+ cycles",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { if (defaultCycleLen > 20) defaultCycleLen-- }) { Text("-") }
                    Text(
                      text = "${defaultCycleLen}d",
                      style = MaterialTheme.typography.titleMedium,
                      modifier = Modifier.padding(horizontal = 10.dp)
                    )
                    OutlinedButton(onClick = { if (defaultCycleLen < 45) defaultCycleLen++ }) { Text("+") }
                  }
                }
              }

              BloomCard {
                Text("Relationship status (optional)", style = MaterialTheme.typography.titleSmall)
                Text(
                  text = "Optional — never required for cycle tracking.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                  RelationshipStatus.entries.forEach { status ->
                    FilterChip(
                      selected = relationshipStatus == status,
                      onClick = { relationshipStatus = status },
                      label = { Text(status.label) }
                    )
                  }
                }
                if (relationshipStatus == RelationshipStatus.CUSTOM) {
                  Spacer(modifier = Modifier.height(8.dp))
                  OutlinedTextField(
                    value = customRelationship,
                    onValueChange = { customRelationship = it },
                    label = { Text("Custom status") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                  )
                }
              }
            }
          }

          2 -> {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
              Text(
                text = "Optional past cycles",
                style = MaterialTheme.typography.headlineMedium
              )
              Text(
                text = "Add any recent period dates you remember so your calendar predictions and insights work right away — or skip and start fresh today.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )

              BloomCard {
                Text("Add a past period", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                  OutlinedButton(
                    onClick = { editingCycleStart = true },
                    modifier = Modifier.weight(1f)
                  ) {
                    Text("Start: ${draftCycleStart.shortLabel()}")
                  }
                  OutlinedButton(
                    onClick = { editingCycleEnd = true },
                    modifier = Modifier.weight(1f)
                  ) {
                    Text("End: ${draftCycleEnd.shortLabel()}")
                  }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                  onClick = {
                    val start = minOf(draftCycleStart, draftCycleEnd)
                    val end = maxOf(draftCycleStart, draftCycleEnd)
                    pastCycles.add(PastCycleRange(start, end))
                    draftCycleStart = start.minusDays(defaultCycleLen)
                    draftCycleEnd = draftCycleStart.plusDays(4)
                  },
                  modifier = Modifier.fillMaxWidth().testTag("add_past_cycle_btn")
                ) {
                  Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Add period dates")
                }
              }

              if (pastCycles.isNotEmpty()) {
                Text("Added history (${pastCycles.size})", style = MaterialTheme.typography.titleSmall)
                pastCycles.forEachIndexed { idx, range ->
                  val dur = range.startDate.daysUntil(range.endDate) + 1
                  Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                  ) {
                    Row(
                      modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Column {
                        Text(
                          text = "${range.startDate.mediumLabel()} – ${range.endDate.shortLabel()}",
                          style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                          text = "$dur day(s)",
                          style = MaterialTheme.typography.bodySmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                      }
                      IconButton(onClick = { pastCycles.removeAt(idx) }) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove cycle")
                      }
                    }
                  }
                }
              }
            }
          }

          else -> {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
              Text(
                text = "Optional privacy PIN",
                style = MaterialTheme.typography.headlineMedium
              )
              Text(
                text = "Add a 4-digit local lock screen whenever BloomCycle opens, or leave it off and enable it anytime later in settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )

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
                      Text(
                        text = if (configuredPin != null) "4-digit PIN ready" else "No PIN set",
                        style = MaterialTheme.typography.titleMedium
                      )
                      Text(
                        text = if (configuredPin != null) "PIN will lock the app on launch" else "Tap Set PIN if you want extra privacy",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                      )
                    }
                  }
                  if (configuredPin != null) {
                    TextButton(onClick = { configuredPin = null }) { Text("Remove") }
                  } else {
                    Button(
                      onClick = { showPinSetup = true },
                      modifier = Modifier.testTag("onboarding_set_pin_btn")
                    ) {
                      Text("Set PIN")
                    }
                  }
                }
              }

              BloomCard(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)) {
                Text("Ready to begin", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                val greetingName = name.trim().ifEmpty { "Friend" }
                Text(
                  text = "Profile: $greetingName (Age $calculatedAge)\nPast periods added: ${pastCycles.size}\nStorage: 100% on-device",
                  style = MaterialTheme.typography.bodyMedium
                )
              }
            }
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        if (step > 0) {
          OutlinedButton(onClick = { step-- }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Back")
          }
        } else {
          Spacer(modifier = Modifier.width(8.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          if (step == 2 && pastCycles.isEmpty()) {
            TextButton(onClick = { step++ }) {
              Text("Skip")
            }
          }
          Button(
            onClick = {
              if (step < 3) {
                step++
              } else {
                val finalProfile = UserProfile(
                  id = UUID.randomUUID().toString(),
                  name = name.trim().ifEmpty { "Bloom User" },
                  dob = dob,
                  relationshipStatus = relationshipStatus,
                  customRelationshipLabel = customRelationship.trim(),
                  defaultCycleLength = defaultCycleLen
                )
                onCompleteOnboarding(finalProfile, pastCycles.toList(), configuredPin)
              }
            },
            modifier = Modifier.testTag("onboarding_next_btn")
          ) {
            Text(if (step < 3) "Continue" else "Start BloomCycle")
            if (step < 3) {
              Spacer(modifier = Modifier.width(6.dp))
              Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
            }
          }
        }
      }
    }
  }

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

  if (editingCycleStart) {
    CycleDatePickerDialog(
      title = "Period Start Date",
      initialDate = draftCycleStart,
      minYear = today.year - 5,
      maxYear = today.year,
      maxDate = today,
      onDismiss = { editingCycleStart = false },
      onDateSelected = {
        draftCycleStart = it
        if (draftCycleEnd < it) draftCycleEnd = minOf(today, it.plusDays(4))
        editingCycleStart = false
      }
    )
  }

  if (editingCycleEnd) {
    CycleDatePickerDialog(
      title = "Period End Date",
      initialDate = draftCycleEnd,
      minYear = today.year - 5,
      maxYear = today.year,
      maxDate = today,
      onDismiss = { editingCycleEnd = false },
      onDateSelected = {
        draftCycleEnd = it
        editingCycleEnd = false
      }
    )
  }

  if (showPinSetup) {
    PinSetupDialog(
      onDismiss = { showPinSetup = false },
      onPinConfirmed = {
        configuredPin = it
        showPinSetup = false
      }
    )
  }
}
