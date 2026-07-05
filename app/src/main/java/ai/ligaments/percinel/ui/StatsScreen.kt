package ai.ligaments.percinel.ui

import ai.ligaments.percinel.data.Entry
import ai.ligaments.percinel.data.Tmdb
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** The list a stat card drills into. */
enum class StatFilter { ALL, BY_RATING, MOVIES, SERIES, THIS_YEAR, RATED_9 }

private fun currentYear() = Instant.now().atZone(ZoneId.systemDefault()).year

fun statTitle(f: StatFilter): String = when (f) {
    StatFilter.ALL -> "All watches"
    StatFilter.BY_RATING -> "Best to worst"
    StatFilter.MOVIES -> "Movies"
    StatFilter.SERIES -> "Series"
    StatFilter.THIS_YEAR -> "Watched in ${currentYear()}"
    StatFilter.RATED_9 -> "Rated 9 and up"
}

fun statEntries(f: StatFilter, all: List<Entry>): List<Entry> {
    val zone = ZoneId.systemDefault()
    return when (f) {
        StatFilter.ALL -> all.sortedByDescending { it.watchedAt }
        StatFilter.BY_RATING -> all.sortedByDescending { it.rating }
        StatFilter.MOVIES -> all.filter { it.mediaType == "movie" }.sortedByDescending { it.watchedAt }
        StatFilter.SERIES -> all.filter { it.mediaType == "tv" }.sortedByDescending { it.watchedAt }
        StatFilter.THIS_YEAR ->
            all.filter { Instant.ofEpochMilli(it.watchedAt).atZone(zone).year == currentYear() }
                .sortedByDescending { it.watchedAt }
        StatFilter.RATED_9 -> all.filter { it.rating >= 9.0 }.sortedByDescending { it.rating }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    entries: List<Entry>?,
    onMenu: () -> Unit,
    onOpenList: (StatFilter) -> Unit,
    onOpenEntry: (Long) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Stats", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onMenu) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { padding ->
        val list = entries
        if (list == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }
        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Watch something first", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }

        val movies = list.count { it.mediaType == "movie" }
        val series = list.count { it.mediaType == "tv" }
        val avg = list.map { it.rating }.average()
        val thisYear = currentYear()
        val zone = ZoneId.systemDefault()
        val thisYearCount = list.count { Instant.ofEpochMilli(it.watchedAt).atZone(zone).year == thisYear }
        val highest = list.maxByOrNull { it.rating }
        val lowest = list.minByOrNull { it.rating }

        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Metric("Total", list.size.toString(), Modifier.weight(1f)) { onOpenList(StatFilter.ALL) }
                Metric("Average", String.format(Locale.US, "%.2f", avg), Modifier.weight(1f)) { onOpenList(StatFilter.BY_RATING) }
            }
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Metric("Movies", movies.toString(), Modifier.weight(1f)) { onOpenList(StatFilter.MOVIES) }
                Metric("Series", series.toString(), Modifier.weight(1f)) { onOpenList(StatFilter.SERIES) }
            }
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Metric("In $thisYear", thisYearCount.toString(), Modifier.weight(1f)) { onOpenList(StatFilter.THIS_YEAR) }
                Metric("Rated 9+", list.count { it.rating >= 9.0 }.toString(), Modifier.weight(1f)) { onOpenList(StatFilter.RATED_9) }
            }

            highest?.let { Highlight("Highest rated", it) { onOpenEntry(it.id) } }
            lowest?.takeIf { it.id != highest?.id }?.let { Highlight("Lowest rated", it) { onOpenEntry(it.id) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatListScreen(title: String, items: List<Entry>, onBack: () -> Unit, onOpen: (Long) -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { padding ->
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Nothing here yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(items, key = { it.id }) { entry ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(entry.id) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PosterImage(
                        posterUrl = Tmdb.posterUrl(entry.posterPath, "w185"),
                        mediaType = entry.mediaType,
                        modifier = Modifier.size(width = 48.dp, height = 72.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            buildString {
                                append(entry.title)
                                if (entry.year != null) append("  ·  ${entry.year}")
                            },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            formatListDate(entry.watchedAt),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    RatingPill(entry.rating)
                }
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun Highlight(label: String, entry: Entry, onClick: () -> Unit) {
    Text(
        label.uppercase(),
        fontSize = 12.sp,
        letterSpacing = 0.8.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 28.dp, bottom = 10.dp),
    )
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PosterImage(
            posterUrl = Tmdb.posterUrl(entry.posterPath, "w185"),
            mediaType = entry.mediaType,
            modifier = Modifier.size(width = 48.dp, height = 72.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                buildString {
                    append(entry.title)
                    if (entry.year != null) append("  ·  ${entry.year}")
                },
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        RatingPill(entry.rating)
    }
}
