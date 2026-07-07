package gopesh.percinel.ui

import gopesh.percinel.data.Entry
import gopesh.percinel.data.Repo
import gopesh.percinel.data.STATUS_WATCHED
import gopesh.percinel.data.SearchResult
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Log another watch (a rewatch) of a film already in the diary — inserts a new viewing that
 *  groups under the same film. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddViewingScreen(repo: Repo, id: Long, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var film by remember { mutableStateOf<Entry?>(null) }
    var rating by remember { mutableStateOf<Double?>(null) }
    var watchedAt by remember { mutableStateOf(System.currentTimeMillis()) }
    var notes by remember { mutableStateOf("") }
    var season by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(id) {
        val e = withContext(Dispatchers.IO) { repo.get(id) }
        if (e == null) onDone() else film = e
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Log another watch", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
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
        val f = film
        if (f == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }
        Box(Modifier.padding(padding)) {
            EntryForm(
                selected = SearchResult(
                    tmdbId = f.tmdbId,
                    mediaType = f.mediaType,
                    title = f.title,
                    year = f.year,
                    posterPath = f.posterPath,
                    overview = "",
                ),
                rating = rating,
                onRating = { rating = it },
                watchedAt = watchedAt,
                onWatchedAt = { watchedAt = it },
                notes = notes,
                onNotes = { notes = it },
                onChange = null,
                saveLabel = "Save this watch",
                season = season,
                onSeason = { season = it },
                onSave = {
                    val r = rating ?: return@EntryForm
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            repo.insert(
                                Entry(
                                    id = 0,
                                    tmdbId = f.tmdbId,
                                    mediaType = f.mediaType,
                                    title = f.title,
                                    posterPath = f.posterPath,
                                    year = f.year,
                                    rating = r,
                                    watchedAt = watchedAt,
                                    notes = notes.trim().ifBlank { null },
                                    status = STATUS_WATCHED,
                                    season = if (f.mediaType == "tv") season else null,
                                ),
                            )
                        }
                        onDone()
                    }
                },
            )
        }
    }
}
