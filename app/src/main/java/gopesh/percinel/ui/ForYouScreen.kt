package gopesh.percinel.ui

import gopesh.percinel.data.Entry
import gopesh.percinel.data.Recommendation
import gopesh.percinel.data.Recommender
import gopesh.percinel.data.Repo
import gopesh.percinel.data.STATUS_WATCHLIST
import gopesh.percinel.data.SearchResult
import gopesh.percinel.data.Tmdb
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForYouScreen(repo: Repo, onMenu: () -> Unit) {
    val scope = rememberCoroutineScope()
    var recs by remember { mutableStateOf<List<Recommendation>?>(null) }
    var added by remember { mutableStateOf(setOf<Long>()) }
    var detail by remember { mutableStateOf<SearchResult?>(null) }

    LaunchedEffect(Unit) {
        recs = Recommender.forYou(repo)
    }

    fun addToWatchlist(item: SearchResult) {
        added = added + item.tmdbId
        scope.launch {
            withContext(Dispatchers.IO) {
                repo.insert(
                    Entry(
                        id = 0,
                        tmdbId = item.tmdbId,
                        mediaType = item.mediaType,
                        title = item.title,
                        posterPath = item.posterPath,
                        year = item.year,
                        rating = 0.0,
                        watchedAt = System.currentTimeMillis(),
                        notes = null,
                        status = STATUS_WATCHLIST,
                    ),
                )
            }
        }
    }

    // Details sub-screen for a tapped recommendation.
    val current = detail
    if (current != null) {
        BackHandler { detail = null }
        TitleDetailScreen(
            item = current,
            onBack = { detail = null },
            added = current.tmdbId in added,
            onAddWatchlist = { addToWatchlist(current) },
        )
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("For you", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onMenu) { Icon(Icons.Default.Menu, contentDescription = "Menu") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { padding ->
        val list = recs
        when {
            list == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Finding films for you…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            list.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No suggestions yet", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    Text(
                        "Rate a few films you loved, and we'll suggest more.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp, start = 32.dp, end = 32.dp),
                    )
                }
            }
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding).then(rememberBouncy()), contentPadding = PaddingValues(vertical = 8.dp)) {
                items(list, key = { it.item.tmdbId }) { rec ->
                    val isAdded = rec.item.tmdbId in added
                    Row(
                        Modifier
                            .clickable { detail = rec.item }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        PosterImage(
                            posterUrl = Tmdb.posterUrl(rec.item.posterPath, "w185"),
                            mediaType = rec.item.mediaType,
                            modifier = Modifier.size(width = 56.dp, height = 84.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                buildString {
                                    append(rec.item.title)
                                    if (rec.item.year != null) append("  ·  ${rec.item.year}")
                                },
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                rec.reason,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        if (isAdded) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "Added to watchlist",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            TextButton(onClick = { addToWatchlist(rec.item) }) {
                                Text("+ Watchlist", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }
    }
}
