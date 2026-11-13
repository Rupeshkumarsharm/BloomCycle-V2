package com.example.util

import java.util.Calendar
import java.util.Locale
import kotlinx.serialization.Serializable

/**
 * Pure-Kotlin ISO calendar date (YYYY-MM-DD) compatible with minSdk 24 without desugaring.
 * Uses Julian Day Number math for exact day differences, leap years, and calendar rendering.
 */
@Serializable
data class CycleDate(
  val year: Int,
  val month: Int, // 1..12
  val day: Int, // 1..31
) : Comparable<CycleDate> {

  init {
    require(month in 1..12) { "Invalid month: $month" }
    require(day in 1..daysInMonth(year, month)) { "Invalid day $day for $year-$month" }
  }

  fun toIsoString(): String =
    String.format(Locale.US, "%04d-%02d-%02d", year, month, day)

  fun toEpochDay(): Long {
    val a = (14 - month) / 12
    val y = year + 4800 - a
    val m = month + 12 * a - 3
    val jdn = day + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045
    return jdn.toLong() - 2440588L // Unix epoch 1970-01-01
  }

  fun plusDays(days: Int): CycleDate = fromEpochDay(toEpochDay() + days)

  fun minusDays(days: Int): CycleDate = plusDays(-days)

  fun daysUntil(other: CycleDate): Int = (other.toEpochDay() - toEpochDay()).toInt()

  /** 1 = Monday .. 7 = Sunday */
  fun dayOfWeekIso(): Int {
    // 1970-01-01 was Thursday (4)
    val mod = ((toEpochDay() + 3) % 7 + 7) % 7
    return mod.toInt() + 1
  }

  fun monthYearLabel(): String = "${monthFullName(month)} $year"

  fun shortLabel(): String = "${monthShortName(month)} $day"

  fun mediumLabel(): String = "${monthShortName(month)} $day, $year"

  fun ageOn(referenceDate: CycleDate): Int {
    var age = referenceDate.year - year
    if (referenceDate.month < month || (referenceDate.month == month && referenceDate.day < day)) {
      age--
    }
    return age.coerceAtLeast(0)
  }

  fun previousMonth(): CycleDate =
    if (month == 1) CycleDate(year - 1, 12, 1) else CycleDate(year, month - 1, 1)

  fun nextMonth(): CycleDate =
    if (month == 12) CycleDate(year + 1, 1, 1) else CycleDate(year, month + 1, 1)

  fun atDay(newDay: Int): CycleDate =
    CycleDate(year, month, newDay.coerceIn(1, daysInMonth(year, month)))

  override fun compareTo(other: CycleDate): Int =
    compareValuesBy(this, other, { it.year }, { it.month }, { it.day })

  companion object {
    fun today(): CycleDate {
      val cal = Calendar.getInstance()
      return CycleDate(
        year = cal.get(Calendar.YEAR),
        month = cal.get(Calendar.MONTH) + 1,
        day = cal.get(Calendar.DAY_OF_MONTH),
      )
    }

    fun parseOrNull(iso: String?): CycleDate? {
      if (iso.isNullOrBlank()) return null
      val trimmed = iso.trim()
      val parts = trimmed.split("-")
      if (parts.size != 3) return null
      val y = parts[0].toIntOrNull() ?: return null
      val m = parts[1].toIntOrNull() ?: return null
      val d = parts[2].toIntOrNull() ?: return null
      if (m !in 1..12) return null
      val maxD = daysInMonth(y, m)
      if (d !in 1..maxD) return null
      return CycleDate(y, m, d)
    }

    fun parse(iso: String): CycleDate =
      parseOrNull(iso) ?: throw IllegalArgumentException("Invalid ISO date: $iso")

    fun fromEpochDay(epochDay: Long): CycleDate {
      val jdn = epochDay + 2440588L
      val f = jdn + 1401 + (((4 * jdn + 274277) / 146097) * 3) / 4 - 38
      val e = 4 * f + 3
      val g = (e % 1461) / 4
      val h = 5 * g + 2
      val d = ((h % 153) / 5 + 1).toInt()
      val m = (((h / 153 + 2) % 12) + 1).toInt()
      val y = (e / 1461 - 4716 + (12 + 2 - m) / 12).toInt()
      return CycleDate(y, m, d)
    }

    fun isLeapYear(year: Int): Boolean =
      (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)

    fun daysInMonth(year: Int, month: Int): Int =
      when (month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        2 -> if (isLeapYear(year)) 29 else 28
        else -> 30
      }

    fun monthShortName(month: Int): String =
      when (month) {
        1 -> "Jan"
        2 -> "Feb"
        3 -> "Mar"
        4 -> "Apr"
        5 -> "May"
        6 -> "Jun"
        7 -> "Jul"
        8 -> "Aug"
        9 -> "Sep"
        10 -> "Oct"
        11 -> "Nov"
        12 -> "Dec"
        else -> ""
      }

    fun monthFullName(month: Int): String =
      when (month) {
        1 -> "January"
        2 -> "February"
        3 -> "March"
        4 -> "April"
        5 -> "May"
        6 -> "June"
        7 -> "July"
        8 -> "August"
        9 -> "September"
        10 -> "October"
        11 -> "November"
        12 -> "December"
        else -> ""
      }
  }
}
