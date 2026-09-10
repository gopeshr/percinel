package gopesh.percinel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Small caps section heading, matching the rest of the app. */
@Composable
fun InsightLabel(text: String, top: androidx.compose.ui.unit.Dp = 28.dp) {
    Text(
        text.uppercase(),
        fontSize = 12.sp,
        letterSpacing = 0.8.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = top, bottom = 10.dp),
    )
}

/**
 * A contribution-style calendar: one square per day of the year, darker where you watched more.
 * Scrolls horizontally because 53 weeks never fits a phone at a legible cell size.
 */
@Composable
fun HeatmapCard(year: Int, days: Map<LocalDate, Int>) {
    val cell = 13.dp
    val gap = 3.dp
    val max = (days.values.maxOrNull() ?: 1).coerceAtLeast(1)
    val start = LocalDate.of(year, 1, 1)
    val end = LocalDate.of(year, 12, 31)
    // Grid starts on the Monday on or before Jan 1 so weekday rows line up.
    val gridStart = start.minusDays(((start.dayOfWeek.value + 6) % 7).toLong())
    val weeks = (ChronoUnit.DAYS.between(gridStart, end) / 7 + 1).toInt()

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$year", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                Text(
                    "${days.values.sum()} watches on ${days.size} days",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                Modifier.padding(top = 12.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                for (w in 0 until weeks) {
                    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                        for (d in 0..6) {
                            val date = gridStart.plusDays((w * 7 + d).toLong())
                            if (date.year != year) {
                                Spacer(Modifier.size(cell))
                            } else {
                                val n = days[date] ?: 0
                                Box(
                                    Modifier
                                        .size(cell)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(heatColor(n, max)),
                                )
                            }
                        }
                    }
                }
            }
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Less", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    listOf(0, 1, 2, 3, 4).forEach { step ->
                        Box(
                            Modifier
                                .size(10.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(heatColor(step, 4)),
                        )
                    }
                }
                Text("More", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun heatColor(count: Int, max: Int): androidx.compose.ui.graphics.Color {
    if (count <= 0) return MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
    // Four visible steps so a 1-watch day still reads as "something happened".
    val step = ((count.toFloat() / max) * 3f).toInt().coerceIn(0, 3)
    val alpha = listOf(0.35f, 0.55f, 0.78f, 1f)[step]
    return MaterialTheme.colorScheme.primary.copy(alpha = alpha)
}

/** Rating distribution, 1–10. Bars are relative to the busiest bucket. */
@Composable
fun HistogramCard(buckets: List<Pair<Int, Int>>) {
    val max = (buckets.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        // Counts, bars and axis labels are three separate rows so a tall bar can never
        // push its own label out of the card.
        Column(Modifier.padding(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                buckets.forEach { (_, count) ->
                    Text(
                        if (count > 0) count.toString() else "",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Row(
                Modifier.height(90.dp).padding(top = 2.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                buckets.forEach { (_, count) ->
                    Box(
                        Modifier
                            .weight(1f)
                            // Always leave a sliver so an empty bucket still reads as a slot.
                            .fillMaxHeight(((count.toFloat() / max) * 0.95f + 0.05f).coerceIn(0.05f, 1f))
                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            .background(
                                if (count > 0) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
                            ),
                    )
                }
            }
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                buckets.forEach { (bucket, _) ->
                    Text(
                        bucket.toString(),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** A ranked "top genres / directors / actors" list with proportional bars. */
@Composable
fun TopListCard(items: List<Pair<String, Int>>, unit: String = "films") {
    if (items.isEmpty()) return
    val max = items.maxOf { it.second }.coerceAtLeast(1)
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            items.forEachIndexed { i, (name, count) ->
                if (i > 0) Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        name,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "$count",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        // "1 film", not "1 films".
                        " " + if (count == 1) unit.removeSuffix("s") else unit,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .padding(top = 0.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(count.toFloat() / max)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }
    }
}

/** Which decades your taste actually lives in. */
@Composable
fun DecadesCard(items: List<Pair<Int, Int>>) {
    if (items.isEmpty()) return
    TopListCard(items.map { "${it.first}s" to it.second })
}
