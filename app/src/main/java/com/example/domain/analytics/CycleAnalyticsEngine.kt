package com.example.domain.analytics

import com.example.domain.model.ActivityLevel
import com.example.domain.model.AgeTrendBucket
import com.example.domain.model.AnalyticsSnapshot
import com.example.domain.model.AnalyticsWindow
import com.example.domain.model.CrossFactorInsight
import com.example.domain.model.CycleAnomaly
import com.example.domain.model.CyclePhase
import com.example.domain.model.CyclePrediction
import com.example.domain.model.DailyEntry
import com.example.domain.model.DefaultCatalog
import com.example.domain.model.DerivedCycle
import com.example.domain.model.MoodStat
import com.example.domain.model.MoodSwingLevel
import com.example.domain.model.PersonalInsightCard
import com.example.domain.model.SymptomFrequencyStat
import com.example.domain.model.UserProfile
import com.example.util.CycleDate
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

object CycleAnalyticsEngine {

  private data class MutablePeriodSpan(
    val startDate: CycleDate,
    var endDate: CycleDate,
    val periodEntries: MutableList<DailyEntry> = mutableListOf()
  )

  /**
   * Derives menstrual cycles chronologically from daily entries.
   * Period days separated by at most 1 unlogged/non-period day (unless explicitly overridden)
   * are grouped into a single menstrual episode.
   */
  fun deriveCycles(
    entries: List<DailyEntry>,
    profile: UserProfile?
  ): List<DerivedCycle> {
    val sorted = entries.sortedBy { it.date }
    val periodDays = sorted.filter { it.isPeriod }
    if (periodDays.isEmpty()) return emptyList()

    val spans = mutableListOf<MutablePeriodSpan>()
    var currentSpan: MutablePeriodSpan? = null

    for (entry in periodDays) {
      val active = currentSpan
      if (active == null) {
        currentSpan = MutablePeriodSpan(entry.date, entry.date, mutableListOf(entry))
      } else {
        val gap = active.endDate.daysUntil(entry.date)
        val explicitSplit = entry.isPeriodStartOverride || active.periodEntries.last().isPeriodEndOverride
        if (!explicitSplit && gap in 1..2) {
          active.endDate = entry.date
          active.periodEntries.add(entry)
        } else {
          spans.add(active)
          currentSpan = MutablePeriodSpan(entry.date, entry.date, mutableListOf(entry))
        }
      }
    }
    currentSpan?.let { spans.add(it) }

    val dob = profile?.dob
    return spans.mapIndexed { index, span ->
      val nextStart = spans.getOrNull(index + 1)?.startDate
      val cycleLen = nextStart?.let { span.startDate.daysUntil(it) }?.takeIf { it in 10..180 }
      val windowEnd = nextStart?.minusDays(1) ?: span.endDate.plusDays(35)
      val cycleEntries = sorted.filter { it.date >= span.startDate && it.date <= windowEnd }
      val crampEntries = cycleEntries.filter { it.crampScore > 0 }
      val avgCramp = if (crampEntries.isNotEmpty()) {
        crampEntries.map { it.crampScore }.average().toFloat()
      } else 0f
      val maxCramp = cycleEntries.maxOfOrNull { it.crampScore } ?: 0
      val symptomTotal = cycleEntries.sumOf { it.symptoms.size }
      val symptomCounts = cycleEntries
        .flatMap { it.symptoms }
        .groupingBy { it }
        .eachCount()
      val moodCounts = cycleEntries
        .flatMap { it.moods }
        .groupingBy { it }
        .eachCount()
      val dominantMood = moodCounts
        .maxByOrNull { it.value }
        ?.key

      DerivedCycle(
        cycleIndex = index + 1,
        startDate = span.startDate,
        endDate = span.endDate,
        periodDurationDays = (span.startDate.daysUntil(span.endDate) + 1).coerceAtLeast(1),
        cycleLengthDays = cycleLen,
        averageCrampScore = avgCramp,
        maxCrampScore = maxCramp,
        totalSymptomLogs = symptomTotal,
        dominantMood = dominantMood,
        userAgeAtStart = dob?.ageOn(span.startDate) ?: 25,
        associatedSymptoms = symptomCounts.keys.sorted(),
        symptomCounts = symptomCounts,
        moodCounts = moodCounts
      )
    }
  }

  fun buildAnalyticsSnapshot(
    profile: UserProfile?,
    entries: List<DailyEntry>,
    showFertileWindow: Boolean,
    today: CycleDate = CycleDate.today(),
    window: AnalyticsWindow = AnalyticsWindow.ALL
  ): AnalyticsSnapshot {
    val sortedEntries = entries.sortedBy { it.date }
    val allCycles = deriveCycles(sortedEntries, profile)
    if (allCycles.isEmpty() && sortedEntries.isEmpty()) {
      return AnalyticsSnapshot.Empty
    }

    val windowedCycles = when (val limit = window.maxCycles) {
      null -> allCycles
      else -> allCycles.takeLast(limit)
    }

    val earliestWindowDate = windowedCycles.firstOrNull()?.startDate
    val windowedEntries = if (earliestWindowDate != null) {
      sortedEntries.filter { it.date >= earliestWindowDate }
    } else {
      sortedEntries
    }

    val completedLengths = windowedCycles.mapNotNull { it.cycleLengthDays }
    val periodDurations = windowedCycles.map { it.periodDurationDays }

    val avgCycleLen = completedLengths.takeIf { it.isNotEmpty() }?.average()?.toFloat()
    val medianCycleLen = completedLengths.medianOrNull()
    val minCycleLen = completedLengths.minOrNull()
    val maxCycleLen = completedLengths.maxOrNull()
    val variabilityStd = completedLengths.stdDevOrNull()

    val avgPeriodDur = periodDurations.takeIf { it.isNotEmpty() }?.average()?.toFloat()
    val minPeriodDur = periodDurations.minOrNull()
    val maxPeriodDur = periodDurations.maxOrNull()

    val variabilityNote = buildVariabilityComparison(allCycles.mapNotNull { it.cycleLengthDays })

    // Prediction from full history (up to last 6 completed cycles weighted)
    val allCompleted = allCycles.mapNotNull { it.cycleLengthDays }
    val latestCycle = allCycles.lastOrNull()
    val fallbackLen = profile?.defaultCycleLength?.coerceIn(20, 45) ?: 28
    val prediction = buildPrediction(
      latestCycle = latestCycle,
      completedLengths = allCompleted,
      avgPeriodDuration = avgPeriodDur?.roundToInt()?.coerceIn(2, 10) ?: 5,
      fallbackCycleLength = fallbackLen,
      showFertileWindow = showFertileWindow,
      today = today
    )

    val currentCycleDay = latestCycle?.let {
      val diff = it.startDate.daysUntil(today) + 1
      if (diff in 1..120) diff else null
    }
    val todayEntry = sortedEntries.firstOrNull { it.date == today }
    val currentPeriodDay = if (todayEntry?.isPeriod == true && latestCycle != null) {
      (latestCycle.startDate.daysUntil(today) + 1).coerceAtLeast(1)
    } else {
      null
    }

    val currentPhase = determineCyclePhase(
      today = today,
      todayEntry = todayEntry,
      latestCycle = latestCycle,
      prediction = prediction,
      showFertileWindow = showFertileWindow
    )

    // Recent cramps (last 30 days or current/latest cycle)
    val thirtyDaysAgo = today.minusDays(30)
    val recentEntries = sortedEntries.filter { it.date >= thirtyDaysAgo }
    val recentCrampScores = recentEntries.filter { it.crampScore > 0 || it.isPeriod }.map { it.crampScore }
    val recentCrampAvg = if (recentCrampScores.isNotEmpty()) recentCrampScores.average().toFloat() else null

    val allMoodCounts = windowedEntries.flatMap { it.moods }.groupingBy { it }.eachCount()
    val totalMoodSelections = allMoodCounts.values.sum().coerceAtLeast(1)
    val topMood = allMoodCounts.maxByOrNull { it.value }?.key
    val moodDistribution = allMoodCounts.entries
      .sortedByDescending { it.value }
      .map { (mood, count) ->
        MoodStat(mood = mood, count = count, percentage = (count * 100f) / totalMoodSelections)
      }

    val entriesWithMoodData = windowedEntries.filter { it.moods.isNotEmpty() || it.moodSwingLevel != MoodSwingLevel.NONE }
    val swingCount = entriesWithMoodData.count { it.moodSwingLevel != MoodSwingLevel.NONE }
    val moodSwingRate = if (entriesWithMoodData.isNotEmpty()) {
      (swingCount * 100f) / entriesWithMoodData.size
    } else 0f

    val sleepEntries = windowedEntries.mapNotNull { it.sleepDurationHours }
    val avgSleepHours = if (sleepEntries.isNotEmpty()) sleepEntries.average().toFloat() else null

    // Cramp intensity by cycle day (Day 1..Day 28)
    val crampByDay = computeCrampByCycleDay(windowedCycles, windowedEntries)

    // Symptom analytics
    val symptomStats = computeSymptomStats(windowedCycles, windowedEntries)

    // Energy by cycle phase
    val energyByPhase = computeEnergyByPhase(allCycles, windowedEntries, fallbackLen, showFertileWindow)

    // Cross-factor correlations (non-causal)
    val crossFactors = computeCrossFactorInsights(windowedEntries)

    // Age-based trends
    val ageTrends = computeAgeTrends(allCycles, sortedEntries)

    // Anomaly detection
    val anomalies = detectAnomalies(allCycles, sortedEntries, today)

    // Personal Insights Engine
    val insights = synthesizePersonalInsights(
      windowedCycles = windowedCycles,
      avgCycleLen = avgCycleLen,
      variabilityNote = variabilityNote,
      crampByDay = crampByDay,
      symptomStats = symptomStats,
      moodSwingRate = moodSwingRate,
      energyByPhase = energyByPhase,
      crossFactors = crossFactors,
      entries = windowedEntries,
      allCycles = allCycles
    )

    return AnalyticsSnapshot(
      currentCycleDay = currentCycleDay,
      currentPeriodDay = currentPeriodDay,
      currentPhase = currentPhase,
      completedCyclesCount = allCompleted.size,
      totalCyclesCount = allCycles.size,
      averageCycleLength = avgCycleLen,
      medianCycleLength = medianCycleLen,
      shortestCycleLength = minCycleLen,
      longestCycleLength = maxCycleLen,
      cycleVariabilityDays = variabilityStd,
      variabilityTrendNote = variabilityNote,
      averagePeriodDuration = avgPeriodDur,
      shortestPeriodDuration = minPeriodDur,
      longestPeriodDuration = maxPeriodDur,
      recentCrampAverage = recentCrampAvg,
      topLoggedMood = topMood,
      averageSleepHours = avgSleepHours,
      prediction = prediction,
      cycles = windowedCycles,
      crampByCycleDay = crampByDay,
      symptomFrequencies = symptomStats,
      moodDistribution = moodDistribution,
      moodSwingRatePercent = moodSwingRate,
      energyByPhase = energyByPhase,
      crossFactorInsights = crossFactors,
      ageTrends = ageTrends,
      anomalies = anomalies,
      personalInsights = insights
    )
  }

  private fun buildPrediction(
    latestCycle: DerivedCycle?,
    completedLengths: List<Int>,
    avgPeriodDuration: Int,
    fallbackCycleLength: Int,
    showFertileWindow: Boolean,
    today: CycleDate
  ): CyclePrediction? {
    val anchor = latestCycle ?: return null

    val estimatedCycleLen: Int = if (completedLengths.isEmpty()) {
      fallbackCycleLength
    } else {
      val recent = completedLengths.takeLast(6)
      var weightSum = 0.0
      var weightedTotal = 0.0
      recent.forEachIndexed { idx, len ->
        val w = (idx + 1).toDouble()
        weightedTotal += len * w
        weightSum += w
      }
      (weightedTotal / weightSum).roundToInt().coerceIn(18, 60)
    }

    val std = completedLengths.stdDevOrNull() ?: 2f
    val margin = when {
      completedLengths.size < 2 -> 3
      std < 1.5f -> 1
      std < 3.5f -> 2
      else -> 3
    }

    var nextStart = anchor.startDate.plusDays(estimatedCycleLen)
    // If predicted date is already well in the past, project forward by estimatedCycleLen increments
    while (nextStart.daysUntil(today) > 14) {
      nextStart = nextStart.plusDays(estimatedCycleLen)
    }

    val windowStart = nextStart.minusDays(margin)
    val windowEnd = nextStart.plusDays(margin)
    val estPeriodEnd = nextStart.plusDays((avgPeriodDuration - 1).coerceAtLeast(1))

    val ovulationDay = if (showFertileWindow) nextStart.minusDays(14) else null
    val fertileStart = ovulationDay?.minusDays(5)
    val fertileEnd = ovulationDay?.plusDays(1)

    val confidence = when {
      completedLengths.size >= 4 -> "Estimated from ${completedLengths.size} logged cycles (±$margin days)"
      completedLengths.size in 1..3 -> "Estimated from ${completedLengths.size} completed cycle(s) — improves with more data"
      else -> "Estimated using your ${fallbackCycleLength}-day baseline until more cycles are logged"
    }

    return CyclePrediction(
      nextPeriodStart = nextStart,
      nextPeriodWindowStart = windowStart,
      nextPeriodWindowEnd = windowEnd,
      estimatedPeriodEnd = estPeriodEnd,
      estimatedOvulationDay = ovulationDay,
      fertileWindowStart = fertileStart,
      fertileWindowEnd = fertileEnd,
      confidenceNote = confidence,
      daysUntilNextPeriod = today.daysUntil(nextStart)
    )
  }

  private fun determineCyclePhase(
    today: CycleDate,
    todayEntry: DailyEntry?,
    latestCycle: DerivedCycle?,
    prediction: CyclePrediction?,
    showFertileWindow: Boolean
  ): CyclePhase {
    if (todayEntry?.isPeriod == true) return CyclePhase.MENSTRUAL
    if (latestCycle == null) return CyclePhase.UNKNOWN
    if (showFertileWindow && prediction?.fertileWindowStart != null && prediction.fertileWindowEnd != null) {
      if (today >= prediction.fertileWindowStart && today <= prediction.fertileWindowEnd) {
        return CyclePhase.FERTILE_WINDOW
      }
      if (today > prediction.fertileWindowEnd) {
        return CyclePhase.LUTEAL
      }
    }
    val dayInCycle = latestCycle.startDate.daysUntil(today) + 1
    return when {
      dayInCycle <= latestCycle.periodDurationDays -> CyclePhase.MENSTRUAL
      dayInCycle <= 13 -> CyclePhase.FOLLICULAR
      else -> CyclePhase.LUTEAL
    }
  }

  private fun buildVariabilityComparison(lengths: List<Int>): String? {
    if (lengths.size < 4) return null
    val half = lengths.size / 2
    val earlierStd = lengths.take(half).stdDevOrNull() ?: return null
    val recentStd = lengths.takeLast(lengths.size - half).stdDevOrNull() ?: return null
    val diff = recentStd - earlierStd
    return when {
      diff > 1.2f -> "Your recent cycles have been more variable than your earlier cycles."
      diff < -1.2f -> "Your recent cycles have been steadier than your earlier cycles."
      else -> "Your cycle-to-cycle timing has stayed relatively consistent."
    }
  }

  private fun computeCrampByCycleDay(
    cycles: List<DerivedCycle>,
    entries: List<DailyEntry>
  ): List<Pair<Int, Float>> {
    if (cycles.isEmpty()) return emptyList()
    val byDay = mutableMapOf<Int, MutableList<Int>>()
    for (entry in entries) {
      val cycle = cycles.lastOrNull { it.startDate <= entry.date } ?: continue
      val day = cycle.startDate.daysUntil(entry.date) + 1
      if (day in 1..32 && (entry.crampScore > 0 || entry.isPeriod)) {
        byDay.getOrPut(day) { mutableListOf() }.add(entry.crampScore)
      }
    }
    return byDay.entries
      .sortedBy { it.key }
      .map { (day, scores) -> day to scores.average().toFloat() }
  }

  private fun computeSymptomStats(
    cycles: List<DerivedCycle>,
    entries: List<DailyEntry>
  ): List<SymptomFrequencyStat> {
    if (entries.isEmpty()) return emptyList()
    val allSymptoms = entries.flatMap { it.symptoms }.distinct()
    val totalCycles = cycles.size.coerceAtLeast(1)

    return allSymptoms.map { symptom ->
      val matchingEntries = entries.filter { symptom in it.symptoms }
      val cyclesWithSymptom = if (cycles.isNotEmpty()) {
        cycles.count { cycle ->
          val nextStart = cycles.firstOrNull { it.cycleIndex == cycle.cycleIndex + 1 }?.startDate
          val cycleEnd = nextStart?.minusDays(1) ?: cycle.endDate.plusDays(35)
          matchingEntries.any { it.date >= cycle.startDate && it.date <= cycleEnd }
        }
      } else 0

      // Determine timing relative to period starts
      var preMenstrual = 0
      var duringPeriod = 0
      var otherDays = 0
      for (entry in matchingEntries) {
        if (entry.isPeriod) {
          duringPeriod++
        } else {
          val upcomingCycle = cycles.firstOrNull {
            val d = entry.date.daysUntil(it.startDate)
            d in 1..3
          }
          if (upcomingCycle != null) preMenstrual++ else otherDays++
        }
      }
      val timingSummary = when {
        preMenstrual > duringPeriod && preMenstrual >= otherDays && preMenstrual > 0 ->
          "Most frequent 1–3 days before period"
        duringPeriod >= preMenstrual && duringPeriod >= otherDays && duringPeriod > 0 ->
          "Most frequent during period days"
        else -> "Observed across non-period days"
      }

      SymptomFrequencyStat(
        symptom = symptom,
        totalLogs = matchingEntries.size,
        cycleCount = cyclesWithSymptom,
        totalCyclesEvaluated = totalCycles,
        primaryTimingSummary = timingSummary
      )
    }.sortedByDescending { it.totalLogs }
  }

  private fun computeEnergyByPhase(
    cycles: List<DerivedCycle>,
    entries: List<DailyEntry>,
    fallbackCycleLen: Int,
    showFertileWindow: Boolean
  ): Map<CyclePhase, Float> {
    if (cycles.isEmpty()) return emptyMap()
    val buckets = mutableMapOf<CyclePhase, MutableList<Int>>()
    for (entry in entries) {
      val energy = entry.energyLevel ?: continue
      val cycle = cycles.lastOrNull { it.startDate <= entry.date } ?: continue
      val day = cycle.startDate.daysUntil(entry.date) + 1
      val cycleLen = cycle.cycleLengthDays ?: fallbackCycleLen
      val phase = when {
        entry.isPeriod || day <= cycle.periodDurationDays -> CyclePhase.MENSTRUAL
        showFertileWindow && day in (cycleLen - 19)..(cycleLen - 13) -> CyclePhase.FERTILE_WINDOW
        day < (cycleLen - 14) -> CyclePhase.FOLLICULAR
        else -> CyclePhase.LUTEAL
      }
      buckets.getOrPut(phase) { mutableListOf() }.add(energy)
    }
    return buckets.mapValues { (_, list) -> list.average().toFloat() }
  }

  private fun computeCrossFactorInsights(entries: List<DailyEntry>): List<CrossFactorInsight> {
    val results = mutableListOf<CrossFactorInsight>()

    // 1. Sleep duration vs Cramp intensity
    val sleepCrampPairs = entries.mapNotNull { e ->
      e.sleepDurationHours?.let { sleep -> sleep to e.crampScore }
    }
    if (sleepCrampPairs.size >= 4) {
      val lowSleep = sleepCrampPairs.filter { it.first < 6.5f }
      val goodSleep = sleepCrampPairs.filter { it.first >= 6.5f }
      if (lowSleep.isNotEmpty() && goodSleep.isNotEmpty()) {
        val avgCrampLow = lowSleep.map { it.second }.average().toFloat()
        val avgCrampGood = goodSleep.map { it.second }.average().toFloat()
        val msg = if (avgCrampLow > avgCrampGood + 0.35f) {
          "Higher cramp scores (${format1(avgCrampLow)}/5) appeared alongside shorter sleep (<6.5h) vs ${format1(avgCrampGood)}/5 on 6.5h+ sleep days. These patterns appeared together in your data."
        } else {
          "Cramp intensity averaged ${format1(avgCrampLow)}/5 on shorter sleep days and ${format1(avgCrampGood)}/5 on well-rested days. These patterns appeared together in your data."
        }
        results.add(
          CrossFactorInsight(
            title = "Sleep vs. Cramp Intensity",
            summary = msg,
            comparisonMetricA = "<6.5h sleep: ${format1(avgCrampLow)}/5 cramps",
            comparisonMetricB = "6.5h+ sleep: ${format1(avgCrampGood)}/5 cramps",
            sampleDays = sleepCrampPairs.size,
            category = "Sleep & Pain"
          )
        )
      }
    }

    // 2. Stress vs Low Mood / Mood Swings
    val stressMoodPairs = entries.mapNotNull { e ->
      e.stressLevel?.let { stress ->
        val hasLowOrSwing = e.moodSwingLevel != MoodSwingLevel.NONE ||
          e.moods.any { it in DefaultCatalog.negativeMoods }
        stress to hasLowOrSwing
      }
    }
    if (stressMoodPairs.size >= 4) {
      val highStress = stressMoodPairs.filter { it.first >= 3 }
      val lowStress = stressMoodPairs.filter { it.first < 3 }
      if (highStress.isNotEmpty() && lowStress.isNotEmpty()) {
        val pctHigh = (highStress.count { it.second } * 100f) / highStress.size
        val pctLow = (lowStress.count { it.second } * 100f) / lowStress.size
        val msg = if (pctHigh > pctLow + 10f) {
          "Lower mood or mood swings appeared on ${pctHigh.roundToInt()}% of higher-stress days (3–5/5) compared with ${pctLow.roundToInt()}% of calmer days. These patterns appeared together in your data."
        } else {
          "Mood shifts occurred on ${pctHigh.roundToInt()}% of higher-stress days vs ${pctLow.roundToInt()}% on lower-stress days. These patterns appeared together in your data."
        }
        results.add(
          CrossFactorInsight(
            title = "Stress vs. Mood Patterns",
            summary = msg,
            comparisonMetricA = "Higher stress: ${pctHigh.roundToInt()}% mood shifts",
            comparisonMetricB = "Lower stress: ${pctLow.roundToInt()}% mood shifts",
            sampleDays = stressMoodPairs.size,
            category = "Stress & Mood"
          )
        )
      }
    }

    // 3. Physical Activity vs Symptom Burden
    val activityPairs = entries.filter { it.hasAnyDetails }
    if (activityPairs.size >= 4) {
      val activeDays = activityPairs.filter { it.activityLevel != ActivityLevel.NONE || (it.exerciseMinutes ?: 0) >= 15 }
      val inactiveDays = activityPairs.filter { it.activityLevel == ActivityLevel.NONE && (it.exerciseMinutes ?: 0) < 15 }
      if (activeDays.isNotEmpty() && inactiveDays.isNotEmpty()) {
        val avgSymActive = activeDays.map { it.symptoms.size + (it.crampScore * 0.5f) }.average().toFloat()
        val avgSymRest = inactiveDays.map { it.symptoms.size + (it.crampScore * 0.5f) }.average().toFloat()
        val msg = if (avgSymActive < avgSymRest - 0.3f) {
          "Symptom load was lighter on active days (${format1(avgSymActive)} score) compared with rest days (${format1(avgSymRest)}). These patterns appeared together in your data."
        } else {
          "Logged symptom load averaged ${format1(avgSymActive)} on active days vs ${format1(avgSymRest)} on non-active days. These patterns appeared together in your data."
        }
        results.add(
          CrossFactorInsight(
            title = "Physical Activity vs. Symptoms",
            summary = msg,
            comparisonMetricA = "Active days: ${format1(avgSymActive)} load",
            comparisonMetricB = "Rest days: ${format1(avgSymRest)} load",
            sampleDays = activityPairs.size,
            category = "Activity & Wellness"
          )
        )
      }
    }

    return results
  }

  private fun computeAgeTrends(
    cycles: List<DerivedCycle>,
    entries: List<DailyEntry>
  ): List<AgeTrendBucket> {
    if (cycles.isEmpty()) return emptyList()
    return cycles.groupBy { it.userAgeAtStart }
      .toSortedMap()
      .map { (age, ageCycles) ->
        val lengths = ageCycles.mapNotNull { it.cycleLengthDays }
        val avgLen = lengths.takeIf { it.isNotEmpty() }?.average()?.toFloat()
        val avgPeriod = ageCycles.map { it.periodDurationDays }.average().toFloat()
        val avgCramp = ageCycles.map { it.averageCrampScore }.average().toFloat()

        val ageEntries = entries.filter { entry ->
          ageCycles.any { c ->
            val nextStart = cycles.firstOrNull { it.cycleIndex == c.cycleIndex + 1 }?.startDate
            val end = nextStart?.minusDays(1) ?: c.endDate.plusDays(35)
            entry.date >= c.startDate && entry.date <= end
          }
        }
        val topSymptom = ageEntries.flatMap { it.symptoms }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key

        AgeTrendBucket(
          ageYears = age,
          cycleCount = ageCycles.size,
          averageCycleLength = avgLen,
          averagePeriodDuration = avgPeriod,
          averageCrampScore = avgCramp,
          topSymptom = topSymptom
        )
      }
  }

  private fun detectAnomalies(
    cycles: List<DerivedCycle>,
    entries: List<DailyEntry>,
    today: CycleDate
  ): List<CycleAnomaly> {
    val anomalies = mutableListOf<CycleAnomaly>()
    if (cycles.size < 2) return anomalies

    val completedLengths = cycles.mapNotNull { it.cycleLengthDays }
    if (completedLengths.size >= 3) {
      val baselineLengths = completedLengths.dropLast(1)
      val latestLen = completedLengths.last()
      val baselineMedian = baselineLengths.medianOrNull() ?: latestLen.toFloat()
      val diff = latestLen - baselineMedian
      if (abs(diff) >= 6f) {
        val dir = if (diff > 0) "longer" else "shorter"
        anomalies.add(
          CycleAnomaly(
            title = "Cycle length shift detected",
            detail = "Your most recent completed cycle ($latestLen days) was ${abs(diff).roundToInt()} days $dir than your personal median (${format1(baselineMedian)} days). This is unusual compared with your previous data.",
            severityLabel = "Observation"
          )
        )
      }
    }

    // Check if active ongoing cycle is significantly longer than usual
    val latestCycle = cycles.last()
    if (completedLengths.size >= 2) {
      val medianLen = completedLengths.medianOrNull() ?: 28f
      val currentDay = latestCycle.startDate.daysUntil(today) + 1
      if (currentDay > medianLen + 7 && currentDay < 100) {
        anomalies.add(
          CycleAnomaly(
            title = "Cycle running longer than typical",
            detail = "Today is Cycle Day $currentDay, which is past your historical median of ${format1(medianLen)} days. Consider logging any updates or consulting a clinician if you notice ongoing changes.",
            severityLabel = "Notice"
          )
        )
      }
    }

    // Check period duration shift
    val durations = cycles.map { it.periodDurationDays }
    if (durations.size >= 3) {
      val priorAvg = durations.dropLast(1).average().toFloat()
      val latestDur = durations.last()
      if (abs(latestDur - priorAvg) >= 3f) {
        anomalies.add(
          CycleAnomaly(
            title = "Unusual period duration",
            detail = "Your most recent period lasted $latestDur days compared with your usual ${format1(priorAvg)}-day average. This is an observation from your logs, not a diagnosis.",
            severityLabel = "Observation"
          )
        )
      }
    }

    // Unexpected spotting outside period in recent 14 days
    val recentSpotting = entries.filter {
      it.date >= today.minusDays(14) && it.spotting && !it.isPeriod
    }
    if (recentSpotting.size >= 2) {
      anomalies.add(
        CycleAnomaly(
          title = "Mid-cycle spotting logged",
          detail = "Spotting outside your logged period appeared on ${recentSpotting.size} day(s) in the last 2 weeks. Continue tracking to see if it becomes a recurring personal pattern.",
          severityLabel = "Pattern watch"
        )
      )
    }

    // Unusually high pain score compared to baseline
    val baselineCramps = cycles.dropLast(1).map { it.maxCrampScore }.filter { it > 0 }
    if (baselineCramps.size >= 2 && latestCycle.maxCrampScore >= 4 && latestCycle.maxCrampScore > baselineCramps.average() + 1.4) {
      anomalies.add(
        CycleAnomaly(
          title = "Higher cramp intensity than usual",
          detail = "Peak cramps in your latest cycle reached ${latestCycle.maxCrampScore}/5, above your earlier cycle average (${format1(baselineCramps.average().toFloat())}/5). If severe pain persists, consider discussing it with a healthcare professional.",
          severityLabel = "Pain watch"
        )
      )
    }

    return anomalies
  }

  private fun synthesizePersonalInsights(
    windowedCycles: List<DerivedCycle>,
    avgCycleLen: Float?,
    variabilityNote: String?,
    crampByDay: List<Pair<Int, Float>>,
    symptomStats: List<SymptomFrequencyStat>,
    moodSwingRate: Float,
    energyByPhase: Map<CyclePhase, Float>,
    crossFactors: List<CrossFactorInsight>,
    entries: List<DailyEntry>,
    allCycles: List<DerivedCycle>
  ): List<PersonalInsightCard> {
    val cards = mutableListOf<PersonalInsightCard>()

    val completedCount = windowedCycles.count { it.cycleLengthDays != null }
    if (avgCycleLen != null && completedCount >= 1) {
      val extra = variabilityNote?.let { " $it" } ?: ""
      cards.add(
        PersonalInsightCard(
          category = "Your pattern",
          title = "Cycle Rhythm",
          statement = "Your last $completedCount completed cycle(s) averaged ${format1(avgCycleLen)} days.$extra"
        )
      )
    }

    // Peak cramp day observation
    if (allCycles.size >= 2 && crampByDay.isNotEmpty()) {
      val peakDay = crampByDay.maxByOrNull { it.second }
      if (peakDay != null && peakDay.second >= 1f) {
        val cyclesWithPeakOnDay = allCycles.count { cycle ->
          val targetDate = cycle.startDate.plusDays(peakDay.first - 1)
          val dayEntry = entries.firstOrNull { it.date == targetDate }
          dayEntry != null && dayEntry.crampScore >= (cycle.maxCrampScore - 1).coerceAtLeast(1)
        }
        cards.add(
          PersonalInsightCard(
            category = "Pain pattern",
            title = "Peak Cramp Timing",
            statement = "Cycle Day ${peakDay.first} has had your highest average cramps (${format1(peakDay.second)}/5), appearing strongly in $cyclesWithPeakOnDay of ${allCycles.size} cycle(s)."
          )
        )
      }
    }

    // Top recurring symptom
    val topSym = symptomStats.firstOrNull()
    if (topSym != null && topSym.cycleCount >= 2) {
      cards.add(
        PersonalInsightCard(
          category = "Symptom pattern",
          title = topSym.symptom,
          statement = "${topSym.symptom} was logged in ${topSym.cycleCount} of your ${topSym.totalCyclesEvaluated} evaluated cycle(s) — ${topSym.primaryTimingSummary.lowercase(Locale.US)}."
        )
      )
    }

    // Mood / Energy pattern
    val menstrualEnergy = energyByPhase[CyclePhase.MENSTRUAL]
    val follicularEnergy = energyByPhase[CyclePhase.FOLLICULAR]
    if (menstrualEnergy != null && follicularEnergy != null && follicularEnergy - menstrualEnergy >= 0.5f) {
      cards.add(
        PersonalInsightCard(
          category = "Energy pattern",
          title = "Phase Energy Shift",
          statement = "You logged lower energy during period days (${format1(menstrualEnergy)}/5) compared with follicular days (${format1(follicularEnergy)}/5)."
        )
      )
    } else if (moodSwingRate >= 25f) {
      cards.add(
        PersonalInsightCard(
          category = "Mood pattern",
          title = "Mood Swing Frequency",
          statement = "Mood swings were recorded on ${moodSwingRate.roundToInt()}% of days with mood check-ins."
        )
      )
    }

    // Top cross-factor card
    crossFactors.firstOrNull()?.let { cf ->
      cards.add(
        PersonalInsightCard(
          category = "Lifestyle pattern",
          title = cf.title,
          statement = cf.summary
        )
      )
    }

    return cards
  }

  private fun List<Int>.medianOrNull(): Float? {
    if (isEmpty()) return null
    val s = sorted()
    val mid = s.size / 2
    return if (s.size % 2 == 1) s[mid].toFloat() else (s[mid - 1] + s[mid]) / 2f
  }

  private fun List<Int>.stdDevOrNull(): Float? {
    if (size < 2) return null
    val mean = average()
    val variance = map { (it - mean) * (it - mean) }.average()
    return sqrt(variance).toFloat()
  }

  fun format1(value: Float): String = String.format(Locale.US, "%.1f", value)
}
