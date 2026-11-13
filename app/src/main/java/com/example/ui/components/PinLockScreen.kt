package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun PinLockOverlay(
  userName: String?,
  errorMessage: String?,
  onPinSubmitted: (String) -> Unit,
  onClearError: () -> Unit
) {
  var pin by remember { mutableStateOf("") }

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
      .systemBarsPadding()
      .padding(24.dp),
    contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(20.dp),
      modifier = Modifier.fillMaxWidth()
    ) {
      Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(64.dp)
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = "App locked",
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(28.dp)
          )
        }
      }

      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
          text = if (!userName.isNullOrBlank()) "Welcome back, $userName" else "BloomCycle Locked",
          style = MaterialTheme.typography.headlineMedium,
          color = MaterialTheme.colorScheme.onBackground,
          textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = "Enter your 4-digit privacy PIN to unlock your local journal.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center
        )
      }

      Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        for (i in 0 until 4) {
          val filled = i < pin.length
          Surface(
            modifier = Modifier.size(18.dp),
            shape = CircleShape,
            color = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(
              1.5.dp,
              if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
          ) {}
        }
      }

      if (errorMessage != null) {
        Text(
          text = errorMessage,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
          textAlign = TextAlign.Center
        )
      } else {
        Spacer(modifier = Modifier.height(16.dp))
      }

      val keys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "DEL")
      )

      Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        keys.forEach { row ->
          Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            row.forEach { key ->
              when (key) {
                "" -> Spacer(modifier = Modifier.size(68.dp))
                "DEL" -> {
                  Surface(
                    modifier = Modifier
                      .size(68.dp)
                      .clip(CircleShape)
                      .clickable {
                        if (pin.isNotEmpty()) {
                          pin = pin.dropLast(1)
                          onClearError()
                        }
                      }
                      .testTag("pin_backspace"),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                  ) {
                    Box(contentAlignment = Alignment.Center) {
                      Icon(
                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "Delete digit",
                        tint = MaterialTheme.colorScheme.onSurface
                      )
                    }
                  }
                }
                else -> {
                  Surface(
                    modifier = Modifier
                      .size(68.dp)
                      .clip(CircleShape)
                      .clickable {
                        if (pin.length < 4) {
                          onClearError()
                          val next = pin + key
                          pin = next
                          if (next.length == 4) {
                            onPinSubmitted(next)
                            pin = ""
                          }
                        }
                      }
                      .testTag("pin_key_$key"),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                  ) {
                    Box(contentAlignment = Alignment.Center) {
                      Text(
                        text = key,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun PinSetupDialog(
  title: String = "Set Local Privacy PIN",
  onDismiss: () -> Unit,
  onPinConfirmed: (String) -> Unit
) {
  var firstPin by remember { mutableStateOf("") }
  var confirmPin by remember { mutableStateOf("") }
  var step by remember { mutableStateOf(1) }
  var error by remember { mutableStateOf<String?>(null) }

  val activePin = if (step == 1) firstPin else confirmPin

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = if (step == 1) title else "Confirm your 4-digit PIN",
        style = MaterialTheme.typography.headlineSmall
      )
    },
    text = {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Text(
          text = if (step == 1) "Choose a 4-digit PIN stored securely as a salted hash on your device." else "Re-enter the same 4 digits to confirm.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          for (i in 0 until 4) {
            val filled = i < activePin.length
            Surface(
              modifier = Modifier.size(16.dp),
              shape = CircleShape,
              color = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {}
          }
        }

        if (error != null) {
          Text(
            text = error!!,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
          )
        }

        val keys = listOf(
          listOf("1", "2", "3"),
          listOf("4", "5", "6"),
          listOf("7", "8", "9"),
          listOf("", "0", "DEL")
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          keys.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
              row.forEach { key ->
                when (key) {
                  "" -> Spacer(modifier = Modifier.size(54.dp))
                  "DEL" -> {
                    Surface(
                      modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .clickable {
                          error = null
                          if (step == 1 && firstPin.isNotEmpty()) firstPin = firstPin.dropLast(1)
                          else if (step == 2 && confirmPin.isNotEmpty()) confirmPin = confirmPin.dropLast(1)
                        },
                      shape = CircleShape,
                      color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                      Box(contentAlignment = Alignment.Center) {
                        Icon(
                          Icons.AutoMirrored.Filled.Backspace,
                          contentDescription = "Delete",
                          modifier = Modifier.size(18.dp)
                        )
                      }
                    }
                  }
                  else -> {
                    Surface(
                      modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .clickable {
                          error = null
                          if (step == 1 && firstPin.length < 4) {
                            firstPin += key
                            if (firstPin.length == 4) step = 2
                          } else if (step == 2 && confirmPin.length < 4) {
                            confirmPin += key
                          }
                        },
                      shape = CircleShape,
                      color = MaterialTheme.colorScheme.surface,
                      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                      Box(contentAlignment = Alignment.Center) {
                        Text(key, style = MaterialTheme.typography.titleMedium)
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (firstPin.length == 4 && confirmPin == firstPin) {
            onPinConfirmed(firstPin)
          } else {
            error = "PINs did not match. Please try again."
            firstPin = ""
            confirmPin = ""
            step = 1
          }
        },
        enabled = confirmPin.length == 4,
        modifier = Modifier.testTag("pin_setup_confirm")
      ) {
        Text("Save PIN")
      }
    },
    dismissButton = {
      OutlinedButton(onClick = onDismiss) {
        Text("Cancel")
      }
    }
  )
}
