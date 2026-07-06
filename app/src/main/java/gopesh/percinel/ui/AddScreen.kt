package gopesh.percinel.ui

import gopesh.percinel.data.Entry
import gopesh.percinel.data.STATUS_WATCHLIST
import gopesh.percinel.data.SearchResult
import gopesh.percinel.data.Tmdb
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddScreen(onCancel: () -> Unit, onSave: (Entry) -> Unit, watchlist: Boolean = false) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var offline by remember { mutableStateOf(false) }

    var selected by remember { mutableStateOf<SearchResult?>(null) }
    var rating by remember { mutableStateOf<Double?>(null) }
    var watchedAt by remember { mutableStateOf(System.currentTimeMillis()) }
    var notes by remember { mutableStateOf("") }

    var manual by remember { mutableStateOf(false) }
    var mTitle by remember { mutableStateOf("") }
    var mType by remember { mutableStateOf("movie") }
    var mYear by remember { mutableStateOf("") }

    LaunchedEffect(query, selected, manual) {
        if (selected != null || manual) return@LaunchedEffect
        val q = query.trim()
        if (q.length < 2) {
            results = emptyList(); error = null; searching = false
            return@LaunchedEffect
        }
        searching = true; error = null; offline = false
        delay(300)
        try {
            results = Tmdb.search(q)
            searching = false
        } catch (e: CancellationException) {
            // A newer keystroke restarted this effect — let cancellation
            // propagate without touching UI state.
            throw e
        } catch (e: Exception) {
            results = emptyList()
            if (e is java.io.IOException) {
                offline = true; error = null
            } else {
                offline = false; error = "Hmm, that didn't work. Give it another try."
            }
            searching = false
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            manual -> "Add manually"
                            watchlist -> "Add to watchlist"
                            selected == null -> "Search"
                            else -> "Add a watch"
                        },
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        when {
                            manual -> manual = false
                            selected != null -> selected = null
                            else -> onCancel()
                        }
                    }) {
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
            if (manual) {
                ManualForm(
                    title = mTitle,
                    onTitle = { mTitle = it },
                    type = mType,
                    onType = { mType = it },
                    year = mYear,
                    onYear = { mYear = it.filter(Char::isDigit).take(4) },
                    rating = rating,
                    onRating = { rating = it },
                    watchedAt = watchedAt,
                    onWatchedAt = { watchedAt = it },
                    notes = notes,
                    onNotes = { notes = it },
                    onSave = {
                        val r = rating ?: return@ManualForm
                        if (mTitle.isBlank()) return@ManualForm
                        onSave(
                            Entry(
                                id = 0,
                                tmdbId = 0,
                                mediaType = mType,
                                title = mTitle.trim(),
                                posterPath = null,
                                year = mYear.toIntOrNull(),
                                rating = r,
                                watchedAt = watchedAt,
                                notes = notes.trim().ifBlank { null },
                            )
                        )
                    },
                )
            } else if (selected == null) {
                SearchPane(
                    query = query,
                    onQuery = { query = it },
                    results = results,
                    searching = searching,
                    error = error,
                    offline = offline,
                    allowManual = !watchlist,
                    onPick = { r ->
                        if (watchlist) {
                            onSave(
                                Entry(
                                    id = 0,
                                    tmdbId = r.tmdbId,
                                    mediaType = r.mediaType,
                                    title = r.title,
                                    posterPath = r.posterPath,
                                    year = r.year,
                                    rating = 0.0,
                                    watchedAt = System.currentTimeMillis(),
                                    notes = null,
                                    status = STATUS_WATCHLIST,
                                )
                            )
                        } else {
                            selected = r
                        }
                    },
                    onAddManual = {
                        if (query.isNotBlank()) mTitle = query.trim()
                        manual = true
                    },
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
                    saveLabel = "Save",
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
    offline: Boolean,
    allowManual: Boolean,
    onPick: (SearchResult) -> Unit,
    onAddManual: () -> Unit,
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
        if (allowManual) {
            Row(
                Modifier.fillMaxWidth().clickable { onAddManual() }.padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Can't find it? ", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Text("Add manually", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
        if (searching) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                Text("Searching…", modifier = Modifier.padding(start = 10.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (offline) {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("You're offline", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(
                    "No worries — jot it down yourself above. You can dress it up with a poster and cast whenever you're back online.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
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
            placeholder = { Text("Any thoughts? Tap the mic to dictate.") },
            trailingIcon = {
                DictationButton(onText = { spoken ->
                    onNotes(listOf(notes.trim(), spoken).filter { it.isNotEmpty() }.joinToString(" "))
                })
            },
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
private fun ManualForm(
    title: String,
    onTitle: (String) -> Unit,
    type: String,
    onType: (String) -> Unit,
    year: String,
    onYear: (String) -> Unit,
    rating: Double?,
    onRating: (Double?) -> Unit,
    watchedAt: Long,
    onWatchedAt: (Long) -> Unit,
    notes: String,
    onNotes: (String) -> Unit,
    onSave: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
    ) {
        SectionLabel("Title")
        OutlinedTextField(
            value = title,
            onValueChange = onTitle,
            placeholder = { Text("e.g. My wedding video") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        SectionLabel("Type", top = 24.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = type == "movie",
                onClick = { onType("movie") },
                label = { Text("Movie") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
            FilterChip(
                selected = type == "tv",
                onClick = { onType("tv") },
                label = { Text("Series") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }

        SectionLabel("Year (optional)", top = 24.dp)
        OutlinedTextField(
            value = year,
            onValueChange = onYear,
            placeholder = { Text("e.g. 2024") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(140.dp),
        )

        SectionLabel("Your rating", top = 24.dp)
        RatingField(initial = rating, onChange = onRating)

        SectionLabel("Watched on", top = 24.dp)
        DateTimeRow(millis = watchedAt, onChange = onWatchedAt)

        SectionLabel("Notes", top = 24.dp)
        OutlinedTextField(
            value = notes,
            onValueChange = onNotes,
            placeholder = { Text("Any thoughts? Tap the mic to dictate.") },
            trailingIcon = {
                DictationButton(onText = { spoken ->
                    onNotes(listOf(notes.trim(), spoken).filter { it.isNotEmpty() }.joinToString(" "))
                })
            },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
        )

        Button(
            onClick = onSave,
            enabled = title.isNotBlank() && rating != null,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 28.dp),
        ) {
            Text("Save", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(vertical = 6.dp))
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
