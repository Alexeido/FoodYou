package com.maksimowiczm.foodyou.app.ui.home.goals

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** One macro's data, so the figures below don't each need six parameters. */
internal data class MacroSlice(
    val label: String,
    val grams: Int,
    val goalGrams: Int,
    val progress: Float,
    val color: Color,
)

// ---------------------------------------------------------------- Columns

/** Consumed over goal with a thin track, in narrow columns. */
@Composable
internal fun MacroColumns(
    macros: List<MacroSlice>,
    showValues: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        macros.forEach { macro ->
            Column(modifier = Modifier.width(54.dp)) {
                Text(
                    text = macro.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (showValues) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "${macro.grams}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = " / ${macro.goalGrams}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1,
                        )
                    }
                }
                MacroTrack(progress = macro.progress, color = macro.color)
            }
        }
    }
}

@Composable
private fun MacroTrack(progress: Float, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        Box(
            modifier =
                Modifier.fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
        )
    }
}

// ---------------------------------------------------------------- Ring / Arc

/** Closed ring showing the day's calorie percentage. */
@Composable
internal fun EnergyRing(progress: Float, centerText: String, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val bar = MaterialTheme.colorScheme.primary

    Box(modifier = modifier.size(80.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(80.dp)) {
            val stroke = 8.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke),
            )
            drawArc(
                color = bar,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Text(text = centerText, style = MaterialTheme.typography.titleSmall)
    }
}

/** Open gauge showing the day's calorie progress. */
@Composable
internal fun EnergyArc(progress: Float, centerText: String, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val bar = MaterialTheme.colorScheme.primary

    Box(modifier = modifier.width(112.dp).height(64.dp), contentAlignment = Alignment.BottomCenter) {
        Canvas(Modifier.fillMaxWidth().height(64.dp)) {
            val stroke = 9.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, (size.height - inset) * 2)
            drawArc(
                color = track,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = bar,
                startAngle = 180f,
                sweepAngle = 180f * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Text(text = centerText, style = MaterialTheme.typography.titleSmall)
    }
}

// ---------------------------------------------------------------- Stacked

/** Legend beside the calories; the bar itself is drawn full width underneath. */
@Composable
internal fun MacroLegend(
    macros: List<MacroSlice>,
    showValues: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        macros.forEach { macro ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier =
                        Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(macro.color)
                )
                if (showValues) {
                    Text(
                        text = "${macro.grams} g",
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                    )
                }
                Text(
                    text = macro.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Single bar showing how the day's calories split between macros — 4 kcal per gram of protein and
 * carbohydrate, 9 per gram of fat.
 */
@Composable
internal fun StackedMacroBar(
    proteins: Int,
    carbohydrates: Int,
    fats: Int,
    proteinsColor: Color,
    carbohydratesColor: Color,
    fatsColor: Color,
    modifier: Modifier = Modifier,
) {
    val total = (proteins * 4 + carbohydrates * 4 + fats * 9).toFloat()

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        if (total <= 0f) return@Row
        Box(Modifier.weight(proteins * 4 / total).fillMaxWidth().background(proteinsColor))
        Box(Modifier.weight(fats * 9 / total).fillMaxWidth().background(fatsColor))
        Box(Modifier.weight(carbohydrates * 4 / total).fillMaxWidth().background(carbohydratesColor))
    }
}

// ---------------------------------------------------------------- Numbers / horizontal bars

/** Figures only — the shortest style. */
@Composable
internal fun MacroNumbers(macros: List<MacroSlice>, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        macros.forEach { macro ->
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = macro.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "${macro.grams}",
                        style = MaterialTheme.typography.titleSmall,
                        color = macro.color,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = " / ${macro.goalGrams}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Three horizontal bars, each with its own goal — the most informative style. */
@Composable
internal fun MacroHorizontalBars(
    macros: List<MacroSlice>,
    showValues: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.width(150.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        macros.forEach { macro ->
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = macro.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                    )
                    if (showValues) {
                        Row {
                            Text(
                                text = "${macro.grams}",
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                            )
                            Text(
                                text = " / ${macro.goalGrams}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                maxLines = 1,
                            )
                        }
                    }
                }
                MacroTrack(progress = macro.progress, color = macro.color)
            }
        }
    }
}

/** Macro grams on one line, for the styles whose figure only reports calories. */
@Composable
internal fun InlineMacros(macros: List<MacroSlice>, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        macros.forEachIndexed { index, macro ->
            Text(
                text = "${macro.grams} g",
                style = MaterialTheme.typography.labelMedium,
                color = macro.color,
                fontWeight = FontWeight.SemiBold,
            )
            if (index != macros.lastIndex) {
                Text(
                    text = "·",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}
