package com.example

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextRange
import com.example.domain.model.DailyEntry
import com.example.ui.components.DailyLogEditor
import com.example.ui.theme.BloomCycleTheme
import com.example.util.CycleDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DailyLogEditorTest {

  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun `rapid typing debounces saves and ignores Room emissions over active draft`() {
    val date = CycleDate(2026, 10, 12)
    var existingEntry by mutableStateOf<DailyEntry?>(null)
    val saved = mutableListOf<DailyEntry>()

    composeRule.mainClock.autoAdvance = false
    composeRule.setContent {
      BloomCycleTheme {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
          DailyLogEditor(
            date = date,
            existingEntry = existingEntry,
            periodDayNumber = null,
            customOptions = emptyList(),
            onSaveEntry = { saved.add(it) },
            onAddCustomOption = { _, _ -> }
          )
        }
      }
    }
    composeRule.mainClock.advanceTimeBy(50L)

    val notesNode = composeRule.onNodeWithTag("daily_notes_input")
    notesNode.performScrollTo().performClick()
    notesNode.performTextInput("Quick")
    composeRule.mainClock.advanceTimeBy(150L)

    // Still within 400ms debounce window -> no save yet
    assertEquals(0, saved.size)

    notesNode.performTextInput(" draft")
    composeRule.mainClock.advanceTimeBy(100L)

    // Simulate a lagging Room emission with older text and a new updatedAtMillis
    existingEntry = DailyEntry(
      date = date,
      notes = "Quick",
      updatedAtMillis = 888_888L
    )
    composeRule.mainClock.advanceTimeBy(100L)

    // Active draft and cursor at end must be completely preserved
    notesNode.assertTextContains("Quick draft")
    notesNode.assert(
      SemanticsMatcher.expectValue(
        SemanticsProperties.TextSelectionRange,
        TextRange(11, 11)
      )
    )
    assertEquals(0, saved.size)

    // Advance past 400ms from last keystroke -> debounced save commits
    composeRule.mainClock.advanceTimeBy(450L)
    composeRule.waitForIdle()
    assertEquals(1, saved.size)
    assertEquals("Quick draft", saved.single().notes)
  }

  @Test
  fun `switching dates and leaving editor flush pending text writes for previous date`() {
    val day1 = CycleDate(2026, 10, 12)
    val day2 = CycleDate(2026, 10, 13)
    var selectedDate by mutableStateOf(day1)
    var showEditor by mutableStateOf(true)
    val saved = mutableListOf<DailyEntry>()

    composeRule.mainClock.autoAdvance = false
    composeRule.setContent {
      BloomCycleTheme {
        if (showEditor) {
          Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            DailyLogEditor(
              date = selectedDate,
              existingEntry = null,
              periodDayNumber = null,
              customOptions = emptyList(),
              onSaveEntry = { saved.add(it) },
              onAddCustomOption = { _, _ -> }
            )
          }
        }
      }
    }
    composeRule.mainClock.advanceTimeBy(50L)

    val notesNode = composeRule.onNodeWithTag("daily_notes_input")
    notesNode.performScrollTo().performClick()
    notesNode.performTextInput("Day 1 unsaved")
    composeRule.mainClock.advanceTimeBy(100L)
    assertEquals(0, saved.size)

    // Switch to Day 2 before debounce fires -> Day 1 pending draft must flush immediately
    selectedDate = day2
    composeRule.mainClock.advanceTimeBy(50L)
    composeRule.waitForIdle()
    assertTrue(saved.any { it.date == day1 && it.notes == "Day 1 unsaved" })

    // Type on Day 2 and leave editor immediately before debounce fires -> Day 2 must flush
    composeRule.onNodeWithTag("daily_notes_input").performScrollTo().performClick()
    composeRule.onNodeWithTag("daily_notes_input").performTextInput("Day 2 unsaved")
    composeRule.mainClock.advanceTimeBy(100L)
    assertEquals(1, saved.size)

    showEditor = false
    composeRule.mainClock.advanceTimeBy(50L)
    composeRule.waitForIdle()
    assertTrue(saved.any { it.date == day2 && it.notes == "Day 2 unsaved" })
  }

  @Test
  fun `exerciseMinutes validates digits and range without persisting invalid durations`() {
    val date = CycleDate(2026, 10, 14)
    val saved = mutableListOf<DailyEntry>()

    composeRule.mainClock.autoAdvance = false
    composeRule.setContent {
      BloomCycleTheme {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
          DailyLogEditor(
            date = date,
            existingEntry = null,
            periodDayNumber = null,
            customOptions = emptyList(),
            onSaveEntry = { saved.add(it) },
            onAddCustomOption = { _, _ -> }
          )
        }
      }
    }
    composeRule.mainClock.advanceTimeBy(50L)

    composeRule.onNodeWithTag("more_details_toggle").performScrollTo().performClick()
    composeRule.mainClock.advanceTimeBy(500L)

    val exerciseInput = composeRule.onNodeWithTag("exercise_minutes_input")
    exerciseInput.performScrollTo().performClick()

    // Over-range value (> 1440) must display validation message and NOT trigger saving 2000
    exerciseInput.performTextInput("2000")
    composeRule.mainClock.advanceTimeBy(500L)
    composeRule.onNodeWithText("Enter 0 to 1440 minutes (24 hours max)").assertIsDisplayed()
    assertTrue(saved.none { it.exerciseMinutes == 2000 })

    // Valid numeric value clears error and saves cleanly after debounce; non-digits are rejected
    exerciseInput.performTextReplacement("45")
    exerciseInput.performTextInput("abc")
    composeRule.mainClock.advanceTimeBy(500L)
    composeRule.waitForIdle()

    exerciseInput.assertTextContains("45")
    assertEquals(45, saved.last().exerciseMinutes)
  }
}
