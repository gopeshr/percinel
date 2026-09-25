package gopesh.percinel.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gopesh.percinel.data.Insights
import kotlinx.coroutines.launch
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

/**
 * The year in review — a single card designed to be readable as a shared image, plus the
 * supporting detail underneath. Everything comes from local data; nothing is uploaded.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilmYearScreen(
    years: List<Int>,
    yearOf: (Int) -> Insights.FilmYear,
    name: String,
    needsEnrich: Boolean,
    onEnrich: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var year by remember { mutableStateOf(years.firstOrNull() ?: 0) }
    val fy = remember(year) { yearOf(year) }
    val graphicsLayer = rememberShareLayer()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Your film year", fontWeight = FontWeight.Bold) },
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
        Column(
            Modifier.fillMaxSize().padding(padding).then(rememberBouncy())
                .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        ) {
            if (years.size > 1) {
                Row(
                    Modifier.padding(top = 4.dp, bottom = 16.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    years.forEach { y ->
                        FilterChip(
                            selected = y == year,
                            onClick = { year = y },
                            label = { Text(y.toString()) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        )
                    }
                }
            } else {
                Spacer(Modifier.height(8.dp))
            }

            if (fy.isEmpty) {
                Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "Nothing logged in $year yet",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                return@Column
            }

            Shareable(graphicsLayer) { FilmYearCard(fy, name) }

            if (needsEnrich) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Hours and genres are incomplete", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(
                            "percinel needs to look up runtimes and genres for your films once. " +
                                "It happens on your device and only fetches what's missing.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        OutlinedButton(
                            onClick = onEnrich,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.padding(top = 10.dp),
                        ) { Text("Analyse my library") }
                    }
                }
            }

            Box(Modifier.padding(top = 12.dp)) {
                ShareMenu(
                    layer = graphicsLayer,
                    fileName = "percinel-$year",
                    caption = "My $year in film — logged with percinel",
                    chooserTitle = "Share your film year",
                )
            }

            // Supporting detail that doesn't belong on the shareable card.
            if (fy.topDirectors.isNotEmpty()) {
                InsightLabel("Directors you watched most")
                TopListCard(fy.topDirectors)
            }
            if (fy.topActors.isNotEmpty()) {
                InsightLabel("On screen most")
                TopListCard(fy.topActors)
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** The shareable card itself — self-contained so it renders identically on screen and as a PNG. */
@Composable
private fun FilmYearCard(fy: Insights.FilmYear, name: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Wordmark(fontSize = 15.sp)
                Spacer(Modifier.width(8.dp))
                if (name.isNotBlank()) {
                    Text(
                        "· $name",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                "${fy.year} in film",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 14.dp),
            )

            Row(Modifier.padding(top = 18.dp)) {
                BigStat(fy.films.toString(), if (fy.films == 1) "film" else "films", Modifier.weight(1f))
                BigStat(
                    if (fy.minutes > 0) "${fy.hours}h" else "—",
                    "watched",
                    Modifier.weight(1f),
                )
                BigStat(
                    fy.avgRating?.let { String.format(Locale.US, "%.1f", it) } ?: "—",
                    "average",
                    Modifier.weight(1f),
                )
            }

            fy.topGenres.firstOrNull()?.let { (genre, count) ->
                CardLine("Most watched", "$genre · $count ${if (count == 1) "film" else "films"}")
            }
            fy.best?.let { CardLine("Your best", "${it.title} · ${formatRating(it.rating)}") }
            fy.busiestMonth?.let { (month, count) ->
                val label = Month.of(month).getDisplayName(TextStyle.FULL, Locale.getDefault())
                CardLine("Busiest month", "$label · $count ${if (count == 1) "watch" else "watches"}")
            }

            val extras = buildList {
                if (fy.movies > 0) add("${fy.movies} ${if (fy.movies == 1) "movie" else "movies"}")
                if (fy.series > 0) add("${fy.series} series")
                if (fy.rewatches > 0) add("${fy.rewatches} ${if (fy.rewatches == 1) "rewatch" else "rewatches"}")
                if (fy.longestStreak > 1) add("${fy.longestStreak}-day streak")
            }
            if (extras.isNotEmpty()) {
                Text(
                    extras.joinToString("  ·  "),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun BigStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CardLine(label: String, value: String) {
    Column(Modifier.padding(top = 14.dp)) {
        Text(
            label.uppercase(),
            fontSize = 10.sp,
            letterSpacing = 0.8.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
