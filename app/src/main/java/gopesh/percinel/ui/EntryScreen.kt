package gopesh.percinel.ui

import gopesh.percinel.data.Entry
import gopesh.percinel.data.Repo
import gopesh.percinel.data.Tmdb
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryScreen(
    repo: Repo,
    id: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onAbout: () -> Unit,
    onFindTmdb: () -> Unit,
    onLogAnother: (() -> Unit)? = null,
    onOpenViewing: (Long) -> Unit = {},
) {
    var entry by remember { mutableStateOf<Entry?>(null) }
    var others by remember { mutableStateOf<List<Entry>>(emptyList()) }

    LaunchedEffect(id) {
        val e = withContext(Dispatchers.IO) { repo.get(id) }
        if (e == null) {
            onBack()
        } else {
            entry = e
            others = withContext(Dispatchers.IO) { repo.viewingsFor(e).filter { it.id != e.id } }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = onEdit) {
                        Text("Edit", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { padding ->
        val e = entry
        if (e == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }

        Column(
            Modifier.fillMaxSize().padding(padding).then(rememberBouncy()).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        ) {
            Row(Modifier.padding(top = 4.dp)) {
                PosterImage(
                    posterUrl = Tmdb.posterUrl(e.posterPath, "w342"),
                    mediaType = e.mediaType,
                    modifier = Modifier.size(width = 84.dp, height = 126.dp),
                )
                Column(Modifier.padding(start = 14.dp)) {
                    Text(e.title, fontSize = 21.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text(
                        buildString {
                            append(if (e.mediaType == "tv") "Series" else "Movie")
                            if (e.year != null) append(" · ${e.year}")
                            if (e.season != null) append(" · Season ${e.season}")
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            Label("Your rating", top = 28.dp)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    formatRating(e.rating),
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    " / 10",
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
                )
            }

            Label("Watched", top = 24.dp)
            Text("${formatDate(e.watchedAt)} · ${formatTime(e.watchedAt)}", fontSize = 15.sp)

            Label("Your notes", top = 24.dp)
            if (!e.notes.isNullOrBlank()) {
                Text(e.notes, fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground)
            } else {
                Text(
                    "Nothing written yet — tap Edit to add your thoughts.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (onLogAnother != null) {
                OutlinedButton(
                    onClick = onLogAnother,
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Log another watch", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }

            if (others.isNotEmpty()) {
                // Collapsed by default — the header carries the count and a one-line
                // season summary, so expanding is only needed for individual watches.
                var showOthers by remember { mutableStateOf(false) }
                val chevron by animateFloatAsState(if (showOthers) 90f else 0f, label = "chevron")
                Row(
                    Modifier.fillMaxWidth()
                        .clickable(onClickLabel = if (showOthers) "Collapse" else "Expand") {
                            showOthers = !showOthers
                        }
                        .semantics {
                            stateDescription = if (showOthers) "Expanded" else "Collapsed"
                        }
                        .padding(top = 24.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "OTHER WATCHES",
                        fontSize = 12.sp,
                        letterSpacing = 0.8.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "  ·  ${others.size}",
                        fontSize = 12.sp,
                        letterSpacing = 0.8.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "›",
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.rotate(chevron).clearAndSetSemantics {},
                    )
                }
                // For series with seasons logged, group viewings by season so
                // "watched S2 three times" is visible at a glance.
                val useGroups = e.mediaType == "tv" && (e.season != null || others.any { it.season != null })
                Column(Modifier.fillMaxWidth().animateContentSize()) {
                if (!showOthers) {
                    if (useGroups) {
                        val bySeason = others.groupBy { it.season }
                        val summary = buildList {
                            bySeason.keys.filterNotNull().sortedDescending().forEach { s ->
                                val n = bySeason.getValue(s).size
                                add(if (n > 1) "S$s ×$n" else "S$s")
                            }
                            bySeason[null]?.let { add("${it.size} with no season") }
                        }.joinToString("  ·  ")
                        Text(summary, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else if (useGroups) {
                    val bySeason = others.groupBy { it.season }
                    val seasonKeys = bySeason.keys.filterNotNull().sortedDescending() +
                        if (null in bySeason) listOf(null) else emptyList()
                    seasonKeys.forEach { s ->
                        val group = bySeason.getValue(s).sortedByDescending { it.watchedAt }
                        // Count the viewing on this page too, so the header shows the true total.
                        val times = group.size + if (e.season == s) 1 else 0
                        SeasonHeader(
                            title = if (s != null) "Season $s" else "No season noted",
                            times = times,
                        )
                        group.forEach { v -> ViewingRow(v, onOpenViewing) }
                    }
                } else {
                    others.forEach { v -> ViewingRow(v, onOpenViewing) }
                }
                }
            }

            if (e.tmdbId != 0L) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 32.dp).clickable { onAbout() },
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("More about this film", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(
                            "›",
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clearAndSetSemantics {},
                        )
                    }
                }
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 32.dp).clickable { onFindTmdb() },
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Just the basics for now", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text(
                                "Add a poster, synopsis, and cast in a tap",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        Text("Find it ›", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
private fun SeasonHeader(title: String, times: Int) {
    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title.uppercase(),
            fontSize = 11.sp,
            letterSpacing = 0.8.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (times > 1) {
            Text(
                "  ·  watched ${times}×",
                fontSize = 11.sp,
                letterSpacing = 0.8.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ViewingRow(v: Entry, onOpen: (Long) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onOpen(v.id) }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            formatRating(v.rating),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            "  ·  ${formatListDate(v.watchedAt)}",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            "›",
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

@Composable
private fun Label(text: String, top: androidx.compose.ui.unit.Dp) {
    Text(
        text.uppercase(),
        fontSize = 12.sp,
        letterSpacing = 0.8.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = top, bottom = 10.dp),
    )
}
