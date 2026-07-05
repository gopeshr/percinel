package ai.ligaments.percinel.ui

import ai.ligaments.percinel.data.Entry
import ai.ligaments.percinel.data.SearchResult
import ai.ligaments.percinel.data.Tmdb
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddScreen(onCancel: () -> Unit, onSave: (Entry) -> Unit) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    var selected by remember { mutableStateOf<SearchResult?>(null) }
    var rating by remember { mutableStateOf<Double?>(null) }
    var watchedAt by remember { mutableStateOf(System.currentTimeMillis()) }
    var notes by remember { mutableStateOf("") }

    LaunchedEffect(query, selected) {
        if (selected != null) return@LaunchedEffect
        val q = query.trim()
        if (q.length < 2) {
            results = emptyList(); error = null; searching = false
            return@LaunchedEffect
        }
        searching = true; error = null
        delay(300)
        try {
            results = Tmdb.search(q)
        } catch (e: Exception) {
            error = e.message ?: "Search failed"; results = emptyList()
        } finally {
            searching = false
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (selected == null) "Search" else "Log a watch", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { if (selected != null) selected = null else onCancel() }) {
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
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (selected == null) {
                SearchPane(
                    query = query,
                    onQuery = { query = it },
                    results = results,
                    searching = searching,
                    error = error,
                    onPick = { selected = it },
                )
            } else {
                EntryForm(
                    selected = selected!!,
                    rating = rating,
                    onRating = { rating = it },
                    watchedAt = watchedAt,
                    onWatchedAt = { watchedAt = it },
                    notes = notes,
                    onNotes = { notes = it },
                    onChange = { selected = null },
                    saveLabel = "Save entry",
                    onSave = {
                        val r = rating ?: return@EntryForm
                        val s = selected!!
                        onSave(
                            Entry(
                                id = 0,
                                tmdbId = s.tmdbId,
                                mediaType = s.mediaType,
                                title = s.title,
                                posterPath = s.posterPath,
                                year = s.year,
                                rating = r,
                                watchedAt = watchedAt,
                                notes = notes.trim().ifBlank { null },
                            )
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun SearchPane(
    query: String,
    onQuery: (String) -> Unit,
    results: List<SearchResult>,
    searching: Boolean,
    error: String?,
    onPick: (SearchResult) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            placeholder = { Text("Search a movie or series…") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (searching) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                Text("Searching…", modifier = Modifier.padding(start = 10.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp), fontSize = 13.sp)
        }
        LazyColumn {
            items(results, key = { "${it.mediaType}-${it.tmdbId}" }) { r ->
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(r) }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PosterImage(
                        posterUrl = Tmdb.posterUrl(r.posterPath, "w185"),
                        mediaType = r.mediaType,
                        modifier = Modifier.size(width = 50.dp, height = 75.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(r.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            buildString {
                                append(if (r.mediaType == "tv") "Series" else "Movie")
                                if (r.year != null) append(" · ${r.year}")
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EntryForm(
    selected: SearchResult,
    rating: Double?,
    onRating: (Double?) -> Unit,
    watchedAt: Long,
    onWatchedAt: (Long) -> Unit,
    notes: String,
    onNotes: (String) -> Unit,
    onChange: (() -> Unit)?,
    saveLabel: String,
    onSave: () -> Unit,
    extra: (@Composable () -> Unit)? = null,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
    ) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp)) {
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PosterImage(
                    posterUrl = Tmdb.posterUrl(selected.posterPath, "w185"),
                    mediaType = selected.mediaType,
                    modifier = Modifier.size(width = 80.dp, height = 120.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(selected.title, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text(
                        buildString {
                            append(if (selected.mediaType == "tv") "Series" else "Movie")
                            if (selected.year != null) append(" · ${selected.year}")
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    if (onChange != null) {
                        Text(
                            "Change ›",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 10.dp).clickable { onChange() },
                        )
                    }
                }
            }
        }

        SectionLabel("Your rating")
        RatingField(initial = rating, onChange = onRating)

        SectionLabel("Watched on", top = 24.dp)
        DateTimeRow(millis = watchedAt, onChange = onWatchedAt)

        SectionLabel("Notes", top = 24.dp)
        OutlinedTextField(
            value = notes,
            onValueChange = onNotes,
            placeholder = { Text("Any thoughts?") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
        )

        if (extra != null) {
            Box(Modifier.padding(top = 8.dp)) { extra() }
        }

        Button(
            onClick = onSave,
            enabled = rating != null,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 28.dp),
        ) {
            Text(saveLabel, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(vertical = 6.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String, top: androidx.compose.ui.unit.Dp = 0.dp) {
    Text(
        text.uppercase(),
        fontSize = 12.sp,
        letterSpacing = 0.8.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = top, bottom = 10.dp),
    )
}
