package gopesh.percinel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gopesh.percinel.data.WatchFilter
import kotlin.math.roundToInt

/**
 * Narrow the Watches list by rating, release decade and genre. Changes apply live — the list
 * behind updates as you go and the button says how many films are left — so there's no
 * separate "apply" step to forget.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterSheet(
    filter: WatchFilter,
    onFilter: (WatchFilter) -> Unit,
    /** Decades present in the library with film counts, newest first. */
    decades: List<Pair<Int, Int>>,
    /** Genres present in the library with film counts, most common first. */
    genres: List<Pair<String, Int>>,
    /** Films with no cached TMDB metadata, which genre filtering can't see. */
    unanalysed: Int,
    resultCount: Int,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        // Capped below the status bar, with the result button pinned outside the scroll area so
        // the live count is always in view however many genres there are.
        val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.85f).dp
        Column(Modifier.heightIn(max = maxHeight).padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Filter watches",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (filter.activeCount > 0) {
                    TextButton(onClick = { onFilter(filter.clearedOfSheet()) }) {
                        Text("Reset", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // ---- Rating
            InsightLabel("Your rating", top = 20.dp)
            Text(
                if (filter.ratingActive) "${fmtRating(filter.minRating)} – ${fmtRating(filter.maxRating)}" else "Any",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            RangeSlider(
                value = filter.minRating.toFloat()..filter.maxRating.toFloat(),
                onValueChange = { r ->
                    onFilter(filter.copy(minRating = halfStep(r.start), maxRating = halfStep(r.endInclusive)))
                },
                valueRange = WatchFilter.MIN_RATING.toFloat()..WatchFilter.MAX_RATING.toFloat(),
                // 1.0 → 10.0 in half steps: 19 stops, 17 between the ends.
                steps = 17,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    activeTickColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.4f),
                    inactiveTickColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                ),
                // The thumbs rest near the screen edges, inside the system back-gesture zone;
                // without this, dragging the minimum up fires Back and closes the sheet.
                modifier = Modifier
                    .systemGestureExclusion()
                    .semantics { contentDescription = "Rating range" },
            )

            // ---- Decade
            if (decades.isNotEmpty()) {
                InsightLabel("Released in")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    decades.forEach { (decade, count) ->
                        Chip(
                            label = "${decade}s",
                            count = count,
                            selected = decade in filter.decades,
                            onClick = { onFilter(filter.copy(decades = filter.decades.toggle(decade))) },
                        )
                    }
                }
            }

            // ---- Genre
            InsightLabel("Genre")
            if (genres.isEmpty()) {
                Text(
                    "Genres appear once percinel has looked your films up. " +
                        "Open Stats and tap Analyse my library.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    genres.forEach { (genre, count) ->
                        Chip(
                            label = genre,
                            count = count,
                            selected = genre in filter.genres,
                            onClick = { onFilter(filter.copy(genres = filter.genres.toggle(genre))) },
                        )
                    }
                }
                if (unanalysed > 0) {
                    Text(
                        "$unanalysed ${if (unanalysed == 1) "film hasn't" else "films haven't"} been " +
                            "looked up yet, so genre filters skip them.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(50.dp),
            ) {
                Text(
                    when (resultCount) {
                        0 -> "No films match"
                        1 -> "Show 1 film"
                        else -> "Show $resultCount films"
                    },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun Chip(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text("$label  $count") },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}

/** One line describing the active sheet filters, for the strip above the list. */
fun filterSummary(f: WatchFilter): String = buildList {
    if (f.ratingActive) add("Rated ${fmtRating(f.minRating)}–${fmtRating(f.maxRating)}")
    if (f.decades.isNotEmpty()) add(f.decades.sorted().joinToString(", ") { "${it}s" })
    if (f.genres.isNotEmpty()) {
        val g = f.genres.sorted()
        add(if (g.size <= 2) g.joinToString(", ") else "${g.first()} +${g.size - 1}")
    }
}.joinToString("  ·  ")

private fun fmtRating(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()

/** Snap slider output to the 0.5 grid so float noise never leaks into the filter. */
private fun halfStep(v: Float): Double = (v * 2).roundToInt() / 2.0

private fun <T> Set<T>.toggle(item: T): Set<T> = if (item in this) this - item else this + item
