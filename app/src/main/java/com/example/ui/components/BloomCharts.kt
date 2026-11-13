package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.analytics.CycleAnalyticsEngine
import com.example.domain.model.DerivedCycle
import kotlin.math.abs

@Composable
fun CycleLengthTrendChart(
  cycles: List<DerivedCycle>,
  averageLength: Float?,
  modifier: Modifier = Modifier
) {
  val completed = remember(cycles) { cycles.filter { it.cycleLengthDays != null } }
  if (completed.isEmpty()) {
    Text(
      text = "Log at least 2 period start dates to visualize cycle length trends.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    return
  }

  var selectedIdx by remember(completed) { mutableIntStateOf(completed.lastIndex) }
  val primaryColor = MaterialTheme.colorScheme.primary
  val secondaryColor = MaterialTheme.colorScheme.secondary
  val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
  val surfaceColor = MaterialTheme.colorScheme.surface

  val lengths = completed.map { it.cycleLengthDays!! }
  val minY = (lengths.min() - 3).coerceAtLeast(15).toFloat()
  val maxY = (lengths.max() + 3).toFloat().coerceAtLeast(minY + 6f)

  val selectedCycle = completed.getOrNull(selectedIdx) ?: completed.last()

  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Surface(
      shape = RoundedCornerShape(12.dp),
      color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "Cycle ${selectedCycle.cycleIndex} (${selectedCycle.startDate.shortLabel()})",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
          text = "${selectedCycle.cycleLengthDays} days • Period ${selectedCycle.periodDurationDays}d",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onPrimaryContainer
        )
      }
    }

    Canvas(
      modifier = Modifier
        .fillMaxWidth()
        .height(170.dp)
        .pointerInput(completed) {
          detectTapGestures { offset ->
            if (completed.size == 1) {
              selectedIdx = 0
              return@detectTapGestures
            }
            val leftPad = 24f
            val usableW = (size.width - leftPad * 2).coerceAtLeast(1f)
            val stepX = usableW / (completed.size - 1).coerceAtLeast(1)
            val closest = completed.indices.minByOrNull { idx ->
              abs(offset.x - (leftPad + idx * stepX))
            } ?: 0
            selectedIdx = closest
          }
        }
    ) {
      val leftPad = 24.dp.toPx()
      val rightPad = 16.dp.toPx()
      val topPad = 14.dp.toPx()
      val bottomPad = 18.dp.toPx()
      val plotW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
      val plotH = (size.height - topPad - bottomPad).coerceAtLeast(1f)

      fun yFor(value: Float): Float {
        val ratio = ((value - minY) / (maxY - minY)).coerceIn(0f, 1f)
        return topPad + plotH * (1f - ratio)
      }

      // Horizontal baseline grid lines
      for (i in 0..2) {
        val y = topPad + plotH * (i / 2f)
        drawLine(
          color = gridColor,
          start = Offset(leftPad, y),
          end = Offset(leftPad + plotW, y),
          strokeWidth = 1.dp.toPx()
        )
      }

      // Average dashed reference line
      if (averageLength != null) {
        val avgY = yFor(averageLength)
        drawLine(
          color = secondaryColor,
          start = Offset(leftPad, avgY),
          end = Offset(leftPad + plotW, avgY),
          strokeWidth = 1.5.dp.toPx(),
          pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
        )
      }

      val points = completed.mapIndexed { idx, cycle ->
        val x = if (completed.size == 1) leftPad + plotW / 2f
        else leftPad + (plotW * idx / (completed.size - 1))
        val y = yFor(cycle.cycleLengthDays!!.toFloat())
        Offset(x, y)
      }

      if (points.size >= 2) {
        val fillPath = Path().apply {
          moveTo(points.first().x, topPad + plotH)
          points.forEach { lineTo(it.x, it.y) }
          lineTo(points.last().x, topPad + plotH)
          close()
        }
        drawPath(
          path = fillPath,
          brush = Brush.verticalGradient(
            listOf(primaryColor.copy(alpha = 0.28f), primaryColor.copy(alpha = 0.02f)),
            startY = topPad,
            endY = topPad + plotH
          )
        )

        val linePath = Path().apply {
          moveTo(points.first().x, points.first().y)
          points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(
          path = linePath,
          color = primaryColor,
          style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )
      }

      points.forEachIndexed { idx, pt ->
        val isSel = idx == selectedIdx
        drawCircle(
          color = surfaceColor,
          radius = if (isSel) 7.dp.toPx() else 4.5.dp.toPx(),
          center = pt
        )
        drawCircle(
          color = if (isSel) primaryColor else primaryColor.copy(alpha = 0.75f),
          radius = if (isSel) 5.5.dp.toPx() else 3.5.dp.toPx(),
          center = pt
        )
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Text(
        text = "Range: ${lengths.min()}–${lengths.max()} days",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      if (averageLength != null) {
        Text(
          text = "Dashed line: ${CycleAnalyticsEngine.format1(averageLength)}d avg",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.secondary
        )
      }
    }
  }
}

@Composable
fun CrampByCycleDayChart(
  crampByDay: List<Pair<Int, Float>>,
  modifier: Modifier = Modifier
) {
  if (crampByDay.isEmpty()) {
    Text(
      text = "Log cramp intensity (0–5) across your cycle days to see where discomfort peaks.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    return
  }

  val displayed = remember(crampByDay) { crampByDay.take(16) }
  var selectedIndex by remember(displayed) {
    mutableIntStateOf(
      displayed.indices.maxByOrNull { displayed[it].second } ?: 0
    )
  }

  val primaryColor = MaterialTheme.colorScheme.primary
  val mutedColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
  val selectedItem = displayed.getOrNull(selectedIndex) ?: displayed.first()

  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "Cycle Day ${selectedItem.first}",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary
      )
      Text(
        text = "Avg cramp: ${CycleAnalyticsEngine.format1(selectedItem.second)} / 5",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }

    Canvas(
      modifier = Modifier
        .fillMaxWidth()
        .height(140.dp)
        .pointerInput(displayed) {
          detectTapGestures { offset ->
            val slotW = size.width / displayed.size.coerceAtLeast(1).toFloat()
            val idx = (offset.x / slotW).toInt().coerceIn(0, displayed.lastIndex)
            selectedIndex = idx
          }
        }
    ) {
      val bottomPad = 14.dp.toPx()
      val plotH = (size.height - bottomPad - 8.dp.toPx()).coerceAtLeast(1f)
      val slotW = size.width / displayed.size.coerceAtLeast(1).toFloat()
      val barW = (slotW * 0.58f).coerceAtMost(24.dp.toPx())

      displayed.forEachIndexed { idx, (_, avgScore) ->
        val norm = (avgScore / 5f).coerceIn(0.06f, 1f)
        val barH = plotH * norm
        val left = idx * slotW + (slotW - barW) / 2f
        val top = 8.dp.toPx() + (plotH - barH)
        drawRoundRect(
          color = if (idx == selectedIndex) primaryColor else mutedColor,
          topLeft = Offset(left, top),
          size = Size(barW, barH),
          cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
        )
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      displayed.forEachIndexed { idx, (day, _) ->
        if (displayed.size <= 10 || idx == 0 || idx == displayed.lastIndex || idx % 3 == 0) {
          Text(
            text = "D$day",
            style = MaterialTheme.typography.labelSmall,
            color = if (idx == selectedIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }
  }
}

@Composable
fun FrequencyBarRow(
  label: String,
  subtitle: String,
  fraction: Float,
  valueText: String,
  barColor: Color = MaterialTheme.colorScheme.primary
) {
  val animatedFraction by animateFloatAsState(
    targetValue = fraction.coerceIn(0.04f, 1f),
    animationSpec = spring(),
    label = "freq_bar"
  )

  Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = label,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold
        )
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        text = valueText,
        style = MaterialTheme.typography.labelLarge,
        color = barColor
      )
    }
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(9.dp)
        .clip(RoundedCornerShape(50))
        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
      Box(
        modifier = Modifier
          .fillMaxWidth(animatedFraction)
          .height(9.dp)
          .clip(RoundedCornerShape(50))
          .background(barColor)
      )
    }
  }
}
